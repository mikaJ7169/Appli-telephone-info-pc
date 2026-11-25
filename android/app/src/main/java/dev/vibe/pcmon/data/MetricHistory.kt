package dev.vibe.pcmon.data

class MetricHistory(val capacity: Int = 120) {
    private val series = mutableMapOf<String, ArrayDeque<Float>>()

    fun add(m: Metrics) {
        put("cpu", m.cpuPercent)
        put("ram", m.ramPercent)
        put("swap", m.swapPercent)
        put("disk_read", m.diskReadMbps)
        put("disk_write", m.diskWriteMbps)
        put("net_down", m.netDownKbps)
        put("net_up", m.netUpKbps)
        if (m.gpuAvailable) {
            put("gpu", m.gpuPercent)
            put("gpu_mem", m.gpuMemPercent)
        }
        if (!m.cpuTemp.isNaN()) put("temp", m.cpuTemp)
    }

    fun get(key: String): List<Float> = series[key]?.toList() ?: emptyList()

    private fun put(
        key: String,
        value: Float,
    ) {
        if (value.isNaN()) return
        val q = series.getOrPut(key) { ArrayDeque(capacity) }
        q.addLast(value)
        while (q.size > capacity) q.removeFirst()
    }
}
