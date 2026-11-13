package com.trueblack.oled

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class EngineState(
    val access: AccessState = AccessState.Unavailable,
    val busy: Boolean = false,
    /** Set when the matrix could not be applied after boot / unlock and is waiting for access. */
    val pendingApply: Boolean = false,
)

/**
 * The only place that talks to SurfaceFlinger. Process-wide singleton; survives as long as the app process.
 * Nothing here runs continuously: the compositor keeps the matrix until reboot or until the system
 * (Night Light, Extra dim, colour correction) replaces it.
 */
object Engine {
    private const val TAG = "TrueBlack.Engine"

    lateinit var prefs: Prefs
        private set
    private lateinit var appContext: Context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> get() = _state
    val settings: StateFlow<Settings> get() = prefs.flow

    private var permissionCallback: ((Boolean) -> Unit)? = null

    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = Prefs(appContext)
        Shizuku.addBinderReceivedListenerSticky {
            refreshAccess()
            if (_state.value.pendingApply) scope.launch { reapply() }
        }
        Shizuku.addBinderDeadListener { refreshAccess() }
        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            refreshAccess()
            permissionCallback?.invoke(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
            permissionCallback = null
        }
        refreshAccess()
    }

    fun refreshAccess() {
        val a = Access.detect()
        _state.value = _state.value.copy(access = a)
        requestTileUpdate()
    }

    /** Shizuku permission request; result lands in [onResult] on the main thread. */
    fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                // User chose "don't ask again" inside Shizuku; the dialog will not appear.
                onResult(false)
                return
            }
            permissionCallback = onResult
            Shizuku.requestPermission(REQ_SHIZUKU)
        } catch (e: Throwable) {
            Log.w(TAG, "requestPermission failed", e)
            permissionCallback = null
            onResult(false)
        }
    }

    // ---------------------------------------------------------------- public actions

    suspend fun setEnabled(enabled: Boolean): Result<Unit> = lock.withLock {
        withContext<Result<Unit>>(Dispatchers.IO) {
            setBusy(true)
            try {
                val s = prefs.current
                val method = currentMethod()
                if (method == AccessMethod.NONE) {
                    return@withContext Result.failure<Unit>(NoAccessException())
                }
                if (enabled) {
                    Access.setColorMatrix(method, ColorMath.matrix(s.threshold, s.saturation))
                    if (s.forceDarkTheme) forceNightMode(method, true)
                } else {
                    Access.setColorMatrix(method, null)
                    if (s.forceDarkTheme) forceNightMode(method, false)
                }
                prefs.update { it.copy(enabled = enabled) }
                _state.value = _state.value.copy(pendingApply = false)
                syncKeeper()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "setEnabled($enabled) failed", e)
                Result.failure<Unit>(e)
            } finally {
                setBusy(false)
                requestTileUpdate()
            }
        }
    }

    suspend fun toggle(): Result<Unit> = setEnabled(!prefs.current.enabled)

    /** Live calibration from the sliders; persists and, when active, pushes the matrix immediately. */
    suspend fun setCalibration(threshold: Float, saturation: Float): Result<Unit> {
        val t = threshold.coerceIn(ColorMath.MIN_THRESHOLD, ColorMath.MAX_THRESHOLD)
        val sat = saturation.coerceIn(ColorMath.MIN_SATURATION, ColorMath.MAX_SATURATION)
        prefs.update { it.copy(threshold = t, saturation = sat) }
        if (!prefs.current.enabled) return Result.success(Unit)
        return lock.withLock {
            withContext<Result<Unit>>(Dispatchers.IO) {
                try {
                    val method = currentMethod()
                    if (method == AccessMethod.NONE) return@withContext Result.failure<Unit>(NoAccessException())
                    Access.setColorMatrix(method, ColorMath.matrix(t, sat))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "setCalibration failed", e)
                    Result.failure<Unit>(e)
                }
            }
        }
    }

    suspend fun applyPreset(p: Preset): Result<Unit> = setCalibration(p.threshold, p.saturation)

    /** Pushes a matrix without persisting anything; used while a slider is being dragged. */
    suspend fun previewCalibration(threshold: Float, saturation: Float) {
        if (!prefs.current.enabled) return
        if (lock.isLocked) return // a push is in flight; the next one will carry the latest value
        lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val method = _state.value.access.method
                    if (method != AccessMethod.NONE) Access.setColorMatrix(method, ColorMath.matrix(threshold, saturation))
                } catch (e: Exception) {
                    Log.w(TAG, "preview failed", e)
                }
            }
        }
    }

    /**
     * Re-pushes the saved matrix if the feature is enabled. Used after boot and after unlock,
     * since system colour features overwrite SurfaceFlinger's single client matrix.
     */
    suspend fun reapply(): Result<Unit> = lock.withLock {
        withContext<Result<Unit>>(Dispatchers.IO) {
            val s = prefs.current
            if (!s.enabled) return@withContext Result.success(Unit)
            try {
                val method = currentMethod()
                if (method == AccessMethod.NONE) {
                    _state.value = _state.value.copy(pendingApply = true)
                    return@withContext Result.failure<Unit>(NoAccessException())
                }
                Access.setColorMatrix(method, ColorMath.matrix(s.threshold, s.saturation))
                _state.value = _state.value.copy(pendingApply = false)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "reapply failed", e)
                _state.value = _state.value.copy(pendingApply = true)
                Result.failure<Unit>(e)
            } finally {
                requestTileUpdate()
            }
        }
    }

    fun reapplyAsync() { scope.launch { reapply() } }

    fun updateOptions(block: (Settings) -> Settings) {
        prefs.update(block)
        syncKeeper()
    }

    // ---------------------------------------------------------------- helpers

    private fun currentMethod(): AccessMethod {
        refreshAccess()
        return _state.value.access.method
    }

    private fun setBusy(b: Boolean) { _state.value = _state.value.copy(busy = b) }

    private fun syncKeeper() {
        val s = prefs.current
        if (s.enabled && s.keepActive) KeeperService.start(appContext) else KeeperService.stop(appContext)
    }

    private fun requestTileUpdate() {
        try {
            TileService.requestListeningState(appContext, ComponentName(appContext, TrueBlackTileService::class.java))
        } catch (_: Throwable) { }
    }

    /** `cmd uimode night yes|no` with the elevated shell; remembers what to restore. */
    private fun forceNightMode(method: AccessMethod, on: Boolean) {
        try {
            if (on) {
                val cur = Access.exec(method, listOf("cmd", "uimode", "night"))
                val value = cur.stdout.substringAfter("Night mode:", "").trim().lowercase()
                if (value.isNotEmpty() && prefs.previousNightMode == null) prefs.previousNightMode = value
                Access.exec(method, listOf("cmd", "uimode", "night", "yes"))
            } else {
                val prev = prefs.previousNightMode
                if (prev != null && prev != "yes") Access.exec(method, listOf("cmd", "uimode", "night", prev))
                prefs.previousNightMode = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "night mode sync failed", e)
        }
    }

    class NoAccessException : Exception("no elevated access")

    const val REQ_SHIZUKU = 4471
}
