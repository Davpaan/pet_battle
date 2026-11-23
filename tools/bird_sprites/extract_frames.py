#!/usr/bin/env python3
"""Extrae los frames de una hoja de referencia (pájaros sobre fondo oscuro, una animación por
fila) y devuelve cada pájaro recortado, con transparencia y agrupado por filas.

Uso como módulo: frames = extract(path) -> lista de filas, cada fila una lista de imágenes RGBA.
"""
import numpy as np
from PIL import Image
from scipy import ndimage

# Brillo (máximo de los canales) del fondo y por encima del cual ya es pájaro (borde suave).
BG_LEVEL = 26
EDGE = 16
MIN_AREA = 1500  # los rótulos de texto son mucho más pequeños
LEGS_BAND = 0.22  # franja inferior (fracción del pájaro) donde los huecos son fondo


def extract(path):
    img = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    brightness = img.max(axis=2)
    alpha = np.clip((brightness - BG_LEVEL) / EDGE, 0, 1)
    mask = brightness > BG_LEVEL + 4
    # Une las piezas finas (patas, puntas de plumas) a su pájaro antes de etiquetar.
    labels, n = ndimage.label(ndimage.binary_dilation(mask, iterations=4))
    birds = []
    for i, sl in enumerate(ndimage.find_objects(labels), start=1):
        if sl is None:
            continue
        region = (labels[sl] == i) & mask[sl]
        if region.sum() < MIN_AREA:
            continue
        # Lo oscuro rodeado de pájaro (el ojo, las franjas negras de las alas) es pájaro, no
        # fondo: opaco. Salvo los huecos de la franja de abajo, entre las patas, que sí son fondo.
        holes = ndimage.binary_fill_holes(region) & ~region
        hole_labels, n_holes = ndimage.label(holes)
        legs_from = region.shape[0] * (1 - LEGS_BAND)
        for h, centre in enumerate(ndimage.center_of_mass(holes, hole_labels, range(1, n_holes + 1)), start=1):
            if centre[0] >= legs_from:
                holes[hole_labels == h] = False
        a = np.where(holes, 1.0, alpha[sl] * region)
        rgba = np.dstack([img[sl], a * 255]).astype(np.uint8)
        y0, x0 = sl[0].start, sl[1].start
        birds.append((y0, x0, sl[0].stop, Image.fromarray(rgba, "RGBA")))
    # Agrupa por filas: un pájaro empieza fila nueva si su centro está lejos del de la anterior.
    birds.sort(key=lambda b: (b[0] + b[2]) / 2)
    rows, current, last_cy = [], [], None
    for b in birds:
        cy = (b[0] + b[2]) / 2
        if last_cy is not None and cy - last_cy > 60:
            rows.append(current)
            current = []
        current.append(b)
        last_cy = cy
    rows.append(current)
    return [[b[3] for b in sorted(r, key=lambda b: b[1])] for r in rows]


if __name__ == "__main__":
    import sys
    for i, r in enumerate(extract(sys.argv[1])):
        print(i, len(r), [im.size for im in r])
