package com.trueblack.oled

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class TrueBlackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            KeeperService.CHANNEL_ID,
            getString(R.string.channel_keeper),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.channel_keeper_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }
}
