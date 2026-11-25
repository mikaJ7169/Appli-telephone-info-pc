package dev.vibe.pcmon.ui

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 o"
    val units = arrayOf("o", "Ko", "Mo", "Go", "To")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) {
        "$bytes ${units[0]}"
    } else {
        String.format(Locale.FRENCH, "%.1f %s", v, units[i])
    }
}

fun formatRate(kbps: Float): String =
    if (kbps >= 1024f) {
        String.format(Locale.FRENCH, "%.1f Mo/s", kbps / 1024f)
    } else {
        String.format(Locale.FRENCH, "%.0f Ko/s", kbps)
    }

fun formatMbps(mbps: Float): String =
    if (mbps >= 1024f) {
        String.format(Locale.FRENCH, "%.2f Go/s", mbps / 1024f)
    } else {
        String.format(Locale.FRENCH, "%.1f Mo/s", mbps)
    }

fun formatPercent(v: Float): String = String.format(Locale.FRENCH, "%.0f %%", v)

fun formatUptime(sec: Long): String {
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return when {
        d > 0 -> "${d}j ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

fun stats(values: List<Float>): Triple<Float, Float, Float> {
    if (values.isEmpty()) return Triple(0f, 0f, 0f)
    var min = Float.MAX_VALUE
    var max = -Float.MAX_VALUE
    var sum = 0f
    for (v in values) {
        if (v < min) min = v
        if (v > max) max = v
        sum += v
    }
    return Triple(min, sum / values.size, max)
}
