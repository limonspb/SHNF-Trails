package com.trailwidget

import android.content.Context

/**
 * SharedPreferences-backed store for trail statuses, timestamps, and user refresh settings.
 */
object StatusStore {

    private const val PREFS = "trail_widget_prefs"
    private const val KEY_EAST = "status_east"
    private const val KEY_WEST = "status_west"
    private const val KEY_UPDATED_AT = "updated_at_ms"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_AUTO_UPDATE = "auto_update"

    // Show grey if the last update is older than 2 hours.
    private const val STALE_MS = 2 * 60 * 60 * 1000L

    /** Interval choices exposed in the settings spinner. */
    val INTERVAL_OPTIONS = intArrayOf(15, 30, 60, 120, 240)

    /** Human-readable labels for [INTERVAL_OPTIONS]. */
    val INTERVAL_LABELS = arrayOf("15 min", "30 min", "1 hour", "2 hours", "4 hours")

    /** Default refresh interval in minutes. */
    const val DEFAULT_INTERVAL_MINUTES = 60

    /**
     * Persists the latest fetched trail statuses and updates the last-successful timestamp.
     */
    fun save(context: Context, statuses: TrailStatuses) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_EAST, statuses.east.name)
            putString(KEY_WEST, statuses.west.name)
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Persists an error state without advancing the timestamp so stale detection still applies.
     */
    fun saveError(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_EAST, TrailStatus.UNKNOWN.name)
            putString(KEY_WEST, TrailStatus.UNKNOWN.name)
            apply()
        }
    }

    /**
     * Returns the current trail statuses, falling back to [TrailStatus.UNKNOWN] when cached data
     * is missing or stale.
     */
    fun load(context: Context): TrailStatuses {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        val isStale = updatedAt == 0L ||
            (System.currentTimeMillis() - updatedAt > STALE_MS)

        if (isStale) {
            return TrailStatuses(TrailStatus.UNKNOWN, TrailStatus.UNKNOWN)
        }

        val east = parseStatus(prefs.getString(KEY_EAST, null))
        val west = parseStatus(prefs.getString(KEY_WEST, null))
        return TrailStatuses(east, west)
    }

    /**
     * Returns the timestamp of the last successful status save, in epoch milliseconds.
     */
    fun lastUpdatedMillis(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UPDATED_AT, 0L)
    }

    /**
     * Returns the currently configured auto-refresh interval in minutes.
     */
    fun getIntervalMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)

    /**
     * Persists the auto-refresh interval in minutes.
     */
    fun setIntervalMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_INTERVAL_MINUTES, minutes)
            .apply()
    }

    /**
     * Returns whether periodic widget refreshes are enabled.
     */
    fun isAutoUpdateEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE, true)

    /**
     * Enables or disables periodic widget refreshes.
     */
    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_UPDATE, enabled)
            .apply()
    }

    private fun parseStatus(value: String?): TrailStatus =
        when (value) {
            "OPEN" -> TrailStatus.OPEN
            "CLOSED" -> TrailStatus.CLOSED
            else -> TrailStatus.UNKNOWN
        }
}
