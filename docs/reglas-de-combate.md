# Reglas del sistema de combate

Este documento recoge las reglas **tal y como están implementadas** en el código. Cada vez
que cambie una regla o un valor de balance, hay que actualizarlo aquí (ver CLAUDE.md).

Última actualización: 2026-11-23.

Fuentes en el código:
- Combate (modo defensa): `features/defense/domain/DefenseRules.kt` (`DefenseBalance`),
  `features/defense/domain/entities/EnemyEntity.kt`, `features/defense/domain/CombatRules.kt`,
  `features/defense/presentation/game/DefenseGame.kt`.
- Habilidades y niveles: `shared_features/pet_skills/domain/PetSkillsCatalog.kt`,
  `shared_features/pet_skills/domain/PetProgression.kt`.
- Stats de combate: `shared_features/pet_combat/domain/PetCombatStatsRules.kt`
  (`PetCombatBalance`), `PetCombatStats.kt`.
- Necesidades de la mascota: `shared_features/pet_status/domain/PetNeedsBalance.kt`,
  `PetNeedsRules.kt`, `PetDerivedNeeds.kt`, `entities/PetNeedsEntity.kt`.
- Economía: `shared_features/pet_status/domain/GameRules.kt`.
- Ciclo del día: `core/time/DayCycle.kt`.

---

## 1. Desarrollo de una partida (modo defensa)

1. **Preparación.** Se colocan en zonas de hierba las mascotas **adultas**, de **cualquier
   bioma** (los polluelos no combaten), cada una una sola vez. Tocar un pájaro colocado lo recoge
   (se ve semitransparente) y el siguiente toque en hierba lo mueve. No aparecen enemigos.
2. **"Empezar".** Se activa con al menos un pájaro colocado. Al pulsarlo desaparece la
   interfaz de colocación y empieza el combate; ya no se puede mover a nadie.
3. **Combate.** Aparece una babosa cada **2,2 s**, alternando entre los caminos del mapa.
   Cada babosa que llega a la salida quita **1 vida** de **10**. Con 0 vidas se pierde.
   No hay victoria: la partida dura lo que se aguante.
4. **Reintentar** vuelve a la preparación con los pájaros donde estaban.

El mapa es siempre el mismo (*jungle camp*), sea cual sea el bioma.

## 2. Enemigos

La dificultad depende solo del **tiempo de combate**:

| Qué | Regla |
|---|---|
| Nivel | Empiezan en nivel 1 y suben **+1 cada 20 s** (solo afecta a los que nacen desde ese momento). |
| Vida | **1,2 × nivel** (nivel 1 = 1,2; nivel 5 = 6). |
| Velocidad | Base 36 px/s, **+20% cada 10 s** (lineal), para todos los del mapa a la vez. |

Los enemigos no atacan a las mascotas: solo caminan hacia la salida.

## 3. Stats de combate

Cada mascota tiene cuatro **stats de combate**. No se guardan: se calculan en cada momento.
Daño, velocidad y confianza tienen un **total** (el máximo, según el nivel) y un valor
**actual**, que es el total multiplicado por la **media de tres necesidades** (todas al 100%
= rinde el total). El **rango** es fijo por especie.

| Stat de combate | Total | Necesidades que lo suben o bajan |
|---|---|---|
| **Daño** (por golpe) | Nivel 0 → 1; nivel N → **1,2 × N** | Hambre (saciedad), plumaje, pico y garras |
| **Velocidad** (ataques/s) | **1 / 0,7 s ≈ 1,43** (no depende del nivel) | Descanso (energía), digestión, clima |
| **Confianza** (% de acierto) | **100** | Diversión, social, higiene |
| **Rango** (alcance, px de mapa) | Fijo por especie, según su tamaño | — (no depende de las necesidades) |

### Rango por especie (las grandes llegan más lejos)
| Especie | Tamaño aprox. | Rango |
|---|---|---|
| Cuervo | 45 cm | 380 |
| Urraca | 45 cm | 370 |
| Cernícalo | 34 cm | 350 |
| Arrendajo | 34 cm | 340 |
| Paloma blanca | 30 cm | 330 |
| Abejaruco | 28 cm | 310 |
| Cardenal | 22 cm | 290 |
| Zorzal | 20 cm | 285 |
| Chucao | 19 cm | 280 |
| Ampelis | 17 cm | 270 |
| Petirrojo | 14 cm | 260 |
| Pinzón | 14 cm | 255 |
| Carbonero | 12 cm | 245 |
| Colibrí | 9 cm | 230 |


