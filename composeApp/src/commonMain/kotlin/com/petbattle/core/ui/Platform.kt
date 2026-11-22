package com.petbattle.core.ui

import androidx.compose.runtime.Composable

/** Fuerza orientación horizontal mientras está en composición (Android); no hace nada en escritorio. */
@Composable
expect fun LandscapeWhileShown()

/** Gesto/botón de atrás del sistema (Android); no hace nada en escritorio. */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)
