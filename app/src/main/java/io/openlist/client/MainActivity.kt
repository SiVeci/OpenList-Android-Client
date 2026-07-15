package io.openlist.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.openlist.client.core.designsystem.OpenListClientTheme
import io.openlist.client.navigation.OpenListNavHost
import io.openlist.client.notifications.SystemDocumentFailureNotificationPublisher

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingSystemDocumentFailureInstanceId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSystemDocumentFailureInstanceId = intent.systemDocumentFailureInstanceId()
        enableEdgeToEdge()
        setContent {
            OpenListClientTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OpenListNavHost(
                        systemDocumentFailureInstanceId = pendingSystemDocumentFailureInstanceId,
                        onSystemDocumentFailureHandled = { pendingSystemDocumentFailureInstanceId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSystemDocumentFailureInstanceId = intent.systemDocumentFailureInstanceId()
    }

    private fun android.content.Intent.systemDocumentFailureInstanceId(): String? =
        takeIf { action == SystemDocumentFailureNotificationPublisher.ACTION_OPEN_SYSTEM_DOCUMENT_FAILURES }
            ?.getStringExtra(SystemDocumentFailureNotificationPublisher.EXTRA_INSTANCE_ID)
            ?.takeIf { it.isNotBlank() && it.length <= 128 }
}
