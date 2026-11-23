#!/usr/bin/env python3
"""Generador de los iconos de la interfaz (menús, monedas, acciones y estados).

Mismo acabado que los pájaros: formas cartoon con contorno oscuro fino, volumen con un
degradado vertical y un brillo en el borde superior izquierdo. Cada icono es una lista de
piezas (máscara + color) que se pintan en orden; cada pieza lleva su propio contorno, así
las de delante se recortan sobre las de detrás.

Uso:
    python3 tools/ui_icons/generate_ui.py OUT_DIR [icono ...]
"""
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = 192          # lado del icono final (px)
SS = 4             # supermuestreo
P = OUT * SS       # lienzo de trabajo
U = P / 100.0      # las coordenadas se escriben en una rejilla de 100x100
OUTLINE = (44, 28, 20)
LINE = 3.2         # grosor del contorno (unidades)


def u(*v):
    return [x * U for x in v]


def mask():
    return Image.new("L", (P, P), 0)


# --- Primitivas de máscara (coordenadas en la rejilla de 100) ------------------------

def ellipse(cx, cy, rx, ry):
    m = mask()
    ImageDraw.Draw(m).ellipse(u(cx - rx, cy - ry, cx + rx, cy + ry), fill=255)
    return m


def poly(*pts):
    m = mask()
    ImageDraw.Draw(m).polygon([(x * U, y * U) for x, y in pts], fill=255)
    return m


def rrect(x0, y0, x1, y1, r):
    m = mask()
    ImageDraw.Draw(m).rounded_rectangle(u(x0, y0, x1, y1), radius=r * U, fill=255)
    return m


def stroke(pts, w):
    """Trazo grueso con extremos redondeados (para ramas, patas, letras...)."""
    m = mask()
    d = ImageDraw.Draw(m)
    pp = [(x * U, y * U) for x, y in pts]
    d.line(pp, fill=255, width=int(w * U), joint="curve")
    for x, y in (pp[0], pp[-1]):
        r = w * U / 2
        d.ellipse((x - r, y - r, x + r, y + r), fill=255)
    return m


def arc_stroke(cx, cy, r, a0, a1, w, steps=40):
    pts = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
           for a in [a0 + (a1 - a0) * i / steps for i in range(steps + 1)]]
    return stroke(pts, w)


