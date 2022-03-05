package com.bandyer.flipper_socket_io_plugin

import com.facebook.flipper.plugins.network.FlipperOkhttpInterceptor
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class FlipperOKHttpClient(
    private val SIONetworkFlipperPlugin: SIONetworkFlipperPlugin,
    okHttpClient: OkHttpClient = OkHttpClient()
) : OkHttpClient() {

    private var client: OkHttpClient = okHttpClient.newBuilder().addInterceptor(FlipperOkhttpInterceptor(SIONetworkFlipperPlugin.networkFlipperPlugin)).build()

    override fun newCall(request: Request): Call = client.newCall(request)

    override fun newBuilder(): Builder = client.newBuilder()

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {

        val url = request.url.toUrl().toString()

        return StethoWebSocket(
            client.newWebSocket(request, object : WebSocketListener() {

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    super.onClosed(webSocket, code, reason)
                    listener.onClosed(webSocket, code, reason)
                    SIONetworkFlipperPlugin.emit(url, "socket closed - ${reason.takeIf { it.isNotBlank() } ?: code}")
                    SIONetworkFlipperPlugin.clearEvents(url)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    super.onClosing(webSocket, code, reason)
                    listener.onClosing(webSocket, code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    super.onFailure(webSocket, t, response)
                    listener.onFailure(webSocket, t, response)
                    SIONetworkFlipperPlugin.emit(url, t.message ?: "socket failed")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    listener.onMessage(webSocket, text)
                    SIONetworkFlipperPlugin.on(url, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    super.onMessage(webSocket, bytes)
                    listener.onMessage(webSocket, bytes)
                    SIONetworkFlipperPlugin.on(url, bytes.string(SIONetworkFlipperPlugin.charset))
                }

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    super.onOpen(webSocket, response)
                    listener.onOpen(webSocket, response)
                    SIONetworkFlipperPlugin.emit(url, response.message)
                }
            }),
            SIONetworkFlipperPlugin
        )
    }

    private class StethoWebSocket(private val wrappedSocket: WebSocket, private val SIONetworkFlipperPlugin: SIONetworkFlipperPlugin) : WebSocket {

        private val url = request().url.toUrl().toString()

        override fun queueSize() = wrappedSocket.queueSize()

        override fun send(text: String): Boolean {
            SIONetworkFlipperPlugin.emit(url, text)
            return wrappedSocket.send(text)
        }

        override fun send(bytes: ByteString): Boolean {
            SIONetworkFlipperPlugin.emit(url, bytes.string(SIONetworkFlipperPlugin.charset))
            return wrappedSocket.send(bytes)
        }

        override fun close(code: Int, reason: String?) = wrappedSocket.close(code, reason)

        override fun cancel() = wrappedSocket.cancel()

        override fun request() = wrappedSocket.request()
    }
}