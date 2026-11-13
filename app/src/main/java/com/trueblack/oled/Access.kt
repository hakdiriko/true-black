package com.trueblack.oled

import android.content.pm.PackageManager
import android.os.Parcel
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.util.concurrent.TimeUnit

/** How the app reaches SurfaceFlinger. Shizuku (ADB or root / Sui) is preferred, plain root is the fallback. */
enum class AccessMethod { NONE, SHIZUKU, ROOT }

sealed class AccessState {
    /** Shizuku not running and no su binary on the device. */
    data object Unavailable : AccessState()
    /** Shizuku is running but has not granted this app permission yet. */
    data object ShizukuNeedsPermission : AccessState()
    /** Shizuku binder alive and permission granted. */
    data object Shizuku : AccessState()
    /** A su binary exists; the grant prompt appears on first use. */
    data object Root : AccessState()

    val method: AccessMethod
        get() = when (this) {
            Shizuku -> AccessMethod.SHIZUKU
            Root -> AccessMethod.ROOT
            else -> AccessMethod.NONE
        }
}

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
}

object Access {
    private const val TAG = "TrueBlack.Access"
    private const val SURFACE_FLINGER = "SurfaceFlinger"
    private const val CODE_SET_COLOR_MATRIX = 1015
    private const val ISURFACE_COMPOSER = "android.ui.ISurfaceComposer"

    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/debug_ramdisk/su", "/data/adb/magisk/su", "/system/sbin/su", "/vendor/bin/su", "/odm/bin/su"
    )

    fun shizukuAlive(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun shizukuGranted(): Boolean = try {
        shizukuAlive() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun hasSuBinary(): Boolean {
        if (suPaths.any { File(it).exists() }) return true
        val path = System.getenv("PATH") ?: return false
        return path.split(':').any { dir -> dir.isNotBlank() && File(dir, "su").exists() }
    }

    fun detect(): AccessState = when {
        shizukuGranted() -> AccessState.Shizuku
        shizukuAlive() -> AccessState.ShizukuNeedsPermission
        hasSuBinary() -> AccessState.Root
        else -> AccessState.Unavailable
    }

    // ---------------------------------------------------------------- compositor

    /**
     * Pushes [matrix] (or identity when null) into SurfaceFlinger.
     * Shizuku: a single binder transaction forwarded with shell/root identity.
     * Root: `su -c service call ...`.
     */
    @Throws(Exception::class)
    fun setColorMatrix(method: AccessMethod, matrix: FloatArray?) {
        when (method) {
            AccessMethod.SHIZUKU -> {
                try {
                    transactShizuku(matrix)
                } catch (e: Throwable) {
                    Log.w(TAG, "binder path failed, falling back to shell", e)
                    val r = exec(method, ColorMath.serviceCallArgs(matrix))
                    if (!r.ok) throw IllegalStateException(r.stderr.ifBlank { r.stdout }.ifBlank { "service call failed (${r.exitCode})" })
                }
            }
            AccessMethod.ROOT -> {
                val r = exec(method, ColorMath.serviceCallArgs(matrix))
                if (!r.ok) throw IllegalStateException(r.stderr.ifBlank { r.stdout }.ifBlank { "su failed (${r.exitCode})" })
            }
            AccessMethod.NONE -> throw IllegalStateException("no access")
        }
    }

    private fun transactShizuku(matrix: FloatArray?) {
        val raw = SystemServiceHelper.getSystemService(SURFACE_FLINGER)
            ?: throw IllegalStateException("SurfaceFlinger binder not found")
        val binder = ShizukuBinderWrapper(raw)
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ISURFACE_COMPOSER)
            if (matrix == null) {
                data.writeInt(0)
            } else {
                data.writeInt(1)
                for (v in matrix) data.writeFloat(v)
            }
            binder.transact(CODE_SET_COLOR_MATRIX, data, reply, 0)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // ---------------------------------------------------------------- shell

    /** Runs a command with elevated identity. Blocking; call off the main thread. */
    fun exec(method: AccessMethod, args: List<String>, timeoutSec: Long = 8): ShellResult {
        val process: Process = when (method) {
            AccessMethod.SHIZUKU -> shizukuProcess(args.toTypedArray())
            AccessMethod.ROOT -> ProcessBuilder("su", "-c", args.joinToString(" ") { shellQuote(it) }).start()
            AccessMethod.NONE -> throw IllegalStateException("no access")
        }
        val out = StringBuilder()
        val err = StringBuilder()
        val tOut = Thread { runCatching { out.append(process.inputStream.bufferedReader().readText()) } }
        val tErr = Thread { runCatching { err.append(process.errorStream.bufferedReader().readText()) } }
        tOut.start(); tErr.start()
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            tOut.join(500); tErr.join(500)
            return ShellResult(-1, out.toString(), "timeout")
        }
        tOut.join(1000); tErr.join(1000)
        return ShellResult(process.exitValue(), out.toString().trim(), err.toString().trim())
    }

    /**
     * Shizuku's remote process API is hidden behind a private method (kept for "su" migrations).
     * It is the simplest way to run `cmd uimode` with shell identity, so we reach it reflectively.
     */
    private fun shizukuProcess(cmd: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, cmd, null, null) as Process
    }

    private fun shellQuote(s: String): String =
        if (s.matches(Regex("[A-Za-z0-9_./=:+-]+"))) s else "'" + s.replace("'", "'\\''") + "'"
}
