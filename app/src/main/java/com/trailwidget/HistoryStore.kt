package com.trailwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A single recorded trail-status snapshot. */
data class HistoryEntry(
    val east: TrailStatus,
    val west: TrailStatus,
    val timestamp: Long,
    /** Human-readable reason when both trails are UNKNOWN (grey). Empty on success. */
    val reason: String = "",
    /** True when grey was caused specifically by no/unvalidated network connection. */
    val isNoNetwork: Boolean = false
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
     * [reason] and [isNoNetwork] are stored when statuses are UNKNOWN/grey.
     * Synchronized to prevent concurrent workers from racing on the read-modify-write.
     */
    @Synchronized
    fun record(context: Context, statuses: TrailStatuses, reason: String = "", isNoNetwork: Boolean = false) {
        val entries = loadAscending(context).toMutableList()

        val last = entries.lastOrNull()
        if (last != null && last.east == statuses.east && last.west == statuses.west
                && last.reason == reason && last.isNoNetwork == isNoNetwork) {
            return  // No change — nothing to record.
        }

        entries.add(HistoryEntry(statuses.east, statuses.west, System.currentTimeMillis(), reason, isNoNetwork))

        val trimmed = if (entries.size > MAX_ENTRIES) entries.drop(entries.size - MAX_ENTRIES) else entries
        persist(context, trimmed)
    }

    /**
     * Returns all history entries, newest first.
     * Applies a one-time migration: legacy grey entries with no reason are assumed to be
     * no-network events (the only source of reasonless grey before v1.5).
     */
    fun load(context: Context): List<HistoryEntry> = migrateIfNeeded(context, loadAscending(context)).reversed()

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
                    timestamp = o.getLong("ts"),
                    reason = o.optString("reason", ""),
                    isNoNetwork = o.optBoolean("nonet", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Backfills legacy grey entries (reason == "" and isNoNetwork == false) as no-network events.
     * Persists the updated list if any entries were changed.
     */
    private fun migrateIfNeeded(context: Context, entries: List<HistoryEntry>): List<HistoryEntry> {
        val isGrey = { e: HistoryEntry -> e.east == TrailStatus.UNKNOWN && e.west == TrailStatus.UNKNOWN }
        val needsMigration = entries.any { isGrey(it) && it.reason.isEmpty() && !it.isNoNetwork }
        if (!needsMigration) return entries

        val migrated = entries.map { e ->
            if (isGrey(e) && e.reason.isEmpty() && !e.isNoNetwork)
                e.copy(reason = "Network not available", isNoNetwork = true)
            else e
        }
        persist(context, migrated)
        return migrated
    }

    private fun persist(context: Context, entries: List<HistoryEntry>) {
        val array = JSONArray()
        for (e in entries) {
            array.put(JSONObject().apply {
                put("east", e.east.name)
                put("west", e.west.name)
                put("ts", e.timestamp)
                if (e.reason.isNotEmpty()) put("reason", e.reason)
                if (e.isNoNetwork) put("nonet", true)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
