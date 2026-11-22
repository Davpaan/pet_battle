#!/usr/bin/env python3
"""Generador de los spritesheets de pájaros en alta resolución.

Cada hoja tiene 4x4 frames de FRAME px (filas: vuelo, reacción, reposo, salto), igual que
BirdSheet en PetSpeciesCatalog.kt. Todo se diseña en una rejilla lógica de 64 unidades por
frame (la misma que usaban los sprites antiguos) y se rasteriza con supermuestreo, campos de
distancia con signo (SDF) para bordes suaves y un sombreado volumétrico sencillo.

Uso:
    python3 tools/bird_sprites/generate_birds.py OUT_DIR [especie ...]
"""
import math
import os
import sys

import numpy as np
from scipy import ndimage

from PIL import Image

FRAME = 256          # lado de un frame en la hoja final
SS = 2               # supermuestreo
P = FRAME * SS       # lado del lienzo de trabajo
GRID = 64.0          # unidades lógicas por frame
K = P / GRID         # píxeles de trabajo por unidad lógica
GROUND = 62.6        # línea del suelo (unidades)

_ys, _xs = np.mgrid[0:P, 0:P].astype(np.float32)
X = (_xs + 0.5) / K
Y = (_ys + 0.5) / K

LIGHT = np.array([-0.45, -0.75, 0.55], np.float32)
LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1], np.float32)
HALF /= np.linalg.norm(HALF)


# ---------------------------------------------------------------------------
# Utilidades de color y máscaras

def C(r, g, b):
    return np.array([r, g, b], np.float32) / 255.0


def S(e0, e1, x):
    """smoothstep (admite e0 > e1 para invertir la transición)."""
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t, np.float32)[..., None]
    return a * (1 - t) + b * t


def full(c):
    return np.broadcast_to(c, (P, P, 3)).astype(np.float32)


def dist_seg(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    t = np.clip(((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy), 0, 1)
    return np.hypot(px - ax - vx * t, py - ay - vy * t)


def _noise_tex(seed, size=256, sigma=3.0):
    rng = np.random.default_rng(seed)
    t = ndimage.gaussian_filter(rng.random((size, size)).astype(np.float32), sigma, mode="wrap")
    t -= t.min()
    t /= t.max()
    return t


NOISE_FINE = _noise_tex(1, sigma=1.6)
NOISE_MED = _noise_tex(2, sigma=4.0)


def sample(tex, lx, ly, scale):
    """Muestrea una textura periódica en coordenadas locales (unidades lógicas)."""
    n = tex.shape[0]
    return ndimage.map_coordinates(tex, [(ly * scale) % n, (lx * scale) % n], order=1, mode="wrap")


def dots(lx, ly, spacing, radius, seed, jitter=0.35, stretch=1.0):
    """Máscara de motas con reparto aleatorio en rejilla (coordenadas locales)."""
    gx = np.floor(lx / spacing)
    gy = np.floor(ly / spacing)
    h1 = np.sin(gx * 127.1 + gy * 311.7 + seed * 17.0) * 43758.5453
    h2 = np.sin(gx * 269.5 + gy * 183.3 + seed * 31.0) * 43758.5453
    h3 = np.sin(gx * 419.2 + gy * 371.9 + seed * 7.0) * 43758.5453
    jx = (h1 - np.floor(h1) - 0.5) * jitter * spacing
    jy = (h2 - np.floor(h2) - 0.5) * jitter * spacing
    r = radius * (0.7 + 0.5 * (h3 - np.floor(h3)))
    cx = (gx + 0.5) * spacing + jx
    cy = (gy + 0.5) * spacing + jy
    d = np.hypot(lx - cx, (ly - cy) / stretch)
    return S(r + 0.12, r - 0.12, d)


# ---------------------------------------------------------------------------
# Campos de distancia (en unidades lógicas, negativos dentro)

def sd_circle(cx, cy, r):
    return np.hypot(X - cx, Y - cy) - r


def sd_ellipse(cx, cy, rx, ry, ang=0.0):
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    dx, dy = X - cx, Y - cy
    u = dx * ca + dy * sa
    v = -dx * sa + dy * ca
    k0 = np.hypot(u / rx, v / ry)
    k1 = np.hypot(u / (rx * rx), v / (ry * ry))
    return k0 * (k0 - 1) / np.maximum(k1, 1e-6)


def sd_capsule(ax, ay, bx, by, ra, rb):
    vx, vy = bx - ax, by - ay
    t = np.clip(((X - ax) * vx + (Y - ay) * vy) / (vx * vx + vy * vy + 1e-9), 0, 1)
    return np.hypot(X - ax - vx * t, Y - ay - vy * t) - (ra + (rb - ra) * t)


def sd_poly(pts):
    pts = [(float(a), float(b)) for a, b in pts]
    x0, y0 = pts[0]
    d = (X - x0) ** 2 + (Y - y0) ** 2
    s = np.ones_like(X)
    n = len(pts)
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[i - 1]
        ex, ey = xj - xi, yj - yi
        wx, wy = X - xi, Y - yi
        t = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey + 1e-9), 0, 1)
        bx, by = wx - ex * t, wy - ey * t
        d = np.minimum(d, bx * bx + by * by)
        c1 = Y >= yi
        c2 = Y < yj
        c3 = ex * wy > ey * wx
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        s = np.where(flip, -s, s)
    return s * np.sqrt(d)


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


# ---------------------------------------------------------------------------
# Lienzo con composición premultiplicada

class Canvas:
    def __init__(self):
        self.rgb = np.zeros((P, P, 3), np.float32)
        self.a = np.zeros((P, P), np.float32)

    @staticmethod
    def cover(sdf):
        return np.clip(0.5 - sdf * K, 0.0, 1.0)

    def over(self, color, cov):
        c3 = cov[..., None]
        self.rgb = color * c3 + self.rgb * (1 - c3)
        self.a = cov + self.a * (1 - cov)

    def part(self, sdf, color, outline=0.4, dark=0.3, shade=None, spec=None):
        """Pinta una pieza: primero su contorno (versión oscura de su color) y luego el relleno."""
        color = full(color) if color.ndim == 1 else color
        if outline > 0:
            self.over(color * dark, self.cover(sdf - outline))
        c = color if shade is None else color * shade[..., None]
        if spec is not None:
            c = c + spec
        self.over(np.clip(c, 0, 1), self.cover(sdf))

    def shadow(self, sdf, dx, dy, blur, strength):
        """Sombra suave proyectada sobre lo ya pintado (no añade opacidad)."""
        sh = np.roll(sdf, (int(round(dy * K)), int(round(dx * K))), axis=(0, 1))
        k = S(blur, -blur, sh) * strength
        self.rgb *= (1 - k)[..., None]

    def image(self):
        rgb = self.rgb.reshape(FRAME, SS, FRAME, SS, 3).mean(axis=(1, 3))
        a = self.a.reshape(FRAME, SS, FRAME, SS).mean(axis=(1, 3))
        out = np.zeros((FRAME, FRAME, 4), np.float32)
        nz = a > 1e-4
        out[..., :3][nz] = rgb[nz] / a[nz][:, None]
        out[..., 3] = a
        return Image.fromarray(np.clip(out * 255 + 0.5, 0, 255).astype(np.uint8), "RGBA")


