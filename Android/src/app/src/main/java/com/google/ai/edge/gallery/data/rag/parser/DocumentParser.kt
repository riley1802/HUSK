package com.google.ai.edge.gallery.data.rag.parser

import android.net.Uri

/**
 * Result of parsing a document into raw text.
 */
data class ParseResult(
	val text: String,
	val metadata: Map<String, String> = emptyMap(),
)

/**
 * Interface for extracting text from documents by MIME type.
 */
interface DocumentParser {
	/** MIME types this parser can handle. */
	val supportedMimeTypes: Set<String>

	/** Parse the document at [uri] into raw text. */
	suspend fun parse(context: android.content.Context, uri: Uri): ParseResult
}
