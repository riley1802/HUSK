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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.ai.edge.gallery.proto.ChatDensity
import com.google.ai.edge.gallery.proto.FontScale

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

val MaterialTheme.customColors: CustomColors
	@Composable @ReadOnlyComposable get() = LocalCustomColors.current

/** Chat layout and font size configuration driven by user preferences. */
@Immutable
data class ChatDisplayConfig(
	val bubblePaddingInner: Dp = 12.dp,
	val bubbleSpacingVertical: Dp = 6.dp,
	val bubbleAlignmentPad: Dp = 48.dp,
	val fontSizeScale: Float = 1.0f,
)

val LocalChatDisplayConfig = staticCompositionLocalOf { ChatDisplayConfig() }

val MaterialTheme.chatDisplayConfig: ChatDisplayConfig
	@Composable @ReadOnlyComposable get() = LocalChatDisplayConfig.current

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

private fun buildChatDisplayConfig(fontScale: FontScale, density: ChatDensity): ChatDisplayConfig {
	val scale = when (fontScale) {
		FontScale.FONT_SCALE_SMALL -> 0.85f
		FontScale.FONT_SCALE_DEFAULT, FontScale.FONT_SCALE_UNSPECIFIED -> 1.0f
		FontScale.FONT_SCALE_LARGE -> 1.15f
		FontScale.FONT_SCALE_EXTRA_LARGE -> 1.3f
		FontScale.UNRECOGNIZED -> 1.0f
	}
	return when (density) {
		ChatDensity.CHAT_DENSITY_COMPACT -> ChatDisplayConfig(
			bubblePaddingInner = 8.dp,
			bubbleSpacingVertical = 3.dp,
			bubbleAlignmentPad = 60.dp,
			fontSizeScale = scale,
		)
		ChatDensity.CHAT_DENSITY_COMFORTABLE, ChatDensity.CHAT_DENSITY_UNSPECIFIED -> ChatDisplayConfig(
			bubblePaddingInner = 12.dp,
			bubbleSpacingVertical = 6.dp,
			bubbleAlignmentPad = 48.dp,
			fontSizeScale = scale,
		)
		ChatDensity.CHAT_DENSITY_SPACIOUS -> ChatDisplayConfig(
			bubblePaddingInner = 16.dp,
			bubbleSpacingVertical = 10.dp,
			bubbleAlignmentPad = 36.dp,
			fontSizeScale = scale,
		)
		ChatDensity.UNRECOGNIZED -> ChatDisplayConfig(fontSizeScale = scale)
	}
}

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
	val view = LocalView.current

	// Read reactive appearance state.
	val isAmoled = ThemeSettings.amoledMode.value
	val customAccentArgb = ThemeSettings.accentColorArgb.value
	val curFontScale = ThemeSettings.fontScale.value
	val curDensity = ThemeSettings.chatDensity.value

	// Resolve background/surface colors based on AMOLED toggle.
	val bg = if (isAmoled) amoledBackground else huskBackground
	val surface = if (isAmoled) amoledSurface else huskSurface
	val surfaceHigh = if (isAmoled) amoledSurfaceHigh else huskSurfaceHigh
	val surfaceHighest = if (isAmoled) amoledSurfaceHighest else huskSurfaceHighest
	val outline = if (isAmoled) amoledOutline else huskOutline
	val outlineSoft = if (isAmoled) amoledOutlineSoft else huskOutlineSoft

	// Resolve accent color.
	val accent = if (customAccentArgb != 0) Color(customAccentArgb) else huskAccent
	val accentMuted = if (customAccentArgb != 0) deriveAccentMuted(accent) else huskAccentMuted
	val onAccent = if (customAccentArgb != 0) deriveOnAccent(accent) else huskOnAccent

	val colorScheme = darkColorScheme(
		primary = accent,
		onPrimary = onAccent,
		primaryContainer = surfaceHigh,
		onPrimaryContainer = huskTextPrimary,
		secondary = accentMuted,
		onSecondary = onAccent,
		secondaryContainer = surfaceHigh,
		onSecondaryContainer = huskTextPrimary,
		tertiary = accent,
		onTertiary = onAccent,
		tertiaryContainer = surfaceHigh,
		onTertiaryContainer = huskTextPrimary,
		error = huskError,
		onError = onAccent,
		errorContainer = surfaceHigh,
		onErrorContainer = huskError,
		background = bg,
		onBackground = huskTextPrimary,
		surface = bg,
		onSurface = huskTextPrimary,
		surfaceVariant = surfaceHigh,
		onSurfaceVariant = huskTextSecondary,
		outline = outline,
		outlineVariant = outlineSoft,
		scrim = Color.Black,
		inverseSurface = huskTextPrimary,
		inverseOnSurface = bg,
		inversePrimary = accent,
		surfaceDim = bg,
		surfaceBright = surfaceHighest,
		surfaceContainerLowest = bg,
		surfaceContainerLow = surface,
		surfaceContainer = surface,
		surfaceContainerHigh = surfaceHigh,
		surfaceContainerHighest = surfaceHighest,
	)

	// All five "task" slots collapse to the same monochrome surface + accent.
	val taskSurfaceList = listOf(surface, surface, surface, surface, surface)
	val taskAccentList = listOf(accent, accent, accent, accent, accent)
	val taskGradientList = listOf(
		listOf(accent, accentMuted),
		listOf(accent, accentMuted),
		listOf(accent, accentMuted),
		listOf(accent, accentMuted),
		listOf(accent, accentMuted),
	)

	val customColors = CustomColors(
		appTitleGradientColors = listOf(accent, accent),
		tabHeaderBgColor = surfaceHigh,
		taskCardBgColor = surface,
		taskBgColors = taskSurfaceList,
		taskBgGradientColors = taskGradientList,
		taskIconColors = taskAccentList,
		taskIconShapeBgColor = surfaceHigh,
		homeBottomGradient = listOf(Color.Transparent, bg),
		agentBubbleBgColor = surfaceHigh,
		userBubbleBgColor = accentMuted,
		linkColor = accent,
		successColor = huskSuccess,
		recordButtonBgColor = accent,
		waveFormBgColor = huskTextSecondary,
		modelInfoIconColor = huskTextSecondary,
		warningContainerColor = surfaceHigh,
		warningTextColor = huskWarning,
		errorContainerColor = surfaceHigh,
		errorTextColor = huskError,
		newFeatureContainerColor = surfaceHigh,
		newFeatureTextColor = accent,
		bgStarColor = accent.copy(alpha = 0.1f),
		promoBannerBgBrush = Brush.linearGradient(
			colors = listOf(surfaceHigh, surface),
			start = Offset(0f, 0f),
			end = Offset(0f, Float.POSITIVE_INFINITY),
		),
		promoBannerIconBgBrush = Brush.linearGradient(
			colors = listOf(accent, accentMuted),
			start = Offset(0f, 1f),
			end = Offset(1f, 0f),
		),
	)

	val chatDisplayConfig = buildChatDisplayConfig(curFontScale, curDensity)

	StatusBarColorController()

	CompositionLocalProvider(
		LocalCustomColors provides customColors,
		LocalChatDisplayConfig provides chatDisplayConfig,
	) {
		MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
	}

	// Make sure the navigation bar stays transparent.
	LaunchedEffect(Unit) {
		val window = (view.context as Activity).window
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			window.isNavigationBarContrastEnforced = false
		}
	}
}
