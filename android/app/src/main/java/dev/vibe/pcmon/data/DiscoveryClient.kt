package dev.vibe.pcmon.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class DiscoveryClient {
    private var job: Job? = null

    fun start(onFound: (String) -> Unit) {
        stop()
        job =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        val socket = DatagramSocket(null)
                        socket.reuseAddress = true
                        socket.bind(InetSocketAddress(8766))
                        socket.soTimeout = 1000
                        while (isActive) {
                            try {
                                val buf = ByteArray(256)
                                val packet = DatagramPacket(buf, buf.size)
                                socket.receive(packet)
                                val msg = String(packet.data, 0, packet.length).trim()
                                if (msg.startsWith("PCMON/1")) {
                                    val port = msg.split(" ").lastOrNull()?.toIntOrNull() ?: 8765
                                    val host = packet.address.hostAddress
                                    if (host != null) onFound("http://$host:$port")
                                }
                            } catch (_: SocketTimeoutException) {
                            } catch (_: Exception) {
                                break
                            }
                        }
                        socket.close()
                    } catch (_: Exception) {
                        delay(2000)
                    }
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
