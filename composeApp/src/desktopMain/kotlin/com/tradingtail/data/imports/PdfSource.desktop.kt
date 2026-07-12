package com.tradingtail.data.imports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun extractPdfText(bytes: ByteArray): String =
    Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }

actual suspend fun pickPdfBytes(): ByteArray? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Select Webull statement PDF", FileDialog.LOAD)
    dialog.file = "*.pdf" // Windows honours this as a filter; harmless elsewhere
    dialog.isVisible = true // blocks on this IO thread until the user picks or cancels
    val name = dialog.file ?: return@withContext null
    File(dialog.directory, name).readBytes()
}
