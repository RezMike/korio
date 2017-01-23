@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package com.soywiz.korio.vfs

import com.soywiz.korio.async.AsyncSequence
import com.soywiz.korio.async.asyncGenerate
import com.soywiz.korio.async.await
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.use
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.nio.charset.Charset

class VfsFile(
	val vfs: Vfs,
	path: String
//) : VfsNamed(VfsFile.normalize(path)) {
) : VfsNamed(path) {
	operator fun get(path: String): VfsFile = VfsFile(vfs, VfsUtil.combine(this.path, path))

	suspend operator fun set(path: String, content: String): Unit = this[path].writeString(content)
	suspend operator fun set(path: String, content: ByteArray): Unit = this[path].write(content)
	suspend operator fun set(path: String, content: AsyncStream): Unit = run { this[path].writeStream(content) }
	suspend operator fun set(path: String, content: VfsFile): Unit = run { this[path].writeFile(content) }

	suspend fun writeStream(src: AsyncStream): Long = this.openUse(VfsOpenMode.CREATE_OR_TRUNCATE) { this.writeStream(src) }
	suspend fun writeFile(file: VfsFile): Long = file.copyTo(this)

	suspend fun copyTo(target: VfsFile): Long = this.openUse(VfsOpenMode.READ) { target.writeStream(this) }

	val parent: VfsFile by lazy { VfsFile(vfs, pathInfo.folder) }
	val root: VfsFile get() = vfs.root

	fun withExtension(ext: String): VfsFile = VfsFile(vfs, fullnameWithoutExtension + if (ext.isNotEmpty()) ".$ext" else "")
	fun appendExtension(ext: String): VfsFile = VfsFile(vfs, fullname + ".$ext")

	suspend fun open(mode: VfsOpenMode = VfsOpenMode.READ): AsyncStream = vfs.open(path, mode)

	suspend inline fun <T> openUse(mode: VfsOpenMode = VfsOpenMode.READ, callback: suspend AsyncStream.() -> T): T {
		return open(mode).use { callback.await(this) }
	}

	//suspend fun read(): ByteArray = vfs.readFully(path)
	suspend fun read(): ByteArray = openUse { this.readAll() }

	suspend fun readAsSyncStream(): SyncStream = read().openSync()

	suspend fun write(data: ByteArray): Unit = openUse(VfsOpenMode.CREATE_OR_TRUNCATE) { this.writeBytes(data) }

	suspend fun readString(charset: Charset = Charsets.UTF_8): String = read().toString(charset)
	suspend fun writeString(data: String, charset: Charset = Charsets.UTF_8): Unit = write(data.toByteArray(charset))

	suspend fun readChunk(offset: Long, size: Int): ByteArray = vfs.readChunk(path, offset, size)
	suspend fun writeChunk(data: ByteArray, offset: Long, resize: Boolean = false): Unit = vfs.writeChunk(path, data, offset, resize)

	suspend fun stat(): VfsStat = vfs.stat(path)
	suspend fun size(): Long = vfs.stat(path).size
	suspend fun exists(): Boolean = try {
		vfs.stat(path).exists
	} catch (e: Throwable) {
		false
	}

	suspend fun isDirectory(): Boolean = stat().isDirectory

	suspend fun setSize(size: Long): Unit = vfs.setSize(path, size)

	fun jail(): VfsFile = JailVfs(this)

	suspend fun delete() = vfs.delete(path)

	suspend fun mkdir() = vfs.mkdir(path)
	suspend fun mkdirs() {
		// @TODO: Create tree up to this
		mkdir()
	}

	suspend fun copyToTree(target: VfsFile, notify: (Pair<VfsFile, VfsFile>) -> Unit = {}): Unit {
		notify(this to target)
		if (this.isDirectory()) {
			target.mkdir()
			for (file in list()) {
				file.copyToTree(target[file.basename], notify)
			}
		} else {
			//println("copyToTree: $this -> $target")
			this.copyTo(target)
		}
	}

	suspend fun ensureParents() = this.apply { parent.mkdirs() }

	suspend fun renameTo(dstPath: String) = vfs.rename(this.path, dstPath)

	suspend fun list(): AsyncSequence<VfsFile> = vfs.list(path)

	suspend fun listRecursive(filter: (VfsFile) -> Boolean = { true }): AsyncSequence<VfsFile> = asyncGenerate {
		for (file in list()) {
			if (!filter(file)) continue
			yield(file)
			val stat = file.stat()
			if (stat.isDirectory) {
				for (f in file.listRecursive()) yield(f)
			}
		}
	}

	suspend fun exec(cmdAndArgs: List<String>, handler: VfsProcessHandler = VfsProcessHandler()): Int = vfs.exec(path, cmdAndArgs, handler)
	suspend fun execToString(cmdAndArgs: List<String>, charset: Charset = Charsets.UTF_8): String {
		val out = ByteArrayOutputStream()

		val result = exec(cmdAndArgs, object : VfsProcessHandler() {
			suspend override fun onOut(data: ByteArray) {
				out.write(data)
			}

			suspend override fun onErr(data: ByteArray) {
				out.write(data)
			}
		})

		if (result != 0) throw VfsProcessException("Process not returned 0, but $result")

		return out.toByteArray().toString(charset)
	}

	suspend fun execToString(vararg cmdAndArgs: String, charset: Charset = Charsets.UTF_8): String = execToString(cmdAndArgs.toList(), charset = charset)

	suspend fun passthru(cmdAndArgs: List<String>, charset: Charset = Charsets.UTF_8): Int {
		return exec(cmdAndArgs.toList(), object : VfsProcessHandler() {
			suspend override fun onOut(data: ByteArray) {
				System.out.print(data.toString(charset))
			}

			suspend override fun onErr(data: ByteArray) {
				System.err.print(data.toString(charset))
			}
		})
	}

	suspend fun passthru(vararg cmdAndArgs: String, charset: Charset = Charsets.UTF_8): Int = passthru(cmdAndArgs.toList(), charset)

	suspend fun watch(handler: (VfsFileEvent) -> Unit): Closeable = vfs.watch(path, handler)

	override fun toString(): String = "$vfs[$path]"

	val absolutePath: String by lazy { VfsUtil.lightCombine(vfs.absolutePath, path) }
}