def volume_shade(sdf, thick, gloss=0.0):
    """Sombreado volumétrico a partir del SDF: devuelve (factor difuso, especular)."""
    h = 1 - (1 - np.clip(-sdf / thick, 0, 1)) ** 2
    gy, gx = np.gradient(h)
    s = K * thick * 0.55
    nx, ny = -gx * s, -gy * s
    inv = 1.0 / np.sqrt(nx * nx + ny * ny + 1)
    nx, ny, nz = nx * inv, ny * inv, inv
    diff = np.clip(nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2], 0, 1)
    shade = 0.6 + 0.52 * diff
    spec = None
    if gloss > 0:
        sp = np.clip(nx * HALF[0] + ny * HALF[1] + nz * HALF[2], 0, 1) ** 18 * gloss
        spec = sp
    return shade, spec


# ---------------------------------------------------------------------------
# Plumas

def feather_color(spec, t, lat, sdf):
    """Color de una pluma: t va de la base (0) a la punta (1), lat es la posición lateral."""
    c = full(spec["base"])
    if "root" in spec:
        rc, rl = spec["root"]
        c = mix(c, full(rc), S(rl + 0.04, rl - 0.04, t))
    if "bars" in spec:
        bc, freq, duty = spec["bars"]
        ph = (t * freq) % 1.0
        c = mix(c, full(bc), S(duty + 0.06, duty - 0.06, ph) * S(0.05, 0.12, t))
    if "band" in spec:
        bc, b0, b1 = spec["band"]
        c = mix(c, full(bc), S(b0 - 0.03, b0 + 0.03, t) * S(b1 + 0.03, b1 - 0.03, t))
    if "tip" in spec:
        tc, tl = spec["tip"]
        c = mix(c, full(tc), S(1 - tl - 0.04, 1 - tl + 0.04, t))
    if "outer" in spec:
        # Borde exterior (lado de ataque) de otro color, p. ej. primarias de la urraca.
        oc, ow = spec["outer"]
        c = mix(c, full(oc), S(-ow + 0.15, -ow - 0.15, lat))
    if spec.get("fringe") is not None:
        fw = spec.get("fringe_w", 0.32)
        c = mix(c, full(spec["fringe"]), S(-fw, -fw * 0.3, sdf) * 0.85)
    if spec.get("shaft") is not None:
        c = mix(c, full(spec["shaft"]), S(0.16, 0.06, np.abs(lat)) * S(0.0, 0.1, t) * 0.6)
    return c


def feather(cv, base, tip, w, spec, dark=1.0, outline=0.28, light_side=1.0, acc=None, shade_k=0.22):
    bx, by = base
    tx, ty = tip
    cx, cy = (bx + tx) / 2, (by + ty) / 2
    L = math.hypot(tx - bx, ty - by)
    ang = math.atan2(ty - by, tx - bx)
    ca, sa = math.cos(ang), math.sin(ang)
    sd = sd_ellipse(cx, cy, L / 2, w / 2, math.degrees(ang))
    cu = ((X - cx) * ca + (Y - cy) * sa) / (L / 2)
    lat = (-(X - cx) * sa + (Y - cy) * ca) / (w / 2)
    t = (cu + 1) / 2
    col = feather_color(spec, t, lat, sd) * dark
    shade = np.clip(0.92 - shade_k * lat * light_side, 0.6, 1.15) * (0.86 + 0.16 * t)
    cv.part(sd, col, outline=outline, dark=0.32, shade=shade)
    if acc is not None:
        acc.append(sd)
    return sd


def rot(vx, vy, deg):
    a = math.radians(deg)
    return vx * math.cos(a) - vy * math.sin(a), vx * math.sin(a) + vy * math.cos(a)


def covert_patch(cv, cx, cy, rx, ry, ang, color_fn, dark=1.0, scallop=6, acc=None, outline=0.3):
    """Mancha de coberteras con el borde inferior festoneado (filas de plumas)."""
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    s = (((X - cx) * ca + (Y - cy) * sa) / rx + 1) / 2
    w = (-(X - cx) * sa + (Y - cy) * ca) / ry
    sd = sd_ellipse(cx, cy, rx, ry, ang)
    if scallop:
        sd = sd + np.where(w > 0, 0.45 * (np.abs(np.sin(s * math.pi * scallop)) - 0.5), 0.0)
    col = color_fn(s, w) * dark
    shade = np.clip(0.98 - 0.16 * w, 0.7, 1.12)
    cv.part(sd, col, outline=outline, dark=0.32, shade=shade)
    if acc is not None:
        acc.append(sd)
    return sd


def as_fn(c):
    if callable(c):
        return c
    return lambda s, w: full(c)


# ---------------------------------------------------------------------------
# Alas

def folded_wing(cv, sp, sh, tip, dark=1.0):
    """Ala plegada desde el hombro `sh` hasta la punta `tip`."""
    wg = sp["wing"]
    sx, sy = sh
    ex, ey = tip
    L = math.hypot(ex - sx, ey - sy)
    dx, dy = (ex - sx) / L, (ey - sy) / L
    qx, qy = -dy, dx            # perpendicular hacia el vientre
    ww = sp["wing_w"]
    acc = []

    def pt(t, q):
        return sx + dx * L * t + qx * q, sy + dy * L * t + qy * q

    if wg.get("stub"):
        sd = sd_ellipse(*pt(0.32, ww * 0.35), L * 0.3, ww * 0.42, math.degrees(math.atan2(dy, dx)) + 12)
        col = as_fn(wg["cov"])(0.5 * np.ones_like(X), np.zeros_like(X))
        shade, _ = volume_shade(sd, 2.5)
        cv.part(sd * 1.0, col * dark, outline=0.35, shade=shade)
        return [sd]

    # Primarias: forman la punta del ala, por debajo del resto.
    n = wg.get("n_pri", 5)
    for i in range(n):
        k = i / max(n - 1, 1)
        b = pt(0.38 + 0.04 * k, ww * (0.55 - 0.75 * k))
        t = pt(1.0 - 0.07 * k, ww * (0.28 - 0.42 * k))
        feather(cv, b, t, ww * 0.42, wg["pri"], dark, acc=acc)
    # Secundarias: panel inferior del ala plegada.
    for i in range(4):
        k = i / 3
        b = pt(0.14 + 0.05 * k, ww * (0.75 - 0.45 * k))
        t = pt(0.7 - 0.06 * k, ww * (0.62 - 0.5 * k))
        feather(cv, b, t, ww * 0.5, wg["sec"], dark, acc=acc)
    # Terciarias: encima, cubren la base de las primarias.
    for i in range(2):
        k = i
        b = pt(0.26 + 0.04 * k, ww * (-0.05 - 0.2 * k))
        t = pt(0.82 - 0.08 * k, ww * (0.1 - 0.25 * k))
        feather(cv, b, t, ww * 0.48, wg.get("ter", wg["sec"]), dark, acc=acc)
    # Coberteras mayores y menores.
    ang = math.degrees(math.atan2(dy, dx))
    covert_patch(cv, *pt(0.3, ww * 0.18), L * 0.27, ww * 0.42, ang, as_fn(wg["cov"]), dark, acc=acc)
    covert_patch(cv, *pt(0.2, -ww * 0.2), L * 0.2, ww * 0.22, ang,
                 as_fn(wg.get("cov2", sp["back"])), dark, scallop=4, acc=acc, outline=0.15)
    return acc


