package dev.vibe.pcmon.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vibe.pcmon.theme.PcmonBlack
import dev.vibe.pcmon.theme.PcmonDim
import dev.vibe.pcmon.theme.PcmonWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcmonApp(vm: DashboardViewModel = viewModel()) {
    val connected by vm.connected.collectAsState()
    val url by vm.url.collectAsState()
    val error by vm.lastError.collectAsState()
    val foundServer by vm.foundServer.collectAsState()
    var draft by remember(url) { mutableStateOf(url) }
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = PcmonBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PCMON_",
                        style = MaterialTheme.typography.titleMedium,
                        color = PcmonWhite,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = PcmonBlack,
                        titleContentColor = PcmonWhite,
                    ),
                actions = {
                    ConnectionDot(connected)
                    if (connected) {
                        IconButton(onClick = { vm.disconnect() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Déconnecter",
                                tint = PcmonWhite,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (connected) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = PcmonBlack,
                    contentColor = PcmonWhite,
                    divider = {},
                    indicator = {},
                ) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("GRAPHIQUES") },
                        selectedContentColor = PcmonWhite,
                        unselectedContentColor = PcmonDim,
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("SYSTÈME") },
                        selectedContentColor = PcmonWhite,
                        unselectedContentColor = PcmonDim,
                    )
                }
                if (tab == 0) {
                    DashboardScreen(vm, modifier = Modifier.weight(1f))
                } else {
                    SystemScreen(vm, modifier = Modifier.weight(1f))
                }
            }
        } else {
            ConnectScreen(
                url = draft,
                foundServer = foundServer,
                error = error,
                onUrlChange = { draft = it },
                onConnect = { vm.connect(draft) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "alpha",
        )
    Box(
        modifier =
            Modifier
                .padding(end = 12.dp)
                .size(10.dp)
                .alpha(if (connected) alpha else 0.3f)
                .background(if (connected) PcmonWhite else PcmonDim),
    )
}
