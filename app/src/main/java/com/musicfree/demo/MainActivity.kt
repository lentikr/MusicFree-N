package com.musicfree.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.musicfree.demo.ui.MusicFreeDemoApp
import com.musicfree.demo.ui.theme.MusicFreeM3ETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicFreeM3ETheme {
                MusicFreeDemoApp()
            }
        }
    }
}
