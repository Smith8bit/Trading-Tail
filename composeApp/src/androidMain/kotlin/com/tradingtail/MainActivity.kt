package com.tradingtail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tradingtail.data.local.createTradeDatabase
import com.tradingtail.data.local.databaseBuilder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ponytail: DB built here with the Activity's Context, never stashed globally in commonMain.
        val module = AppModule(createTradeDatabase(databaseBuilder(applicationContext)))
        setContent { App(module) }
    }
}