def open_wing(cv, sp, sh, ang, dark=1.0, span=1.0):
    """Ala extendida: el brazo sale del hombro con ángulo `ang` (grados, pantalla)."""
    wg = sp["wing"]
    sx, sy = sh
    sz = sp["size"] * span
    dx, dy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    qx, qy = -dy, dx            # lado del borde de salida
    AL = 10.0 * sz * wg.get("arm", 1.0)
    acc = []

    if wg.get("stub"):
        L = 6.5 * sz
        cx, cy = sx + dx * L * 0.5 + qx * 1.0, sy + dy * L * 0.5 + qy * 1.0
        sd = sd_ellipse(cx, cy, L * 0.55, 2.6 * sz, ang)
        col = as_fn(wg["cov"])(0.5 * np.ones_like(X), np.zeros_like(X))
        shade, _ = volume_shade(sd, 2.5)
        cv.part(sd, col * dark, outline=0.35, shade=shade)
        return [sd]

    def arm(t):
        return sx + dx * AL * t, sy + dy * AL * t

    pl = wg.get("pri_len", 1.0)
    n = wg.get("n_pri", 6)
    # Primarias (de la punta hacia dentro).
    for i in range(n):
        k = i / max(n - 1, 1)
        b = arm(1.0 - 0.42 * k)
        rx, ry = rot(dx, dy, 12 + 48 * k)
        ln = (12.5 - 3.5 * k) * sz * pl
        feather(cv, b, (b[0] + rx * ln, b[1] + ry * ln), 2.7 * sz, wg["pri"], dark, acc=acc)
    # Secundarias a lo largo del brazo.
    ns = wg.get("n_sec", 6)
    for i in range(ns):
        k = i / max(ns - 1, 1)
        b = arm(0.55 - 0.47 * k)
        rx, ry = rot(qx, qy, -18 + 6 * k)
        ln = (8.0 - 0.8 * k) * sz * wg.get("sec_len", 1.0)
        feather(cv, b, (b[0] + rx * ln, b[1] + ry * ln), 3.0 * sz, wg["sec"], dark, acc=acc)
    # Coberteras.
    cx, cy = sx + dx * AL * 0.5 + qx * 2.0 * sz, sy + dy * AL * 0.5 + qy * 2.0 * sz
    covert_patch(cv, cx, cy, AL * 0.56, 2.9 * sz, ang, as_fn(wg["cov"]), dark, acc=acc)
    cx, cy = sx + dx * AL * 0.42 + qx * 0.6 * sz, sy + dy * AL * 0.42 + qy * 0.6 * sz
    covert_patch(cv, cx, cy, AL * 0.42, 1.5 * sz, ang, as_fn(wg.get("cov2", sp["back"])), dark,
                 scallop=4, acc=acc, outline=0.15)
    return acc


# ---------------------------------------------------------------------------
# Pájaro completo

def render(sp, pose):
    cv = Canvas()
    sz = sp["size"]
    rx, ry = sp["rx"] * sz, sp["ry"] * sz
    hr = sp["head_r"] * sz
    leg_len = sp["leg_len"] * sz
    flying = pose.get("legs") == "tuck"

    by = GROUND - leg_len - ry * 0.95 + pose.get("dy", 0)
    bx = sp["body_x"] + pose.get("dx", 0)
    tilt = pose.get("tilt", sp["tilt"])
    ta = math.radians(tilt)
    ax_, ay_ = math.cos(ta), math.sin(ta)
    nx_, ny_ = -ay_, ax_
    hox, hoy = sp["head_off"]
    HX = bx + hox * sz + pose.get("hdx", 0)
    HY = by + hoy * sz + pose.get("hdy", 0)

    def bpt(u, v):
        return bx + ax_ * rx * u + nx_ * ry * v, by + ay_ * rx * u + ny_ * ry * v

    # Coordenadas locales para los patrones de color.
    dxw, dyw = X - bx, Y - by
    u = (dxw * ax_ + dyw * ay_) / rx
    v = (dxw * nx_ + dyw * ny_) / ry
    hx = (X - HX) / hr
    hy = (Y - HY) / hr

    # --- Silueta del cuerpo (cuerpo + cabeza fundidos: cuello continuo) ---
    # Cuerpo en forma de lágrima: pecho redondo que se estrecha hacia la cola.
    cx_, cy_ = bpt(-0.18, 0.04)
    chest = sd_ellipse(cx_, cy_, rx * 0.8, ry, tilt)
    r0x, r0y = bpt(0.15, -0.02)
    r1x, r1y = bpt(0.98, -0.12)
    rear = sd_capsule(r0x, r0y, r1x, r1y, ry * 0.82, ry * 0.32)
    d_body = sd_ellipse(bx, by, rx, ry, tilt) if sp.get("round") else smin(chest, rear, 2.5 * sz)
    d_head = sd_circle(HX, HY, hr)
    torso = smin(d_body, d_head, sp["neck_k"] * sz)
    crest = sp.get("crest", 0)
    if crest > 0:
        ch = (crest + pose.get("crest", 0)) * sz
        lean = sp.get("crest_lean", 0.45)
        tipx, tipy = HX + hr * lean + ch * 0.35, HY - hr * 0.7 - ch
        pts = [
            (HX - hr * 0.7, HY - hr * 0.45),
            (HX - hr * 0.42, HY - hr * 0.88 - ch * 0.25),
            (HX - hr * 0.1 + ch * 0.12, HY - hr * 0.8 - ch * 0.55),
            (tipx, tipy),
            (HX + hr * 0.5 + ch * 0.2, HY - hr * 0.72 - ch * 0.42),
            (HX + hr * 0.85, HY - hr * 0.35),
            (HX, HY),
        ]
        torso = smin(torso, sd_poly(pts), 1.2 * sz)
    if sp.get("fluffy"):
        # Borde ligeramente plumoso.
        torso = torso + (sample(NOISE_FINE, X, Y, 14.0) - 0.5) * 0.45

    hw = S(-2.0 * sz, 2.0 * sz, d_body - d_head)
    lx = mix_scalar(u * rx, hx * hr + 40.0, hw)
    ly = mix_scalar(v * ry, hy * hr + 40.0, hw)
    ctx = dict(u=u, v=v, hx=hx, hy=hy, hw=hw, lx=lx, ly=ly, hr=hr, rx=rx, ry=ry,
               bu=u * rx, bv=v * ry, hu=hx * hr, hv=hy * hr)

    shoulder_folded = bpt(-0.42, -0.55)
    shoulder_open = bpt(-0.2, -0.62)
    wing = pose.get("wing")
    wing_dark = sp.get("far_dark", 0.62)

    # --- Ala lejana (solo con el ala abierta) ---
    if wing is not None:
        far_sh = (shoulder_open[0] + 1.4 * sz, shoulder_open[1] - 0.8 * sz)
        open_wing(cv, sp, far_sh, wing + 14, dark=wing_dark, span=0.92)

    # --- Cola ---
    tbx, tby = bpt(0.78, -0.05)
    tang = pose.get("tail", sp["tail_ang"])
    tl = sp["tail_len"] * sz
    tw = sp["tail_w"] * sz
    nt = sp.get("tail_n", 5)
    spread = sp.get("tail_spread", 7)
    tail_spec = sp["tail"]
    for i in range(nt):
        k = i / max(nt - 1, 1) - 0.5
        a = math.radians(tang + spread * k)
        ln = tl * (1 - 0.06 * abs(k) * 2) * (1 - sp.get("tail_fork", 0) * (0.5 - abs(k)))
        if abs(k) < 1e-6:
            ln *= 1 + sp.get("tail_streamer", 0)  # plumas centrales largas (abejaruco)
        ox, oy = -math.sin(a) * k * tw * 0.4, math.cos(a) * k * tw * 0.4
        b = (tbx - math.cos(a) * 2 + ox, tby - math.sin(a) * 2 + oy)
        t = (tbx + math.cos(a) * ln + ox, tby + math.sin(a) * ln + oy)
        d = 0.75 + 0.25 * (k + 0.5)
        w_ = tw * (0.6 if abs(k) < 1e-6 and sp.get("tail_streamer") else 1.0)
        feather(cv, b, t, w_, tail_spec, dark=d, light_side=-1, shade_k=0.15)

    # --- Patas ---
    legs = pose.get("legs", "stand")
    if legs != "tuck":
        leg_c = sp["leg"]
        for j, off in enumerate(sp.get("leg_xs", (-3.5, 0.6))):
            hipx, hipy = bx + off * sz, by + ry * 0.7
            if legs == "air":
                fx, fy = hipx + 1.6 * sz, min(hipy + leg_len * 0.95 + ry * 0.12, GROUND - 1.5)
            else:
                fx, fy = hipx - 0.4 * sz, GROUND
            dk = 0.72 if j == 0 else 1.0
            lc = leg_c * dk
            lw = sp.get("leg_w", 0.62) * sz
            sd = sd_capsule(hipx, hipy, fx, fy - 0.5, lw * 1.25, lw)
            cv.part(sd, full(lc), outline=0.3, shade=np.clip(1.05 - 0.25 * np.clip((X - fx) / lw, -1, 1), 0.7, 1.2))
            toe = sp.get("toe", 3.0) * sz
            curl = 0.55 if legs == "air" else 1.0
            for tx_, ty_ in ((-toe * curl, 0.1), (-toe * 0.75 * curl, -0.35 * curl), (toe * 0.55 * curl, 0.0)):
                sd = sd_capsule(fx, fy - 0.45, fx + tx_, fy - 0.45 + ty_ + (0.8 if legs == "air" else 0), lw * 0.85, lw * 0.55)
                cv.part(sd, full(lc * 0.92), outline=0.25)

    # --- Cuerpo y cabeza ---
    col = sp["color"](ctx)
    tex = sample(NOISE_FINE, lx, ly, 6.0) - 0.5
    col = col * (1 + sp.get("tex", 0.08) * tex)[..., None]
    shade, spec = volume_shade(torso, 6.0 * sz, sp.get("gloss", 0.0))
    spec_rgb = None if spec is None else spec[..., None] * sp.get("spec_color", C(255, 255, 255))
    cv.part(torso, col, outline=0.5, dark=0.28, shade=shade, spec=spec_rgb)
    torso_col = np.clip(col * shade[..., None], 0, 1)

    # --- Mechones del polluelo ---
    if sp.get("tuft"):
        for k, (ex, ey) in enumerate(((-1.2, -3.6), (0.6, -4.2), (2.2, -3.0))):
            x0, y0 = HX + (k - 1) * 1.2 * sz, HY - hr * 0.9
            sd = sd_capsule(x0, y0 + 1.0, x0 + ex * sz, y0 + ey * sz, 0.55 * sz, 0.18 * sz)
            cv.part(sd, full(sp["back"]), outline=0.25)

    # --- Ala cercana ---
    if wing is None:
        tip = bpt(1.22 + sp.get("wing_ext", 0), -0.12)
        acc = folded_wing(cv, sp, shoulder_folded, tip)
    else:
        acc = open_wing(cv, sp, shoulder_open, wing)
    if acc:
        wsd = acc[0]
        for a in acc[1:]:
            wsd = np.minimum(wsd, a)
        # La sombra del ala sólo oscurece el cuerpo que queda debajo.
        before = cv.rgb.copy()
        cv.shadow(wsd, 0.5, 0.9, 1.2, 0.28)
        mask = (wsd > 0)[..., None]
        cv.rgb = np.where(mask, cv.rgb, before)

    # --- Ojo ---
    er = sp.get("eye_r", 1.25) * sz
    ex, ey = HX - hr * 0.36, HY - hr * 0.14
    if pose.get("blink"):
        lid = sd_circle(ex, ey, er + 0.35)
        cv.over(torso_col, Canvas.cover(lid))
        for i in range(6):
            t0, t1 = i / 6, (i + 1) / 6
            a0, a1 = math.pi * (0.1 + 0.8 * t0), math.pi * (0.1 + 0.8 * t1)
            sd = sd_capsule(ex - math.cos(a0) * er, ey + math.sin(a0) * er * 0.35,
                            ex - math.cos(a1) * er, ey + math.sin(a1) * er * 0.35, 0.22, 0.22)
            cv.part(sd, full(C(25, 20, 22)), outline=0)
    else:
        ring = sp.get("ring")
        if ring is not None:
            rc, rw = ring
            sd = sd_circle(ex, ey, er + rw)
            if sp.get("ring_arcs"):
                sd = np.maximum(sd, -(np.abs(Y - ey) - er * 0.45))
            cv.part(sd, full(rc), outline=0)
        cv.part(sd_circle(ex, ey, er), full(sp.get("iris", C(40, 26, 22))), outline=0.25, dark=0.15)
        cv.part(sd_circle(ex - er * 0.05, ey, er * sp.get("pupil", 0.55)), full(C(12, 10, 12)), outline=0)
        cv.part(sd_circle(ex - er * 0.38, ey - er * 0.4, er * 0.3), full(C(255, 255, 255)), outline=0)
        cv.part(sd_circle(ex + er * 0.35, ey + er * 0.4, er * 0.13), full(C(230, 235, 245)), outline=0)

    # --- Pico ---
    draw_beak(cv, sp, HX, HY, hr, sz)
    return cv.image()


