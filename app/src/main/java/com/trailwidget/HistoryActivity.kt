package com.trailwidget

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows the log of trail-status changes, newest first.
 * No-network grey events are hidden by default; a checkbox at the top reveals them.
 */
class HistoryActivity : AppCompatActivity() {

    private val dateFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US)

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var checkbox: CheckBox
    private lateinit var inflater: LayoutInflater
    private var allEntries: List<HistoryEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.apply {
            title = "Status History"
            setDisplayHomeAsUpEnabled(true)
        }

        container = findViewById(R.id.history_container)
        emptyView = findViewById(R.id.text_empty)
        checkbox = findViewById(R.id.checkbox_show_no_network)
        inflater = LayoutInflater.from(this)

        allEntries = HistoryStore.load(this)

        if (allEntries.isEmpty()) {
            val current = StatusStore.load(this)
            val failMsg = StatusStore.getFailMessage(this)
            when {
                current.east != TrailStatus.UNKNOWN || current.west != TrailStatus.UNKNOWN -> {
                    HistoryStore.record(this, current)
                    allEntries = HistoryStore.load(this)
                }
                failMsg.isNotEmpty() -> {
                    showEmpty("Last check failed:\n$failMsg\n\nTap \u201cCheck Now\u201d in the app to retry.")
                    return
                }
                else -> {
                    showEmpty("No status recorded yet.\n\nTap \u201cCheck Now\u201d in the app to fetch trail status.")
                    return
                }
            }
        }

        checkbox.setOnCheckedChangeListener { _, _ -> renderList() }
        renderList()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun renderList() {
        container.removeAllViews()
        val showNoNetwork = checkbox.isChecked
        val filtered = allEntries.filter { showNoNetwork || !it.isNoNetwork }

        // When no-network events are hidden, collapse consecutive entries with the same
        // east+west status — they look identical because the hidden grey blips between
        // them don't represent a real status change.
        val visible = if (showNoNetwork) filtered else {
            filtered.fold(mutableListOf<HistoryEntry>()) { acc, entry ->
                val last = acc.lastOrNull()
                if (last != null && last.east == entry.east && last.west == entry.west) acc
                else { acc.add(entry); acc }
            }
        }

        if (visible.isEmpty()) {
            showEmpty(
                if (allEntries.any { it.isNoNetwork })
                    "All recorded events were no-network failures.\n\nCheck \"Show no-network events\" above to see them."
                else
                    "No status recorded yet.\n\nTap \u201cCheck Now\u201d in the app to fetch trail status."
            )
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        val isFirst = visible.firstOrNull() == allEntries.firstOrNull()
        for ((index, entry) in visible.withIndex()) {
            addRow(entry, isNow = isFirst && index == 0 && allEntries.size == 1 && entry.east == TrailStatus.UNKNOWN)
            if (index < visible.lastIndex) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                divider.setBackgroundColor(0xFF1E3020.toInt())
                container.addView(divider)
            }
        }
    }

    private fun showEmpty(msg: String) {
        emptyView.text = msg
        emptyView.visibility = View.VISIBLE
        container.visibility = View.GONE
    }

    private fun addRow(entry: HistoryEntry, isNow: Boolean) {
        val row = inflater.inflate(R.layout.item_history, container, false)
        setDotColor(row.findViewById(R.id.dot_east), entry.east)
        setDotColor(row.findViewById(R.id.dot_west), entry.west)
        row.findViewById<TextView>(R.id.text_history_status).text =
            "E: ${label(entry.east)}  ·  W: ${label(entry.west)}"
        row.findViewById<TextView>(R.id.text_history_time).text =
            if (isNow) "Now (no changes recorded yet)" else dateFormat.format(Date(entry.timestamp))

        val reasonView = row.findViewById<TextView>(R.id.text_history_reason)
        if (entry.reason.isNotEmpty()) {
            reasonView.text = entry.reason
            reasonView.visibility = View.VISIBLE
        } else {
            reasonView.visibility = View.GONE
        }

        container.addView(row)
    }

    private fun setDotColor(dot: View, status: TrailStatus) {
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(when (status) {
                TrailStatus.OPEN -> 0xFF2E7D32.toInt()
                TrailStatus.CLOSED -> 0xFFB71C1C.toInt()
                TrailStatus.UNKNOWN -> 0xFF424242.toInt()
            })
        }
    }

    companion object {
        private fun label(s: TrailStatus) = when (s) {
            TrailStatus.OPEN -> "Open"
            TrailStatus.CLOSED -> "Closed"
            TrailStatus.UNKNOWN -> "Unknown"
        }
    }
}
