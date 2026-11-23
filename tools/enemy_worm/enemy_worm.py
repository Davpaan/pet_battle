# Genera la hoja de sprites del enemigo (enemy_slime.png) a partir del dibujo del gusano
# (worm_source.jpg). El JPG trae el fondo de cuadros "pintado", no transparencia real: como el
# fondo es gris puro y el gusano es marrón, se separa por croma (max(RGB) - min(RGB)) y luego se
# rellenan los huecos interiores (blancos y negros de los ojos). La sombra gris del suelo se
# descarta: el juego dibuja la suya.
#
# Disposición de la salida (frames cuadrados de FRAME px, mirando a la izquierda como espera
# SlimeSheet; el dibujo original mira a la derecha):
#   fila 0: arrastrarse (5 frames, en bucle)
#   fila 1: muerte (golpe con estrellas, tumbado, derritiéndose, charco)
#   fila 2: dolor (cara de susto, se muestra mientras parpadea al recibir un golpe)
# Todos los frames comparten escala y línea del suelo (GROUND_Y), y se centran en horizontal
# por el centro del cuerpo, para que la animación no dé saltos.
#
# Uso: python enemy_worm.py worm_source.jpg salida.png [vista_previa.png]
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

FRAME = 128
GROUND_Y = 116  # fila del frame donde el gusano toca el suelo
MARGIN = 4  # margen lateral mínimo dentro del frame
CHROMA_MIN = 14  # por debajo, píxel gris = fondo de cuadros

# Recuadros (x0, y0, x1, y1) de cada pose en el dibujo original.
WALK = [(20, 45, 280, 205), (318, 70, 535, 205), (570, 45, 800, 205), (838, 100, 1068, 205), (1108, 45, 1362, 205)]
DEATH = [(590, 270, 845, 465), (200, 620, 425, 728), (545, 640, 830, 730), (925, 660, 1175, 726)]
HURT = [(975, 310, 1195, 465)]
ROWS = [WALK, DEATH, HURT]

src_path, out_path = sys.argv[1], sys.argv[2]
preview_path = sys.argv[3] if len(sys.argv) > 3 else None

src = np.array(Image.open(src_path).convert("RGB"))
chroma = src.astype(int).max(2) - src.astype(int).min(2)


def cut(box):
    """Recorta una pose y devuelve (rgba, bbox del contenido) sin fondo."""
    x0, y0, x1, y1 = box
    rgb = src[y0:y1, x0:x1]
    mask = chroma[y0:y1, x0:x1] > CHROMA_MIN
    mask = ndi.binary_closing(mask, iterations=2)
    mask = ndi.binary_fill_holes(mask)
    # Fuera motas sueltas del JPG; se conservan las piezas grandes (estrellas, polvo).
    lab, n = ndi.label(mask)
    sizes = ndi.sum(mask, lab, range(1, n + 1))
    mask = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= 60])
    # Alfa binario: la reducción a FRAME px ya suaviza el contorno.
    rgba = np.dstack([rgb, mask.astype(np.uint8) * 255])
    ys, xs = np.nonzero(mask)
    return rgba, (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)


def body_box(rgba):
    """Caja del cuerpo: la pieza más grande (sin estrellas ni polvo), para alinear."""
    lab, n = ndi.label(rgba[..., 3] > 127)
    sizes = ndi.sum(lab > 0, lab, range(1, n + 1))
    ys, xs = np.nonzero(lab == int(np.argmax(sizes)) + 1)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


cuts = [[cut(b) for b in row] for row in ROWS]
# Escala común: la pose más ancha cabe en el frame con margen.
widest = max(bx1 - bx0 for row in cuts for _, (bx0, _, bx1, _) in row)
scale = (FRAME - 2 * MARGIN) / widest

sheet = Image.new("RGBA", (FRAME * max(len(r) for r in ROWS), FRAME * len(ROWS)), (0, 0, 0, 0))
body_top = FRAME
for r, row in enumerate(cuts):
    for c, (rgba, (bx0, by0, bx1, by1)) in enumerate(row):
        cx0, cy0, cx1, cy1 = body_box(rgba)
        crop = Image.fromarray(rgba[by0:by1, bx0:bx1], "RGBA")
        w, h = round((bx1 - bx0) * scale), round((by1 - by0) * scale)
        # Reescalado con alfa premultiplicado, para que el borde no herede color del fondo.
        crop = crop.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")
        crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        # Centro del cuerpo (en el recorte ya volteado) al centro del frame; suelo a GROUND_Y.
        body_cx = (bx1 - (cx0 + cx1) / 2) * scale
        left = round(FRAME / 2 - body_cx)
        left = max(0, min(FRAME - w, left))
        top = GROUND_Y - round((cy1 - by0) * scale)
        if r == 0:
            body_top = min(body_top, top + round((cy0 - by0) * scale))
        sheet.alpha_composite(crop, (c * FRAME + left, r * FRAME + max(0, top)))

sheet.save(out_path, optimize=True)
print(f"{out_path}: {sheet.size[0]}x{sheet.size[1]}, escala {scale:.3f}, "
      f"frames por fila {[len(r) for r in ROWS]}, cuerpo desde y={body_top}")

if preview_path:
    # Vista previa ampliada sobre fondo verde, con la línea del suelo marcada.
    bg = Image.new("RGBA", sheet.size, (110, 170, 90, 255))
    bg.alpha_composite(sheet)
    px = bg.load()
    for y in range(len(ROWS)):
        for x in range(sheet.size[0]):
            px[x, y * FRAME + GROUND_Y] = (255, 0, 0, 255)
    bg.resize((sheet.size[0] * 2, sheet.size[1] * 2), Image.NEAREST).save(preview_path)
