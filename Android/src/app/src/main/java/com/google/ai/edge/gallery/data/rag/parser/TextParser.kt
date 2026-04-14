package com.google.ai.edge.gallery.data.rag.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses plain text and Markdown files (.txt, .md).
 */
class TextParser : DocumentParser {

	override val supportedMimeTypes: Set<String> = setOf(
		"text/plain",
		"text/markdown",
		"text/x-markdown",
	)

	override suspend fun parse(context: Context, uri: Uri): ParseResult =
		withContext(Dispatchers.IO) {
			val text = context.contentResolver.openInputStream(uri)?.use { stream ->
				stream.bufferedReader(Charsets.UTF_8).readText()
			} ?: throw IllegalArgumentException("Could not open file: $uri")

			if (text.isBlank()) {
				throw IllegalArgumentException("File is empty: $uri")
			}

			ParseResult(
				text = text,
				metadata = mapOf("parser" to "TextParser"),
			)
		}
}
