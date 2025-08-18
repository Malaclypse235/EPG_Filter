// File: WorkerScheduler.kt
package com.example.epgutility

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkerScheduler {

    private const val WORK_NAME = "AutoFilterWork"

    fun scheduleWork(context: Context) {
        val config = ConfigManager.loadConfig(context).config

        if (!config.system.autoModeEnabled) {
            cancelWork(context)
            return
        }

        val intervalMillis = config.system.autoCheckInterval.toAutoIntervalMillis()
        val flexMillis = (intervalMillis * 0.1).toLong().coerceAtLeast(5 * 60 * 1000L)

        // ⚠️ WorkManager minimum interval is 15 minutes
        val minInterval = 15 * 60 * 1000L
        val finalInterval = intervalMillis.coerceAtLeast(minInterval)

        // ✅ Use WorkRequest.Builder (Java-style) — guaranteed to work
        val builder = PeriodicWorkRequest.Builder(
            AutoFilterWorker::class.java,
            finalInterval,
            TimeUnit.MILLISECONDS,
            flexMillis,
            TimeUnit.MILLISECONDS
        )

        // Optional: Add constraints (remove if not needed)
        builder.setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
        )

        // ✅ Build and enqueue
        val workRequest = builder.build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

        android.util.Log.d("WorkerScheduler", "🔁 Scheduled AutoFilterWorker every ${finalInterval / 60_000} minutes")
    }

    fun cancelWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        android.util.Log.d("WorkerScheduler", "🛑 Canceled AutoFilterWorker")
    }
}