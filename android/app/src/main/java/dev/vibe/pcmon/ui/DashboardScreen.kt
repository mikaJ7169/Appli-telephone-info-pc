package dev.vibe.pcmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vibe.pcmon.data.Metrics
import dev.vibe.pcmon.theme.PcmonDim
import dev.vibe.pcmon.theme.PcmonMid
import dev.vibe.pcmon.theme.PcmonPanel
import dev.vibe.pcmon.theme.PcmonWhite
import java.util.Locale
import kotlin.math.max

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val metrics by vm.metrics.collectAsState()
    val m = metrics
    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 1100.dp)
                    .align(Alignment.TopCenter),
            columns = GridCells.Adaptive(minSize = 180.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (m == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val error by vm.lastError.collectAsState()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Connexion au PC…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PcmonDim,
                        )
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "erreur : $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = PcmonMid,
                            )
                        }
                    }
                }
                return@LazyVerticalGrid
            }
            val h = vm.history

            item {
                MetricCard(
                    "CPU",
                    formatPercent(m.cpuPercent),
                    sub =
                        buildString {
                            append(m.cpuCount)
                            append(" cœurs")
                            if (m.cpuFreqMhz > 0) {
                                append(" · ")
                                append(m.cpuFreqMhz.toInt())
                                append(" MHz")
                            }
                        },
                ) {
                    LineChart(h.get("cpu"), max = 100f)
                }
            }

            item {
                MetricCard(
                    "RAM",
                    formatPercent(m.ramPercent),
                    sub =
                        buildString {
                            append(formatBytes(m.ramUsed))
                            append(" / ")
                            append(formatBytes(m.ramTotal))
                            if (m.swapPercent > 0) {
                                append(" · swap ")
                                append(formatPercent(m.swapPercent))
                            }
                        },
                ) {
                    LineChart(h.get("ram"), max = 100f)
                }
            }

            if (m.gpuAvailable) {
                item {
                    MetricCard(
                        "GPU",
                        formatPercent(m.gpuPercent),
                        sub =
                            buildString {
                                append(m.gpuName)
                                append(" · VRAM ")
                                append(formatPercent(m.gpuMemPercent))
                                if (m.gpuTemp > 0) {
                                    append(" · ")
                                    append(m.gpuTemp.toInt())
                                    append("°C")
                                }
                            },
                    ) {
                        LineChart(h.get("gpu"), max = 100f)
                    }
                }
            } else {
                item {
                    MetricCard("GPU", "N/A", sub = "GPU non détecté : NVIDIA (NVML) ou AMD requis") {
                        LineChart(emptyList(), max = 100f)
                    }
                }
            }

            val netMax = max(64f, (h.get("net_down").maxOrNull() ?: 0f) * 1.2f)
            val netStats = stats(h.get("net_down"))
            item {
                MetricCard(
                    "RÉSEAU",
                    "↓ ${formatRate(m.netDownKbps)}",
                    sub =
                        buildString {
                            append("↑ ")
                            append(formatRate(m.netUpKbps))
                            append(" · min/max ↓ ")
                            append(formatRate(netStats.first))
                            append(" / ")
                            append(formatRate(netStats.third))
                        },
                ) {
                    LineChart(h.get("net_down"), secondary = h.get("net_up"), max = netMax)
                }
            }

            val ioMax =
                max(
                    16f,
                    max(h.get("disk_read").maxOrNull() ?: 0f, h.get("disk_write").maxOrNull() ?: 0f) * 1.2f,
                )
            item {
                MetricCard(
                    "DISQUE",
                    formatPercent(m.diskPercent),
                    sub =
                        buildString {
                            append("R ")
                            append(formatMbps(m.diskReadMbps))
                            append(" · W ")
                            append(formatMbps(m.diskWriteMbps))
                            append(" · ")
                            append(formatBytes(m.diskUsed))
                            append("/")
                            append(formatBytes(m.diskTotal))
                        },
                ) {
                    LineChart(h.get("disk_read"), secondary = h.get("disk_write"), max = ioMax)
                }
            }

            if (!m.cpuTemp.isNaN()) {
                item {
                    MetricCard(
                        "TEMP CPU",
                        "${m.cpuTemp.toInt()}°C",
                        sub = "max ${stats(h.get("temp")).third.toInt()}°C sur la fenêtre",
                    ) {
                        LineChart(h.get("temp"), max = 100f)
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SystemCard(m) }
            item(span = { GridItemSpan(maxLineSpan) }) { ProcessesCard(m) }
        }
    }
}

@Composable
private fun SystemCard(m: Metrics) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PcmonPanel)
                .padding(14.dp),
    ) {
        Text("SYSTÈME", style = MaterialTheme.typography.labelLarge, color = PcmonDim)
        Spacer(Modifier.height(8.dp))
        InfoLine("Hôte", m.host)
        InfoLine("OS", m.os)
        InfoLine("CPU", m.cpuModel)
        InfoLine("Uptime", formatUptime(m.uptimeSec))
        if (m.hasBattery) {
            InfoLine(
                "Batterie",
                "${m.batteryPercent.toInt()} %" + if (m.batteryPlugged) " (secteur)" else " (batterie)",
            )
        }
    }
}

@Composable
private fun InfoLine(
    key: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = PcmonDim)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = PcmonWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProcessesCard(m: Metrics) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PcmonPanel)
                .padding(14.dp),
    ) {
        Text("PROCESSUS (top CPU)", style = MaterialTheme.typography.labelLarge, color = PcmonDim)
        Spacer(Modifier.height(8.dp))
        if (m.processes.isEmpty()) {
            Text("n/a", style = MaterialTheme.typography.bodySmall, color = PcmonDim)
        }
        m.processes.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcmonMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    String.format(Locale.FRENCH, "CPU %.0f %% · RAM %.1f %%", p.cpu, p.mem),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcmonWhite,
                )
            }
        }
    }
}
