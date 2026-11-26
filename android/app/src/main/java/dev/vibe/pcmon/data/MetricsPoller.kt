package dev.vibe.pcmon.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MetricsPoller {
    private var job: Job? = null

    fun start(
        baseUrl: String,
        intervalMs: Long = 1000,
        onMetrics: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        stop()
        val trimmed = baseUrl.trimEnd('/')
        job =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        val conn =
                            (URL("$trimmed/metrics").openConnection() as HttpURLConnection).apply {
                                requestMethod = "GET"
                                connectTimeout = 4000
                                readTimeout = 4000
                            }
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().readText()
                            conn.disconnect()
                            onMetrics(body)
                            delay(intervalMs)
                        } else {
                            conn.disconnect()
                            throw IllegalStateException("HTTP ${conn.responseCode}")
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            onError(e)
                            delay(3000)
                        }
                    }
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
