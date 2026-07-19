package com.trailwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager task that refreshes trail statuses and pushes the latest state to widgets.
 */
class TrailUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    /**
     * Fetches the latest trail statuses, persists them locally, and refreshes any active widgets.
     *
     * Returns [Result.success] after a successful refresh, or [Result.retry] when the scrape
     * fails so WorkManager can attempt the update again later.
     */
    override fun doWork(): Result {
        Log.d(TAG, "Starting trail status update")

        return try {
            val statuses = TrailScraper.fetchStatuses()
            Log.d(TAG, "Fetched statuses: east=${statuses.east}, west=${statuses.west}")

            StatusStore.save(context, statuses)
            pushWidgetUpdate(context)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}", e)

            // Save UNKNOWN on failure so the widget goes grey.
            StatusStore.saveError(context)
            pushWidgetUpdate(context)

            Result.retry()
        }
    }

    private fun pushWidgetUpdate(ctx: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(ctx)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(ctx, TrailWidgetProvider::class.java)
        )

        if (appWidgetIds.isNotEmpty()) {
            TrailWidgetProvider.updateWidgets(ctx, appWidgetManager, appWidgetIds)
            Log.d(TAG, "Widgets updated: ${appWidgetIds.size}")
        }
    }

    companion object {
        const val TAG = "TrailUpdateWorker"
    }
}
