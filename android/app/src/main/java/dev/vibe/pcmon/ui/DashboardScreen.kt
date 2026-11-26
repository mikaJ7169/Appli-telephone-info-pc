package dev.vibe.pcmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private data class ChartSpec(
    val label: String,
    val value: String,
    val sub: String,
    val primary: List<Float>,
    val secondary: List<Float>?,
    val max: Float,
)

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val metrics by vm.metrics.collectAsState()
    val m = metrics
    if (m == null) {
        val error by vm.lastError.collectAsState()
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Connexion au PC…", style = MaterialTheme.typography.bodyMedium, color = PcmonDim)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("erreur : $error", style = MaterialTheme.typography.bodySmall, color = PcmonMid)
                }
            }
        }
        return
    }
    val h = vm.history

    val specs = mutableListOf<ChartSpec>()
    specs.add(
        ChartSpec(
            label = "CPU",
            value = formatPercent(m.cpuPercent),
            sub =
                buildString {
                    append(m.cpuCount)
                    append(" cœurs")
                    if (m.cpuFreqMhz > 0 && !m.cpuFreqMhz.isNaN()) {
                        append(" · ")
                        append(m.cpuFreqMhz.toInt())
                        append(" MHz")
                    }
                },
            primary = h.get("cpu"),
            secondary = null,
            max = 100f,
        ),
    )
    specs.add(
        ChartSpec(
            label = "RAM",
            value = formatPercent(m.ramPercent),
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
            primary = h.get("ram"),
            secondary = null,
            max = 100f,
        ),
    )
    if (m.gpuAvailable) {
        specs.add(
            ChartSpec(
                label = "GPU",
                value = formatPercent(m.gpuPercent),
                sub =
                    buildString {
                        append(m.gpuName)
                        append("\nVRAM ")
                        append(formatPercent(m.gpuMemPercent))
                        if (m.gpuTemp > 0) {
                            append(" · ")
                            append(m.gpuTemp.toInt())
                            append("°C")
                        }
                    },
                primary = h.get("gpu"),
                secondary = null,
                max = 100f,
            ),
        )
    }
    val netMax = max(64f, (h.get("net_down").maxOrNull() ?: 0f) * 1.2f)
    val netStats = stats(h.get("net_down"))
    specs.add(
        ChartSpec(
            label = "RÉSEAU",
            value = "↓ ${formatRate(m.netDownKbps)}",
            sub =
                buildString {
                    append("↑ ")
                    append(formatRate(m.netUpKbps))
                    append("\nmin/max ↓ ")
                    append(formatRate(netStats.first))
                    append(" / ")
                    append(formatRate(netStats.third))
                },
            primary = h.get("net_down"),
            secondary = h.get("net_up"),
            max = netMax,
        ),
    )
    val ioMax =
        max(
            16f,
            max(h.get("disk_read").maxOrNull() ?: 0f, h.get("disk_write").maxOrNull() ?: 0f) * 1.2f,
        )
    specs.add(
        ChartSpec(
            label = "DISQUE",
            value = formatPercent(m.diskPercent),
            sub =
                buildString {
                    append("R ")
                    append(formatMbps(m.diskReadMbps))
                    append("\nW ")
                    append(formatMbps(m.diskWriteMbps))
                    append("\n")
                    append(formatBytes(m.diskUsed))
                    append("/")
                    append(formatBytes(m.diskTotal))
                },
            primary = h.get("disk_read"),
            secondary = h.get("disk_write"),
            max = ioMax,
        ),
    )
    if (!m.cpuTemp.isNaN()) {
        specs.add(
            ChartSpec(
                label = "TEMP CPU",
                value = "${m.cpuTemp.toInt()}°C",
                sub = "max ${stats(h.get("temp")).third.toInt()}°C",
                primary = h.get("temp"),
                secondary = null,
                max = 100f,
            ),
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cols = if (maxWidth >= 620.dp) 4 else 2
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            specs.chunked(cols).forEach { rowSpecs ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowSpecs.forEach { spec ->
                        ChartCard(spec, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    spec: ChartSpec,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(PcmonPanel)
                .padding(10.dp),
    ) {
        LineChart(
            primary = spec.primary,
            secondary = spec.secondary,
            max = spec.max,
            modifier = Modifier.weight(1.6f).fillMaxHeight(),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(spec.label, style = MaterialTheme.typography.labelSmall, color = PcmonDim)
            Spacer(Modifier.height(2.dp))
            Text(
                spec.value,
                style = MaterialTheme.typography.titleMedium,
                color = PcmonWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(spec.sub, style = MaterialTheme.typography.bodySmall, color = PcmonMid)
        }
    }
}

@Composable
fun SystemScreen(
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val metrics by vm.metrics.collectAsState()
    val m = metrics
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (m == null) {
            Text("Connexion au PC…", style = MaterialTheme.typography.bodyMedium, color = PcmonDim)
            return
        }
        SystemCard(m)
        ProcessesCard(m)
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
