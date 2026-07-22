package com.trailwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.util.Log
import android.widget.RemoteViews
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * App widget provider that renders the current trail state and manages refresh scheduling.
 */
class TrailWidgetProvider : AppWidgetProvider() {

    /**
     * Redraws active widgets and ensures periodic background work is scheduled.
     * Also triggers an immediate fetch so newly placed widgets show fresh data right away.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        try {
            updateWidgets(context, appWidgetManager, appWidgetIds)
            scheduleWork(context)
            enqueueImmediateUpdate(context)
        } catch (t: Throwable) {
            Log.e(TAG, "onUpdate failed", t)
        }
    }

    /**
     * Starts periodic updates and triggers an initial refresh when the first widget is added.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        try {
            scheduleWork(context)
            enqueueImmediateUpdate(context)
        } catch (t: Throwable) {
            Log.e(TAG, "onEnabled failed", t)
        }
    }

    /**
     * Re-renders a widget when launcher size options change.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(
            context,
            appWidgetManager,
            appWidgetId,
            newOptions
        )

        try {
            updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
        } catch (t: Throwable) {
            Log.e(TAG, "onAppWidgetOptionsChanged failed", t)
        }
    }

    /**
     * Stops periodic updates once the final widget instance is removed.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)

        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (_: Throwable) {
        }
    }

    /**
     * Handles widget refresh broadcasts.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            try {
                enqueueImmediateUpdate(context)
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        const val TAG = "TrailWidget"
        const val WORK_NAME = "trail_periodic_update"
        private const val IMMEDIATE_WORK_NAME = "trail_immediate_update"
        const val ACTION_REFRESH = "com.trailwidget.REFRESH"

        /**
         * Fill colors used to communicate each trail state at a glance.
         */
        private val COLOR_OPEN = 0xFF2E7D32.toInt()
        private val COLOR_CLOSED = 0xFFB71C1C.toInt()
        private val COLOR_UNKNOWN = 0xFF424242.toInt()

        private const val DEFAULT_BITMAP_SIZE_PX = 200
        private const val MIN_BITMAP_SIZE_PX = 80
        private const val EDGE_SHADOW_ALPHA = 50
        private const val TEXT_SHADOW_ALPHA = 140
        private val COLOR_WIDGET_BACKGROUND = 0xFF1B3A1B.toInt()
        private val COLOR_SIGN_POST = 0xFF784818.toInt()

