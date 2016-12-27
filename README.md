# korio

[![Build Status](https://travis-ci.org/soywiz/korio.svg?branch=master)](https://travis-ci.org/soywiz/korio)

[![Maven Version](https://img.shields.io/github/tag/soywiz/korio.svg?style=flat&label=maven)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22korio%22)

## Kotlin cORoutines I/O : Streams + Virtual File System

Use with gradle:

```
compile "com.soywiz:korio:0.1.1"
```

This is a kotlin coroutine library that provides asynchronous nonblocking I/O and virtual filesystem operations
for custom and extensible filesystems with an homogeneous API. This repository doesn't require any special library
dependency and just requires Kotlin 1.1-M04 or greater.

This library is specially useful for webserver where asynchronous is the way to go. And completely asynchronous or
single threaded targets like javascript or as3, with kotlin-js or jtransc.

### Streams

Korio provides AsyncStream and SyncStream classes with a simplified readable, writable and seekable API,
for reading binary and text potentially huge data from files, network or whatever.
AsyncStream is designed to be able to read from disk or network asynchronously.
While SyncStream is designed to be able to read in-memory data faster while keeping the same API.

Both stream classes allow to read and write raw bytes, little and big endian primitive data, strings and structs while
allowing optimized stream slicing and reading for a simple binary file handling.

Some stream methods:
```
read, write
setPosition, getPosition
setLength, getLength
getAvailable, sliceWithSize, sliceWithBounds, slice, readSlice
readStringz, readString, readExact, readBytes, readBytesExact
readU8, readU16_le, readU32_le, readS16_le, readS32_le, readS64_le, readF32_le, readF64_le, readU16_be, readU32_be, readS16_be, readS32_be, readS64_be, readF32_be, readF64_be, readAvailable
writeBytes, write8, write16_le, write32_le, write64_le, writeF32_le, writeF64_le, write16_be, write32_be, write64_be, writeF32_be, writeF64_be
```

### VFS

Korio provides an asynchronous VirtualFileSystem extensible engine.
There is a Vfs class and a Vfs.Proxy class that provides you a base for your VFS. But at the application level, you
are using a VfsFile class that represents a file inside a Vfs.

As an example, in a suspend block, you can do the following:

```kotlin
val zip = ResourcesVfs()["hello.zip"].openAsZip()
for (file in zip.listRecursively()) {
    println(file.name)
}
```

In order to increase security, Vfs engine provides a JailVfs that allows you to sandbox VFS operations inside an
specific folder. So you can do the following:

```kotlin
val base = LocalVfs(File("/path/to/sandbox/folder")).jail()
base["../../../etc/passwd"].readString() // this won't work
```

There are several filesystems included and you can find examples of usage in the test folder:

```kotlin
LocalVfs, UrlVfs, ZipVfs, IsoVfs, ResourcesVfs, JailVfs
```

The VfsFile API:

```kotlin
class VfsFile {
    val vfs: Vfs
    val path: String
    val basename: String
    operator fun get(path: String): VfsFile
    suspend fun open(mode: VfsOpenMode): AsyncStream
    suspend inline fun <reified T : Any> readSpecial(): T
    suspend fun read(): ByteArray
    suspend fun write(data: ByteArray): Unit
    suspend fun readString(charset: Charset = Charsets.UTF_8): String
    suspend fun writeString(data: String, charset: Charset = Charsets.UTF_8): Unit
    suspend fun readChunk(offset: Long, size: Int): ByteArray
    suspend fun writeChunk(data: ByteArray, offset: Long, resize: Boolean = false): Unit
    suspend fun stat(): VfsStat
    suspend fun size(): Long
    suspend fun exists(): Boolean
    suspend fun setSize(size: Long): Unit
    fun jail(): VfsFile = JailVfs(this)
    suspend fun list(): AsyncSequence<VfsStat>
    suspend fun listRecursive(): AsyncSequence<VfsStat>
}
```

But since it is extensible you can create custom ones (for S3, for Windows Registry, for FTP/SFTP, an ISO file...).
