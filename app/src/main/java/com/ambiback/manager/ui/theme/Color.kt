package com.ambiback.manager.ui.theme

import androidx.compose.ui.graphics.Color

// Primary — deep blue-purple (LED controller feel)
val Primary = Color(0xFF6650A4)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFEADDFF)
val OnPrimaryContainer = Color(0xFF21005D)

// Secondary — teal accent
val Secondary = Color(0xFF00BCD4)
val OnSecondary = Color(0xFF000000)
// Containers (selected chips, slider tracks) stay in the purple family; the teal
// container made every slider and chip a second accent color next to the purple.
val SecondaryContainer = Color(0xFFE8DEF8)
val OnSecondaryContainer = Color(0xFF1D192B)

// Tertiary — amber for LED status
val Tertiary = Color(0xFFF9A825)
val OnTertiary = Color(0xFF000000)
val TertiaryContainer = Color(0xFFFFECB3)
val OnTertiaryContainer = Color(0xFF3E2000)

// Dark-theme primary — the light purple keeps text and icons readable on dark
// surfaces (#D0BCFF on #1C1C1E is about 10:1; #6650A4 there was only 2.6:1).
val PrimaryDark = Color(0xFFD0BCFF)
val OnPrimaryDark = Color(0xFF381E72)
val PrimaryContainerDark = Color(0xFF4F378B)
val OnPrimaryContainerDark = Color(0xFFEADDFF)
val SecondaryContainerDark = Color(0xFF4A4458)
val OnSecondaryContainerDark = Color(0xFFE8DEF8)
val TertiaryContainerDark = Color(0xFF5C4300)
val OnTertiaryContainerDark = Color(0xFFFFDF9E)

// Background & surface (dark). Containers stay neutral grey to match the surfaces.
val SurfaceContainerLowestDark = Color(0xFF151517)
val SurfaceContainerLowDark = Color(0xFF232325)
val SurfaceContainerDark = Color(0xFF29292B)
val SurfaceContainerHighDark = Color(0xFF323234)
val SurfaceContainerHighestDark = Color(0xFF3A3A3C)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)

// Background & surface
val Background = Color(0xFF1C1C1E)
val OnBackground = Color(0xFFE6E1E5)
val Surface = Color(0xFF2C2C2E)
val OnSurface = Color(0xFFE6E1E5)
val SurfaceVariant = Color(0xFF3A3A3C)
val OnSurfaceVariant = Color(0xFFCAC4D0)

// LED status indicator colors
val LedOff = Color(0xFF636366)
val LedAmbient = Color(0xFF30D158)
val LedVideo = Color(0xFF007AFF)
