package com.bandyer.flipper_socket_io_plugin

import android.content.Context
import com.bandyer.flipper_socket_io_plugin.SIONetworkFlipperPlugin.SocketEvent.Disconnect
import com.bandyer.flipper_socket_io_plugin.SIONetworkFlipperPlugin.SocketEvent.Emit
import com.bandyer.flipper_socket_io_plugin.SIONetworkFlipperPlugin.SocketEvent.On
import com.bandyer.flipper_socket_io_plugin.SIONetworkFlipperPlugin.SocketEvent.Ping
import com.bandyer.flipper_socket_io_plugin.SIONetworkFlipperPlugin.SocketEvent.Pong
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.core.FlipperArray
import com.facebook.flipper.core.FlipperConnection
import com.facebook.flipper.core.FlipperObject
import com.facebook.flipper.core.FlipperPlugin
import com.facebook.flipper.core.FlipperReceiver
import com.facebook.flipper.core.FlipperResponder
import com.facebook.flipper.plugins.network.NetworkFlipperPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.UUID

class SIONetworkFlipperPlugin(context: Context) : FlipperPlugin {

    val networkFlipperPlugin = NetworkFlipperPlugin().apply {
        AndroidFlipperClient.getInstance(context).addPlugin(this)
    }

    override fun getId(): String = "SocketIO"

    private var connection: FlipperConnection? = null

    private var socketEvents: MutableMap<String, MutableList<SocketEvent>> = mutableMapOf()

    var charset = Charset.defaultCharset()

    sealed class SocketEvent(val body: String, val type: Type) {

        private val id: Int = UUID.randomUUID().toString().hashCode()

        val asJsonObject: JSONObject = JSONObject()
            .put("id", id)
            .put("body", body.replace("^[- 0-9]*".toRegex(), "").takeIf { it.isNotBlank() } ?: body)
            .put("type", type.value)

        enum class Type(val value: String) {
            Send("emit"),
            Receive("on")
        }

        object Ping : SocketEvent(body = "ping", type = Type.Receive)
        class On(body: String) : SocketEvent(body, type = Type.Receive)

        object Open : SocketEvent(body = "socket-open", type = Type.Send)
        object Disconnect : SocketEvent(body = "socket disconnected", type = Type.Send)
        object Pong : SocketEvent(body = "pong", type = Type.Send)
        class Emit(body: String) : SocketEvent(body, type = Type.Send)
    }

    @Synchronized
    fun emit(url: String, text: String) {
        val event = when (text) {
            "41" -> Disconnect
            "40" -> SocketEvent.Open
            "3"  -> Pong
            else -> Emit(text)
        }

        if (socketEvents[url].isNullOrEmpty()) socketEvents[url] = mutableListOf(event)
        else socketEvents[url]!!.add(event)

        updateEvents(url, socketEvents[url]!!)
    }

    @Synchronized
    fun on(url: String, text: String) {
        val event = when (text) {
            "2"  -> Ping
            else -> On(text)
        }

        if (socketEvents[url].isNullOrEmpty()) socketEvents[url] = mutableListOf(event)
        else socketEvents[url]!!.add(event)

        updateEvents(url, socketEvents[url]!!)
    }

    override fun onConnect(connection: FlipperConnection?) {
        this.connection = connection

        connection?.receive("clearLogs") { params, _ ->
            val url = params.getString("url")
            socketEvents[url]?.clear()
            updateEvents(url, socketEvents[url]!!)
        }
    }

    private fun updateEvents(url: String, events: List<SocketEvent>) {
        val payload = JSONArray().apply { events.forEach { put(it.asJsonObject) } }.toString()

        val row = FlipperObject.Builder()
            .put("id", url.hashCode())
            .put("url", url)
            .put("events", FlipperArray(payload))
            .build()

        connection?.send("newSocket", row)
    }

    @Synchronized
    override fun onDisconnect() {
        connection = null
        socketEvents.clear()
    }

    override fun runInBackground(): Boolean = true

    @Synchronized
    fun clearEvents(url: String) {
        socketEvents[url]!!.clear()
    }
}
