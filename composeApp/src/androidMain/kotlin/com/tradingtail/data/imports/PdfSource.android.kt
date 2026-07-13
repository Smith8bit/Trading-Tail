package com.tradingtail.data.imports

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// ponytail: single-Activity app — a module-level slot for the resolver + launch trigger is simpler
// than threading Context/Activity through the shared expect/actual signature. MainActivity wires
// this once in onCreate (registerForActivityResult must run before the Activity is STARTED).
private var resolver: ContentResolver? = null
private var launchPicker: (() -> Unit)? = null
private var pending: CancellableContinuation<Uri?>? = null

/** Wire the SAF document picker to the app's Activity. Call once from MainActivity.onCreate. */
fun registerPdfPicker(contentResolver: ContentResolver, launch: () -> Unit) {
    resolver = contentResolver
    launchPicker = launch
}

/** Forward the ActivityResultLauncher's callback here. */
fun onPdfPicked(uri: Uri?) {
    pending?.resume(uri)
    pending = null
}

actual suspend fun pickPdfBytes(): ByteArray? {
    val launch = launchPicker ?: return null
    val uri = suspendCancellableCoroutine<Uri?> { cont -> pending = cont; launch() } ?: return null
    return withContext(Dispatchers.IO) { resolver?.openInputStream(uri)?.use { it.readBytes() } }
}

actual fun extractPdfText(bytes: ByteArray): String =
    PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
