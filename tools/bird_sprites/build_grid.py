#!/usr/bin/env python3
"""Monta las hojas de sprites de pájaro con la rejilla de 8 columnas x 6 filas (frames de 256 px),
una animación por fila (ver BirdSheet en PetSpeciesCatalog.kt):

    0 volar (8)   1 reposo (6)   2 andar / saltar (6)
    3 mirar arriba (4)   4 descansar (4)   5 dormir (4)

Una especie puede tener más frames por animación (movimiento más fluido): su hoja es más ancha
y sus números van en BirdSheet.framesFor. Los pájaros miran a la izquierda (el juego los
refleja al girarse).

Uso:
    python3 tools/bird_sprites/build_grid.py frames CARPETA SALIDA.png
        Hoja a partir de una imagen transparente por animación (rejilla de 4 columnas, frames en
        orden de lectura, mirando a la derecha): CARPETA/vuelo.png, reposo.png, andar.png,
        mirar_arriba.png, descansar.png y dormir.png. Así está hecho el arrendajo
        (tools/bird_sprites/arrendajo -> pet_blue_jay.png, 12 + 5 x 16 frames).
    python3 tools/bird_sprites/build_grid.py jay REFERENCIA.jpeg SALIDA.png
        Hoja de 8 columnas a partir de una referencia con el estilo nuevo (una animación por fila).
    python3 tools/bird_sprites/build_grid.py convert HOJA_4x4.png SALIDA.png
        Pasa una hoja antigua (4x4: volar, reaccionar, reposo, salto) a la rejilla nueva,
        rellenando los frames que faltan con copias del más parecido, hasta tener su arte nuevo.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from extract_frames import extract  # noqa: E402

FRAME = 256
COLUMNS = 8
ROWS = 6
FRAMES_PER_ROW = [8, 6, 6, 4, 4, 4]
FLY, IDLE, HOP, LOOK_UP, REST, SLEEP = range(ROWS)

# Colocación dentro de la casilla (igual que las hojas antiguas): los pies casi en el borde
# inferior y el pájaro centrado; en vuelo, el cuerpo algo por encima del centro.
FEET_Y = 252
FEET_X = 124
FLY_CENTER = (128, 115)
STANDING_WIDTH = 185  # ancho de un pájaro de pie en una casilla de 256


def new_sheet():
    return Image.new("RGBA", (FRAME * COLUMNS, FRAME * ROWS), (0, 0, 0, 0))


def feet_x(im):
    """Posición horizontal media de las patas (última franja con píxeles opacos)."""
    a = np.asarray(im.split()[3]) > 128
    ys = np.where(a.any(axis=1))[0]
    band = a[max(ys[-1] - max(3, im.height // 14), 0): ys[-1] + 1]
    xs = np.where(band.any(axis=0))[0]
    return float(xs.mean())


def place(sheet, im, row, col):
    cx, cy = col * FRAME, row * FRAME
    bbox = im.getbbox()
    im = im.crop(bbox)
    if row == FLY:
        x = cx + FLY_CENTER[0] - im.width // 2
        y = cy + FLY_CENTER[1] - im.height // 2
    else:
        x = cx + int(round(FEET_X - feet_x(im)))
        y = cy + FEET_Y - im.height
    x = min(max(x, cx), cx + FRAME - im.width)
    y = min(max(y, cy), cy + FRAME - im.height)
    sheet.alpha_composite(im, (x, y))


def build_jay(reference, out):
    rows = extract(reference)
    counts = [len(r) for r in rows]
    if counts != FRAMES_PER_ROW:
        raise SystemExit(f"se esperaban {FRAMES_PER_ROW} frames por fila y hay {counts}")
    # Una sola escala para todos: el pájaro de pie mide como los demás de la colección.
    scale = STANDING_WIDTH / float(np.median([im.width for im in rows[IDLE]]))
    sheet = new_sheet()
    for r, frames in enumerate(rows):
        for c, im in enumerate(frames):
            size = (round(im.width * scale), round(im.height * scale))
            im = im.resize(size, Image.LANCZOS).transpose(Image.FLIP_LEFT_RIGHT)
            place(sheet, im, r, c)
    sheet.save(out, optimize=True)


# Imagen de cada animación en el modo "frames", en el orden de las filas.
ANIMATION_FILES = ["vuelo", "reposo", "andar", "mirar_arriba", "descansar", "dormir"]
SOURCE_COLUMNS = 4


def build_from_frames(folder, out):
    """Hoja con todos los frames de cada animación, sin recolocarlos uno a uno: se conserva la
    posición de cada pájaro dentro de su casilla (los saltos al andar, la cabeza al mirar
    arriba) y se mueve la animación entera de una vez, para que el movimiento sea fluido."""
    rows = []
    for name in ANIMATION_FILES:
        img = Image.open(os.path.join(folder, f"{name}.png")).convert("RGBA")
        cell = img.width // SOURCE_COLUMNS
        frames = []
        for r in range(img.height // cell):
            for c in range(SOURCE_COLUMNS):
                frame = img.crop((c * cell, r * cell, (c + 1) * cell, (r + 1) * cell))
                if frame.getbbox():
                    frames.append(frame.transpose(Image.FLIP_LEFT_RIGHT))
        rows.append(frames)
    # Una sola escala para todos: el pájaro de pie mide como los demás de la colección.
    scale = STANDING_WIDTH / float(np.median([f.getbbox()[2] - f.getbbox()[0] for f in rows[IDLE]]))
    columns = max(len(r) for r in rows)
    sheet = Image.new("RGBA", (FRAME * columns, FRAME * ROWS), (0, 0, 0, 0))
    for r, frames in enumerate(rows):
        size = round(frames[0].width * scale)
        frames = [f.resize((size, size), Image.LANCZOS) for f in frames]
        boxes = [f.getbbox() for f in frames]
        union = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
        if r == FLY:
            # El conjunto del aleteo, centrado donde vuelan las demás especies.
            dx = FLY_CENTER[0] - (union[0] + union[2]) / 2
            dy = FLY_CENTER[1] - (union[1] + union[3]) / 2
        else:
            # Las patas más bajas de la animación sobre la línea del suelo, y las patas (de media)
            # a la altura horizontal de las demás especies.
            dx = FEET_X - float(np.median([feet_x(f.crop(b)) + b[0] for f, b in zip(frames, boxes)]))
            dy = FEET_Y - union[3]
        dx = min(max(dx, -union[0]), FRAME - union[2])
        dy = min(max(dy, -union[1]), FRAME - union[3])
        for c, f in enumerate(frames):
            sheet.alpha_composite(f, (c * FRAME + round(dx), r * FRAME + round(dy)))
    sheet.save(out, optimize=True)
    print(f"{out}: {sheet.width}x{sheet.height}, frames por fila {[len(r) for r in rows]}, escala {scale:.3f}")


# Hoja antigua (4x4): fila -> animación.
OLD_FLY, OLD_REACT, OLD_IDLE, OLD_HOP = range(4)

# Para cada fila nueva, qué frames (fila, columna) de la hoja antigua se copian.
OLD_TO_NEW = {
    FLY: [(OLD_FLY, c % 4) for c in range(8)],
    IDLE: [(OLD_IDLE, c) for c in (0, 1, 2, 3, 2, 1)],  # ida y vuelta, sin saltos
    HOP: [(OLD_HOP, c) for c in range(4)] + [(OLD_IDLE, 0), (OLD_IDLE, 0)],
    LOOK_UP: [(OLD_REACT, c) for c in range(4)],
    REST: [(OLD_IDLE, c) for c in range(4)],
    SLEEP: [(OLD_IDLE, 0)] * 4,  # quieto, como dormían hasta ahora
}


def convert(old_path, out):
    old = Image.open(old_path).convert("RGBA")
    size = old.width // 4
    if old.width != old.height or size != FRAME:
        raise SystemExit(f"{old_path}: se esperaba una hoja 4x4 de {FRAME} px y mide {old.size}")
    sheet = new_sheet()
    for row, sources in OLD_TO_NEW.items():
        for col, (r, c) in enumerate(sources):
            cell = old.crop((c * size, r * size, (c + 1) * size, (r + 1) * size))
            sheet.alpha_composite(cell, (col * FRAME, row * FRAME))
    sheet.save(out, optimize=True)


if __name__ == "__main__":
    mode, src, dst = sys.argv[1:4]
    {"frames": build_from_frames, "jay": build_jay, "convert": convert}[mode](src, dst)
