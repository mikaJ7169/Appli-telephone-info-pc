package dev.vibe.pcmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
                .clip(RoundedCornerShape(18.dp))
                .background(PcmonPanel)
                .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = PcmonDim)
            Text(value, style = MaterialTheme.typography.titleLarge, color = PcmonWhite)
        }
        Spacer(Modifier.height(10.dp))
        chart()
        if (sub != null) {
            Spacer(Modifier.height(8.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = PcmonDim)
        }
    }
}