- Con el daño total, una mascota de nivel N mata de un golpe a enemigos de nivel ≤ N.
- **Sin energía está agotada** y no ataca, aunque digestión y clima estén bien.
- La confianza **es** la probabilidad de acertar cada disparo.
- Las mascotas disparan solas a la babosa **más adelantada** dentro de **su rango**.
- **Cada disparo** gasta **1 de descanso**, resta **0,5** de higiene y de plumaje, y afila
  **+0,5** el pico y las garras (ver sección 5).

En el juego, el panel de la mascota tiene una pestaña **Combate** con daño, velocidad y
confianza como "actual / total", y el rango (con una barra que lo compara con el de más
alcance). Su botón **i** explica cómo se calcula cada stat; en la pestaña
**Necesidades**, el mismo botón explica qué sube y qué baja cada necesidad.

### Habilidades por elemento (ataque básico, desde el nivel 0)
| Elemento | Habilidad | Efecto |
|---|---|---|
| Fuego | Llamarada | Quema: 120% del daño repartido en 3 s (las quemaduras se acumulan). |
| Agua | Marea | Golpe normal y ralentiza a la mitad durante 2 s (no se acumula: gana el más fuerte). |
| Aire | Ráfaga | Golpe normal y empuja 30 px hacia atrás por su camino. |
| Tierra | Roca | Golpe normal, sin efectos. |

Las tablas de habilidades por tipo y por especie+tipo existen pero están vacías.

## 4. Recompensas y progresión

- Cada babosa derrotada da **1 XP** a la mascota que la remata (también por quemadura) y
  **1 moneda de oro** al jugador.
- Subir del nivel n al n+1 cuesta **10 × (n + 1)** XP (10, 20, 30…). La XP vuelve a 0 al
  subir. Nivel máximo: **10**. El nuevo daño se aplica al instante, en mitad del combate.
- Con el oro: un **huevo** cuesta **10** (el primero es gratis) y un **bioma** **20** (la
  pradera viene gratis).

## 5. Necesidades de la mascota

Su mantenimiento. Todas van de 0 a 100. **El tiempo es absoluto y común**: todas las
mascotas viven en tiempo real a la vez, estén seleccionadas o no y en cualquier bioma (y
también mientras la app está cerrada).

### Resumen: qué mueve cada necesidad
| Necesidad | Grupo | Baja con el tiempo | Mejora con | Empeora con | Stat de combate |
|---|---|---|---|---|---|
| Hambre (saciedad) | — | −18/h | Comida de su dieta: +25 | El tiempo | Daño |
| Descanso (energía) | — | −4/h despierta | Dormir o la noche: de 0 a 100 en ~10 s | Combate: −1 por disparo | Velocidad |
| Diversión | — | −12/h (+50% por cada necesidad básica descuidada) | Interacción directa (mimos): +2 por toque | El tiempo | Confianza |
| Digestión | — | −5/h | Su comida con hambre: +8 | Comida ajena: −15 · comer sin hambre: −10 | Velocidad |
| Higiene | **Salud** | −8/h | Limpieza: +45 | Combate: −0,5 por disparo | Confianza |
| Plumaje | **Salud** | −3/h | Limpieza: +20 | Combate: −0,5 por disparo | Daño |
| Pico y garras | **Salud** | −6/h | Limpieza: +15 · combate: +0,5 por disparo | El tiempo | Daño |
| Social | **Ambientales** | No (se calcula) | Otra de su especie en el bioma: 100 · solo otras especies: 50 | Estar sola: 0 | Confianza |
| Clima | **Ambientales** | No (se calcula) | — | — (siempre 100, pendiente de reglas) | Velocidad |

Las **ambientales** no dependen de la interacción del jugador y no se guardan.

### Reglas de detalle
- **Descansando** (dormida o de noche) todo lo que baja con el tiempo baja al **40%**, y el
  descanso se recupera en vez de bajar.
- **Descuido**: hambre, descanso o higiene por debajo de **20** cuentan como descuidadas;
  cada una hace que la diversión baje un 50% más rápido.
- **Comida** (siempre se la come):
  - de su dieta y con hambre (saciedad < 75): sacia +25 y digestión +8;
  - de su dieta sin hambre: sacia +25 y digestión −10;
  - fuera de su dieta: no sacia y digestión −15 (−25 si además no tenía hambre);
  - **comer no ensucia**;
  - el polluelo come de todo (nunca hay comida ajena para él).
- **Limpiar** mejora a la vez higiene, plumaje y pico y garras; se puede si a cualquiera de
  las tres le falta algo.
- **Dormida** no come, no se limpia ni se deja mimar, y se despierta sola con el descanso
  lleno. De noche descansa pero sigue despierta (se puede interactuar).

### En el panel de la mascota
Plegado solo muestra su nombre. Al desplegarlo aparece su especie y todo lo demás: la
pestaña **Necesidades**
(hambre, descanso, diversión y digestión; **Salud**: higiene, plumaje, pico y garras; y, al
final, **Ambientales**: social y clima), la pestaña **Combate**, el botón **i** y el botón
de liberarla.