def mix_scalar(a, b, t):
    return a * (1 - t) + b * t


def draw_beak(cv, sp, HX, HY, hr, sz):
    bk = sp["beak"]
    L = bk["L"] * sz * 1.2
    d = bk["d"] * sz * 1.15
    hook = bk.get("hook", 0.2) * sz
    droop = bk.get("droop", 8)
    x0 = HX - hr * 0.78
    y0 = HY + hr * bk.get("y", 0.12)
    n = 10
    top, bot = [], []
    for i in range(n + 1):
        t = i / n
        yt = -d / 2 * (1 - t) ** bk.get("pt", 0.85) + hook * t ** 3
        yb = d / 2 * (1 - t) ** bk.get("pb", 1.15) + hook * t ** 3 * 0.6
        top.append((-L * t, yt))
        bot.append((-L * t, yb))
    pts = top + bot[::-1]
    a = math.radians(-droop)
    ca, sa = math.cos(a), math.sin(a)
    # Rotar hacia abajo (droop) alrededor de la base y trasladar.
    wpts = [(x0 + px * ca + py * sa, y0 - px * sa + py * ca) for px, py in pts]
    sd = sd_poly(wpts)
    # Coordenadas locales del pico para el color.
    lxp = (X - x0) * ca - (Y - y0) * sa
    lyp = (X - x0) * sa + (Y - y0) * ca
    tt = np.clip(-lxp / L, 0, 1)
    mouth = hook * tt ** 3 * 0.8 + d * 0.06 * (1 - tt)
    up = full(bk["color"])
    lo = full(bk.get("lower", bk["color"] * 0.85))
    col = mix(up, lo, S(mouth - 0.12, mouth + 0.12, lyp))
    if "tip" in bk:
        col = mix(col, full(bk["tip"]), S(0.6, 0.8, tt))
    # Brillo en el culmen y línea de la comisura.
    col = mix(col, np.clip(col * 1.35 + 0.08, 0, 1), S(0.0, -d * 0.35, lyp - (-d / 2 * (1 - tt))) * 0.0)
    shade = np.clip(1.15 - 0.35 * (lyp + d / 2) / max(d, 0.1), 0.75, 1.2)
    cv.part(sd, col, outline=0.32, dark=0.3, shade=shade)
    line = sd_capsule(x0 + 0.2, y0 + d * 0.06, x0 - L * 0.78 * ca, y0 + L * 0.78 * sa + hook * 0.3, 0.14, 0.08)
    line = np.maximum(line, sd)
    cv.part(line, full(bk["color"] * 0.25), outline=0)
    if "cere" in bk:
        cc = sd_ellipse(x0 - L * 0.12, y0 - d * 0.35, L * 0.2, d * 0.28, -droop)
        cv.part(cc, full(bk["cere"]), outline=0.2)


