package com.petbattle.core.ui

import androidx.compose.runtime.Composable

/** Forces landscape while in composition (Android); no-op on desktop. */
@Composable
expect fun LandscapeWhileShown()

/** System back gesture/button (Android); no-op on desktop. */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)