def union(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def minus(a, b):
    return ImageChops.subtract(a, b)


def shift(m, dx, dy):
    return ImageChops.offset(m, int(dx * U), int(dy * U))


def star(cx, cy, r_out, r_in, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return poly(*pts)


# --- Pintado -------------------------------------------------------------------------

def shade(color, k):
    return tuple(max(0, min(255, int(c * k))) for c in color)


def paint(canvas, m, color, outline=True, gradient=0.28, highlight=True, line=LINE):
    """Pinta una pieza: contorno, relleno con degradado vertical y brillo."""
    if outline:
        size = int(line * U) * 2 + 1
        ring = m.filter(ImageFilter.MaxFilter(min(size, 61)))
        canvas.paste(Image.new("RGBA", (P, P), OUTLINE + (255,)), (0, 0), ring)
    bbox = m.getbbox()
    if not bbox:
        return
    top, bottom = bbox[1], bbox[3]
    grad = Image.new("RGBA", (P, P))
    gd = ImageDraw.Draw(grad)
    h = max(1, bottom - top)
    for y in range(top, bottom + 1, 2):
        t = (y - top) / h
        gd.rectangle((0, y, P, y + 2), fill=shade(color, 1.08 - gradient * t) + (255,))
    canvas.paste(grad, (0, 0), m)
    if highlight:
        rim = minus(m, shift(m, 3.2, 4.2)).filter(ImageFilter.GaussianBlur(U * 0.8))
        hl = Image.new("RGBA", (P, P), (255, 255, 255, 255))
        canvas.paste(hl, (0, 0), rim.point(lambda v: int(v * 0.55)))


def detail(canvas, m, color, alpha=1.0):
    """Detalle plano sin contorno (motas, brillos, letras interiores)."""
    canvas.paste(Image.new("RGBA", (P, P), color + (255,)), (0, 0), m.point(lambda v: int(v * alpha)))


def new_canvas():
    return Image.new("RGBA", (P, P), (0, 0, 0, 0))


# --- Paleta --------------------------------------------------------------------------

GOLD = (250, 192, 46)
GOLD_DARK = (214, 140, 24)
WOOD = (196, 128, 66)
WOOD_DARK = (138, 84, 40)
GREEN = (102, 186, 74)
GREEN_DARK = (58, 128, 52)
LEAF = (126, 200, 88)
BLUE = (88, 170, 236)
SKY = (150, 210, 245)
RED = (232, 78, 72)
PINK = (244, 120, 150)
CREAM = (250, 238, 214)
PURPLE = (150, 96, 214)
GREY = (150, 158, 170)
WHITE = (252, 252, 252)
ORANGE = (246, 146, 52)
NIGHT = (70, 80, 160)


# --- Iconos --------------------------------------------------------------------------

def coin(c):
    paint(c, ellipse(50, 52, 40, 40), GOLD_DARK)
    paint(c, ellipse(50, 48, 40, 40), GOLD)
    paint(c, ellipse(50, 48, 30, 30), GOLD_DARK, gradient=-0.15, highlight=False)
    # Pluma en relieve (emblema propio del juego).
    feather = poly((50, 24), (61, 38), (59, 58), (50, 70), (41, 58), (39, 38))
    paint(c, feather, (255, 222, 110), line=2.4)
    detail(c, stroke([(50, 30), (50, 72)], 2.2), GOLD_DARK)


def egg(c):
    paint(c, ellipse(50, 56, 31, 38), CREAM)
    for x, y, r in ((40, 44, 5), (58, 60, 6.5), (44, 72, 4), (62, 40, 3.5)):
        detail(c, ellipse(x, y, r, r * 0.85), (196, 168, 120))


def biome(c):
    paint(c, rrect(10, 14, 90, 86, 12), SKY, gradient=-0.25)
    paint(c, ellipse(70, 34, 10, 10), GOLD, line=2.4)
    paint(c, poly((14, 82), (40, 36), (66, 82)), (126, 140, 170))
    detail(c, poly((40, 36), (32, 50), (36, 52), (40, 47), (45, 52), (48, 50)), WHITE)
    paint(c, poly((40, 82), (64, 48), (88, 82)), GREEN_DARK)
    paint(c, rrect(10, 72, 90, 86, 8), GREEN, highlight=False)


def menu(c):
    # Nido de ramitas con un huevo: el "menú de juego".
    paint(c, ellipse(50, 66, 40, 20), WOOD_DARK)
    paint(c, ellipse(50, 58, 28, 12), (88, 52, 26), gradient=0.0, highlight=False, outline=False)
    paint(c, ellipse(50, 50, 15, 19), CREAM, line=2.6)
    detail(c, ellipse(45, 45, 3, 2.6), (196, 168, 120))
    nest_front = minus(ellipse(50, 66, 40, 20), ellipse(50, 56, 30, 12))
    paint(c, nest_front, WOOD)
    for x0, x1, y in ((18, 44, 70), (40, 70, 76), (58, 84, 68), (26, 52, 80)):
        detail(c, stroke([(x0, y), (x1, y - 4)], 1.8), WOOD_DARK)


def level(c):
    # Insignia de nivel: escudo dorado con una hoja (el número se dibuja encima en la UI).
    shield = poly((50, 6), (88, 18), (84, 58), (50, 94), (16, 58), (12, 18))
    paint(c, shield, PURPLE)
    inner = poly((50, 16), (78, 25), (75, 56), (50, 84), (25, 56), (22, 25))
    paint(c, inner, (186, 140, 240), gradient=0.2, line=2.2)


def feed(c):
    # Cuenco con semillas.
    paint(c, ellipse(50, 50, 30, 10), (250, 214, 120), highlight=False)
    for x, y in ((38, 48), (48, 45), (58, 48), (44, 52), (54, 52), (62, 51), (36, 52)):
        paint(c, ellipse(x, y, 4.2, 3), (214, 160, 72), line=1.6, highlight=False)
    bowl = minus(ellipse(50, 56, 38, 28), rrect(0, 0, 100, 52, 0))
    paint(c, union(bowl, rrect(12, 52, 88, 58, 2)), RED)
    detail(c, stroke([(22, 62), (78, 62)], 2.2), (255, 160, 150))


def sleep(c):
    moon = minus(ellipse(46, 54, 34, 34), ellipse(64, 42, 28, 28))
    paint(c, moon, (250, 222, 120))
    for x, y, s in ((68, 22, 11), (82, 40, 8)):
        z = stroke([(x - s / 2, y - s / 2), (x + s / 2, y - s / 2), (x - s / 2, y + s / 2), (x + s / 2, y + s / 2)], 3.4)
        paint(c, z, NIGHT, line=2.0, highlight=False)


def wake(c):
    for i in range(8):
        a = math.radians(i * 45)
        x0, y0 = 50 + 28 * math.cos(a), 50 + 28 * math.sin(a)
        x1, y1 = 50 + 42 * math.cos(a), 50 + 42 * math.sin(a)
        paint(c, stroke([(x0, y0), (x1, y1)], 7), ORANGE, line=2.4, highlight=False)
    paint(c, ellipse(50, 50, 24, 24), GOLD)


def clean(c):
    paint(c, rrect(16, 46, 70, 80, 10), (130, 210, 220))
    detail(c, rrect(22, 52, 50, 58, 3), (200, 245, 250))
    for x, y, r in ((66, 30, 13), (82, 52, 9), (42, 26, 9), (24, 36, 6)):
        paint(c, ellipse(x, y, r, r), (220, 240, 255), gradient=0.15, line=2.2)
        detail(c, ellipse(x - r * 0.35, y - r * 0.35, r * 0.25, r * 0.25), WHITE)


def defend(c):
    shield = poly((50, 8), (86, 20), (82, 58), (50, 92), (18, 58), (14, 20))
    paint(c, shield, (96, 150, 220))
    paint(c, poly((50, 18), (76, 27), (73, 56), (50, 82)), (70, 116, 190), highlight=False, outline=False)
    # Ala estilizada.
    wing = poly((30, 56), (52, 34), (72, 30), (60, 44), (68, 46), (52, 56), (58, 60), (40, 64))
    paint(c, wing, WHITE, line=2.2)


def release(c):
    # Pájaro alzando el vuelo, con estela: liberarlo.
    for i, y in enumerate((74, 84)):
        detail(c, stroke([(10 + i * 6, y), (34 + i * 4, y - 8)], 3), (170, 196, 222))
    paint(c, poly((24, 66), (40, 58), (38, 72)), BLUE, line=2.2)  # cola
    paint(c, ellipse(52, 60, 22, 14), SKY)  # cuerpo
    paint(c, ellipse(72, 46, 12, 12), SKY)  # cabeza
    paint(c, poly((82, 44), (94, 47), (82, 51)), ORANGE, line=2.0)  # pico
    detail(c, ellipse(75, 44, 2.4, 2.4), OUTLINE)
    wing = poly((40, 56), (44, 20), (58, 14), (54, 32), (64, 34), (58, 58))
    paint(c, wing, BLUE)


def plus(c):
    paint(c, ellipse(50, 50, 40, 40), GREEN)
    cross = union(rrect(42, 24, 58, 76, 5), rrect(24, 42, 76, 58, 5))
    paint(c, cross, WHITE, line=2.4)


def arrow_down(c):
    paint(c, poly((20, 34), (80, 34), (50, 72)), CREAM)


def close(c):
    paint(c, ellipse(50, 50, 40, 40), RED)
    x = union(stroke([(34, 34), (66, 66)], 11), stroke([(66, 34), (34, 66)], 11))
    paint(c, x, WHITE, line=2.4)


def play(c):
    paint(c, ellipse(50, 50, 40, 40), GREEN)
    paint(c, poly((40, 30), (72, 50), (40, 70)), WHITE, line=2.4)


# Estados básicos.
def stat_hunger(c):
    paint(c, stroke([(50, 90), (50, 30)], 5), (190, 150, 70), line=2.2, highlight=False)
    for y, s in ((26, 1), (40, -1), (40, 1), (54, -1), (54, 1), (68, -1), (68, 1)):
        paint(c, ellipse(50 + s * 9, y, 7, 10), (236, 190, 80), line=2.2)
    paint(c, ellipse(50, 18, 6, 9), (236, 190, 80), line=2.2)


def stat_energy(c):
    bolt = poly((58, 6), (24, 56), (46, 56), (38, 94), (76, 40), (54, 40), (64, 6))
    paint(c, bolt, GOLD)


def stat_fun(c):
    paint(c, star(50, 52, 40, 18), (255, 150, 200))
    detail(c, ellipse(42, 44, 5, 4), WHITE, 0.6)


def stat_hygiene(c):
    drop = union(poly((50, 8), (76, 56), (24, 56)), ellipse(50, 62, 26, 26))
    paint(c, drop, BLUE)


# Estados avanzados.
def stat_social(c):
    paint(c, ellipse(34, 58, 18, 15), ORANGE)
    paint(c, ellipse(36, 40, 12, 12), ORANGE)
    paint(c, ellipse(66, 58, 18, 15), BLUE)
    paint(c, ellipse(64, 40, 12, 12), BLUE)
    paint(c, poly((22, 40), (14, 44), (22, 46)), GOLD, line=1.6)
    paint(c, poly((78, 40), (86, 44), (78, 46)), GOLD, line=1.6)
    detail(c, ellipse(33, 38, 2.2, 2.2), OUTLINE)
    detail(c, ellipse(67, 38, 2.2, 2.2), OUTLINE)
    paint(c, union(ellipse(45, 18, 6, 6), ellipse(55, 18, 6, 6), poly((40, 20), (60, 20), (50, 31))), RED, line=1.8)


def stat_climate(c):
    paint(c, rrect(40, 8, 60, 70, 10), WHITE)
    paint(c, ellipse(50, 76, 16, 16), RED)
    detail(c, rrect(46, 30, 54, 72, 4), RED)
    for y in (20, 32, 44):
        detail(c, stroke([(62, y), (70, y)], 2.2), OUTLINE)


def stat_plumage(c):
    vane = poly((70, 8), (82, 26), (72, 54), (44, 82), (28, 86), (34, 64), (50, 32))
    paint(c, vane, (120, 200, 230))
    detail(c, stroke([(26, 92), (52, 52), (74, 14)], 2.6), (60, 120, 170))
    for t in (0.35, 0.55, 0.75):
        x, y = 26 + (74 - 26) * t, 92 + (14 - 92) * t
        detail(c, stroke([(x, y), (x - 10, y - 2)], 1.6), (60, 120, 170))


def stat_digestion(c):
    belly = union(ellipse(48, 58, 30, 28), ellipse(62, 30, 12, 14))
    paint(c, belly, (250, 168, 150))
    swirl = arc_stroke(46, 58, 12, 0, 300, 3.4)
    detail(c, swirl, (200, 100, 92))


def stat_beak(c):
    # Pico ganchudo de rapaz, de perfil: la punta se curva hacia abajo.
    lower = poly((18, 60), (52, 58), (66, 62), (58, 72), (22, 70))
    paint(c, lower, (214, 170, 80))
    upper = poly((14, 34), (40, 22), (62, 22), (78, 32), (88, 50), (88, 70), (80, 88),
                 (76, 70), (70, 58), (52, 54), (14, 58))
    paint(c, upper, GOLD)
    paint(c, rrect(10, 28, 26, 62, 6), (240, 150, 70), line=2.4)  # cera (base del pico)
    detail(c, ellipse(36, 36, 2.6, 2.6), GOLD_DARK)


def stat_trust(c):  # confianza (corazón)
    heart = union(ellipse(34, 38, 20, 20), ellipse(66, 38, 20, 20), poly((14, 44), (86, 44), (50, 88)))
    paint(c, heart, RED)
    detail(c, ellipse(30, 32, 6, 4), WHITE, 0.6)


# Stats de combate e información.
def info(c):
    paint(c, ellipse(50, 50, 40, 40), BLUE)
    paint(c, ellipse(50, 28, 7, 7), WHITE, line=2.2, highlight=False)
    paint(c, rrect(43, 40, 57, 76, 5), WHITE, line=2.2, highlight=False)


def combat_damage(c):
    # Tres zarpazos.
    for dx in (-18, 0, 18):
        slash = poly((30 + dx, 84), (40 + dx, 84), (74 + dx, 16), (66 + dx, 14))
        paint(c, slash, RED, line=2.4)


def combat_speed(c):
    for i, y in enumerate((34, 50, 66)):
        detail(c, stroke([(8 + i * 4, y), (34 + i * 4, y)], 4), (170, 200, 230))
    wing = poly((34, 72), (52, 30), (76, 16), (90, 22), (80, 34), (90, 38), (78, 50), (86, 56), (66, 66))
    paint(c, wing, SKY)
    detail(c, stroke([(44, 66), (76, 28)], 2.2), BLUE)


def combat_range(c):
    # Diana: alcance.
    paint(c, ellipse(50, 50, 40, 40), WHITE)
    paint(c, ellipse(50, 50, 30, 30), RED, highlight=False)
    paint(c, ellipse(50, 50, 20, 20), WHITE, highlight=False)
    paint(c, ellipse(50, 50, 9, 9), RED, line=2.2, highlight=False)


# Elementos y tipos.
def element_fire(c):
    outer = union(ellipse(50, 66, 28, 26), poly((24, 60), (34, 30), (44, 44), (52, 8), (66, 40), (72, 28), (78, 62)))
    paint(c, outer, ORANGE)
    inner = union(ellipse(50, 72, 15, 15), poly((36, 70), (46, 48), (52, 58), (60, 42), (66, 70)))
    paint(c, inner, GOLD, line=2.0, highlight=False)


def element_air(c):
    # Viento: líneas con un remolino al final.
    for y, x1 in ((30, 66), (52, 78), (74, 58)):
        gust = union(stroke([(12, y), (x1, y)], 7), arc_stroke(x1, y - 9, 9, 90, -190, 7))
        paint(c, gust, (196, 228, 246), line=2.2, highlight=False)


def element_water(c):
    # Olas: dos líneas onduladas.
    for y, col in ((40, SKY), (66, BLUE)):
        pts = [(10 + i * 2, y + 7 * math.sin(i * 2 * math.pi / 20)) for i in range(41)]
        paint(c, stroke(pts, 9), col, line=2.4, highlight=False)


def element_earth(c):
    rock = poly((14, 78), (24, 44), (46, 24), (72, 30), (88, 58), (80, 82), (40, 88))
    paint(c, rock, (164, 128, 92))
    detail(c, stroke([(44, 40), (54, 58), (48, 74)], 2.4), (110, 82, 58))
    paint(c, ellipse(30, 82, 10, 6), GREEN, line=2.0, highlight=False)


def type_light(c):
    paint(c, star(50, 50, 42, 20, n=8, rot=-90), (255, 232, 120))
    paint(c, ellipse(50, 50, 16, 16), WHITE, line=2.2)


def type_darkness(c):
    moon = minus(ellipse(50, 52, 36, 36), ellipse(66, 40, 30, 30))
    paint(c, moon, (120, 80, 190))
    for x, y in ((70, 64), (80, 46), (62, 80)):
        detail(c, star(x, y, 5, 2), (230, 220, 255))


def type_elemental(c):
    for (dx, dy), col in zip(((0, -18), (18, 0), (0, 18), (-18, 0)), (ORANGE, SKY, BLUE, (164, 128, 92))):
        paint(c, ellipse(50 + dx, 50 + dy, 15, 15), col, line=2.4)
    paint(c, star(50, 50, 14, 6, n=4, rot=-90), WHITE, line=2.0)


# Comidas.
def food_seeds(c):
    for x, y, r in ((36, 62, 0), (58, 58, 30), (46, 40, -20), (68, 76, 10), (30, 82, -30), (52, 80, 40)):
        seed = ellipse(x, y, 8, 12)
        paint(c, seed.rotate(r, center=(x * U, y * U)), (226, 184, 96), line=2.2)


def food_nuts(c):
    paint(c, ellipse(50, 62, 24, 28), (196, 132, 64))  # bellota
    cap = minus(ellipse(50, 42, 30, 18), rrect(0, 46, 100, 100, 0))
    paint(c, union(cap, rrect(20, 38, 80, 48, 6)), (128, 84, 44))
    paint(c, stroke([(50, 24), (56, 12)], 5), (110, 70, 36), line=2.0, highlight=False)


def food_fruit(c):
    paint(c, stroke([(36, 56), (56, 18), (70, 60)], 3.5), GREEN_DARK, line=1.8, highlight=False)
    paint(c, ellipse(62, 18, 12, 6), LEAF, line=2.0)
    paint(c, ellipse(34, 66, 18, 18), RED)
    paint(c, ellipse(68, 70, 18, 18), RED)


def food_insects(c):
    for y in (46, 58, 70):
        detail(c, stroke([(20, y), (80, y + 4)], 3), OUTLINE)
    paint(c, ellipse(50, 60, 22, 28), (76, 150, 96))
    detail(c, stroke([(50, 34), (50, 88)], 2.6), OUTLINE)
    paint(c, ellipse(50, 30, 12, 10), (60, 70, 80))
    for x in (42, 58):
        detail(c, stroke([(x, 22), (x + (x - 50), 10)], 2.4), OUTLINE)


def food_worms(c):
    pts = [(14 + i * 2.4, 56 + 14 * math.sin(i * 2 * math.pi / 15)) for i in range(31)]
    paint(c, stroke(pts, 14), PINK)
    detail(c, ellipse(84, 52, 2.4, 2.4), OUTLINE)
    for i in (8, 14, 20):
        x, y = pts[i]
        detail(c, stroke([(x - 1, y - 6), (x + 1, y + 6)], 1.8), (200, 90, 120))


def food_nectar(c):
    for i in range(5):
        a = math.radians(i * 72 - 90)
        paint(c, ellipse(50 + 20 * math.cos(a), 46 + 20 * math.sin(a), 14, 14), (250, 150, 200), line=2.4)
    paint(c, ellipse(50, 46, 11, 11), GOLD, line=2.2)
    paint(c, stroke([(50, 66), (50, 94)], 5), GREEN_DARK, line=2.0, highlight=False)


def food_carrion(c):
    bone = union(stroke([(22, 78), (66, 34)], 9), ellipse(16, 76, 7, 7), ellipse(24, 86, 7, 7))
    paint(c, bone, CREAM, line=2.4)
    paint(c, ellipse(66, 38, 26, 22), (196, 84, 72))
    detail(c, ellipse(62, 34, 9, 6), (236, 150, 140))


# Rareza (tier): escudo de nivel de cada color y estrella.
TIER_COLORS = {
    1: ((150, 158, 170), (196, 204, 214)),  # común: gris
    2: ((80, 170, 72), (150, 216, 120)),  # poco común: verde
    3: ((60, 128, 222), (130, 186, 245)),  # raro: azul
    4: ((150, 96, 214), (186, 140, 240)),  # épico: morado
    5: ((232, 168, 30), (255, 220, 110)),  # legendario: dorado
}


def tier_shield(tier):
    outer, inner = TIER_COLORS[tier]

    def draw(c):
        paint(c, poly((50, 6), (88, 18), (84, 58), (50, 94), (16, 58), (12, 18)), outer)
        paint(c, poly((50, 16), (78, 25), (75, 56), (50, 84), (25, 56), (22, 25)), inner, gradient=0.2, line=2.2)
    return draw


def star_icon(c):
    paint(c, star(50, 52, 42, 19), GOLD)
    detail(c, ellipse(40, 40, 6, 4), WHITE, 0.6)


# Sprites de escena.
def pet_egg(c):
    # Huevo de pájaro (azul claro con motas) en un nido: lo que ponen las parejas.
    paint(c, ellipse(50, 80, 40, 14), WOOD_DARK)
    paint(c, ellipse(50, 52, 24, 31), (196, 228, 236))
    for x, y, r in ((42, 40, 3.2), (58, 52, 4.2), (46, 62, 2.6), (60, 36, 2.2), (38, 54, 2.2)):
        detail(c, ellipse(x, y, r, r * 0.8), (96, 120, 110))
    nest_front = minus(ellipse(50, 82, 40, 14), ellipse(50, 72, 30, 9))
    paint(c, nest_front, WOOD)
    for x0, x1, y in ((16, 42, 86), (38, 66, 90), (56, 84, 84)):
        detail(c, stroke([(x0, y), (x1, y - 3)], 1.8), WOOD_DARK)


ICONS = {
    "coin": coin, "egg": egg, "biome": biome, "menu": menu, "level": level,
    "feed": feed, "sleep": sleep, "wake": wake, "clean": clean, "defend": defend,
    "release": release, "plus": plus, "arrow_down": arrow_down, "close": close, "play": play,
    "stat_hunger": stat_hunger, "stat_energy": stat_energy, "stat_fun": stat_fun,
    "stat_hygiene": stat_hygiene, "stat_social": stat_social, "stat_climate": stat_climate,
    "stat_plumage": stat_plumage, "stat_digestion": stat_digestion, "stat_beak": stat_beak,
    "stat_confidence": stat_trust, "info": info, "combat_damage": combat_damage, "combat_speed": combat_speed, "combat_range": combat_range,
    "element_fire": element_fire, "element_air": element_air, "element_water": element_water,
    "element_earth": element_earth, "type_light": type_light, "type_darkness": type_darkness,
    "type_elemental": type_elemental,
    "food_seeds": food_seeds, "food_nuts": food_nuts, "food_fruit": food_fruit,
    "food_insects": food_insects, "food_worms": food_worms, "food_nectar": food_nectar,
    "food_carrion": food_carrion, "pet_egg": pet_egg, "star": star_icon,
    **{f"tier_{t}": tier_shield(t) for t in TIER_COLORS},
}


def build(name):
    c = new_canvas()
    ICONS[name](c)
    return c.resize((OUT, OUT), Image.LANCZOS)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for name in sys.argv[2:] or list(ICONS):
        # Los sprites de escena ("pet_*") conservan su nombre; el resto llevan "ui_".
        file = name if name.startswith("pet_") else f"ui_{name}"
        build(name).save(os.path.join(out, f"{file}.png"), optimize=True)
        print(file)


if __name__ == "__main__":
    main()
