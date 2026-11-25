package dev.vibe.pcmon.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vibe.pcmon.data.DiscoveryClient
import dev.vibe.pcmon.data.MetricHistory
import dev.vibe.pcmon.data.Metrics
import dev.vibe.pcmon.data.SseClient
import dev.vibe.pcmon.data.parseMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pcmon", Context.MODE_PRIVATE)
    private val client = SseClient()
    private val discovery = DiscoveryClient()
    private val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val history = MetricHistory()

    private val _url = MutableStateFlow(prefs.getString("url", "") ?: "")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _metrics = MutableStateFlow<Metrics?>(null)
    val metrics: StateFlow<Metrics?> = _metrics.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _foundServer = MutableStateFlow<String?>(null)
    val foundServer: StateFlow<String?> = _foundServer.asStateFlow()

    private val discovered = Channel<String>(Channel.UNLIMITED)

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModelScope.launch { pushGatewayCandidates() }
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) {
                viewModelScope.launch { pushGatewayCandidates() }
            }
        }

    init {
        discovery.start { found ->
            _foundServer.value = found
            discovered.trySend(found)
        }
        runCatching {
            connectivity.registerNetworkCallback(
                android.net.NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback,
            )
        }
        viewModelScope.launch { pushGatewayCandidates() }
        viewModelScope.launch { autoConnectLoop() }
    }

    private fun gatewayUrls(): List<String> {
        val out = mutableListOf<String>()
        runCatching {
            val lp = connectivity.getLinkProperties(connectivity.activeNetwork)
            lp?.routes?.forEach { route ->
                if (route.isDefaultRoute) {
                    route.gateway?.hostAddress?.let { out.add("http://$it:8765") }
                }
            }
        }
        return out.distinct()
    }

    private suspend fun pushGatewayCandidates() {
        gatewayUrls().forEach { discovered.trySend(it) }
    }

    private suspend fun probe(url: String, timeoutMs: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val conn =
                    (URL("${url.trimEnd('/')}/metrics").openConnection() as HttpURLConnection).apply {
                        connectTimeout = timeoutMs
                        readTimeout = timeoutMs
                    }
                val ok = conn.responseCode == 200
                conn.disconnect()
                ok
            } catch (_: Exception) {
                false
            }
        }

    private suspend fun autoConnectLoop() {
        while (isActive) {
            if (!connected.value) {
                val saved = _url.value
                val base = mutableListOf<String>()
                if (saved.isNotBlank()) base.add(saved)
                base.addAll(gatewayUrls())
                val deadline = System.currentTimeMillis() + 8000
                var connectedNow = false
                while (!connectedNow && System.currentTimeMillis() < deadline && !connected.value) {
                    for (u in base.toSet()) {
                        if (connected.value) break
                        if (probe(u, 2500)) {
                            connect(u)
                            connectedNow = true
                            break
                        }
                    }
                    if (!connectedNow) {
                        val d = discovered.tryReceive().getOrNull()
                        if (d != null) {
                            if (probe(d, 2500)) {
                                connect(d)
                                connectedNow = true
                            }
                        } else {
                            delay(1000)
                        }
                    }
                }
            }
            delay(3000)
        }
    }

    fun connect(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val normalized = if (trimmed.startsWith("http")) trimmed else "http://$trimmed"
        _url.value = normalized
        prefs.edit().putString("url", normalized).apply()
        _lastError.value = null
        client.start(
            normalized,
            autoReconnect = true,
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
        discovery.stop()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
    }
}
