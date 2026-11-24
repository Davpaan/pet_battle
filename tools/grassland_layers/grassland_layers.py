# Prepara las capas del bioma pradera a partir de los dibujos de esta carpeta:
#   cielo.png        -> biome_grassland_sky.png     (cielo y nubes; se desplaza sin parar)
#   fondo.png        -> biome_grassland_hills.png   (montañas y lago; se mece, parallax)
#   primer_plano.png -> biome_grassland_terrain.png (prado y árboles; fijo, con viento)
#   + biome_grassland_wind.png (mapa de viento) y biome_grassland.png (todo junto, miniatura).
#
# Las capas traen manchas y restos donde la capa de delante tapaba el dibujo. Quietas no se
# ven, pero al moverse asoman, así que se rehacen:
#   - Cielo: el juego lo desplaza en horizontal, así que cualquier punto de la franja alta acaba
#     viéndose. En esa franja, lo que tapaban el fondo y el prado (y los restos verdes u oscuros
#     de árboles) se sustituye por el degradado del cielo de esa fila, con transición suave.
#   - Fondo: solo se mece unos píxeles; lo tapado por el prado se rellena con el píxel visible
#     más cercano de la misma fila, para que no asome la mancha junto a los árboles.
#
# Mapa de viento (a 1/4 de resolución, se muestrea suavizado): rojo = cuánto se mecen los
# árboles (crece con la altura sobre el prado), verde = cuánto ondea la hierba (crece hacia
# la cámara). El shader de BiomeScene desplaza cada píxel del primer plano según estos pesos.
#
# Uso: python grassland_layers.py <carpeta de drawable> [vista_previa.png]
import os
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

here = os.path.dirname(os.path.abspath(__file__))
out_dir = sys.argv[1]
preview_path = sys.argv[2] if len(sys.argv) > 2 else None


def load(name):
    return np.array(Image.open(os.path.join(here, name)).convert("RGBA")).astype(np.float64)


sky = load("cielo.png")
back = load("fondo.png")
front = load("primer_plano.png")
H, W = front.shape[:2]

front_opaque = front[..., 3] > 127
back_opaque = back[..., 3] > 127

# ---------------------------------------------------------------- cielo
SKY_BAND = 340  # filas que pueden llegar a verse al desplazarse (por encima de las montañas)
FEATHER = 28.0  # px de transición entre lo original y el degradado

rgb = sky[..., :3]
r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
# Restos de árboles y montañas: verdes, u oscuros (el cielo más oscuro es azul intenso).
foreign = ((g > b + 4) & (g > r)) | (rgb.max(2) < 120)
foreign = ndi.binary_dilation(foreign, iterations=5)
hidden = ndi.binary_dilation(front_opaque | back_opaque, iterations=8) | foreign
authentic = ~hidden

# Degradado: por fila, mediana del cielo despejado (azul) visible; se suaviza en vertical.
clear = authentic & (b > r + 45)
rows = np.full((H, 3), np.nan)
for y in range(SKY_BAND + 40):
    m = clear[y]
    if m.sum() >= 12:
        rows[y] = np.median(rgb[y][m], axis=0)
known = ~np.isnan(rows[:, 0])
ys = np.arange(H)
for c in range(3):
    rows[:, c] = np.interp(ys, ys[known], rows[known, c])
rows = ndi.gaussian_filter1d(rows, 6, axis=0)
gradient = np.repeat(rows[:, None, :], W, axis=1)

# Peso de lo original: 1 lejos de lo oculto, 0 dentro, con rampa de FEATHER px.
dist = ndi.distance_transform_edt(authentic)
keep = np.clip(dist / FEATHER, 0, 1)[..., None]
clean = rgb * keep + gradient * (1 - keep)
sky_out = rgb.copy()
sky_out[:SKY_BAND] = clean[:SKY_BAND]
# Debajo de la franja (siempre tapado) basta el degradado, para que pese poco el PNG.
sky_out[SKY_BAND:] = gradient[SKY_BAND:]
# Las dos últimas columnas del dibujo salen más oscuras: en la junta del espejo (el cielo se
# repite volteado) se verían como una raya. Se copian de la columna sana más cercana.
sky_out[:, -2:] = sky_out[:, -3:-2]
sky_img = np.dstack([sky_out, np.full((H, W), 255.0)])

