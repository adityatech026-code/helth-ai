package com.swasthsaathi.voice

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swasthsaathi.voice.ui.AppViewModel
import com.swasthsaathi.voice.ui.NotebookScreen
import com.swasthsaathi.voice.ui.VoiceScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val vm: AppViewModel = viewModel()
                    var screen by remember { mutableStateOf("voice") }
                    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    LaunchedEffect(Unit) { askMic.launch(Manifest.permission.RECORD_AUDIO) }
                    if (screen == "voice") VoiceScreen(vm) { screen = "notebook" } else NotebookScreen(vm) { screen = "voice" }
                }
            }
        }
    }
}