        /**
         * Renders the latest stored trail state into every provided widget instance.
         */
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val statuses = StatusStore.load(context)
            val density = context.resources.displayMetrics.density

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                0,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            for (widgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val widthDp = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                    0
                )
                val heightDp = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                    0
                )
                val bitmapWidth = if (widthDp > 0) {
                    (widthDp * density).toInt()
                } else {
                    DEFAULT_BITMAP_SIZE_PX
                }
                val bitmapHeight = if (heightDp > 0) {
                    (heightDp * density).toInt()
                } else {
                    DEFAULT_BITMAP_SIZE_PX
                }

                val bitmap = buildBitmap(
                    statuses.east,
                    statuses.west,
                    bitmapWidth,
                    bitmapHeight
                )
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setOnClickPendingIntent(R.id.widget_image, tapPendingIntent)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun buildBitmap(
            east: TrailStatus,
            west: TrailStatus,
            bmpW: Int = DEFAULT_BITMAP_SIZE_PX,
            bmpH: Int = DEFAULT_BITMAP_SIZE_PX
        ): Bitmap {
            // Always square — fitCenter in the ImageView handles portrait cells gracefully.
            val size = minOf(bmpW, bmpH).coerceAtLeast(MIN_BITMAP_SIZE_PX)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val side = size.toFloat()

            paint.color = COLOR_WIDGET_BACKGROUND
            canvas.drawRoundRect(
                RectF(0f, 0f, side, side),
                side * 0.12f,
                side * 0.12f,
                paint
            )

            val barLeft = side * 0.45f
            val barRight = side * 0.55f
            paint.color = COLOR_SIGN_POST
            canvas.drawRoundRect(
                RectF(barLeft, side * 0.08f, barRight, side * 0.92f),
                side * 0.03f,
                side * 0.03f,
                paint
            )

            paint.color = colorFor(east)
            val topPath = Path().apply {
                moveTo(side * 0.09f, side * 0.11f)
                lineTo(side * 0.76f, side * 0.11f)
                lineTo(side * 0.88f, side * 0.25f)
                lineTo(side * 0.76f, side * 0.47f)
                lineTo(side * 0.09f, side * 0.47f)
                close()
            }
            canvas.drawPath(topPath, paint)

            paint.color = colorFor(west)
            val bottomPath = Path().apply {
                moveTo(side * 0.91f, side * 0.53f)
                lineTo(side * 0.24f, side * 0.53f)
                lineTo(side * 0.12f, side * 0.67f)
                lineTo(side * 0.24f, side * 0.83f)
                lineTo(side * 0.91f, side * 0.83f)
                close()
            }
            canvas.drawPath(bottomPath, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(EDGE_SHADOW_ALPHA, 0, 0, 0)
            paint.strokeWidth = side * 0.012f
            canvas.drawPath(topPath, paint)
            canvas.drawPath(bottomPath, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.WHITE
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.setShadowLayer(
                side * 0.025f,
                0f,
                side * 0.012f,
                Color.argb(TEXT_SHADOW_ALPHA, 0, 0, 0)
            )
            paint.textAlign = Paint.Align.CENTER

            val topMidX = (side * 0.09f + side * 0.76f) / 2f
            val topMidY = (side * 0.11f + side * 0.47f) / 2f
            val bottomMidX = (side * 0.24f + side * 0.91f) / 2f
            val bottomMidY = (side * 0.53f + side * 0.83f) / 2f

            paint.textSize = side * 0.18f
            drawCentered(canvas, "E", topMidX, topMidY - side * 0.03f, paint)
            drawCentered(
                canvas,
                "W",
                bottomMidX,
                bottomMidY - side * 0.03f,
                paint
            )

            paint.typeface = Typeface.DEFAULT
            paint.textSize = side * 0.10f
            drawCentered(
                canvas,
                statusText(east),
                topMidX,
                topMidY + side * 0.10f,
                paint
            )
            drawCentered(
                canvas,
                statusText(west),
                bottomMidX,
                bottomMidY + side * 0.10f,
                paint
            )

            return bitmap
        }

        private fun drawCentered(
            canvas: Canvas,
            text: String,
            cx: Float,
            cy: Float,
            paint: Paint
        ) {
            canvas.drawText(
                text,
                cx,
                cy - (paint.ascent() + paint.descent()) / 2f,
                paint
            )
        }

        private fun statusText(status: TrailStatus): String =
            when (status) {
                TrailStatus.OPEN -> "OPEN"
                TrailStatus.CLOSED -> "CLSD"
                TrailStatus.UNKNOWN -> "N/A"
            }

        private fun colorFor(status: TrailStatus): Int =
            when (status) {
                TrailStatus.OPEN -> COLOR_OPEN
                TrailStatus.CLOSED -> COLOR_CLOSED
                TrailStatus.UNKNOWN -> COLOR_UNKNOWN
            }

        /**
         * Schedules or cancels the periodic widget refresh job based on saved settings.
         * Uses KEEP so that an existing schedule is never reset when called from onUpdate().
         * Only UPDATE is used when settings change (interval reschedule).
         */
        fun scheduleWork(context: Context, forceReschedule: Boolean = false) {
            if (!StatusStore.isAutoUpdateEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            val intervalMin = StatusStore.getIntervalMinutes(context).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request =
                PeriodicWorkRequestBuilder<TrailUpdateWorker>(
                    intervalMin,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        15,
                        TimeUnit.MINUTES
                    )
                    .build()

            // KEEP: don't reset the timer on every onUpdate() call — that destabilizes scheduling.
            // CANCEL_AND_REENQUEUE only when the user explicitly changes the interval.
            val policy = if (forceReschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                         else ExistingPeriodicWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }

        /**
         * Enqueues a one-time refresh so widgets can update immediately.
         * Uses unique work with REPLACE policy so N concurrent onUpdate() calls (one per widget
         * instance) collapse into a single worker rather than spawning N parallel fetches.
         */
        fun enqueueImmediateUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrailUpdateWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(IMMEDIATE_WORK_NAME, androidx.work.ExistingWorkPolicy.REPLACE, request)
        }
    }
}
