package com.trailwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager task that refreshes trail statuses and pushes the latest state to widgets.
 *
 * Before fetching, checks that the network is fully validated (internet reachable, not just
 * connected). This avoids the common failure mode where WorkManager fires right as the phone
 * radio wakes up — the interface is "connected" but DNS isn't ready yet.
 *
 * On failure the worker retries once after [RETRY_DELAY_MS] before giving up.
 * [Result.success] is always returned so WorkManager does not apply exponential back-off.
 */
class TrailUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        AppLogger.i(context, TAG, "Background update started")

        if (!isNetworkValidated()) {
            AppLogger.e(context, TAG, "Network not validated (DNS may not be ready) — waiting ${RETRY_DELAY_MS}ms before retry")
            Thread.sleep(RETRY_DELAY_MS)
            if (!isNetworkValidated()) {
                AppLogger.e(context, TAG, "Network still not validated after wait — widget set to grey")
                StatusStore.saveError(context, "Network not available")
                pushWidgetUpdate()
                return Result.success()
            }
            AppLogger.i(context, TAG, "Network validated after wait — proceeding")
        }

        val first = TrailScraper.fetchStatuses(context)
        if (first is ScrapeResult.Success) {
            commit(first.statuses)
            return Result.success()
        }

        val firstReason = when (first) {
            is ScrapeResult.NetworkFailure -> first.reason
            is ScrapeResult.ParseFailure -> first.reason
            else -> "unknown"
        }
        AppLogger.e(context, TAG, "Attempt 1 failed: $firstReason — retrying in ${RETRY_DELAY_MS}ms")
        Thread.sleep(RETRY_DELAY_MS)

        val second = TrailScraper.fetchStatuses(context)
        if (second is ScrapeResult.Success) {
            AppLogger.i(context, TAG, "Retry succeeded")
            commit(second.statuses)
            return Result.success()
        }

        when (second) {
            is ScrapeResult.NetworkFailure -> {
                AppLogger.e(context, TAG, "Retry also failed (network): ${second.reason} — widget set to grey")
                StatusStore.saveError(context, second.reason)
            }
            is ScrapeResult.ParseFailure -> {
                AppLogger.e(context, TAG, "Retry also failed (parse): ${second.reason} — widget set to grey")
                StatusStore.saveError(context, second.reason)
            }
            else -> Unit
        }
        pushWidgetUpdate()

        return Result.success()
    }

    /** Returns true if the active network has been validated for internet connectivity. */
    private fun isNetworkValidated(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
        private const val RETRY_DELAY_MS = 20_000L
    }
}

