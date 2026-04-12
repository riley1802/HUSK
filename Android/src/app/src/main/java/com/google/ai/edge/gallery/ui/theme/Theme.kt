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

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val huskColorScheme =
  darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
  )

@Immutable
data class CustomColors(
  val appTitleGradientColors: List<Color> = listOf(),
  val tabHeaderBgColor: Color = Color.Transparent,
  val taskCardBgColor: Color = Color.Transparent,
  val taskBgColors: List<Color> = listOf(),
  val taskBgGradientColors: List<List<Color>> = listOf(),
  val taskIconColors: List<Color> = listOf(),
  val taskIconShapeBgColor: Color = Color.Transparent,
  val homeBottomGradient: List<Color> = listOf(),
  val userBubbleBgColor: Color = Color.Transparent,
  val agentBubbleBgColor: Color = Color.Transparent,
  val linkColor: Color = Color.Transparent,
  val successColor: Color = Color.Transparent,
  val recordButtonBgColor: Color = Color.Transparent,
  val waveFormBgColor: Color = Color.Transparent,
  val modelInfoIconColor: Color = Color.Transparent,
  val warningContainerColor: Color = Color.Transparent,
  val warningTextColor: Color = Color.Transparent,
  val errorContainerColor: Color = Color.Transparent,
  val errorTextColor: Color = Color.Transparent,
  val newFeatureContainerColor: Color = Color.Transparent,
  val newFeatureTextColor: Color = Color.Transparent,
  val bgStarColor: Color = Color.Transparent,
  val promoBannerBgBrush: Brush = Brush.verticalGradient(listOf(Color.Transparent)),
  val promoBannerIconBgBrush: Brush = Brush.verticalGradient(listOf(Color.Transparent)),
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

// All five "task" slots collapse to the same monochrome surface + sand accent. Existing
// per-task indexing (red/green/blue/yellow lookups in legacy task cards) keeps working,
// it just resolves to the same Husk values regardless of index.
private val huskTaskSurfaceList =
  listOf(huskSurface, huskSurface, huskSurface, huskSurface, huskSurface)
private val huskTaskAccentList =
  listOf(huskAccent, huskAccent, huskAccent, huskAccent, huskAccent)
private val huskTaskGradientList =
  listOf(
    listOf(huskAccent, huskAccentMuted),
    listOf(huskAccent, huskAccentMuted),
    listOf(huskAccent, huskAccentMuted),
    listOf(huskAccent, huskAccentMuted),
    listOf(huskAccent, huskAccentMuted),
  )

val huskCustomColors =
  CustomColors(
    appTitleGradientColors = listOf(huskAccent, huskAccent),
    tabHeaderBgColor = huskSurfaceHigh,
    taskCardBgColor = huskSurface,
    taskBgColors = huskTaskSurfaceList,
    taskBgGradientColors = huskTaskGradientList,
    taskIconColors = huskTaskAccentList,
    taskIconShapeBgColor = huskSurfaceHigh,
    homeBottomGradient = listOf(Color.Transparent, huskBackground),
    agentBubbleBgColor = huskSurfaceHigh,
    userBubbleBgColor = huskAccentMuted,
    linkColor = huskAccent,
    successColor = huskSuccess,
    recordButtonBgColor = huskAccent,
    waveFormBgColor = huskTextSecondary,
    modelInfoIconColor = huskTextSecondary,
    warningContainerColor = huskSurfaceHigh,
    warningTextColor = huskWarning,
    errorContainerColor = huskSurfaceHigh,
    errorTextColor = huskError,
    newFeatureContainerColor = huskSurfaceHigh,
    newFeatureTextColor = huskAccent,
    bgStarColor = Color(0x1AD6C9A8),
    promoBannerBgBrush =
      Brush.linearGradient(
        colors = listOf(huskSurfaceHigh, huskSurface),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
      ),
    promoBannerIconBgBrush =
      Brush.linearGradient(
        colors = listOf(huskAccent, huskAccentMuted),
        start = Offset(0f, 1f),
        end = Offset(1f, 0f),
      ),
  )

val MaterialTheme.customColors: CustomColors
  @Composable @ReadOnlyComposable get() = LocalCustomColors.current

/**
 * Forces the system status bar icons to render in light mode (because Husk is always dark).
 */
@Composable
fun StatusBarColorController() {
  val view = LocalView.current
  val currentWindow = (view.context as? Activity)?.window

  if (currentWindow != null) {
    SideEffect {
      WindowCompat.setDecorFitsSystemWindows(currentWindow, false)
      val controller = WindowCompat.getInsetsController(currentWindow, view)
      controller.isAppearanceLightStatusBars = false
    }
  }
}

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
  val view = LocalView.current

  StatusBarColorController()

  CompositionLocalProvider(LocalCustomColors provides huskCustomColors) {
    MaterialTheme(colorScheme = huskColorScheme, typography = AppTypography, content = content)
  }

  // Make sure the navigation bar stays transparent — Husk is always dark, but the system
  // navigation-bar contrast enforcement can otherwise wash out our background.
  LaunchedEffect(Unit) {
    val window = (view.context as Activity).window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
  }
}
