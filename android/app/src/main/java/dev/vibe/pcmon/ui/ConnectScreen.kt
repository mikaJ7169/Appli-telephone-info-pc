package dev.vibe.pcmon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.vibe.pcmon.theme.PcmonBorder
import dev.vibe.pcmon.theme.PcmonDim
import dev.vibe.pcmon.theme.PcmonLight
import dev.vibe.pcmon.theme.PcmonMid
import dev.vibe.pcmon.theme.PcmonWhite

private val HELP =
    """
    USB (adb) :  adb reverse tcp:8765 tcp:8765
                 puis adresse : http://127.0.0.1:8765

    Câble USB + partage de connexion :
                 adresse : http://192.168.42.129:8765
                 (IP du PC côté partage USB)

    Wi-Fi (même réseau) :
                 lancer le serveur avec --host 0.0.0.0
                 adresse : http://<IP-du-PC>:8765
    """.trimIndent()

@Composable
fun ConnectScreen(
    url: String,
    error: String?,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(url) { mutableStateOf(url) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PCMON", style = MaterialTheme.typography.displayMedium, color = PcmonWhite)
        Spacer(Modifier.height(4.dp))
        Text(
            "moniteur PC en temps réel",
            style = MaterialTheme.typography.bodyMedium,
            color = PcmonDim,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onUrlChange(it)
            },
            label = { Text("Adresse du serveur") },
            placeholder = { Text("http://127.0.0.1:8765", color = PcmonDim) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PcmonWhite,
                    unfocusedBorderColor = PcmonBorder,
                    focusedLabelColor = PcmonMid,
                    unfocusedLabelColor = PcmonDim,
                    cursorColor = PcmonWhite,
                    focusedTextColor = PcmonWhite,
                    unfocusedTextColor = PcmonLight,
                ),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = PcmonWhite,
                    contentColor = Color.Black,
                ),
        ) {
            Text("CONNEXION")
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "hors ligne : $error — reconnexion automatique...",
                style = MaterialTheme.typography.bodySmall,
                color = PcmonMid,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(HELP, style = MaterialTheme.typography.bodySmall, color = PcmonDim)
    }
}
