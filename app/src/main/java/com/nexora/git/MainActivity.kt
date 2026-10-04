package com.nexora.git

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexora.git.core.auth.AuthCallbackBus
import com.nexora.git.ui.NexoraGitApp
import com.nexora.git.ui.theme.NexoraGitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authCallbackBus: AuthCallbackBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NexoraGitTheme {
                NexoraGitApp()
            }
        }

        intent?.data?.let(authCallbackBus::dispatch)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(authCallbackBus::dispatch)
    }
}
