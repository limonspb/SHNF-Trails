package com.trailwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * Main configuration screen for manual checks, widget pinning, and auto-refresh settings.
 */
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var isChecking = false
    private var suppressSpinner = false

    // Refreshes both status cards and the timer display every 30 seconds while visible.
    private val timerTick = object : Runnable {
        override fun run() {
            refreshStatusCards()
            refreshTimerSection()
            handler.postDelayed(this, TIMER_REFRESH_INTERVAL_MS)
        }
    }

    /**
     * Inflates the screen and wires up controls.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupButtons()
        setupSettings()
    }

    /**
     * Refreshes visible status data when the activity enters the foreground.
     */
    override fun onStart() {
        super.onStart()
        refreshAll()
        handler.post(timerTick)
    }

    /**
     * Stops periodic UI refreshes while the activity is no longer visible.
     */
    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(timerTick)
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            if (!isChecking) startCheck()
        }
        findViewById<Button>(R.id.btn_website).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.samhoustontrails.com/")))
        }
        findViewById<Button>(R.id.btn_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btn_add_widget).setOnClickListener {
            pinWidget()
        }
    }

    private fun pinWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, TrailWidgetProvider::class.java)
        val hint = findViewById<TextView>(R.id.text_widget_hint)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(provider, null, null)
            hint.text = ""
        } else {
            hint.text =
                "Not supported by this launcher — long-press home screen → Widgets → Trail Widget"
        }
    }

    private fun setupSettings() {
        val autoUpdateSwitch = findViewById<SwitchCompat>(R.id.switch_auto_update)
        autoUpdateSwitch.isChecked = StatusStore.isAutoUpdateEnabled(this)
        autoUpdateSwitch.setOnCheckedChangeListener { _, checked ->
            StatusStore.setAutoUpdateEnabled(this, checked)
            TrailWidgetProvider.scheduleWork(this)
            updateIntervalSpinnerEnabled()
            refreshTimerSection()
        }

        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            StatusStore.INTERVAL_LABELS
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val selectedIndex = StatusStore.INTERVAL_OPTIONS
            .indexOfFirst { it == StatusStore.getIntervalMinutes(this) }
            .coerceAtLeast(0)
        suppressSpinner = true
        spinner.setSelection(selectedIndex)
        suppressSpinner = false

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                pos: Int,
                id: Long
            ) {
                if (suppressSpinner) {
                    return
                }

                StatusStore.setIntervalMinutes(
                    this@MainActivity,
                    StatusStore.INTERVAL_OPTIONS[pos]
                )
                TrailWidgetProvider.scheduleWork(this@MainActivity)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        updateIntervalSpinnerEnabled()
    }

    private fun updateIntervalSpinnerEnabled() {
        val enabled = StatusStore.isAutoUpdateEnabled(this)
        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        spinner.isEnabled = enabled
        spinner.alpha = if (enabled) 1f else DISABLED_CONTROL_ALPHA
    }

    private fun startCheck() {
        isChecking = true

        val refreshButton = findViewById<Button>(R.id.btn_refresh)
        refreshButton.isEnabled = false
        refreshButton.text = "Checking…"

        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        progressBar.isIndeterminate = true

        Thread {
            when (val result = TrailScraper.fetchStatuses(this)) {
                is ScrapeResult.Success -> StatusStore.save(this, result.statuses)
                is ScrapeResult.Failure -> StatusStore.saveError(this, result.reason)
            }

            val appWidgetManager = AppWidgetManager.getInstance(this)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(this, TrailWidgetProvider::class.java)
            )
            if (appWidgetIds.isNotEmpty()) {
                TrailWidgetProvider.updateWidgets(this, appWidgetManager, appWidgetIds)
            }

            runOnUiThread {
                isChecking = false
                refreshButton.isEnabled = true
                refreshButton.text = "Check Now"
                progressBar.isIndeterminate = false
                refreshAll()
            }
        }.start()
    }

    private fun refreshAll() {
        refreshStatusCards()
        refreshTimerSection()
    }

    private fun refreshStatusCards() {
        val statuses = StatusStore.load(this)
        updateCard(R.id.card_east, R.id.text_east, statuses.east)
        updateCard(R.id.card_west, R.id.text_west, statuses.west)

        val errorView = findViewById<TextView>(R.id.text_error_reason)
        val isGrey = statuses.east == TrailStatus.UNKNOWN || statuses.west == TrailStatus.UNKNOWN
        val failMessage = StatusStore.getFailMessage(this)
        if (isGrey && failMessage.isNotEmpty()) {
            errorView.text = "⚠ $failMessage"
            errorView.visibility = View.VISIBLE
        } else {
            errorView.visibility = View.GONE
        }
    }

    private fun updateCard(
        cardId: Int,
        labelId: Int,
        status: TrailStatus
    ) {
        val card = findViewById<View>(cardId)
        val label = findViewById<TextView>(labelId)
        val cornerRadiusPx = CARD_CORNER_RADIUS_DP * resources.displayMetrics.density

        val backgroundColor = when (status) {
            TrailStatus.OPEN -> 0xFF2E7D32.toInt()
            TrailStatus.CLOSED -> 0xFFB71C1C.toInt()
            TrailStatus.UNKNOWN -> 0xFF363636.toInt()
        }
        val statusText = when (status) {
            TrailStatus.OPEN -> "OPEN"
            TrailStatus.CLOSED -> "CLOSED"
            TrailStatus.UNKNOWN -> "Unknown"
        }
        val textColor = when (status) {
            TrailStatus.OPEN -> 0xFFA5D6A7.toInt()
            TrailStatus.CLOSED -> 0xFFEF9A9A.toInt()
            TrailStatus.UNKNOWN -> 0xFF9E9E9E.toInt()
        }

        card.background = GradientDrawable().apply {
            cornerRadius = cornerRadiusPx
            setColor(backgroundColor)
        }
        label.text = statusText
        label.setTextColor(textColor)
    }

    private fun refreshTimerSection() {
        val lastUpdatedMillis = StatusStore.lastUpdatedMillis(this)
        val intervalMillis = StatusStore.getIntervalMinutes(this) * 60_000L
        val autoUpdateEnabled = StatusStore.isAutoUpdateEnabled(this)
        val now = System.currentTimeMillis()

        val lastUpdatedView = findViewById<TextView>(R.id.text_last_updated)
        val nextUpdateView = findViewById<TextView>(R.id.text_next_update)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        if (progressBar.isIndeterminate) {
            return
        }

        if (lastUpdatedMillis == 0L) {
            lastUpdatedView.text = "Last checked: never"
            nextUpdateView.text =
                if (autoUpdateEnabled) "pending first check…" else "auto-refresh off"
            progressBar.progress = 0
            return
        }

        val elapsedMillis = now - lastUpdatedMillis
        val elapsedMinutes = elapsedMillis / 60_000
        lastUpdatedView.text = when {
            elapsedMinutes < 1 -> "Checked: just now"
            elapsedMinutes == 1L -> "Checked: 1 min ago"
            elapsedMinutes < 60 -> "Checked: ${elapsedMinutes}m ago"
            else -> {
                "Checked: ${elapsedMinutes / 60}h ${elapsedMinutes % 60}m ago"
            }
        }

        if (!autoUpdateEnabled) {
            nextUpdateView.text = "auto-refresh off"
            progressBar.progress = 0
            return
        }

        val remainingMillis = (intervalMillis - elapsedMillis).coerceAtLeast(0)
        val remainingMinutes = remainingMillis / 60_000
        nextUpdateView.text = when {
            remainingMillis == 0L -> "due now"
            remainingMinutes < 1 -> "next check: <1 min"
            remainingMinutes == 1L -> "next check: 1 min"
            else -> "next check: ${remainingMinutes}m"
        }
        progressBar.progress = ((elapsedMillis.toFloat() / intervalMillis) * 100)
            .coerceIn(0f, 100f)
            .toInt()
    }

    companion object {
        private const val TIMER_REFRESH_INTERVAL_MS = 30_000L
        private const val DISABLED_CONTROL_ALPHA = 0.4f
        private const val CARD_CORNER_RADIUS_DP = 14f
    }
}
