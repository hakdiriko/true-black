package com.trueblack.oled

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick Settings tile — the Android counterpart of the Scroll Lock hotkey. */
class TrueBlackTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        Engine.refreshAccess()
        render()
    }

    override fun onClick() {
        super.onClick()
        val access = Engine.state.value.access
        if (access.method == AccessMethod.NONE) {
            openApp()
            return
        }
        scope.launch {
            val result = Engine.toggle()
            withContext(Dispatchers.Main) {
                if (result.isFailure) openApp() else render()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render() {
        val tile = qsTile ?: return
        val s = Engine.settings.value
        val access = Engine.state.value.access
        tile.label = getString(R.string.app_name)
        tile.state = when {
            access.method == AccessMethod.NONE -> Tile.STATE_UNAVAILABLE
            s.enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = when {
            access.method == AccessMethod.NONE -> getString(R.string.tile_no_access)
            s.enabled -> presetLabel(s)
            else -> getString(R.string.state_off)
        }
        tile.updateTile()
    }

    private fun presetLabel(s: Settings): String = when (s.preset) {
        Preset.STANDARD -> getString(R.string.preset_standard)
        Preset.DEEP -> getString(R.string.preset_deep)
        Preset.VIVID -> getString(R.string.preset_vivid)
        Preset.SUBTLE -> getString(R.string.preset_subtle)
        null -> getString(R.string.preset_custom)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
