import com.soywiz.korio.async.EventLoop
import com.soywiz.korio.async.spawn
import com.soywiz.korio.net.ws.websockets
import java.net.URI

object KorioWebsocketExample {
	@JvmStatic fun main(args: Array<String>) = EventLoop.main {
		//val ws = websockets.create(URI("ws://echo.websocket.org"), protocols = listOf("echo"))
		val ws = websockets.create(URI("ws://echo.websocket.org"), debug = true)
		spawn {
			for (message in ws.onAnyMessage) {
				when (message) {
					is String -> println("recv.text: $message")
					is ByteArray -> println("recv.binary: ${message.toList()}")
				}

			}
		}
		ws.send("hello")
		ws.send(byteArrayOf(1, 2, 3, 4))
	}
}
