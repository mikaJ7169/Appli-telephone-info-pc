package dev.vibe.pcmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vibe.pcmon.theme.PcmonBorder
import dev.vibe.pcmon.theme.PcmonDim
import dev.vibe.pcmon.theme.PcmonPanel
import dev.vibe.pcmon.theme.PcmonWhite

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    chart: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, PcmonBorder)
                .background(PcmonPanel)
                .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = PcmonDim)
            Text(value, style = MaterialTheme.typography.labelMedium, color = PcmonWhite)
        }
        Spacer(Modifier.height(8.dp))
        chart()
        if (sub != null) {
            Spacer(Modifier.height(6.dp))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = PcmonDim)
        }
    }
}
