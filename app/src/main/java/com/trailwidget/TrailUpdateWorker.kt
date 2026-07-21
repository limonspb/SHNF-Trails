package com.trailwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager task that refreshes trail statuses and pushes the latest state to widgets.
 *
 * On failure the worker retries once after [RETRY_DELAY_MS] before giving up. If both attempts
 * fail, grey status is saved immediately with a human-readable reason — no stale timer needed.
 * [Result.success] is always returned so WorkManager does not apply exponential back-off that
 * would delay the next scheduled hourly run.
 */
class TrailUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        AppLogger.i(context, TAG, "Background update started")

        val first = TrailScraper.fetchStatuses(context)
        if (first is ScrapeResult.Success) {
            commit(first.statuses)
            return Result.success()
        }

        // First attempt failed — wait briefly and retry once.
        val firstReason = (first as ScrapeResult.Failure).reason
        AppLogger.e(context, TAG, "Attempt 1 failed: $firstReason — retrying in ${RETRY_DELAY_MS}ms")
        Thread.sleep(RETRY_DELAY_MS)

        val second = TrailScraper.fetchStatuses(context)
        if (second is ScrapeResult.Success) {
            AppLogger.i(context, TAG, "Retry succeeded")
            commit(second.statuses)
            return Result.success()
        }

        val finalReason = (second as ScrapeResult.Failure).reason
        AppLogger.e(context, TAG, "Retry also failed: $finalReason — widget set to grey")
        StatusStore.saveError(context, finalReason)
        pushWidgetUpdate()

        return Result.success()
    }

    private fun commit(statuses: TrailStatuses) {
        StatusStore.save(context, statuses)
        pushWidgetUpdate()
        AppLogger.i(context, TAG, "Update committed — east=${statuses.east}, west=${statuses.west}")
    }

    private fun pushWidgetUpdate() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TrailWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            TrailWidgetProvider.updateWidgets(context, manager, ids)
            AppLogger.d(context, TAG, "Widget update pushed to ${ids.size} widget(s)")
        }
    }

    companion object {
        const val TAG = "TrailUpdateWorker"
        private const val RETRY_DELAY_MS = 3_000L
    }
}
