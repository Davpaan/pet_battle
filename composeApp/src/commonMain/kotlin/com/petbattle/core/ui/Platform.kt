package com.petbattle.core.ui

import androidx.compose.runtime.Composable

/** Gesto/botón de atrás del sistema (Android); no hace nada en escritorio. */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)
