package dev.vibe.pcmon.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vibe.pcmon.data.MetricHistory
import dev.vibe.pcmon.data.Metrics
import dev.vibe.pcmon.data.SseClient
import dev.vibe.pcmon.data.parseMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pcmon", Context.MODE_PRIVATE)
    private val client = SseClient()
    val history = MetricHistory()

    private val _url =
        MutableStateFlow(prefs.getString("url", "http://127.0.0.1:8765") ?: "http://127.0.0.1:8765")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _metrics = MutableStateFlow<Metrics?>(null)
    val metrics: StateFlow<Metrics?> = _metrics.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun connect(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val normalized = if (trimmed.startsWith("http")) trimmed else "http://$trimmed"
        _url.value = normalized
        prefs.edit().putString("url", normalized).apply()
        _connected.value = false
        _lastError.value = null
        client.start(
            normalized,
            onOpen = {
                viewModelScope.launch(Dispatchers.Main) { _connected.value = true }
            },
            onEvent = { json ->
                viewModelScope.launch(Dispatchers.Default) {
                    try {
                        val m = parseMetrics(json)
                        history.add(m)
                        withContext(Dispatchers.Main) {
                            _metrics.value = m
                            _connected.value = true
                        }
                    } catch (_: Exception) {
                    }
                }
            },
            onError = { e ->
                viewModelScope.launch(Dispatchers.Main) {
                    _connected.value = false
                    _lastError.value = e.message ?: e.javaClass.simpleName
                }
            },
        )
    }

    fun disconnect() {
        client.stop()
        _connected.value = false
    }

    override fun onCleared() {
        super.onCleared()
        client.stop()
    }
}
