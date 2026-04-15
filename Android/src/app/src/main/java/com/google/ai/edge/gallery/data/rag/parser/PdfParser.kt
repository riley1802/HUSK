package com.google.ai.edge.gallery.data.rag.parser

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses PDF files into text using PDFBox-Android.
 * Returns an error for image-only PDFs with no extractable text.
 */
class PdfParser : DocumentParser {

	override val supportedMimeTypes: Set<String> = setOf(
		"application/pdf",
	)

	override suspend fun parse(context: Context, uri: Uri): ParseResult =
		withContext(Dispatchers.IO) {
			// PDFBox requires one-time resource initialization on Android
			PDFBoxResourceLoader.init(context)

			val document: PDDocument = context.contentResolver.openInputStream(uri)?.use { stream ->
				PDDocument.load(stream)
			} ?: throw IllegalArgumentException("Could not open PDF: $uri")

			document.use { doc ->
				val pageCount = doc.numberOfPages
				val stripper = PDFTextStripper()
				val text = stripper.getText(doc)

				if (text.isBlank()) {
					throw IllegalArgumentException(
						"No extractable text (image-only PDF). " +
							"This PDF contains $pageCount page(s) but no embedded text. " +
							"OCR support is planned for a future update."
					)
				}

				ParseResult(
					text = text,
					metadata = mapOf(
						"parser" to "PdfParser",
						"pageCount" to pageCount.toString(),
					),
				)
			}
		}
}
