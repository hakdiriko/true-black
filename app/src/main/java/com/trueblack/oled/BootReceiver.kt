package com.trueblack.oled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot SurfaceFlinger starts with an identity matrix; put ours back if the user wants that. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val s = Engine.settings.value
        if (!s.enabled || !s.restoreOnBoot) return
        if (s.keepActive) KeeperService.start(context)
        Engine.reapplyAsync()
    }
}
