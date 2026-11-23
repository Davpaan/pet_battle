# Genera las hojas de sprites de los enemigos del modo defensa a partir de sus dibujos:
#   gusano   (worm_source.jpg)      -> enemy_slime.png
#   mariposa (butterfly_source.jpg) -> enemy_butterfly.png
#
# Los JPG traen el fondo de cuadros "pintado", no transparencia real: como el fondo es gris
# puro y los enemigos tienen color, se separa por croma (max(RGB) - min(RGB)) y luego se
# rellenan los huecos interiores (blancos y negros de los ojos). Las sombras grises se
# descartan: el juego dibuja las suyas.
#
# Cada hoja tiene frames cuadrados de FRAME px, mirando a la izquierda (los dibujos miran a
# la derecha y se voltean), con todas las poses a la misma escala:
#   fila 0: movimiento (en bucle)
#   fila 1: muerte
#   fila 2: dolor (se muestra mientras parpadea al recibir un golpe)
# Para que la animación no dé saltos, cada pose se alinea por un ancla común:
#   "suelo":  centro horizontal del cuerpo y su base sobre la línea ground_y.
#   "cuerpo": centro de la parte marrón (el cuerpo del insecto) en el punto (FRAME/2, body_y);
#             sirve para las poses en vuelo, donde las alas suben y bajan.
#
# Uso: python enemy_sheet.py gusano|mariposa fuente.jpg salida.png [vista_previa.png]
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

FRAME = 128
MARGIN = 4  # margen lateral mínimo dentro del frame
CHROMA_MIN = 14  # por debajo, píxel gris = fondo de cuadros

# Recuadros (x0, y0, x1, y1) de cada pose en el dibujo original, con su ancla.
ENEMIES = {
    "gusano": {
        "ground_y": 116,
        "rows": [
            # Arrastrarse.
            [((20, 45, 280, 205), "suelo"), ((318, 70, 535, 205), "suelo"), ((570, 45, 800, 205), "suelo"),
             ((838, 100, 1068, 205), "suelo"), ((1108, 45, 1362, 205), "suelo")],
            # Muerte: golpe con estrellas, tumbado, derritiéndose, charco.
            [((590, 270, 845, 465), "suelo"), ((200, 620, 425, 728), "suelo"),
             ((545, 640, 830, 730), "suelo"), ((925, 660, 1175, 726), "suelo")],
            # Dolor: cara de susto.
            [((975, 310, 1195, 465), "suelo")],
        ],
    },
    "mariposa": {
        "ground_y": 120,
        "body_y": 64,
        "rows": [
            # Vuelo: aleteo de lado, de frente con las alas abiertas y vuelta.
            [((65, 30, 225, 205), "cuerpo"), ((325, 35, 500, 200), "cuerpo"), ((575, 30, 830, 215), "cuerpo"),
             ((893, 30, 1052, 192), "cuerpo"), ((1150, 35, 1318, 205), "cuerpo")],
            # Muerte: cae, se posa en la hierba, ala rota.
            [((210, 535, 360, 705), "cuerpo"), ((605, 550, 755, 720), "suelo"), ((970, 550, 1120, 720), "suelo")],
            # Dolor: sorprendida, ladeada.
            [((210, 535, 360, 705), "cuerpo")],
        ],
    },
}

name, src_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
preview_path = sys.argv[4] if len(sys.argv) > 4 else None
cfg = ENEMIES[name]
rows = cfg["rows"]

src = np.array(Image.open(src_path).convert("RGB"))
chroma = src.astype(int).max(2) - src.astype(int).min(2)


