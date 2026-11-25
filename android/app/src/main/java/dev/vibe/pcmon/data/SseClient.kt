package dev.vibe.pcmon.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class SseClient {
    private var job: Job? = null

    fun start(
        baseUrl: String,
        onOpen: () -> Unit,
        onEvent: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        stop()
        val trimmed = baseUrl.trimEnd('/')
        val scope = CoroutineScope(Dispatchers.IO)
        job =
            scope.launch {
                while (isActive) {
                    try {
                        val conn =
                            (URL("$trimmed/stream").openConnection() as HttpURLConnection).apply {
                                requestMethod = "GET"
                                setRequestProperty("Accept", "text/event-stream")
                                connectTimeout = 5000
                                readTimeout = 0
                                instanceFollowRedirects = true
                            }
                        if (conn.responseCode == 200) {
                            onOpen()
                            conn.inputStream.bufferedReader().use { reader ->
                                readEvents(reader, onEvent)
                            }
                        } else {
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

    private fun readEvents(
        reader: BufferedReader,
        onEvent: (String) -> Unit,
    ) {
        val sb = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.startsWith("data:")) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line.removePrefix("data:").trim())
            } else if (line.isEmpty() && sb.isNotEmpty()) {
                onEvent(sb.toString())
                sb.setLength(0)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
