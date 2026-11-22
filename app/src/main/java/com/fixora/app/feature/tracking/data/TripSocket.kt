package com.fixora.app.feature.tracking.data

import com.fixora.app.BuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Una conexión al tiempo real de la API (`/ws`). Interfaz para probar sin red. */
interface TripSocket {
    /** Mensajes de texto del servidor; se cierra cuando se cae o se cierra la conexión. */
    val incoming: ReceiveChannel<String>

    /** false si la conexión ya no está abierta. */
    fun send(text: String): Boolean

    fun close()
}

fun interface TripSocketFactory {
    fun open(): TripSocket
}

/** WebSocket de OkHttp a la misma API que Retrofit: `http://host/api/` usa `ws://host/ws`. */
class OkHttpTripSocketFactory(client: OkHttpClient) : TripSocketFactory {

    // Sin el token de Retrofit (el socket se autentica con un mensaje) y con ping para notar
    // pronto una conexión muerta al cambiar de antena o de wifi a datos.
    private val client = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    private val url = BuildConfig.API_URL.toHttpUrlOrNull()?.resolve("/ws")

    override fun open(): TripSocket {
        val channel = Channel<String>(Channel.UNLIMITED)
        val url = url
        if (url == null) {
            channel.close()
            return ClosedSocket(channel)
        }
        val webSocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    channel.trySend(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                    channel.close()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    channel.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    channel.close()
                }
            },
        )
        return object : TripSocket {
            override val incoming: ReceiveChannel<String> = channel
            override fun send(text: String) = webSocket.send(text)
            override fun close() {
                webSocket.close(NORMAL_CLOSURE, null)
                channel.close()
            }
        }
    }

    private class ClosedSocket(override val incoming: ReceiveChannel<String>) : TripSocket {
        override fun send(text: String) = false
        override fun close() = Unit
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val PING_INTERVAL_SECONDS = 25L
    }
}
