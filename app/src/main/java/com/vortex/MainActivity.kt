package com.vortex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vortex.ui.HomeScreen
import com.vortex.ui.ProjectScreen
import com.vortex.ui.ProjectsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screen stays on while the app is open (live event use).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot(vm: ProjectsViewModel = viewModel()) {
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var setupId by rememberSaveable { mutableStateOf<Long?>(null) }

    when {
        openId != null -> ProjectScreen(openId!!) { openId = null }
        setupId != null -> NotBuilt("Initial setup", "Stage 2") { setupId = null }
        else -> HomeScreen(
            vm,
            onOpen = { vm.touch(it); openId = it },
            onInitialSetup = { setupId = it },
        )
    }
}

/** Honest placeholder: this feature is not implemented yet. */
@Composable
private fun NotBuilt(name: String, stage: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0B0D)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$name: not built yet ($stage)", color = Color(0xFF888888))
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onBack) { Text("Back") }
    }
}
