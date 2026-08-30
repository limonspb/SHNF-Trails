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
    private const val MAX_ENTRIES = 5000

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

        val trimmed = if (entries.size > MAX_ENTRIES) smartTrim(entries) else entries
        persist(context, trimmed)
    }

    /**
     * Returns all history entries, newest first.
     * Applies a one-time migration: legacy grey entries with no reason are assumed to be
     * no-network events (the only source of reasonless grey before v1.5).
     * Synchronized on the same monitor as [record] to prevent lost-update races during migration.
     */
    @Synchronized
    fun load(context: Context): List<HistoryEntry> = migrateIfNeeded(context, loadAscending(context)).reversed()

    private fun loadAscending(context: Context): List<HistoryEntry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            // Parse per-entry so one corrupt/unknown record doesn't wipe the entire history.
            (0 until array.length()).mapNotNull { i ->
                try {
                    val o = array.getJSONObject(i)
                    HistoryEntry(
                        east = TrailStatus.valueOf(o.getString("east")),
                        west = TrailStatus.valueOf(o.getString("west")),
                        timestamp = o.getLong("ts"),
                        reason = o.optString("reason", ""),
                        isNoNetwork = o.optBoolean("nonet", false)
                    )
                } catch (_: Exception) {
                    null  // Skip the single corrupt entry; keep the rest.
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Backfills grey entries that should be flagged as no-network but aren't:
     * 1. Legacy entries with empty reason (pre-v1.5).
     * 2. Entries whose reason text is a known network-error string but isNoNetwork=false
     *    (caused by the MainActivity.startCheck() bug fixed in v1.6).
     */
    private fun migrateIfNeeded(context: Context, entries: List<HistoryEntry>): List<HistoryEntry> {
        val isGrey = { e: HistoryEntry -> e.east == TrailStatus.UNKNOWN && e.west == TrailStatus.UNKNOWN }
        fun isNetworkReason(r: String) = r.isEmpty()
                || r.startsWith("No internet")
                || r.startsWith("Network not available")
                || r.startsWith("Connection timed out")
                || r.startsWith("Cannot reach")
        val needsMigration = entries.any { isGrey(it) && !it.isNoNetwork && isNetworkReason(it.reason) }
        if (!needsMigration) return entries

        val migrated = entries.map { e ->
            if (isGrey(e) && !e.isNoNetwork && isNetworkReason(e.reason))
                e.copy(reason = if (e.reason.isEmpty()) "Network not available" else e.reason, isNoNetwork = true)
            else e
        }
        persist(context, migrated)
        return migrated
    }

    /**
     * Trims one entry from [entries] (which is one over [MAX_ENTRIES]) using a smart strategy:
     * 1. Look for the oldest no-network entry within the oldest half of the list and remove it.
     * 2. If none found there, remove the absolute oldest entry.
     *
     * This preserves old meaningful status changes while evicting low-value network-blip entries first.
     */
    private fun smartTrim(entries: List<HistoryEntry>): List<HistoryEntry> {
        val halfIndex = entries.size / 2
        val noNetIdx = (0 until halfIndex).firstOrNull { entries[it].isNoNetwork }
        return if (noNetIdx != null) {
            entries.toMutableList().also { it.removeAt(noNetIdx) }
        } else {
            entries.drop(1)
        }
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
