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
    private const val KEY_FAIL_MESSAGE = "fail_message"

    /** Interval choices exposed in the settings spinner. */
    val INTERVAL_OPTIONS = intArrayOf(15, 30, 60, 120, 240)

    /** Human-readable labels for [INTERVAL_OPTIONS]. */
    val INTERVAL_LABELS = arrayOf("15 min", "30 min", "1 hour", "2 hours", "4 hours")

    /** Default refresh interval in minutes. */
    const val DEFAULT_INTERVAL_MINUTES = 60

    /**
     * Persists the latest fetched trail statuses and clears any previous error message.
     */
    fun save(context: Context, statuses: TrailStatuses) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_EAST, statuses.east.name)
            putString(KEY_WEST, statuses.west.name)
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            putString(KEY_FAIL_MESSAGE, "")
            apply()
        }
    }

    /**
     * Persists an error state with a [reason] string shown in the app when the widget is grey.
     * The timestamp is NOT updated so the "last checked" timer stays accurate.
     */
    fun saveError(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_EAST, TrailStatus.UNKNOWN.name)
            putString(KEY_WEST, TrailStatus.UNKNOWN.name)
            putString(KEY_FAIL_MESSAGE, reason)
            apply()
        }
    }

    /**
     * Returns the current trail statuses. Falls back to [TrailStatus.UNKNOWN] when no data
     * has been saved yet (first launch before any check completes).
     */
    fun load(context: Context): TrailStatuses {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val east = parseStatus(prefs.getString(KEY_EAST, null))
        val west = parseStatus(prefs.getString(KEY_WEST, null))
        return TrailStatuses(east, west)
    }

    /**
     * Returns the stored failure reason, or an empty string when the last update succeeded.
     */
    fun getFailMessage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FAIL_MESSAGE, "") ?: ""

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
