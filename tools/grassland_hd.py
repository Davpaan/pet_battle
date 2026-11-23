# Genera el fondo biome_grassland en alta definición (x4) a partir del sprite original de
# 576x324: reescala con contornos suavizados (sin escalones), degradado en el cielo, luz en
# las nubes, colinas con volumen y arbolado, y un prado con perspectiva, sombras de nubes,
# briznas y flores que crecen hacia el primer plano (falso 3D).
# Además de la imagen completa, exporta tres capas del mismo tamaño para parallax:
#   <salida>_terrain.png (prado, estático), <salida>_hills.png (colinas) y <salida>_sky.png (cielo y nubes).
# Uso: python grassland_hd.py original.png salida.png
import sys, math, random
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter, zoom

S = 4
src_path, out_path = sys.argv[1], sys.argv[2]
rng = random.Random(21)
nrng = np.random.default_rng(21)

orig = np.array(Image.open(src_path).convert("RGB")).astype(int)
bh, bw, _ = orig.shape
H, W = bh * S, bw * S

# ---------------------------------------------------------------- reescalado suave
palette = sorted({tuple(c) for c in orig.reshape(-1, 3)})
idx1 = np.zeros((bh, bw), int)
for i, c in enumerate(palette):
    idx1[(orig == c).all(axis=2)] = i
best = np.full((H, W), -1.0); cls = np.zeros((H, W), int)
for i in range(len(palette)):
    m = zoom((idx1 == i).astype(np.float32), S, order=1)
    m = gaussian_filter(m, 2.0)
    upd = m > best
    best[upd] = m[upd]; cls[upd] = i
pal = np.array(palette)
img = pal[cls].astype(int)                    # imagen x4 con bordes suaves
def is_col(c):
    return (img == c).all(axis=2)

SKY = (101,186,227)
CLOUD = [(232,242,244), (212,237,244), (188,225,241), (166,216,238)]
HILL = [(148,203,163), (130,188,156), (102,163,145)]
GRASS = [(82,177,85), (79,167,96), (103,183,91), (54,159,92), (142,196,76)]

sky = is_col(SKY)
cloud = np.zeros((H, W), bool)
for c in CLOUD: cloud |= is_col(c)
hillc = np.full((H, W), -1)
for i, c in enumerate(HILL): hillc[is_col(c)] = i
grass = np.zeros((H, W), bool)
for c in GRASS: grass |= is_col(c)

out = img.copy()
Y, X = np.mgrid[0:H, 0:W]
bayer = np.array([[0,8,2,10],[12,4,14,6],[3,11,1,9],[15,7,13,5]]) / 16.0
B = bayer[Y % 4, X % 4]

# ---------------------------------------------------------------- cielo
# Degradado por bandas: más profundo arriba, más claro hacia el horizonte (con tramado en el borde)
SKY_BANDS = [(70,156,212), (84,168,219), (101,186,227), (118,196,231), (136,205,235)]
edges = np.array([0.0, 0.14, 0.30, 0.48, 0.60]) * H
f = np.interp(Y, edges, np.arange(5)) + (B - 0.5) * 0.9
band = np.clip(np.round(f), 0, 4).astype(int)
skycol = np.array(SKY_BANDS)[band]
out[sky] = skycol[sky]

# ---------------------------------------------------------------- nubes
# Borde superior iluminado (el sol viene de arriba) y base algo más sombreada
up = np.roll(sky, 3, axis=0); up[:3] = False
rim = cloud & up
out[rim] = (246,250,250)
down = np.roll(sky | (hillc >= 0), -3, axis=0)
under = cloud & down & ~rim
out[under] = (176,214,234)

sky_stage = out.copy()          # estado tras pintar cielo y nubes

# ---------------------------------------------------------------- colinas
HRIM  = [(172,219,184), (150,203,169), (120,177,155)]
HMID  = [(160,212,174), (140,196,163), (111,170,150)]
HBASE = [(134,193,156), (117,176,148), (90,148,135)]
HDARK = [(108,172,132), (96,160,128), (78,138,118)]
HHI   = [(132,192,146), (118,180,140), (96,156,130)]

