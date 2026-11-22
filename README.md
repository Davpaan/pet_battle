# Pet Battle

Port a **Kotlin Multiplatform + Compose Multiplatform** del juego Mini Pou
(Flutter): una mascota virtual con 12 especies de pájaro y un modo
**defender la torre** donde las mascotas que has criado combaten contra
babosas usando la habilidad de su elemento.

Objetivos: **Android** y **escritorio (JVM)**, probado en Ubuntu/Linux.

## Ejecutar

Requisitos: JDK 17 y, para Android, el Android SDK (`local.properties` con `sdk.dir`).

```bash
# Escritorio (Linux/Ubuntu)
./gradlew :composeApp:run

# Paquete de escritorio autónomo (incluye su propio runtime de Java)
./gradlew :composeApp:createDistributable
./composeApp/build/compose/binaries/main/app/pet-battle/bin/pet-battle

# Paquete .deb para Ubuntu
./gradlew :composeApp:packageDeb

# Android (APK de depuración)
./gradlew :composeApp:assembleDebug
# -> composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Tests (dominio + prueba visual que renderiza el modo defensa fuera de pantalla)
./gradlew :composeApp:desktopTest
```

La partida se guarda sola: en Android en SharedPreferences y en escritorio en
`~/.local/share/pet_battle/store.json`.

## Cómo se juega

- **Mascota**: elige una especie y ponle nombre (obligatorio). Cada especie se
  cría una vez y empieza en el nivel 0. Aliméntala, límpiala, mándala a dormir
  y tócala para subir su diversión.
- **Defender**: coloca en la hierba cada mascota que hayas criado. Los enemigos
  avanzan por el camino y aceleran cada 10 s; suben de nivel cada 20 s.
- **Progresión**: 1 XP por enemigo derrotado; pasar del nivel n al n+1 cuesta
  10·(n+1) XP. Máximo nivel 10.
- **Estado en combate**: descanso → velocidad de ataque, hambre → daño,
  diversión → % de acierto. El descanso solo baja combatiendo y se recupera
  entero durmiendo 10 s.
- **Habilidades por elemento**: fuego quema (120% del daño en 3 s), agua
  ralentiza, aire empuja hacia atrás, tierra golpea normal.

## Arquitectura

```
composeApp/src/
  commonMain/kotlin/com/petbattle/
    core/                      reloj, almacenamiento, utilidades de dibujo
    shared_features/
      pet_species/             especie, catálogo de sprites, BirdFrame
      pet_skills/              domain/entities (PetSkillsEntity, PetSkillEntity,
                               SkillEffectEntity), rasgos, progresión, catálogo
      pet_status/              domain/entities (PetEntity, PetStatusEntity,
                               PetRosterEntity), reglas, efectos de combate,
                               repositorio y controlador (StateFlow)
    features/
      pet/                     pantalla de la mascota (escena en Canvas + UI)
      defense/                 domain/entities (EnemyEntity, DefenseMapEntity),
                               reglas de combate, motor del juego y renderizado
  androidMain/                 MainActivity, SharedPreferences, orientación
  desktopMain/                 ventana, almacenamiento en fichero
  commonTest/ desktopTest/     tests
```

Dependencias en un solo sentido: `pet_species ← pet_skills ← pet_status ← features`.
El dominio es Kotlin puro (sin Compose), por eso se prueba sin interfaz.

Equivalencias con la versión Flutter: Flame → bucle de juego propio con
`withFrameNanos` + `Canvas`; Riverpod → controlador con `StateFlow`;
freezed/json → `data class` + kotlinx.serialization.

Los sprites (pájaros, babosa, habilidades) y el mapa provienen del proyecto
Flutter, donde se generan con scripts en `tool/`.