# ---------------------------------------------------------------------------
# Especies

def base_species(**kw):
    sp = dict(size=1.0, rx=13.0, ry=9.5, head_r=7.5, head_off=(-11.5, -12.0), neck_k=4.5,
              tilt=28, body_x=31.0, leg_len=8.5, tail_len=17.0, tail_w=3.2, tail_ang=46,
              wing_w=7.0, eye_r=1.3, tex=0.08)
    sp.update(kw)
    return sp


def F(base, **kw):
    d = dict(base=base)
    d.update(kw)
    return d


def jay_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    body = mix(full(C(200, 164, 144)), full(C(172, 140, 130)), S(0.1, -0.7, v))
    body = mix(body, full(C(226, 204, 188)), S(0.1, 0.7, v) * S(0.6, -0.2, u))
    body = mix(body, full(C(246, 244, 240)), S(0.55, 0.85, u) * S(0.4, -0.1, v))
    body = mix(body, full(C(240, 236, 230)), S(0.4, 0.7, u) * S(0.3, 0.7, v))
    head = full(C(206, 170, 150))
    crown = S(-0.2, -0.5, hy)
    st = dots(c["hu"], c["hv"], 0.95, 0.2, 3, stretch=2.2)
    head = mix(head, mix(full(C(234, 228, 222)), full(C(42, 38, 46)), st), crown)
    head = mix(head, full(C(248, 246, 242)), S(0.1, 0.35, hy) * S(0.45, 0.05, hx))
    m = S(0.17, 0.09, dist_seg(hx, hy, -0.78, 0.2, -0.28, 0.85))
    head = mix(head, full(C(28, 24, 28)), m)
    return mix(body, head, hw)


def jay_coverts(s, w):
    f = (s * 7.5) % 1.0
    col = np.where((f < 0.38)[..., None], full(C(96, 168, 245)),
                   np.where((f < 0.62)[..., None], full(C(34, 88, 205)),
                            np.where((f < 0.86)[..., None], full(C(22, 22, 32)), full(C(240, 244, 250)))))
    return col


def cardinal_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    body = mix(full(C(206, 36, 40)), full(C(160, 24, 32)), S(0.1, -0.8, v))
    body = mix(body, full(C(230, 72, 62)), S(0.1, 0.8, v) * S(0.5, -0.3, u))
    head = mix(full(C(218, 42, 44)), full(C(190, 28, 36)), S(-0.8, -1.3, hy))
    mask = S(-0.12, -0.38, hx - 0.45 * np.maximum(hy - 0.15, 0)) * S(-0.62, -0.42, hy) * S(1.15, 0.95, hy)
    head = mix(head, full(C(22, 16, 18)), mask)
    return mix(body, head, hw)


def waxwing_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    body = mix(full(C(186, 148, 108)), full(C(146, 128, 110)), S(0.0, -0.8, v) + S(0.2, 0.7, u) * S(0.3, -0.3, v))
    body = mix(body, full(C(232, 210, 128)), S(0.35, 0.75, v) * S(-0.4, 0.1, u))
    body = mix(body, full(C(244, 240, 228)), S(0.5, 0.8, u) * S(0.3, 0.7, v))
    head = mix(full(C(196, 152, 104)), full(C(176, 134, 92)), S(-0.8, -1.4, hy))
    d = dist_seg(hx, hy, -0.98, 0.08, 0.8, -0.32)
    band = S(0.24, 0.15, d)
    edge = S(0.33, 0.25, d) * (1 - band) * S(-0.2, 0.2, hy - (0.08 - 0.22 * (hx + 0.98)))
    head = mix(head, full(C(250, 250, 248)), edge * S(0.7, 0.4, hx))
    head = mix(head, full(C(18, 14, 18)), band)
    head = mix(head, full(C(26, 20, 22)), S(0.3, 0.12, np.hypot(hx + 0.62, (hy - 0.5) * 1.2)))
    return mix(body, head, hw)


def chickadee_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    body = mix(full(C(130, 135, 142)), full(C(240, 236, 226)), S(-0.15, 0.35, v) * S(0.55, 0.1, u))
    body = mix(body, full(C(216, 190, 152)), S(-0.1, 0.4, u) * S(0.0, 0.45, v) * S(0.9, 0.6, v))
    body = mix(body, full(C(236, 232, 222)), S(0.55, 0.85, u) * S(0.35, 0.75, v))
    head = full(C(246, 246, 243))
    cap = S(0.06, -0.08, hy - 0.45 * np.maximum(hx - 0.1, 0) + 0.04)
    head = mix(head, full(C(22, 22, 26)), cap)
    bib = S(0.32, 0.48, hy) * S(0.55, 0.3, hx)
    head = mix(head, full(C(24, 24, 28)), bib)
    nape = S(0.6, 0.95, hx) * S(-0.3, 0.2, hy)
    head = mix(head, full(C(132, 136, 142)), nape * (1 - cap))
    return mix(body, head, hw)


def crow_color(c):
    u, v, hw = c["u"], c["v"], c["hw"]
    body = mix(full(C(36, 34, 46)), full(C(26, 26, 34)), S(-0.5, 0.6, v))
    return mix(body, full(C(32, 32, 42)), hw)


def finch_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    back = mix(full(C(138, 102, 76)), full(C(84, 60, 46)), dots(c["bu"], c["bv"] * 0.45, 1.6, 0.42, 5))
    belly = mix(full(C(234, 224, 212)), full(C(146, 104, 82)), dots(c["bu"] * 0.9, c["bv"] * 0.4, 1.5, 0.38, 6))
    body = mix(back, belly, S(-0.05, 0.4, v))
    # Pecho rojo que se difumina hacia el vientre listado.
    breast = S(0.1, -0.45, u + 0.25 * np.maximum(v - 0.3, 0)) * S(-0.7, -0.3, v)
    body = mix(body, full(C(214, 54, 66)), breast)
    head = full(C(222, 60, 70))
    brown = S(-0.15, -0.35, hy) * S(-0.15, 0.25, hx) + S(0.2, 0.55, hx) * S(0.3, -0.1, hy) * 0.8
    head = mix(head, full(C(140, 100, 74)), np.clip(brown, 0, 1))
    return mix(body, head, hw)


def hummer_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    irid = sample(NOISE_MED, c["lx"], c["ly"], 3.0)
    green = mix(full(C(52, 140, 62)), full(C(132, 196, 70)), irid)
    body = mix(green, full(C(234, 236, 230)), S(0.1, 0.45, v) * S(0.5, -0.1, u))
    body = mix(body, full(C(150, 170, 140)), S(-0.1, 0.4, u) * S(0.1, 0.5, v) * S(0.9, 0.6, v))
    head = green
    gor = S(0.12, 0.3, hy) * S(0.55, 0.25, hx)
    ruby = mix(full(C(190, 14, 62)), full(C(255, 70, 110)), irid)
    head = mix(head, ruby, gor)
    head = mix(head, full(C(246, 246, 244)), S(0.2, 0.1, np.hypot(hx - 0.08, hy + 0.16)))
    return mix(body, head, hw)


def magpie_color(c):
    u, v, hw = c["u"], c["v"], c["hw"]
    black = full(C(24, 24, 32))
    body = mix(black, full(C(246, 246, 244)), S(-0.05, 0.25, v) * S(0.5, 0.25, u) * S(-0.85, -0.55, u))
    return mix(body, black, hw)


