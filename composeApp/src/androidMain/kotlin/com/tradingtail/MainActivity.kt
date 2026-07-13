package com.tradingtail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tradingtail.data.imports.onPdfPicked
import com.tradingtail.data.imports.registerPdfPicker
import com.tradingtail.data.local.createTradeDatabase
import com.tradingtail.data.local.databaseBuilder

class MainActivity : ComponentActivity() {
    // Must be registered unconditionally before the Activity reaches STARTED.
    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPdfPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PDFBoxResourceLoader.init(applicationContext)
        registerPdfPicker(contentResolver) { pdfPicker.launch(arrayOf("application/pdf")) }
        // ponytail: DB built here with the Activity's Context, never stashed globally in commonMain.
        val module = AppModule(createTradeDatabase(databaseBuilder(applicationContext)))
        setContent { App(module) }
    }
}
