package com.tradingtail.data.imports

/** Extract all text from a PDF's raw bytes. Platform-backed (PDFBox on desktop). */
expect fun extractPdfText(bytes: ByteArray): String

/** Show a native file picker for a PDF and return its bytes, or null if the user cancels. */
expect suspend fun pickPdfBytes(): ByteArray?