def robin_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    body = mix(full(C(98, 92, 94)), full(C(206, 86, 40)), S(-0.45, -0.05, v + 0.35 * np.minimum(u, 0)))
    body = mix(body, full(C(242, 240, 236)), S(0.4, 0.7, u) * S(0.3, 0.7, v))
    body = mix(body, full(C(176, 70, 34)), S(0.2, 0.9, v) * S(0.3, -0.5, u) * 0.4)
    head = full(C(44, 42, 48))
    thr = S(0.35, 0.55, hy) * S(0.3, 0.0, hx)
    st = (np.sin(c["hu"] * 4.2) > 0.2).astype(np.float32)
    head = mix(head, mix(full(C(238, 236, 232)), full(C(44, 42, 48)), st), thr)
    return mix(body, head, hw)


def steller_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    body = mix(full(C(46, 98, 180)), full(C(38, 78, 150)), S(0.1, -0.7, v))
    body = mix(body, full(C(78, 128, 204)), S(0.2, 0.9, v) * S(0.5, -0.2, u))
    body = mix(body, full(C(32, 38, 54)), S(-0.2, -0.6, u) * S(0.5, -0.1, v))
    head = full(C(30, 34, 48))
    st = (np.sin(c["hu"] * 5.5) > 0.35).astype(np.float32)
    brow = S(-0.7, -0.5, hx) * S(-0.1, -0.3, hx) * S(-0.65, -0.5, hy) * S(-0.2, -0.32, hy)
    head = mix(head, full(C(126, 186, 244)), st * brow)
    return mix(body, head, hw)


def chick_color(c):
    u, v, hy, hw = c["u"], c["v"], c["hy"], c["hw"]
    body = mix(full(C(238, 218, 172)), full(C(214, 188, 142)), S(0.1, -0.8, v))
    body = mix(body, full(C(250, 240, 214)), S(0.1, 0.8, v))
    head = mix(full(C(240, 222, 178)), full(C(222, 198, 152)), S(-0.3, -0.9, hy))
    return mix(body, head, hw)


def dove_color(c):
    u, v, hw = c["u"], c["v"], c["hw"]
    body = mix(full(C(250, 250, 252)), full(C(228, 230, 238)), S(0.2, -0.8, v))
    return mix(body, full(C(252, 252, 253)), hw)


def thrush_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    back = mix(full(C(166, 106, 60)), full(C(150, 100, 62)), S(0.0, 0.8, u))
    white = full(C(248, 244, 236))
    spots = dots(c["bu"], c["bv"], 2.5, 0.75, 9) * S(0.65, 0.4, u)
    belly = mix(white, full(C(58, 40, 30)), spots)
    body = mix(back, belly, S(-0.4, 0.0, v + 0.3 * np.minimum(u, 0)))
    head = mix(full(C(198, 116, 56)), full(C(180, 108, 56)), S(0.2, 0.8, hx))
    face = S(-0.05, 0.12, hy) * S(0.35, 0.1, hx)
    stc = (np.sin(c["hu"] * 3.8 + c["hv"] * 1.5) > 0.25).astype(np.float32)
    head = mix(head, mix(white, full(C(110, 70, 44)), stc), face)
    head = mix(head, mix(white, full(C(58, 40, 30)), dots(c["hu"], c["hv"], 2.2, 0.6, 4)), S(0.45, 0.65, hy) * S(0.5, 0.2, hx))
    return mix(body, head, hw)


def bee_eater_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    turq = mix(full(C(44, 170, 196)), full(C(70, 196, 210)), S(0.0, 0.8, v))
    body = mix(full(C(150, 96, 40)), full(C(232, 196, 64)), S(-0.2, 0.5, u) * S(0.2, -0.6, v))
    body = mix(body, turq, S(-0.45, -0.05, v + 0.3 * np.minimum(u, 0)))
    body = mix(body, full(C(70, 150, 120)), S(0.45, 0.85, u) * S(-0.3, 0.3, v))
    head = full(C(150, 64, 34))  # píleo castaño
    head = mix(head, full(C(250, 246, 214)), S(-0.55, -0.4, hx) * S(-0.5, -0.35, hy) * S(-0.15, -0.3, hy))
    throat = S(0.12, 0.3, hy) * S(0.6, 0.3, hx)
    head = mix(head, full(C(250, 214, 50)), throat)
    collar = S(0.08, 0.02, np.abs(hy - 0.95 + 0.25 * np.maximum(hx, 0)))
    head = mix(head, full(C(20, 18, 22)), collar * S(0.6, 0.3, hx))
    d = dist_seg(hx, hy, -1.0, 0.05, 0.85, -0.05)
    head = mix(head, full(C(18, 16, 20)), S(0.2, 0.12, d))
    head = mix(head, turq, S(0.95, 1.2, hy) * S(0.5, 0.2, hx))
    return mix(body, head, hw)


def kestrel_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    rufous = mix(full(C(190, 92, 44)), full(C(40, 26, 22)), dots(c["bu"], c["bv"], 2.3, 0.5, 12) * S(0.2, -0.3, v))
    buff = mix(full(C(238, 214, 176)), full(C(70, 44, 30)), dots(c["bu"], c["bv"] * 0.55, 2.0, 0.42, 13) * S(-0.2, 0.2, u + 0.3))
    body = mix(rufous, buff, S(-0.3, 0.1, v + 0.35 * np.minimum(u, 0)))
    body = mix(body, full(C(120, 130, 146)), S(0.6, 0.95, u) * S(0.3, -0.2, v))
    head = mix(full(C(118, 128, 146)), full(C(140, 150, 166)), S(-0.6, 0.4, hy))
    head = mix(head, full(C(240, 226, 204)), S(0.0, 0.3, hy) * S(0.5, 0.15, hx))
    m = S(0.16, 0.08, dist_seg(hx, hy, -0.4, 0.05, -0.38, 0.75))
    head = mix(head, full(C(30, 30, 38)), m)
    return mix(body, head, hw)


def chucao_color(c):
    u, v, hx, hy, hw = c["u"], c["v"], c["hx"], c["hy"], c["hw"]
    brown = mix(full(C(92, 64, 44)), full(C(70, 48, 34)), S(0.0, 0.9, u))
    rufous = full(C(210, 104, 44))
    body = mix(brown, rufous, S(-0.35, 0.0, v + 0.6 * np.minimum(u + 0.1, 0)) * S(0.35, -0.1, u))
    bars = (np.sin(c["bv"] * 3.6 + c["bu"] * 0.4) > 0.15).astype(np.float32)
    flank = mix(full(C(232, 228, 220)), full(C(36, 32, 34)), bars)
    body = mix(body, flank, S(0.25, 0.6, v) * S(-0.15, 0.25, u))
    body = mix(body, full(C(150, 150, 156)), S(0.35, 0.7, v) * S(0.0, -0.4, u) * 0.6)
    head = full(C(88, 62, 44))
    head = mix(head, rufous, S(0.0, 0.25, hy) * S(0.55, 0.2, hx) + S(-0.55, -0.8, hx) * S(0.0, -0.3, hy))
    head = mix(head, full(C(110, 100, 96)), S(0.2, 0.08, np.hypot(hx - 0.15, hy + 0.05)) * 0.7)
    return mix(body, head, np.clip(hw, 0, 1))


