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

Una especie puede tener **más frames** por animación, para que se mueva con más fluidez: su hoja
es más ancha (tantas columnas como frames tenga su animación más larga) y sus números van en
`BirdSheet.frames(row, species)`. Cada ciclo dura lo mismo en todas las especies
(`BirdSheet.cycleSeconds`), así que con más frames cada uno dura menos. El test
`BirdSheetsTest` comprueba que cada hoja tiene dibujo en todos los frames que declara el código.

## Estado del arte

- **Arrendajo** (`pet_blue_jay.png`): estilo nuevo, con hoja ampliada de **16 columnas** (4096 ×
  1536): 12 frames de vuelo y 16 en cada una de las demás animaciones. Se monta con el modo
  `frames` a partir de `tools/bird_sprites/arrendajo/` (una imagen transparente por animación).
  Su reacción al tocarlo (mirar arriba) dura 1,2 s, porque sube la cabeza y la vuelve a bajar.
- **Resto de especies**: estilo antiguo pasado a esta rejilla; los frames que no tenían se han
  rellenado con copias del más parecido (ver `OLD_TO_NEW` en `build_grid.py`). Hay que
  sustituirlos cuando exista su arte nuevo.

## Herramientas (`tools/bird_sprites/`)

```bash
# Hoja a partir de una imagen transparente por animación (rejilla de 4 columnas, frames en orden
# de lectura, mirando a la derecha): vuelo.png, reposo.png, andar.png, mirar_arriba.png,
# descansar.png y dormir.png. Conserva la posición de cada pájaro en su casilla, para que el
# movimiento entre frames (saltos, cabeza) se mantenga:
python3 tools/bird_sprites/build_grid.py frames tools/bird_sprites/arrendajo composeApp/src/commonMain/composeResources/drawable/pet_blue_jay.png

# Hoja de 8 columnas a partir de una referencia con el estilo nuevo (pájaros sobre fondo
# oscuro, una animación por fila en el orden de la tabla y mirando a la derecha):
python3 tools/bird_sprites/build_grid.py jay REFERENCIA.jpeg composeApp/src/commonMain/composeResources/drawable/pet_blue_jay.png

# Pasar una hoja antigua de 4 × 4 a esta rejilla:
python3 tools/bird_sprites/build_grid.py convert HOJA_4x4.png SALIDA.png
```

`extract_frames.py` recorta cada pájaro de la referencia y le quita el fondo (los huecos
oscuros dentro del pájaro, como el ojo, se conservan; los de entre las patas, no).
