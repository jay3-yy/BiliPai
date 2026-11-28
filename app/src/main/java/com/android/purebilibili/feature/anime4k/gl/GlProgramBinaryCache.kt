package com.android.purebilibili.feature.anime4k.gl

import android.opengl.GLES30
import android.os.Build
import com.android.purebilibili.core.coroutines.AppScope
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

internal fun buildGlDriverSignature(
    vendor: String,
    renderer: String,
    version: String,
    buildFingerprint: String = Build.FINGERPRINT.orEmpty(),
): String = "$vendor|$renderer|$version|$buildFingerprint"

internal fun buildGlProgramBinaryCacheKey(
    driverSignature: String,
    vertexSource: String,
    fragmentSource: String,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(driverSignature.toByteArray(Charsets.UTF_8))
    digest.update(0)
    digest.update(vertexSource.toByteArray(Charsets.UTF_8))
    digest.update(0)
    digest.update(fragmentSource.toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}

internal fun serializeGlProgramBinary(format: Int, binary: ByteArray): ByteArray {
    val out = ByteArray(4 + binary.size)
    out[0] = (format ushr 24).toByte()
    out[1] = (format ushr 16).toByte()
    out[2] = (format ushr 8).toByte()
    out[3] = format.toByte()
    System.arraycopy(binary, 0, out, 4, binary.size)
    return out
}

internal fun deserializeGlProgramBinary(raw: ByteArray): Pair<Int, ByteArray>? {
    if (raw.size <= 4) return null
    val format = ((raw[0].toInt() and 0xFF) shl 24) or
        ((raw[1].toInt() and 0xFF) shl 16) or
        ((raw[2].toInt() and 0xFF) shl 8) or
        (raw[3].toInt() and 0xFF)
    return format to raw.copyOfRange(4, raw.size)
}

internal fun writeGlProgramBinaryAtomically(
    dir: File,
    target: File,
    serializedPayload: ByteArray,
    maxCachedPrograms: Int,
) {
    if (!dir.exists()) dir.mkdirs()
    val temp = File(dir, "${target.name}.tmp")
    temp.writeBytes(serializedPayload)
    if (!temp.renameTo(target)) {
        target.writeBytes(serializedPayload)
        temp.delete()
    }
    val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".bin") } ?: return
    if (files.size <= maxCachedPrograms) return
    files.sortBy { it.lastModified() }
    val removeCount = files.size - maxCachedPrograms
    for (i in 0 until removeCount) {
        files[i].delete()
    }
}

/**
 * GLES 3.0 Program Binary 磁盘缓存。
 * 绑定当前 GPU 驱动指纹与系统构建指纹，二次创建 Surface 时跳过多 Pass CNN GLSL 编译；
 * 二进制提取在 GL 线程完成，磁盘落盘与 LRU 淘汰异步派发至后台 IO 线程。
 */
internal class GlProgramBinaryCache(cacheRootDir: File?) {
    private val cacheDir: File? = cacheRootDir?.let { File(it, CACHE_DIR_NAME) }
    private var supported = false
    private var driverSignature = ""

    fun onGlContextReady() {
        if (cacheDir == null) {
            supported = false
            driverSignature = ""
            return
        }
        val count = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_NUM_PROGRAM_BINARY_FORMATS, count, 0)
        supported = count[0] > 0
        driverSignature = if (supported) {
            buildGlDriverSignature(
                vendor = GLES30.glGetString(GLES30.GL_VENDOR).orEmpty(),
                renderer = GLES30.glGetString(GLES30.GL_RENDERER).orEmpty(),
                version = GLES30.glGetString(GLES30.GL_VERSION).orEmpty(),
            )
        } else {
            ""
        }
    }

    val isSupported: Boolean
        get() = cacheDir != null && supported && driverSignature.isNotEmpty()

    fun tryLoadProgram(vertexSource: String, fragmentSource: String): Int {
        if (!isSupported) return 0
        val file = cacheFile(vertexSource, fragmentSource) ?: return 0
        if (!file.isFile) return 0
        val payload = runCatching { file.readBytes() }.getOrNull() ?: return 0
        val (format, binary) = deserializeGlProgramBinary(payload) ?: run {
            deleteFileAsync(file)
            return 0
        }
        val program = GLES30.glCreateProgram()
        if (program == 0) return 0
        val buffer = ByteBuffer.allocateDirect(binary.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(binary)
                position(0)
            }
        GLES30.glProgramBinary(program, format, buffer, binary.size)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        if (linked[0] == GLES30.GL_TRUE) {
            touchFileAsync(file)
            return program
        }
        GLES30.glDeleteProgram(program)
        deleteFileAsync(file)
        return 0
    }

    fun saveProgram(program: Int, vertexSource: String, fragmentSource: String) {
        val dir = cacheDir ?: return
        if (!isSupported || program == 0) return
        val binaryLength = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_PROGRAM_BINARY_LENGTH, binaryLength, 0)
        val size = binaryLength[0]
        if (size <= 0) return

        val actualLength = IntArray(1)
        val binaryFormat = IntArray(1)
        val buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        GLES30.glGetProgramBinary(program, size, actualLength, 0, binaryFormat, 0, buffer)
        val written = actualLength[0]
        if (written <= 0 || written > size) return

        val bytes = ByteArray(written)
        buffer.position(0)
        buffer.get(bytes, 0, written)
        val target = cacheFile(vertexSource, fragmentSource) ?: return
        val serialized = serializeGlProgramBinary(binaryFormat[0], bytes)
        AppScope.ioScope.launch(cacheIoDispatcher) {
            runCatching {
                writeGlProgramBinaryAtomically(
                    dir = dir,
                    target = target,
                    serializedPayload = serialized,
                    maxCachedPrograms = MAX_CACHED_PROGRAMS,
                )
            }
        }
    }

    private fun touchFileAsync(file: File) {
        AppScope.ioScope.launch(cacheIoDispatcher) {
            runCatching { file.setLastModified(System.currentTimeMillis()) }
        }
    }

    private fun deleteFileAsync(file: File) {
        AppScope.ioScope.launch(cacheIoDispatcher) {
            runCatching { file.delete() }
        }
    }

    private fun cacheFile(vertexSource: String, fragmentSource: String): File? =
        cacheDir?.let { File(it, "${buildGlProgramBinaryCacheKey(driverSignature, vertexSource, fragmentSource)}.bin") }

    private companion object {
        const val CACHE_DIR_NAME = "anime4k_gl_cache"
        const val MAX_CACHED_PROGRAMS = 48

        // 单并发 FIFO 串行派发，确保 tryLoadProgram 触发的坏缓存删除严格先于紧随其后的新缓存写入。
        @OptIn(ExperimentalCoroutinesApi::class)
        val cacheIoDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
