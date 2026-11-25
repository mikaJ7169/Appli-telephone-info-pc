package dev.vibe.pcmon.data

import org.json.JSONObject

data class Proc(val name: String, val cpu: Float, val mem: Float)

data class Metrics(
    val ts: Double,
    val host: String,
    val os: String,
    val cpuModel: String,
    val uptimeSec: Long,
    val cpuPercent: Float,
    val cpuPerCore: List<Float>,
    val cpuCount: Int,
    val cpuFreqMhz: Float,
    val ramTotal: Long,
    val ramUsed: Long,
    val ramPercent: Float,
    val swapPercent: Float,
    val diskPercent: Float,
    val diskTotal: Long,
    val diskUsed: Long,
    val diskReadMbps: Float,
    val diskWriteMbps: Float,
    val netDownKbps: Float,
    val netUpKbps: Float,
    val gpuAvailable: Boolean,
    val gpuName: String,
    val gpuPercent: Float,
    val gpuMemPercent: Float,
    val gpuMemUsed: Long,
    val gpuMemTotal: Long,
    val gpuTemp: Float,
    val cpuTemp: Float,
    val hasBattery: Boolean,
    val batteryPercent: Float,
    val batteryPlugged: Boolean,
    val processes: List<Proc>,
)

fun parseMetrics(json: String): Metrics {
    val o = JSONObject(json)
    val cpu = o.getJSONObject("cpu")
    val ram = o.getJSONObject("ram")
    val disk = o.getJSONObject("disk")
    val net = o.getJSONObject("net")
    val gpu = o.optJSONObject("gpu")
    val temps = o.optJSONObject("temps")
    val bat = o.optJSONObject("battery")
    val swap = o.optJSONObject("swap")
    val perCore = cpu.optJSONArray("per_core")
    val perCoreList =
        if (perCore != null) List(perCore.length()) { perCore.getDouble(it).toFloat() } else emptyList()
    val procs = o.optJSONArray("processes")
    val procList =
        if (procs != null) {
            List(procs.length()) { i ->
                val p = procs.getJSONObject(i)
                Proc(p.optString("name"), p.optDouble("cpu").toFloat(), p.optDouble("mem").toFloat())
            }
        } else {
            emptyList()
        }
    return Metrics(
        ts = o.optDouble("ts"),
        host = o.optString("host"),
        os = o.optString("os"),
        cpuModel = o.optString("cpu_model"),
        uptimeSec = o.optLong("uptime"),
        cpuPercent = cpu.optDouble("percent").toFloat(),
        cpuPerCore = perCoreList,
        cpuCount = cpu.optInt("count"),
        cpuFreqMhz = cpu.optDouble("freq_mhz").toFloat(),
        ramTotal = ram.optLong("total"),
        ramUsed = ram.optLong("used"),
        ramPercent = ram.optDouble("percent").toFloat(),
        swapPercent = swap?.optDouble("percent")?.toFloat() ?: 0f,
        diskPercent = disk.optDouble("percent").toFloat(),
        diskTotal = disk.optLong("total"),
        diskUsed = disk.optLong("used"),
        diskReadMbps = disk.optDouble("read_mbps").toFloat(),
        diskWriteMbps = disk.optDouble("write_mbps").toFloat(),
        netDownKbps = net.optDouble("down_kbps").toFloat(),
        netUpKbps = net.optDouble("up_kbps").toFloat(),
        gpuAvailable = gpu?.optBoolean("available") == true,
        gpuName = gpu?.optString("name") ?: "",
        gpuPercent = gpu?.optDouble("percent")?.toFloat() ?: 0f,
        gpuMemPercent = gpu?.optDouble("mem_percent")?.toFloat() ?: 0f,
        gpuMemUsed = gpu?.optLong("mem_used") ?: 0L,
        gpuMemTotal = gpu?.optLong("mem_total") ?: 0L,
        gpuTemp = gpu?.optDouble("temp")?.toFloat() ?: 0f,
        cpuTemp = temps?.optDouble("cpu")?.toFloat() ?: Float.NaN,
        hasBattery = bat != null,
        batteryPercent = bat?.optDouble("percent")?.toFloat() ?: 0f,
        batteryPlugged = bat?.optBoolean("plugged") == true,
        processes = procList,
    )
}
