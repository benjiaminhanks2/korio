package com.soywiz.korio.vfs.js

import com.jtransc.js.*
import kotlin.coroutines.suspendCoroutine

object NodeJsUtils {
	suspend fun readRangeBytes(path: String, start: Double, end: Double): ByteArray = suspendCoroutine { c ->
		val http = jsRequire("http")
		val url = jsRequire("url")
		val info = url.methods["parse"](path)
		val headers = jsObject()

		if (start >= 0 && end >= 0) headers["Range"] = "bytes=$start-$end"

		http.methods["get"](jsObject(
			"host" to info["hostname"],
			"port" to info["port"],
			"path" to info["path"],
			"agent" to false,
			"encoding" to null,
			"headers" to headers
		), jsFunctionRaw1 { res ->
			val body = jsArray()
			res.methods["on"]("data", jsFunctionRaw1 { d -> body.methods["push"](d) })
			res.methods["on"]("end", jsFunctionRaw0 {
				val r = global["Buffer"].methods["concat"](body)
				val u8array = jsNew("Int8Array", r)
				val out = ByteArray(r["length"].toInt())
				out.asJsDynamic().methods["setArraySlice"](0, u8array)
				c.resume(out)
			})
		}).methods["on"]("error", jsFunctionRaw1 { e ->
			c.resumeWithException(RuntimeException("Error: ${e.toJavaString()}"))
		})
	}

	suspend fun httpStat(path: String): JsStat = suspendCoroutine { c ->
		val http = jsRequire("http")
		val url = jsRequire("url")
		val info = url.methods["parse"](path)

		http.methods["get"](jsObject(
			"method" to "HEAD",
			"host" to info["hostname"],
			"port" to info["port"],
			"path" to info["path"]
		), jsFunctionRaw1 { res ->
			val len = global.methods["parseFloat"](res["headers"]["content-length"])
			val out = JsStat(len.toDouble())
			c.resume(out)
		}).methods["on"]("error", jsFunctionRaw1 { e ->
			c.resumeWithException(RuntimeException("Error: ${e.toJavaString()}"))
		})
	}

	suspend fun open(path: String, mode: String): JsDynamic = suspendCoroutine { c ->
		val fs = jsRequire("fs")
		fs.methods["open"](path, mode, jsFunctionRaw2 { err, fd ->
			if (err != null) {
				c.resumeWithException(RuntimeException("Error ${err.toJavaString()} opening $path"))
			} else {
				c.resume(fd!!)
			}
		})
	}

	suspend fun read(fd: JsDynamic?, position: Double, len: Double): ByteArray = suspendCoroutine { c ->
		val fs = jsRequire("fs")
		val buffer = jsNew("Buffer", len)
		fs.methods["read"](fd, buffer, 0, len, position, jsFunctionRaw3 { err, bytesRead, buffer ->
			if (err != null) {
				c.resumeWithException(RuntimeException("Error ${err.toJavaString()} opening ${fd.toJavaString()}"))
			} else {
				val u8array = jsNew("Int8Array", buffer, 0, bytesRead)
				val out = ByteArray(bytesRead.toInt())
				out.asJsDynamic().methods["setArraySlice"](0, u8array)
				c.resume(out)
			}
		})
	}

	suspend fun close(fd: Any): Unit = suspendCoroutine { c ->
		val fs = jsRequire("fs")
		fs.methods["close"](fd, jsFunctionRaw2 { err, fd ->
			if (err != null) {
				c.resumeWithException(RuntimeException("Error ${err.toJavaString()} closing file"))
			} else {
				c.resume(Unit)
			}
		})
	}

	fun getCWD(): String = global["process"].methods["cwd"]().toJavaString()

	suspend fun fstat(path: String): JsStat = suspendCoroutine { c ->
		// https://nodejs.org/api/fs.html#fs_class_fs_stats
		val fs = jsRequire("fs")
		fs.methods["stat"](path, jsFunctionRaw2 { err, stat ->
			if (err != null) {
				c.resumeWithException(RuntimeException("Error ${err.toJavaString()} opening $path"))
			} else {
				val out = JsStat(stat["size"].toDouble())
				out.isDirectory = stat.methods["isDirectory"]().toBool()
				c.resume(out)
			}
		})
	}
}