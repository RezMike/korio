package com.soywiz.korio.net

import com.soywiz.korio.async.*
import com.soywiz.korio.concurrent.atomic.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.stream.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

abstract class AsyncSocketFactory {
	abstract suspend fun createClient(secure: Boolean = false): AsyncClient
	abstract suspend fun createServer(port: Int, host: String = "127.0.0.1", backlog: Int = 511, secure: Boolean = false): AsyncServer
}

internal expect val asyncSocketFactory: AsyncSocketFactory

suspend fun createTcpClient(secure: Boolean = false): AsyncClient = asyncSocketFactory.createClient(secure)
suspend fun createTcpServer(port: Int = AsyncServer.ANY_PORT, host: String = "127.0.0.1", backlog: Int = 511, secure: Boolean = false): AsyncServer = asyncSocketFactory.createServer(port, host, backlog, secure)

suspend fun createTcpClient(host: String, port: Int, secure: Boolean = false): AsyncClient = asyncSocketFactory.createClient(secure).apply { connect(host, port) }

interface AsyncClient : AsyncInputStream, AsyncOutputStream, AsyncCloseable {
	suspend fun connect(host: String, port: Int)
	val connected: Boolean
	override suspend fun read(buffer: ByteArray, offset: Int, len: Int): Int
	override suspend fun write(buffer: ByteArray, offset: Int, len: Int)
	override suspend fun close()
	//suspend open fun reconnect() = Unit

	object Stats {
		val writeCountStart = korAtomic(0L)
		val writeCountEnd = korAtomic(0L)
		val writeCountError = korAtomic(0L)

		override fun toString(): String = "AsyncClient.Stats($writeCountStart/$writeCountEnd/$writeCountError)"
	}

	companion object {
		suspend operator fun invoke(host: String, port: Int, secure: Boolean = false) = createAndConnect(host, port, secure)
		suspend fun create(secure: Boolean = false): AsyncClient = asyncSocketFactory.createClient(secure)
		suspend fun createAndConnect(host: String, port: Int, secure: Boolean = false): AsyncClient {
			val socket = asyncSocketFactory.createClient(secure)
			socket.connect(host, port)
			return socket
		}
	}
}

interface AsyncServer: AsyncCloseable {
	val requestPort: Int
	val host: String
	val backlog: Int
	val port: Int

	companion object {
		val ANY_PORT = 0

		suspend operator fun invoke(port: Int, host: String = "127.0.0.1", backlog: Int = -1) =
			asyncSocketFactory.createServer(port, host, backlog)
	}

	suspend fun accept(): AsyncClient

	suspend fun listen(handler: suspend (AsyncClient) -> Unit): Closeable {
		val job = coroutineScope {
			launchImmediately {
				while (true) {
					handler(accept())
				}
			}
		}
		return Closeable { job.cancel() }
	}

	suspend fun listen(): ReceiveChannel<AsyncClient> {
		val ctx = coroutineContext
		return CoroutineScope(ctx).produce {
			launchImmediately(ctx) {
				listen {
					send(it)
				}
			}
		}
	}
}