def species_table():
    T = {}
    T["blue_jay"] = base_species(
        color=jay_color, back=C(172, 140, 130), crest=6.0, crest_lean=0.5,
        beak=dict(L=4.6, d=2.8, color=C(62, 58, 64), hook=0.25),
        iris=C(178, 204, 232), pupil=0.45, leg=C(176, 136, 120),
        tail=F(C(34, 30, 38), fringe=C(60, 56, 66), shaft=C(20, 18, 22)),
        wing=dict(cov=jay_coverts, cov2=C(172, 140, 130),
                  sec=F(C(30, 28, 34), root=(C(246, 246, 244), 0.5), fringe=None),
                  ter=F(C(36, 30, 32), fringe=C(120, 70, 50)),
                  pri=F(C(36, 34, 40), fringe=C(150, 150, 160), fringe_w=0.25)),
    )
    T["cardinal"] = base_species(
        color=cardinal_color, back=C(160, 24, 32), crest=7.5, crest_lean=0.4,
        beak=dict(L=4.4, d=4.0, color=C(244, 98, 52), hook=0.3, droop=10, pt=0.7, pb=0.9),
        leg=C(176, 122, 112), tail_len=18,
        tail=F(C(150, 26, 34), fringe=C(120, 20, 28), shaft=C(110, 20, 26)),
        wing=dict(cov=C(178, 32, 38), cov2=C(165, 28, 34),
                  sec=F(C(150, 28, 34), fringe=C(196, 50, 52)),
                  pri=F(C(124, 24, 30), fringe=C(180, 46, 48))),
    )
    T["cedar_waxwing"] = base_species(
        color=waxwing_color, back=C(146, 128, 110), crest=5.0, crest_lean=0.9,
        beak=dict(L=3.2, d=2.4, color=C(34, 32, 36), hook=0.15),
        leg=C(48, 44, 50), tail_len=16,
        tail=F(C(116, 114, 118), tip=(C(246, 204, 40), 0.17), shaft=C(80, 78, 84)),
        wing=dict(cov=C(126, 120, 118), cov2=C(146, 128, 110),
                  sec=F(C(112, 108, 110), tip=(C(222, 26, 30), 0.12), fringe=C(140, 136, 136)),
                  pri=F(C(62, 60, 66), fringe=C(236, 232, 210), fringe_w=0.2)),
    )
    T["chickadee"] = base_species(
        color=chickadee_color, back=C(130, 135, 142), rx=12.0, ry=9.8, head_r=8.0,
        head_off=(-10.5, -11.5), neck_k=5.0,
        beak=dict(L=2.6, d=2.0, color=C(30, 30, 34), hook=0.1),
        leg=C(64, 70, 82), tail_len=16, tail_w=3.0,
        tail=F(C(118, 124, 132), fringe=C(220, 222, 226), fringe_w=0.25, shaft=C(80, 84, 90)),
        wing=dict(cov=C(124, 130, 138), cov2=C(130, 135, 142),
                  sec=F(C(104, 110, 118), fringe=C(236, 238, 240), fringe_w=0.35),
                  pri=F(C(90, 96, 104), fringe=C(190, 196, 204), fringe_w=0.22)),
    )
    T["crow"] = base_species(
        color=crow_color, back=C(30, 30, 40), size=1.04, rx=13.5, head_r=7.3, head_off=(-12.0, -11.5),
        beak=dict(L=7.4, d=3.6, color=C(30, 30, 36), hook=0.7, pt=0.7, pb=1.4, droop=6),
        leg=C(30, 30, 36), tail_len=18, tail_w=3.4, gloss=0.5, spec_color=C(120, 130, 210),
        iris=C(30, 22, 20),
        tail=F(C(32, 32, 42), fringe=C(56, 60, 84), shaft=C(20, 20, 26)),
        wing=dict(cov=C(34, 34, 46), cov2=C(30, 30, 40),
                  sec=F(C(30, 30, 40), fringe=C(58, 64, 92)),
                  pri=F(C(28, 28, 36), fringe=C(64, 68, 98), fringe_w=0.25)),
    )
    T["house_finch"] = base_species(
        color=finch_color, back=C(138, 102, 76), rx=12.5, ry=9.2, head_r=7.4,
        beak=dict(L=3.6, d=3.2, color=C(170, 158, 150), hook=0.25, droop=10, pt=0.75),
        leg=C(168, 128, 116), tail_len=16, tail_fork=0.15,
        tail=F(C(112, 80, 60), fringe=C(170, 140, 116), shaft=C(80, 56, 42)),
        wing=dict(cov=lambda s, w: mix(full(C(120, 86, 64)), full(C(232, 220, 204)), S(0.55, 0.85, w)),
                  cov2=C(130, 94, 70),
                  sec=F(C(104, 74, 56), fringe=C(212, 190, 168), fringe_w=0.3),
                  pri=F(C(92, 66, 52), fringe=C(176, 150, 128), fringe_w=0.22)),
    )
    T["hummingbird"] = base_species(
        color=hummer_color, back=C(60, 150, 66), size=0.92, rx=11.0, ry=7.6, head_r=6.6,
        head_off=(-10.5, -9.5), neck_k=3.5, tilt=30, leg_len=3.2, leg_w=0.45, toe=1.8, leg=C(30, 30, 34),
        beak=dict(L=12.0, d=1.25, color=C(28, 28, 32), hook=0.15, droop=4, pt=0.5, pb=0.5),
        tail_len=10, tail_w=3.0, tail_ang=50, tail_fork=0.35, gloss=0.55, spec_color=C(200, 255, 200),
        wing_w=4.6, wing_ext=0.25,
        tail=F(C(40, 80, 50), tip=(C(28, 30, 32), 0.3), shaft=C(20, 30, 22)),
        wing=dict(cov=C(70, 130, 70), cov2=C(60, 150, 66), n_pri=5, pri_len=1.35, sec_len=0.6, arm=0.75,
                  sec=F(C(74, 72, 80), fringe=C(110, 110, 118)),
                  pri=F(C(70, 68, 76), fringe=C(112, 112, 122), fringe_w=0.2)),
    )
    T["magpie"] = base_species(
        color=magpie_color, back=C(24, 24, 32), body_x=26.0, tail_len=24, tail_w=3.4, tail_ang=32,
        tail_spread=5, gloss=0.45, spec_color=C(110, 150, 220),
        beak=dict(L=5.0, d=2.8, color=C(26, 26, 30), hook=0.35),
        leg=C(28, 28, 32),
        tail=F(C(28, 92, 92), tip=(C(74, 44, 120), 0.45), fringe=C(60, 130, 140), shaft=C(16, 30, 34)),
        wing=dict(cov=C(246, 246, 244), cov2=C(24, 24, 32),
                  sec=F(C(30, 58, 130), fringe=C(60, 100, 180)),
                  ter=F(C(26, 40, 80), fringe=C(50, 80, 150)),
                  pri=F(C(244, 244, 242), outer=(C(24, 24, 30), 0.1), tip=(C(24, 24, 30), 0.22))),
    )
    T["red_robin"] = base_species(
        color=robin_color, back=C(98, 92, 94), head_r=7.6,
        beak=dict(L=4.4, d=2.4, color=C(240, 186, 40), tip=C(90, 70, 30), hook=0.2),
        ring=(C(246, 244, 240), 0.45), ring_arcs=True,
        leg=C(140, 116, 104), tail_len=18,
        tail=F(C(52, 50, 56), tip=(C(240, 240, 236), 0.08), shaft=C(30, 30, 34)),
        wing=dict(cov=C(92, 86, 88), cov2=C(98, 92, 94),
                  sec=F(C(80, 76, 78), fringe=C(140, 134, 134)),
                  pri=F(C(66, 62, 66), fringe=C(120, 116, 118), fringe_w=0.22)),
    )
    T["bee_eater"] = base_species(
        color=bee_eater_color, back=C(150, 96, 40), rx=12.0, ry=8.6, head_r=7.0, tilt=24,
        beak=dict(L=7.6, d=2.0, color=C(26, 24, 28), hook=0.9, droop=10, pt=0.6, pb=0.9),
        iris=C(200, 30, 30), pupil=0.45, leg=C(110, 90, 84), leg_len=6.5, tail_len=14,
        tail_streamer=0.45, tail_ang=40, wing_ext=0.1,
        tail=F(C(70, 150, 110), fringe=C(110, 190, 150), shaft=C(30, 60, 40)),
        wing=dict(cov=C(170, 84, 40), cov2=C(196, 130, 50),
                  sec=F(C(56, 156, 150), tip=(C(28, 30, 32), 0.15), fringe=C(100, 196, 180)),
                  ter=F(C(70, 160, 120), fringe=C(120, 200, 160)),
                  pri=F(C(48, 120, 160), tip=(C(28, 30, 34), 0.2), fringe=C(90, 160, 200), fringe_w=0.22)),
    )
    T["kestrel"] = base_species(
        color=kestrel_color, back=C(190, 92, 44), rx=12.5, ry=9.4, head_r=7.6, tilt=40,
        head_off=(-9.5, -12.5), body_x=28.0, tail_len=17.5, tail_w=3.6, tail_ang=50, wing_ext=0.05,
        beak=dict(L=3.0, d=3.4, color=C(70, 80, 110), hook=1.3, droop=12, pt=0.55, pb=1.6,
                  cere=C(248, 206, 60)),
        iris=C(44, 30, 22), ring=(C(248, 206, 60), 0.45), leg=C(246, 200, 60), leg_len=7.5,
        leg_w=0.8, toe=3.4,
        tail=F(C(126, 136, 152), band=(C(26, 26, 32), 0.8, 0.93), tip=(C(246, 244, 240), 0.06),
               shaft=C(80, 86, 100)),
        wing=dict(cov=lambda s, w: mix(full(C(190, 92, 44)), full(C(36, 24, 20)), dots(X * 1.0, Y * 1.0, 2.2, 0.55, 21)),
                  cov2=C(190, 92, 44),
                  sec=F(C(60, 42, 36), fringe=C(130, 90, 70)),
                  ter=F(C(186, 90, 44), bars=(C(40, 26, 20), 4, 0.25)),
                  pri=F(C(40, 32, 32), fringe=C(110, 96, 90), fringe_w=0.2, bars=(C(90, 80, 76), 7, 0.15))),
    )
    T["chucao"] = base_species(
        color=chucao_color, back=C(84, 58, 40), rx=11.5, ry=9.6, head_r=7.4, tilt=22,
        head_off=(-10.5, -11.0), body_x=29.0, leg_len=9.5, leg_w=0.75, toe=3.4,
        tail_len=12, tail_w=3.4, tail_ang=-58, tail_spread=12, wing_w=6.2, wing_ext=-0.25,
        beak=dict(L=3.4, d=2.4, color=C(36, 32, 34), hook=0.2),
        leg=C(80, 76, 80),
        tail=F(C(64, 44, 32), fringe=C(96, 70, 50), shaft=C(40, 28, 20)),
        wing=dict(cov=C(86, 60, 42), cov2=C(84, 58, 40), n_pri=4,
                  sec=F(C(76, 52, 36), fringe=C(110, 80, 56)),
                  pri=F(C(66, 46, 34), fringe=C(104, 76, 54), fringe_w=0.2)),
    )
    T["vanilla_chick"] = base_species(
        color=chick_color, back=C(214, 188, 142), rx=12.0, ry=11.0, head_r=10.0,
        head_off=(-8.0, -12.5), neck_k=6.0, tilt=18, body_x=33.0, leg_len=5.0, leg_w=0.8,
        tail_len=4.5, tail_w=4.0, tail_ang=30, tail_n=3, fluffy=True, tuft=True, tex=0.07, round=True,
        eye_r=1.9, pupil=0.75, toe=2.6, wing_w=5.5,
        beak=dict(L=2.8, d=2.6, color=C(246, 162, 58), hook=0.1, y=0.18),
        leg=C(242, 152, 60),
        tail=F(C(220, 196, 150)),
        wing=dict(stub=True, cov=C(224, 200, 154)),
    )
    T["white_dove"] = base_species(
        color=dove_color, back=C(232, 234, 240), rx=13.5, ry=9.8, head_r=6.4,
        head_off=(-12.5, -11.0), neck_k=5.0, gloss=0.15,
        beak=dict(L=3.6, d=1.9, color=C(228, 142, 152), hook=0.15, cere=C(250, 238, 238), y=0.18),
        iris=C(214, 74, 42), pupil=0.45, ring=(C(230, 150, 150), 0.3),
        leg=C(228, 112, 124), tail_len=16, tail_w=3.6,
        tail=F(C(244, 244, 248), fringe=C(255, 255, 255), shaft=C(220, 222, 230)),
        wing=dict(cov=C(244, 245, 250), cov2=C(236, 238, 244),
                  sec=F(C(236, 238, 244), fringe=C(255, 255, 255)),
                  pri=F(C(222, 224, 234), fringe=C(252, 252, 255), fringe_w=0.25)),
    )
    T["wood_thrush"] = base_species(
        color=thrush_color, back=C(160, 104, 62),
        beak=dict(L=4.0, d=2.3, color=C(96, 78, 66), lower=C(218, 176, 160), hook=0.15),
        ring=(C(250, 248, 244), 0.4),
        leg=C(226, 172, 166), tail_len=16,
        tail=F(C(146, 96, 60), fringe=C(176, 124, 84), shaft=C(110, 70, 44)),
        wing=dict(cov=C(158, 102, 60), cov2=C(162, 106, 62),
                  sec=F(C(146, 94, 56), fringe=C(186, 132, 88)),
                  pri=F(C(126, 84, 52), fringe=C(176, 128, 90), fringe_w=0.22)),
    )
    return T


