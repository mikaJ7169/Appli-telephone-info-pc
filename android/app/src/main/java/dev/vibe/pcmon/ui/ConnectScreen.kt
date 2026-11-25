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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Connexion automatique : branche le téléphone en USB (partage de
    connexion) ou mets-le sur le même Wi-Fi que le PC, puis lance
    l'appli — le PC est détecté tout seul.

    Le champ ci-dessous sert uniquement de secours manuel
    (ex. http://192.168.1.21:8765).
    """.trimIndent()

@Composable
fun ConnectScreen(
    url: String,
    foundServer: String?,
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
        Spacer(Modifier.height(6.dp))
        Text(
            "moniteur PC en temps réel",
            style = MaterialTheme.typography.bodyMedium,
            color = PcmonDim,
        )
        Spacer(Modifier.height(28.dp))
        if (foundServer != null) {
            Text(
                "PC trouvé : $foundServer — connexion…",
                style = MaterialTheme.typography.bodyMedium,
                color = PcmonWhite,
            )
        } else {
            Text(
                "Recherche automatique d'un PC sur le réseau…",
                style = MaterialTheme.typography.bodyMedium,
                color = PcmonMid,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "hors ligne : $error — nouvelle tentative…",
                style = MaterialTheme.typography.bodySmall,
                color = PcmonDim,
            )
        }
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onUrlChange(it)
            },
            label = { Text("Adresse manuelle (secours)") },
            placeholder = { Text("http://192.168.1.21:8765", color = PcmonDim) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
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
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = PcmonWhite,
                    contentColor = Color.Black,
                ),
        ) {
            Text("CONNEXION MANUELLE")
        }
        Spacer(Modifier.height(24.dp))
        Text(HELP, style = MaterialTheme.typography.bodySmall, color = PcmonDim)
    }
}
