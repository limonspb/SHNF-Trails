package com.trailwidget

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent file-based logger that mirrors output to Android's logcat.
 *
 * Logs are written to [LOG_FILE] inside the app's private files directory, readable via:
 *   adb shell run-as com.trailwidget cat /data/data/com.trailwidget/files/shnf_trails.log
 *
 * When the file exceeds [MAX_BYTES], the oldest half is discarded so recent history is always
 * preserved without unbounded growth. 1 MB comfortably holds several years of hourly entries.
 */
object AppLogger {

    private const val LOG_FILE = "shnf_trails.log"
    private const val MAX_BYTES = 1 * 1024 * 1024L  // 1 MB

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun d(context: Context, tag: String, msg: String) {
        Log.d(tag, msg)
        append(context, "D", tag, msg)
    }

    fun i(context: Context, tag: String, msg: String) {
        Log.i(tag, msg)
        append(context, "I", tag, msg)
    }

    fun e(context: Context, tag: String, msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
        val detail = if (throwable != null) {
            "$msg — ${throwable.javaClass.simpleName}: ${throwable.message}"
        } else {
            msg
        }
        append(context, "E", tag, detail)
    }

    /** Returns the underlying log [File] for diagnostics or ADB inspection. */
    fun logFile(context: Context): File = File(context.filesDir, LOG_FILE)

    private fun append(context: Context, level: String, tag: String, msg: String) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists() && file.length() > MAX_BYTES) {
                rotateLog(file)
            }
            val timestamp = dateFormat.format(Date())
            file.appendText("$timestamp $level/$tag: $msg\n")
        } catch (_: Exception) {
            // Logging must never crash the app.
        }
    }

    /** Drops the oldest half of the log file to keep size bounded. */
    private fun rotateLog(file: File) {
        val content = file.readText()
        file.writeText(content.substring(content.length / 2))
    }
}
