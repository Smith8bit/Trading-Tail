package com.tradingtail.data.imports

// ponytail: Android import deferred — desktop is the primary target and Android is compile-only today.
// Real extraction needs a Context-initialised pdfbox-android; the picker needs an Activity result
// launcher. Stubbed (no-op) so the shared UI compiles; wire both when Android gets device testing.
// Add pdfbox-android + a SAF OpenDocument launcher, then mirror the desktop actuals.
actual fun extractPdfText(bytes: ByteArray): String = ""

actual suspend fun pickPdfBytes(): ByteArray? = null