# ---------------------------------------------------------------------------
# Poses de la hoja (filas: vuelo, reacción, reposo, salto)

FLY = dict(legs="tuck", tilt=12, tail=24, hdx=-1.5, hdy=3.5)
POSES = [
    [dict(FLY, dy=-8, wing=-78), dict(FLY, dy=-7, wing=-22),
     dict(FLY, dy=-6, wing=38), dict(FLY, dy=-7, wing=-22)],
    [dict(crest=1.5, hdy=-0.5), dict(crest=3.0, hdy=-1.5, dy=-0.5),
     dict(crest=3.0, wing=-80), dict(crest=2.0, wing=-28)],
    [dict(), dict(dy=0.5, hdy=0.3), dict(blink=True), dict(dy=0.5, crest=0.6)],
    [dict(dy=2.0, hdy=1.0), dict(dy=-6.0, legs="air", wing=-50),
     dict(dy=-10.0, legs="air", wing=-85, crest=1.5), dict(dy=-1.0, wing=-28)],
]


def build_sheet(sp):
    sheet = Image.new("RGBA", (FRAME * 4, FRAME * 4), (0, 0, 0, 0))
    for r, row in enumerate(POSES):
        for c, pose in enumerate(row):
            sheet.paste(render(sp, pose), (c * FRAME, r * FRAME))
    return sheet


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    table = species_table()
    names = sys.argv[2:] or list(table)
    for name in names:
        build_sheet(table[name]).save(os.path.join(out, f"pet_{name}.png"), optimize=True)
        print("ok", name, flush=True)


if __name__ == "__main__":
    main()
