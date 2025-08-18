// File: BootReceiver.kt
package com.example.epgutility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            WorkerScheduler.scheduleWork(context)
            Log.d("BootReceiver", "📺 Device booted — rescheduled AutoFilterWorker")
        }
    }
}