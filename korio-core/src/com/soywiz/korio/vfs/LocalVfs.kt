package com.soywiz.korio.vfs

import java.io.File

fun LocalVfs(base: String): VfsFile = LocalVfs()[base]
fun TempVfs() = LocalVfs()[System.getProperty("java.io.tmpdir")]
fun LocalVfs(base: File): VfsFile = LocalVfs()[base.absolutePath]
fun JailedLocalVfs(base: File): VfsFile = LocalVfs()[base.absolutePath].jail()
fun JailedLocalVfs(base: String): VfsFile = LocalVfs()[base].jail()
suspend fun File.open(mode: VfsOpenMode) = LocalVfs(this).open(mode)

fun CacheVfs() = LocalVfs()[localVfsProvider.getCacheFolder()].jail()
fun ExternalStorageVfs() = LocalVfs()[localVfsProvider.getExternalStorageFolder()].jail()

fun LocalVfs(): VfsFile = localVfsProvider().root

abstract class LocalVfsProvider {
	abstract operator fun invoke(): Vfs
	open fun getCacheFolder(): String = System.getProperty("java.io.tmpdir")
	open fun getExternalStorageFolder(): String = System.getProperty("java.io.tmpdir")
}