for x in range(W):
    col = hillc[:, x]
    y = 0
    while y < H:
        c = col[y]
        if c < 0: y += 1; continue
        y0 = y
        while y < H and col[y] == c: y += 1
        y1 = y
        run = y1 - y0
        above = col[y0 - 1] if y0 > 0 else -1
        lit_top = above < 0 or above > c
        wav = 2 * math.sin(x * 0.025) + math.sin(x * 0.09 + 1.1)
        for yy in range(y0, y1):
            dt, db = yy - y0, y1 - 1 - yy
            color = None
            if lit_top:
                if dt < 3: color = HRIM[c]
                elif dt < 14 + wav * 2:
                    color = HMID[c] if (dt < 10 or (x + yy) % 2 == 0) else None
            if color is None and run > 20 and db < 9 + wav * 1.5 + (B[yy, x] * 2):
                color = HBASE[c]
            if color is not None:
                out[yy, x] = color

def hill_ok(x, y, c, w, h):
    if x < 0 or x + w >= W or y - h < 0 or y + 2 >= H: return False
    return bool((hillc[y - h:y + 2, x:x + w] == c).all())

# Matas lejanas: manchas redondeadas con luz arriba-izquierda y sombra al pie
for _ in range(600):
    x = rng.randrange(W); y = rng.randrange(H)
    c = hillc[y, x]
    if c < 0: continue
    w = rng.choice([6, 8, 10, 12, 16]); h = max(3, w // 2 - rng.randrange(2))
    if not hill_ok(x, y, c, w, h): continue
    for dx in range(w):
        u = (dx + 0.5) / w * 2 - 1
        hh = int(h * math.sqrt(max(0, 1 - u * u)) + 0.5)
        for dy in range(hh):
            v = dy / max(1, hh)
            col = HHI[c] if (v > 0.55 and u < 0.1) else HDARK[c]
            out[y - dy, x + dx] = col
    for dx in range(1, w - 1):
        out[y + 1, x + dx] = HBASE[c]

# Arbolitos lejanos (copa redonda y tronco) en las colinas claras
for _ in range(70):
    x = rng.randrange(W); y = rng.randrange(H)
    c = hillc[y, x]
    if c != 0: continue
    r = rng.choice([5, 6, 7, 9])
    if not hill_ok(x - r, y, c, 2 * r + 1, 2 * r + 4): continue
    cyc = y - r - 3
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy <= r * r + rng.random() * r:
                lit = dx + dy < -r * 0.4
                out[cyc + dy, x + dx] = (130,190,156) if lit else (106,168,140)
    out[cyc + r: y + 1, x] = (86,130,120)
    out[y + 1, x - r // 2: x + r] = HBASE[0]

# Hierbecitas en las crestas contra el cielo
for x in range(W):
    ys = np.where((hillc[1:, x] >= 0) & (hillc[:-1, x] < 0) & sky[:-1, x])[0]
    for y in ys:
        if rng.random() < 0.18:
            c = hillc[y + 1, x]
            for j in range(rng.choice([2, 3, 4, 5])):
                out[y - j, x] = HRIM[c]

hill_stage = out.copy()         # estado tras pintar las colinas

# ---------------------------------------------------------------- prado
RAMP_G = np.array([(160,208,158),(130,198,128),(103,183,91),(82,177,85),(66,165,84),
                   (52,150,80),(40,130,74),(31,110,66),(24,90,58),(18,72,50)])
RAMP_Y = np.array([(178,216,140),(162,208,100),(142,196,76),(122,186,70),(100,172,68),
                   (80,156,66),(62,136,62),(48,116,58),(38,96,54),(30,78,48)])
BASE_IDX = {(103,183,91):(0,2),(82,177,85):(0,3),(79,167,96):(0,4),
            (54,159,92):(0,5),(142,196,76):(1,2)}

# Borde superior del prado en cada columna
top = np.full(W, H - 1)
search = grass.copy(); search[:200 * S] = False
has = search.any(axis=0)
top[has] = np.argmax(search[:, has], axis=0)
horizon = int(top.min())
cx = W / 2
vy = horizon - 60 * S

# Textura del prado: la del original reescalado suave
tex_kind = np.full((H, W), 0); tex_idx = np.full((H, W), 3)
for c, (k, i) in BASE_IDX.items():
    m = is_col(c); tex_kind[m] = k; tex_idx[m] = i

ys = np.arange(horizon, H)
YY, XX = np.meshgrid(ys, np.arange(W), indexing="ij")
TP = top[XX]
valid = YY >= TP
t = np.clip((YY - TP) / np.maximum(1, H - 1 - TP), 0, 1)
s = t ** 0.5
# Ondulación del muestreo para que las manchas tengan bordes orgánicos
warp_y = (5 * np.sin(XX * 0.013 + YY * 0.021) + 3 * np.sin(XX * 0.041 - YY * 0.017 + 2.0)) * (0.3 + t)
warp_x = (14 * np.sin(YY * 0.035 + XX * 0.006) + 7 * np.sin(YY * 0.09 + XX * 0.023 + 1.0)) * (0.3 + t)
sy = np.clip((TP + s * (H - 1 - TP) + warp_y).astype(int), 0, H - 1)
scale = 1.0 + 1.8 * (1 - t) ** 1.6
sx = (cx + (XX - cx) * scale + warp_x).astype(int) % (2 * W)
sx = np.where(sx >= W, 2 * W - 1 - sx, sx)
kind = tex_kind[sy, sx]; base_i = tex_idx[sy, sx]
nongrass = ~grass[sy, sx]
base_i = np.where(nongrass, 3, base_i); kind = np.where(nongrass, 0, kind)

jag = nrng.choice([0, 0, 1, 2, 3, 4, 5, 6], size=W)
tj = t + jag[XX] * 0.003
shift = np.round(-2.2 + 3.8 * tj ** 0.9)

# Ondulaciones del terreno (volumen), iluminadas desde arriba a la izquierda
z = YY - vy
gx = (XX - cx) / z * 2.2
gz = 150.0 * S / z
a1 = gx * 0.8 + gz * 0.9; a2 = gx * 1.5 - gz * 1.1 + 1.3
dxh = 0.48 * np.cos(a1) + 0.6 * np.cos(a2)
dzh = 0.54 * np.cos(a1) - 0.44 * np.cos(a2)
shade = (dxh * 0.55 - dzh * 0.45) * np.minimum(1.0, t * 2.5)
Bm = bayer[YY % 4, XX % 4] - 0.5
shift += np.where(shade + Bm * 0.12 > 0.45, 1, 0)
shift -= np.where((shade + Bm * 0.12 < -0.45) & (t > 0.25), 1, 0)

# Sombras de nubes sobre el prado (manchas grandes en coordenadas de suelo)
cs = (np.sin(gx * 0.55 + 0.7) * np.sin(gz * 0.8 + 2.0) + 0.6 * np.sin(gx * 1.3 - gz * 0.5))
shift += np.where((cs + Bm * 0.15 > 0.75) & (t > 0.08), 1, 0)

lo = np.round(2.0 * t) + 1 - (t < 0.12)
fi = np.clip(np.maximum(lo, base_i + shift), 0, 9).astype(int)
mcol = np.where(kind[..., None] == 1, RAMP_Y[fi], RAMP_G[fi])
region = out[horizon:H]
region[valid] = mcol[valid]

# Franja de neblina en el horizonte
for x in range(W):
    tp = top[x]
    n = 5 + jag[x]
    out[tp:tp + 3, x] = (148,203,163)
    out[tp + 3:tp + n, x] = (130,198,128)

# Índice de color del prado para sombrear briznas sobre lo que haya debajo
def ramp_of(c):
    c = tuple(int(v) for v in c)
    for k, r in enumerate((RAMP_G, RAMP_Y)):
        for i, rc in enumerate(r):
            if tuple(rc) == c: return k, i
    return 0, 3
def rc(k, i):
    r = RAMP_G if k == 0 else RAMP_Y
    return r[max(0, min(len(r) - 1, i))]

def put(x, y, c):
    if 0 <= x < W and 0 <= y < H and y >= top[x] - 10:
        out[y, x] = c

items = []
for _ in range(19000):
    x = rng.randrange(W); tt = rng.random() ** 0.6
    y = int(top[x] + tt * (H - 1 - top[x]))
    if y > top[x] + 6: items.append((y, x, tt, 0))
for _ in range(900):
    x = rng.randrange(W); tt = rng.random() ** 0.8
    y = int(top[x] + tt * (H - 1 - top[x]))
    if y > top[x] + 12: items.append((y, x, tt, 1))
items.sort()

PETALS = [((248,246,236),(236,170,60)), ((252,214,88),(214,140,40)),
          ((240,238,250),(236,170,60)), ((246,170,190),(250,226,120))]
for y, x, tt, kind_ in items:
    k, i = ramp_of(out[y, x])
    if kind_ == 0:
        # Brizna afilada: base ancha y oscura, punta fina y con luz
        h = 2 + int(tt ** 1.4 * 30 + rng.random() * 9 * tt)
        w = 1 + int(tt * 2.2)
        lean = rng.choice([-1, -1, 0, 1, 1]) * (0.5 + rng.random())
        for j in range(h):
            f = j / max(1, h - 1)
            xx = x + int(round(lean * f * f * h * 0.3))
            wj = max(1, int(round(w * (1 - f))))
            for q in range(wj):
                if f < 0.3: c = rc(k, i + 3) if q == 0 else rc(k, i + 2)
                elif f < 0.75: c = rc(k, i + 1) if q < wj - 1 or wj == 1 else rc(k, i + 2)
                else: c = rc(k, i - 1)
                put(xx + q, y - j, c)
    else:
        petal, core = rng.choice(PETALS)
        shadow = tuple(int(v * 0.8) for v in petal)
        if tt < 0.25:
            put(x, y, petal)
        elif tt < 0.55:
            for dx, dy in ((0,0),(1,0),(0,-1),(1,-1)): put(x + dx, y + dy - 2, petal)
            put(x + 1, y - 2, core); put(x, y, rc(k, i + 2)); put(x, y - 1, rc(k, i + 2))
        else:
            r = 2 if tt < 0.8 else 3
            stem = int(4 + tt * 6)
            for j in range(stem): put(x, y - j, rc(k, i + 2 if j < stem // 2 else i + 1))
            cy = y - stem - r
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    if dx * dx + dy * dy <= r * r:
                        put(x + dx, cy + dy, shadow if dx + dy > r * 0.6 else petal)
            put(x, cy, core); put(x - 1, cy - 1, core) if r == 3 else None
            put(x + 1, y + 1, rc(k, i + 3)); put(x - 1, y + 1, rc(k, i + 3))

# Puntas de hierba en la cresta del prado, recortadas contra las colinas
for x in range(W):
    if rng.random() < 0.3:
        for j in range(1, rng.choice([2, 3, 4, 5])):
            put(x, top[x] - j, (130,198,128))

# Capa delantera: hierba alta y oscura en las esquinas inferiores (parallax)
for x in range(W):
    edge = abs(x - cx) / cx
    hmax = 10 + int(110 * edge ** 2.0)
    if rng.random() < 0.75:
        h = int(hmax * (0.45 + rng.random() * 0.65))
        lean = (-1 if x < cx else 1) * (0.6 + rng.random())
        w = 2 + int(edge * 3)
        for j in range(h):
            f = j / max(1, h)
            xx = x + int(round(lean * f * f * h * 0.3))
            wj = max(1, int(round(w * (1 - f))))
            for q in range(wj):
                c = (16,62,44) if f < 0.6 else ((24,84,56) if f < 0.9 else (40,110,66))
                if 0 <= xx + q < W: out[H - 1 - j, xx + q] = c

Image.fromarray(out.astype(np.uint8), "RGB").save(out_path)

# ---------------------------------------------------------------- capas
stem = out_path[:-4] if out_path.lower().endswith(".png") else out_path
def rgba(rgb, mask):
    a = np.zeros((H, W, 4), np.uint8)
    a[..., :3] = rgb; a[..., 3] = np.where(mask, 255, 0)
    return Image.fromarray(a, "RGBA")

# Capa 1, terreno: el prado y todo lo que se pintó encima (briznas, flores, puntas)
terr = (Y >= top[X]) | (out != hill_stage).any(axis=2)
rgba(out, terr).save(stem + "_terrain.png", optimize=True)

# Capa 2, colinas: se prolongan hacia abajo por detrás del prado para que no queden huecos
hill = (hillc >= 0) | (hill_stage != sky_stage).any(axis=2)
hl = hill_stage.copy(); hmask = hill.copy()
for x in range(W):
    ys = np.where(hill[:, x])[0]
    if len(ys):
        y1 = ys.max()
        hl[y1 + 1:, x] = (102,163,145); hmask[y1 + 1:, x] = True
rgba(hl, hmask).save(stem + "_hills.png", optimize=True)

# Capa 3, cielo: opaca; lo que tapaban colinas y prado se rellena continuando el degradado
# del cielo, así las nubes muestran su base en vez de cortarse
skyish = (sky | cloud) & ~hill
sk = sky_stage.copy()
sk[~skyish] = skycol[~skyish]
rgba(sk, np.ones((H, W), bool)).save(stem + "_sky.png", optimize=True)

# Comprobación: las tres capas superpuestas reproducen la imagen completa
comp = Image.open(stem + "_sky.png")
comp.alpha_composite(Image.open(stem + "_hills.png"))
comp.alpha_composite(Image.open(stem + "_terrain.png"))
diff = (np.array(comp.convert("RGB")).astype(int) != out).any(axis=2).sum()
print("píxeles distintos al recomponer:", int(diff))
print("size", (W, H), "horizon", horizon)
