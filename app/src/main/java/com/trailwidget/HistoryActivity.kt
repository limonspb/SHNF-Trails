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

        val entries = HistoryStore.load(this)
        val container = findViewById<LinearLayout>(R.id.history_container)
        val emptyView = findViewById<TextView>(R.id.text_empty)

        if (entries.isEmpty()) {
            val current = StatusStore.load(this)
            emptyView.text = buildString {
                append("No status changes recorded yet.\n\n")
                append("Current status:\n")
                append("MUT-East: ${label(current.east)}\n")
                append("MUT-West: ${label(current.west)}")
            }
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            container.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(this)
            for (entry in entries) {
                val row = inflater.inflate(R.layout.item_history, container, false)
                setDotColor(row.findViewById(R.id.dot_east), entry.east)
                setDotColor(row.findViewById(R.id.dot_west), entry.west)
                row.findViewById<TextView>(R.id.text_history_status).text =
                    "E: ${label(entry.east)}  ·  W: ${label(entry.west)}"
                row.findViewById<TextView>(R.id.text_history_time).text =
                    dateFormat.format(Date(entry.timestamp))
                container.addView(row)

                // Divider
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                divider.setBackgroundColor(0xFF1E3020.toInt())
                container.addView(divider)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
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
