package com.trailwidget

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows the log of trail-status changes, newest first.
 * Only transitions (open → closed, etc.) are stored — not every hourly ping.
 */
class HistoryActivity : AppCompatActivity() {

    private val dateFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.apply {
            title = "Status History"
            setDisplayHomeAsUpEnabled(true)
        }

        var entries = HistoryStore.load(this)
        val container = findViewById<LinearLayout>(R.id.history_container)
        val emptyView = findViewById<TextView>(R.id.text_empty)
        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)

        if (entries.isEmpty()) {
            val current = StatusStore.load(this)
            val failMsg = StatusStore.getFailMessage(this)
            when {
                current.east != TrailStatus.UNKNOWN || current.west != TrailStatus.UNKNOWN -> {
                    // Real status data exists but no history yet — seed it as baseline.
                    HistoryStore.record(this, current)
                    entries = HistoryStore.load(this)
                    for (entry in entries) addRow(inflater, container, entry, isNow = true)
                }
                failMsg.isNotEmpty() -> {
                    emptyView.text = "Last check failed:\n$failMsg\n\nTap \u201cCheck Now\u201d in the app to retry."
                    emptyView.visibility = View.VISIBLE
                    container.visibility = View.GONE
                }
                else -> {
                    emptyView.text = "No status recorded yet.\n\nTap \u201cCheck Now\u201d in the app to fetch trail status."
                    emptyView.visibility = View.VISIBLE
                    container.visibility = View.GONE
                }
            }
        } else {
            for (entry in entries) {
                addRow(inflater, container, entry, isNow = false)
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                divider.setBackgroundColor(0xFF1E3020.toInt())
                container.addView(divider)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun addRow(inflater: LayoutInflater, container: LinearLayout, entry: HistoryEntry, isNow: Boolean) {
        val row = inflater.inflate(R.layout.item_history, container, false)
        setDotColor(row.findViewById(R.id.dot_east), entry.east)
        setDotColor(row.findViewById(R.id.dot_west), entry.west)
        row.findViewById<TextView>(R.id.text_history_status).text =
            "E: ${label(entry.east)}  ·  W: ${label(entry.west)}"
        row.findViewById<TextView>(R.id.text_history_time).text =
            if (isNow) "Now (no changes recorded yet)" else dateFormat.format(Date(entry.timestamp))
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
