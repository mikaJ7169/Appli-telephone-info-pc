package dev.vibe.pcmon.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vibe.pcmon.data.DiscoveryClient
import dev.vibe.pcmon.data.MetricHistory
import dev.vibe.pcmon.data.Metrics
import dev.vibe.pcmon.data.MetricsPoller
import dev.vibe.pcmon.data.parseMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pcmon", Context.MODE_PRIVATE)
    private val client = MetricsPoller()
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

    @Volatile
    private var lastScan = 0L

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                lastScan = 0L
                viewModelScope.launch { pushGatewayCandidates() }
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) {
                lastScan = 0L
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
            val lp = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return@runCatching
            val routes: List<RouteInfo> = lp.routes
            for (route in routes) {
                if (route.isDefaultRoute) {
                    route.gateway?.hostAddress?.let { out.add("http://$it:8765") }
                }
            }
        }
        return out.distinct()
    }

    private fun subnetHosts(): List<String> {
        val out = mutableListOf<String>()
        runCatching {
            val lp = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return@runCatching
            val linkAddresses: List<LinkAddress> = lp.linkAddresses
            for (la in linkAddresses) {
                val addr = la.address
                if (addr is Inet4Address) {
                    val ip = addr.hostAddress ?: continue
                    val prefix = la.prefixLength
                    if (prefix in 8..30) out.addAll(scanTargets(ip, prefix))
                }
            }
        }
        return out.distinct()
    }

    private fun scanTargets(ip: String, prefix: Int): List<String> {
        val parts = ip.split(".").map { it.toInt() }
        val total = 1 shl (32 - prefix)
        val cap = minOf(total - 2, 254)
        val base = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val network = base and mask
        val hosts = mutableListOf<String>()
        for (i in 1..cap) {
            val a = network + i
            hosts.add("${(a ushr 24) and 0xFF}.${(a ushr 16) and 0xFF}.${(a ushr 8) and 0xFF}.${a and 0xFF}")
        }
        return hosts
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

    private suspend fun probeSubnet(): String? =
        coroutineScope {
            val targets = subnetHosts()
            if (targets.isEmpty()) return@coroutineScope null
            val sem = Semaphore(24)
            val found = AtomicReference<String?>(null)
            targets.forEach { host ->
                launch(Dispatchers.IO) {
                    sem.withPermit {
                        if (found.get() == null && probe("http://$host:8765", 1200)) {
                            found.set("http://$host:8765")
                        }
                    }
                }
            }
            found.get()
        }

    private suspend fun autoConnectLoop() {
        while (coroutineContext.isActive) {
            if (!connected.value) {
                val saved = _url.value
                val quick = mutableListOf<String>()
                if (saved.isNotBlank()) quick.add(saved)
                quick.addAll(gatewayUrls())
                var ok = false
                for (u in quick.toSet()) {
                    if (connected.value) {
                        ok = true
                        break
                    }
                    if (probe(u, 2500)) {
                        connect(u)
                        ok = true
                        break
                    }
                }
                if (!ok) {
                    val deadline = System.currentTimeMillis() + 8000
                    while (System.currentTimeMillis() < deadline && !connected.value) {
                        val d = discovered.tryReceive().getOrNull()
                        if (d != null) {
                            if (probe(d, 2500)) {
                                connect(d)
                                ok = true
                                break
                            }
                        } else {
                            delay(500)
                        }
                    }
                }
                if (!ok && !connected.value) {
                    val found = _foundServer.value
                    if (found != null) {
                        _lastError.value =
                            "PC détecté ($found) mais port 8765 injoignable — pare-feu Windows ? ou serveur lancé sans --host 0.0.0.0 ? Teste depuis le navigateur du téléphone : $found/"
                    } else {
                        _lastError.value = "Aucun PC détecté sur le réseau (UDP 8766 + scan du sous-réseau)"
                    }
                }
                if (!ok && !connected.value && System.currentTimeMillis() - lastScan > 20000) {
                    lastScan = System.currentTimeMillis()
                    val found = probeSubnet()
                    if (found != null && !connected.value) connect(found)
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
            onMetrics = { json ->
                viewModelScope.launch(Dispatchers.Default) {
                    try {
                        val m = parseMetrics(json)
                        history.add(m)
                        withContext(Dispatchers.Main) {
                            _metrics.value = m
                            _connected.value = true
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            _lastError.value = "parse: ${e.message ?: e.javaClass.simpleName}"
                        }
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
