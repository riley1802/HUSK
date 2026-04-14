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

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.ui.audioscribe.TranscriptSegment
import com.google.ai.edge.gallery.ui.audioscribe.TranscriptView
import com.google.ai.edge.gallery.ui.common.MarkdownText
import com.google.ai.edge.gallery.ui.theme.chatDisplayConfig

/** Composable function to display the text content of a ChatMessageText. */
@Composable
fun MessageBodyText(message: ChatMessageText, inProgress: Boolean) {
	val config = MaterialTheme.chatDisplayConfig
	val innerPadding = config.bubblePaddingInner

	// Check if this is a Whisper transcript message.
	val transcriptSegments = if (message.side == ChatSide.AGENT) {
		TranscriptSegment.fromJson(message.content)
	} else {
		null
	}

	if (transcriptSegments != null) {
		TranscriptView(
			segments = transcriptSegments,
			modifier = Modifier.padding(innerPadding),
		)
		return
	}

	SelectionContainer {
		if (message.side == ChatSide.USER) {
			MarkdownText(
				text = message.content,
				modifier = Modifier.padding(innerPadding),
				textColor = Color.White,
				linkColor = Color.White,
				fontSizeScale = config.fontSizeScale,
			)
		} else if (message.side == ChatSide.AGENT) {
			val cdResponse = stringResource(R.string.cd_model_response_text)
			if (message.isMarkdown) {
				MarkdownText(
					text = message.content,
					modifier =
						Modifier.padding(innerPadding).semantics(mergeDescendants = true) {
							contentDescription = cdResponse
							if (!inProgress) {
								liveRegion = LiveRegionMode.Polite
							}
						},
					fontSizeScale = config.fontSizeScale,
				)
			} else {
				val scaledStyle = MaterialTheme.typography.bodyMedium.copy(
					fontSize = MaterialTheme.typography.bodyMedium.fontSize * config.fontSizeScale,
				)
				Text(
					message.content,
					style = scaledStyle,
					color = MaterialTheme.colorScheme.onSurface,
					modifier =
						Modifier.padding(innerPadding).semantics {
							contentDescription = cdResponse
							if (!inProgress) {
								liveRegion = LiveRegionMode.Polite
							}
						},
				)
			}
		}
	}
}
