# Hojas de sprites de pájaros

Cada especie tiene una hoja `composeApp/src/commonMain/composeResources/drawable/pet_<especie>.png`
con la misma estructura, para que se pueda sustituir el arte de una especie sin tocar código.

## Rejilla

- **8 columnas × 6 filas** de frames de **256 × 256 px** (hoja de 2048 × 1536).
- Una **animación por fila**; las filas con menos de 8 frames dejan el resto vacío.
- El pájaro **mira a la izquierda** (el juego lo refleja cuando se gira).
- De pie, los **pies** quedan a ~252 px del borde superior de su casilla y centrados en
  horizontal (~124 px); en vuelo, el cuerpo centrado algo por encima del centro de la casilla.

| Fila | Animación | Frames | Cuándo se usa |
|---|---|---|---|
| 0 | Volar | 8 | Mientras vuela de un sitio a otro |
| 1 | Reposo | 6 | De pie, despierta (también en el modo defensa y en las fichas) |
| 2 | Andar / saltar | 6 | Reservada (aún no se usa) |
| 3 | Mirar arriba | 4 | Al tocarla (mimo) |
| 4 | Descansar | 4 | De noche, despierta |
| 5 | Dormir | 4 | Dormida |

Las constantes están en `BirdSheet` (`PetSpeciesCatalog.kt`).

## Estado del arte

- **Arrendajo** (`pet_blue_jay.png`): estilo nuevo, con sus 32 frames propios (sacados de
  `docs/demo_images/`).
- **Resto de especies**: estilo antiguo pasado a esta rejilla; los frames que no tenían se han
  rellenado con copias del más parecido (ver `OLD_TO_NEW` en `build_grid.py`). Hay que
  sustituirlos cuando exista su arte nuevo.

## Herramientas (`tools/bird_sprites/`)

```bash
# Hoja de una especie a partir de una referencia con el estilo nuevo (pájaros sobre fondo
# oscuro, una animación por fila en el orden de la tabla y mirando a la derecha):
python3 tools/bird_sprites/build_grid.py jay REFERENCIA.jpeg composeApp/src/commonMain/composeResources/drawable/pet_blue_jay.png

# Pasar una hoja antigua de 4 × 4 a esta rejilla:
python3 tools/bird_sprites/build_grid.py convert HOJA_4x4.png SALIDA.png
```

`extract_frames.py` recorta cada pájaro de la referencia y le quita el fondo (los huecos
oscuros dentro del pájaro, como el ojo, se conservan; los de entre las patas, no).
