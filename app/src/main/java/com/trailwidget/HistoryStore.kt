package com.trailwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A single recorded trail-status snapshot. */
data class HistoryEntry(
    val east: TrailStatus,
    val west: TrailStatus,
    val timestamp: Long
)

/**
 * Append-only log of trail status changes persisted in SharedPreferences.
 *
 * Only entries where at least one trail status differs from the previous record are saved,
 * so the list grows only when something actually changes. Capped at [MAX_ENTRIES].
 */
object HistoryStore {

    private const val PREFS = "trail_history_prefs"
    private const val KEY_HISTORY = "history"
    private const val MAX_ENTRIES = 200

    /**
     * Records [statuses] only if they differ from the last stored entry.
     * Call this after every successful fetch (not after errors).
     */
    fun record(context: Context, statuses: TrailStatuses) {
        val entries = loadAscending(context).toMutableList()

        val last = entries.lastOrNull()
        if (last != null && last.east == statuses.east && last.west == statuses.west) {
            return  // No change — nothing to record.
        }

        entries.add(HistoryEntry(statuses.east, statuses.west, System.currentTimeMillis()))

        val trimmed = if (entries.size > MAX_ENTRIES) entries.drop(entries.size - MAX_ENTRIES) else entries
        persist(context, trimmed)
    }

    /**
     * Returns all history entries, newest first.
     */
    fun load(context: Context): List<HistoryEntry> = loadAscending(context).reversed()

    private fun loadAscending(context: Context): List<HistoryEntry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HistoryEntry(
                    east = TrailStatus.valueOf(o.getString("east")),
                    west = TrailStatus.valueOf(o.getString("west")),
                    timestamp = o.getLong("ts")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(context: Context, entries: List<HistoryEntry>) {
        val array = JSONArray()
        for (e in entries) {
            array.put(JSONObject().apply {
                put("east", e.east.name)
                put("west", e.west.name)
                put("ts", e.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