## 5b. Cría

- **Satisfacción global**: la media de **todas** las necesidades de la mascota (hambre,
  descanso, diversión, digestión, higiene, plumaje, pico y garras, social y clima), en %. Se ve
  en el panel de la mascota.
- **Pareja**: dos adultos de la **misma especie** que viven en el **mismo bioma** (si hay más,
  se emparejan de dos en dos; los huevos y los polluelos no cuentan).
- **Una vez al día** (fecha local), la primera vez que **los dos** superan el **90 %** de
  satisfacción, tienen un **50 %** de probabilidad de poner un **huevo**. Salga o no, ese día ya
  no lo vuelven a intentar.
- El **huevo** aparece en el bioma de los padres con un nombre automático ("Cría 1", "Cría 2"…).
  No se puede seleccionar ni cuidar, y sus necesidades no corren hasta que nace. En su última
  hora se balancea.
- A las **8 horas** eclosiona en un **polluelo** con la **especie destinada** de sus padres:
  crecerá a esa especie (a las 10 comidas, como cualquier polluelo) coma lo que coma.
- Los huevos de la tienda siguen igual: sus polluelos crecen según la comida favorita.
- Todo esto ocurre también con la app cerrada (al volver se ponen al día eclosiones y crías).

## 5c. Rareza (tier)

Cada mascota tiene una **rareza** de 1 a 5 estrellas, que colorea el escudo de su nivel y se
ve (con sus estrellas) en su ficha. **De momento no tiene efecto en el juego.**

| Estrellas | Rareza | Escudo |
|---|---|---|
| 1 | Común | Gris |
| 2 | Poco común | Verde |
| 3 | Raro | Azul |
| 4 | Épico | Morado |
| 5 | Legendario | Dorado |

- **Huevos comprados**: 90 % de 1 estrella, 10 % de 2.
- **Cría, padres del mismo tier**: 20 % de que la cría nazca con **una estrella más** (máximo 5);
  si no, con el tier de sus padres.
- **Cría, padres de distinto tier**: cualquiera entre el más bajo y el más alto, todos igual de
  probables (2 + 4 → 2, 3 o 4).
- Las mascotas de partidas anteriores tienen 1 estrella.

## 6. Ciclo del día (hora local)

Cuatro franjas de 6 horas. Cada una cambia el aspecto del bioma con un filtro (una matriz de
color sobre la imagen y un degradado de cielo y suelo); los pájaros reciben el filtro algo
más suave. El cambio entre franjas es gradual.

| Momento | Horas | Aspecto | Mascotas |
|---|---|---|---|
| Noche | 0:00–6:00 | Oscuro y azulado, poco color | **Descansan** sin dormirse (recuperan descanso, todo baja al 40%); se puede seguir interactuando con ellas |
| Mañana | 6:00–12:00 | El alba: cielo rosa coral, luz dorada baja, algo de neblina | Activas |
| Mediodía | 12:00–18:00 | La imagen tal cual | Activas |
| Tarde | 18:00–24:00 | Atardecer anaranjado, suelo hacia el violeta | Activas |

Dormir a una mascota a mano solo cambia su animación (y la hace descansar); dormida no come,
no se limpia ni se deja mimar, y se despierta sola con el descanso lleno.

Con la app cerrada, al volver se simula lo ocurrido minuto a minuto (sabiendo qué horas
fueron de noche), con un máximo de 7 días.

## 7. Interferencias conocidas (pendientes de equilibrar)

1. El descanso se rellena casi al instante (10 s durmiendo, o al llegar la noche): pese a que
   ahora baja con el tiempo (−4/h), sigue siendo fácil de mantener.
2. Como todas viven a la vez, cuantas más mascotas, más trabajo de cuidado: una mascota
   olvidada en otro bioma sigue pasando hambre.
3. Una mascota sola en su bioma tiene social 0, así que su confianza no pasa de ~67 aunque
   esté perfecta: conviene tener compañía.
4. El clima siempre vale 100: un tercio de la velocidad no se puede perder.
5. La digestión solo sube comiendo bien (+8 por comida con hambre) y baja −5/h: sin comidas
   bien medidas tiende a bajar, y con ella la velocidad.
6. El hambre es lo que más aprieta: de 80 a descuidada en unas 3 h despierta.
7. Entre "tener hambre" (< 75) y alimentar (+25) el margen es estrecho: con 76 ya cuenta como
   comer sin hambre. Una mascota recién nacida (80) siempre empieza "sin hambre".
8. Limpiar a menudo compensa de sobra lo que el combate ensucia y despeina (0,5 por disparo
   frente a +45 / +20 por limpieza).
9. La dificultad del combate solo depende del reloj: siempre se acaba perdiendo.