# ---------------------------------------------------------------- fondo (montañas y lago)
back_visible = back_opaque & ~ndi.binary_dilation(front_opaque, iterations=3)
back_out = back.copy()
# Para cada píxel tapado, el visible más cercano de su fila (a izquierda o derecha).
idx = np.arange(W)
for y in range(H):
    vis = back_visible[y]
    need = back_opaque[y] & ~vis
    if not need.any() or not vis.any():
        continue
    xs = idx[vis]
    pos = np.clip(np.searchsorted(xs, idx), 1, len(xs) - 1)
    left, right = xs[pos - 1], xs[pos]
    nearest = np.where(np.abs(idx - left) <= np.abs(right - idx), left, right)
    back_out[y, need, :3] = back[y, nearest[need], :3]
# Suavizado horizontal solo en lo rellenado, para que no se vean rayas.
filled = back_opaque & ~back_visible
blur = ndi.uniform_filter1d(back_out[..., :3], 9, axis=1)
back_out[..., :3] = np.where(filled[..., None], blur, back_out[..., :3])

# ---------------------------------------------------------------- mapa de viento
# Borde superior del prado (y por columna), trazado a mano sobre el dibujo.
MEADOW_EDGE = [(0, 495), (200, 532), (400, 560), (560, 586), (700, 602), (800, 632),
               (950, 655), (1100, 672), (1284, 690)]
TREE_SWAY_HEIGHT = 560.0  # altura sobre el prado a la que una copa se mece del todo
ex, ey = zip(*MEADOW_EDGE)
edge = np.interp(np.arange(W), ex, ey)[None, :]
Y = np.arange(H)[:, None].astype(float)
tree_w = np.clip((edge - Y) / TREE_SWAY_HEIGHT, 0, 1) ** 1.2
grass_w = np.clip((Y - edge) / (H - edge), 0, 1) ** 0.8
wind = np.zeros((H, W, 3))
wind[..., 0] = tree_w
wind[..., 1] = grass_w
small = (W // 4, H // 4)
wind_img = Image.fromarray((wind * 255).round().astype(np.uint8), "RGB").resize(small, Image.BILINEAR)

# ---------------------------------------------------------------- salida
def save(arr, name, mode="RGBA"):
    img = Image.fromarray(np.clip(arr, 0, 255).round().astype(np.uint8), mode)
    img.save(os.path.join(out_dir, name), optimize=True)
    return img


sky_png = save(sky_img, "biome_grassland_sky.png").convert("RGB")
back_png = save(back_out, "biome_grassland_hills.png")
front_png = save(front, "biome_grassland_terrain.png")
wind_img.save(os.path.join(out_dir, "biome_grassland_wind.png"), optimize=True)
full = sky_png.convert("RGBA")
full.alpha_composite(back_png)
full.alpha_composite(front_png)
full.convert("RGB").save(os.path.join(out_dir, "biome_grassland.png"), optimize=True)
print(f"capas {W}x{H}, viento {small[0]}x{small[1]}")

if preview_path:
    # Arriba: cielo limpio y fondo limpio sobre magenta. Abajo: compuesto y mapa de viento.
    mag = Image.new("RGBA", (W, H), (255, 0, 255, 255))
    b2 = mag.copy(); b2.alpha_composite(back_png)
    wv = wind_img.resize((W, H)).convert("RGBA")
    overlay = full.copy().convert("RGBA")
    overlay = Image.blend(overlay, wv, 0.45)
    sheet = Image.new("RGB", (W * 2, H * 2))
    sheet.paste(sky_png, (0, 0)); sheet.paste(b2.convert("RGB"), (W, 0))
    sheet.paste(full.convert("RGB"), (0, H)); sheet.paste(overlay.convert("RGB"), (W, H))
    sheet.resize((W, H)).save(preview_path)