def cut(box):
    """Recorta una pose y devuelve (rgba, bbox del contenido) sin fondo."""
    x0, y0, x1, y1 = box
    rgb = src[y0:y1, x0:x1]
    mask = chroma[y0:y1, x0:x1] > CHROMA_MIN
    mask = ndi.binary_closing(mask, iterations=2)
    # Los huecos interiores se rellenan (ojos), salvo los grises oscuros: son fondo de cuadros
    # encerrado, p. ej. entre las antenas.
    holes = ndi.binary_fill_holes(mask) & ~mask
    dark_grey = (rgb.max(2) < 150) & (chroma[y0:y1, x0:x1] <= CHROMA_MIN)
    mask = mask | (holes & ~dark_grey)
    # Fuera motas sueltas del JPG; se conservan las piezas grandes (estrellas, polvo, hierba).
    lab, n = ndi.label(mask)
    sizes = ndi.sum(mask, lab, range(1, n + 1))
    mask = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= 60])
    # Alfa binario: la reducción a FRAME px ya suaviza el contorno.
    rgba = np.dstack([rgb, mask.astype(np.uint8) * 255])
    ys, xs = np.nonzero(mask)
    return rgba, (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)


def body_box(rgba):
    """Caja de la pieza más grande (sin estrellas, polvo ni proyectiles)."""
    lab, n = ndi.label(rgba[..., 3] > 127)
    sizes = ndi.sum(lab > 0, lab, range(1, n + 1))
    ys, xs = np.nonzero(lab == int(np.argmax(sizes)) + 1)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def brown_center(rgba):
    """Centro de los píxeles marrones (rojo claramente por encima del azul): el cuerpo."""
    r, g, b, a = (rgba[..., i].astype(int) for i in range(4))
    ys, xs = np.nonzero((a > 127) & (r > b + 25) & (r > g))
    return xs.mean(), ys.mean()


cuts = [[(cut(box), anchor) for box, anchor in row] for row in rows]
# Escala común: la pose más ancha (o más alta) cabe en el frame con margen.
largest = max(max(bx1 - bx0, by1 - by0) for row in cuts for (_, (bx0, by0, bx1, by1)), _ in row)
scale = (FRAME - 2 * MARGIN) / largest

sheet = Image.new("RGBA", (FRAME * max(len(r) for r in rows), FRAME * len(rows)), (0, 0, 0, 0))
body_top = FRAME
for r, row in enumerate(cuts):
    for c, ((rgba, (bx0, by0, bx1, by1)), anchor) in enumerate(row):
        crop = Image.fromarray(rgba[by0:by1, bx0:bx1], "RGBA")
        w, h = round((bx1 - bx0) * scale), round((by1 - by0) * scale)
        # Reescalado con alfa premultiplicado, para que el borde no herede color del fondo.
        crop = crop.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")
        crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        if anchor == "suelo":
            cx0, cy0, cx1, cy1 = body_box(rgba)
            # Centro del cuerpo (en el recorte ya volteado) al centro del frame; base a ground_y.
            left = round(FRAME / 2 - (bx1 - (cx0 + cx1) / 2) * scale)
            top = cfg["ground_y"] - round((cy1 - by0) * scale)
        else:
            mx, my = brown_center(rgba)
            left = round(FRAME / 2 - (bx1 - mx) * scale)
            top = round(cfg["body_y"] - (my - by0) * scale)
        left = max(0, min(FRAME - w, left))
        top = max(0, min(FRAME - h, top))
        if r == 0:
            body_top = min(body_top, top)
        sheet.alpha_composite(crop, (c * FRAME + left, r * FRAME + top))

sheet.save(out_path, optimize=True)
print(f"{out_path}: {sheet.size[0]}x{sheet.size[1]}, escala {scale:.3f}, "
      f"frames por fila {[len(r) for r in rows]}, movimiento desde y={body_top}")

if preview_path:
    # Vista previa ampliada sobre fondo verde, con las líneas de referencia en rojo.
    bg = Image.new("RGBA", sheet.size, (110, 170, 90, 255))
    bg.alpha_composite(sheet)
    px = bg.load()
    for y in range(len(rows)):
        for x in range(sheet.size[0]):
            px[x, y * FRAME + cfg["ground_y"]] = (255, 0, 0, 255)
            if "body_y" in cfg:
                px[x, y * FRAME + cfg["body_y"]] = (255, 120, 120, 255)
    bg.resize((sheet.size[0] * 2, sheet.size[1] * 2), Image.NEAREST).save(preview_path)
