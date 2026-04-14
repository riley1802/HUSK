/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils as AndroidColorUtils

// Husk palette — cool monochrome dark with a single warm-sand accent.
val huskBackground = Color(0xFF0E1013)
val huskSurface = Color(0xFF15181C)
val huskSurfaceHigh = Color(0xFF1B1F24)
val huskSurfaceHighest = Color(0xFF22272D)
val huskOutline = Color(0xFF2C313A)
val huskOutlineSoft = Color(0xFF1F232A)
val huskTextPrimary = Color(0xFFE6E8EC)
val huskTextSecondary = Color(0xFFA0A6B0)
val huskTextMuted = Color(0xFF6B7280)
val huskAccent = Color(0xFFD6C9A8)
val huskAccentMuted = Color(0xFF9C9276)
val huskOnAccent = Color(0xFF0E1013)
val huskError = Color(0xFFC97A6E)
val huskWarning = Color(0xFFC9A86E)
val huskSuccess = Color(0xFF7E9B7A)

// AMOLED black palette — true black background with proportionally darker surfaces.
val amoledBackground = Color.Black
val amoledSurface = Color(0xFF0A0A0A)
val amoledSurfaceHigh = Color(0xFF121212)
val amoledSurfaceHighest = Color(0xFF1A1A1A)
val amoledOutline = Color(0xFF222222)
val amoledOutlineSoft = Color(0xFF161616)

// Accent color presets — all desaturated/muted to fit the monochrome aesthetic.
data class AccentPreset(val name: String, val argb: Int, val color: Color)

val accentPresets = listOf(
	AccentPreset("Sand", 0, huskAccent), // 0 means "use default"
	AccentPreset("Ice", 0xFFA8C4D6.toInt(), Color(0xFFA8C4D6)),
	AccentPreset("Teal", 0xFF8CBEB2.toInt(), Color(0xFF8CBEB2)),
	AccentPreset("Rose", 0xFFC9A0A0.toInt(), Color(0xFFC9A0A0)),
	AccentPreset("Lavender", 0xFFB0A8C9.toInt(), Color(0xFFB0A8C9)),
	AccentPreset("Amber", 0xFFC9B07A.toInt(), Color(0xFFC9B07A)),
	AccentPreset("Sage", 0xFF9BB09A.toInt(), Color(0xFF9BB09A)),
	AccentPreset("Slate", 0xFFA0A8B0.toInt(), Color(0xFFA0A8B0)),
)

/** Derive a muted variant of an accent color (reduced saturation + brightness). */
fun deriveAccentMuted(accent: Color): Color {
	val hsl = FloatArray(3)
	AndroidColorUtils.colorToHSL(android.graphics.Color.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt()), hsl)
	hsl[1] = hsl[1] * 0.7f // reduce saturation
	hsl[2] = hsl[2] * 0.65f // reduce lightness
	val argb = AndroidColorUtils.HSLToColor(hsl)
	return Color(argb)
}

/** Determine whether text on the accent should be light or dark. */
fun deriveOnAccent(accent: Color): Color {
	val luminance = AndroidColorUtils.calculateLuminance(
		android.graphics.Color.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt())
	)
	return if (luminance > 0.4) huskBackground else huskTextPrimary
}

// The light palette is retained as a build-time fallback only — Husk runs dark always
// (see GalleryTheme in Theme.kt). These tokens map to the same Husk values so any stray
// reference still resolves to a coherent palette instead of a stale Material default.
val primaryLight = huskAccent
val onPrimaryLight = huskOnAccent
val primaryContainerLight = huskSurfaceHigh
val onPrimaryContainerLight = huskTextPrimary
val secondaryLight = huskAccentMuted
val onSecondaryLight = huskOnAccent
val secondaryContainerLight = huskSurfaceHigh
val onSecondaryContainerLight = huskTextPrimary
val tertiaryLight = huskAccent
val onTertiaryLight = huskOnAccent
val tertiaryContainerLight = huskSurfaceHigh
val onTertiaryContainerLight = huskTextPrimary
val errorLight = huskError
val onErrorLight = huskOnAccent
val errorContainerLight = huskSurfaceHigh
val onErrorContainerLight = huskError
val backgroundLight = huskBackground
val onBackgroundLight = huskTextPrimary
val surfaceLight = huskBackground
val onSurfaceLight = huskTextPrimary
val surfaceVariantLight = huskSurfaceHigh
val onSurfaceVariantLight = huskTextSecondary
val surfaceContainerLowestLight = huskBackground
val surfaceContainerLowLight = huskSurface
val surfaceContainerLight = huskSurface
val surfaceContainerHighLight = huskSurfaceHigh
val surfaceContainerHighestLight = huskSurfaceHighest
val inverseSurfaceLight = huskTextPrimary
val inverseOnSurfaceLight = huskBackground
val outlineLight = huskOutline
val outlineVariantLight = huskOutlineSoft
val inversePrimaryLight = huskAccent
val surfaceDimLight = huskBackground
val surfaceBrightLight = huskSurfaceHighest
val scrimLight = Color(0xFF000000)

// Dark palette — the live Husk palette.
val primaryDark = huskAccent
val onPrimaryDark = huskOnAccent
val primaryContainerDark = huskSurfaceHigh
val onPrimaryContainerDark = huskTextPrimary
val secondaryDark = huskAccentMuted
val onSecondaryDark = huskOnAccent
val secondaryContainerDark = huskSurfaceHigh
val onSecondaryContainerDark = huskTextPrimary
val tertiaryDark = huskAccent
val onTertiaryDark = huskOnAccent
val tertiaryContainerDark = huskSurfaceHigh
val onTertiaryContainerDark = huskTextPrimary
val errorDark = huskError
val onErrorDark = huskOnAccent
val errorContainerDark = huskSurfaceHigh
val onErrorContainerDark = huskError
val backgroundDark = huskBackground
val onBackgroundDark = huskTextPrimary
val surfaceDark = huskBackground
val onSurfaceDark = huskTextPrimary
val surfaceVariantDark = huskSurfaceHigh
val onSurfaceVariantDark = huskTextSecondary
val surfaceContainerLowestDark = huskBackground
val surfaceContainerLowDark = huskSurface
val surfaceContainerDark = huskSurface
val surfaceContainerHighDark = huskSurfaceHigh
val surfaceContainerHighestDark = huskSurfaceHighest
val inverseSurfaceDark = huskTextPrimary
val inverseOnSurfaceDark = huskBackground
val outlineDark = huskOutline
val outlineVariantDark = huskOutlineSoft
val inversePrimaryDark = huskAccent
val surfaceDimDark = huskBackground
val surfaceBrightDark = huskSurfaceHighest
val scrimDark = Color(0xFF000000)
