package com.trailwidget

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Possible availability states for a trail. */
enum class TrailStatus { OPEN, CLOSED, UNKNOWN }

/**
 * Pair of scraped statuses for the east and west MUT trails.
 *
 * @property east parsed status for MUT-East
 * @property west parsed status for MUT-West
 */
data class TrailStatuses(val east: TrailStatus, val west: TrailStatus)

/**
 * Typed result returned by [TrailScraper.fetchStatuses].
 *
 * [Success] carries the parsed statuses. [Failure] carries a human-readable reason string
 * suitable for display in the app UI (e.g. "No internet connection", "Connection timed out").
 */
sealed class ScrapeResult {
    data class Success(val statuses: TrailStatuses) : ScrapeResult()
    data class Failure(val reason: String) : ScrapeResult()
}

/**
 * Network scraper that reads Sam Houston trail pages and infers current open/closed status
 * for MUT-East and MUT-West.
 *
 * The homepage is always fetched first; the secondary URL is only requested when one or both
 * trail statuses are still unresolved after the first page, saving a network round-trip.
 */
object TrailScraper {

    private const val TAG = "TrailScraper"
    private const val CONTEXT_WINDOW = 300

    private val URLS = listOf(
        "https://www.samhoustontrails.com/",
        "https://www.samhoustontrails.com/closed-trail-status"
    )
    private val EAST_NAMES = listOf(
        "MUT-East", "MUT East", "Multi-Use Trail East", "Multi Use Trail East", "MUTEAST"
    )
    private val WEST_NAMES = listOf(
        "MUT-West", "MUT West", "Multi-Use Trail West", "Multi Use Trail West", "MUTWEST"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches and parses trail statuses from the Sam Houston website.
     *
     * Returns [ScrapeResult.Success] on any useful result, or [ScrapeResult.Failure] with a
     * display-ready reason string when all URLs fail or when neither trail can be found on any page.
     */
    fun fetchStatuses(context: Context): ScrapeResult {
        var lastException: Exception? = null
        var fetchedAny = false
        var east = TrailStatus.UNKNOWN
        var west = TrailStatus.UNKNOWN

        for (url in URLS) {
            if (east != TrailStatus.UNKNOWN && west != TrailStatus.UNKNOWN) break
            try {
                val text = fetchPage(url)
                fetchedAny = true
                AppLogger.d(context, TAG, "Fetched $url (${text.length} chars)")
                if (east == TrailStatus.UNKNOWN) east = parseStatus(context, text, EAST_NAMES)
                if (west == TrailStatus.UNKNOWN) west = parseStatus(context, text, WEST_NAMES)
            } catch (e: Exception) {
                AppLogger.e(context, TAG, "Failed to fetch $url", e)
                lastException = e
            }
        }

        return when {
            !fetchedAny -> classifyNetworkError(lastException)
            east == TrailStatus.UNKNOWN && west == TrailStatus.UNKNOWN -> {
                AppLogger.e(context, TAG, "Both trails UNKNOWN — website structure may have changed")
                ScrapeResult.Failure("Trail status not found on website")
            }
            else -> {
                AppLogger.d(context, TAG, "Scrape success — east=$east, west=$west")
                ScrapeResult.Success(TrailStatuses(east, west))
            }
        }
    }

    /**
     * Fetches a single URL, strips HTML tags, and returns visible page text.
     * Throws [IOException] (or a subtype) on network failure or non-2xx HTTP response.
     */
    private fun fetchPage(url: String): String {
        val request = Request.Builder().url(url).build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response body")
        }
        val document = Jsoup.parse(html)
        document.select("script, style").remove()
        return document.body()?.text() ?: throw IOException("Could not parse HTML body")
    }

    private fun parseStatus(context: Context, text: String, names: List<String>): TrailStatus {
        val uppercaseText = text.uppercase()

        for (name in names) {
            val uppercaseName = name.uppercase()
            var index = uppercaseText.indexOf(uppercaseName)

            while (index >= 0) {
                val start = maxOf(0, index - CONTEXT_WINDOW)
                val end = minOf(uppercaseText.length, index + uppercaseName.length + CONTEXT_WINDOW)
                val window = uppercaseText.substring(start, end)

                when {
                    containsClosedKeyword(window) -> {
                        AppLogger.d(context, TAG, "Trail '${names[0]}' → CLOSED")
                        return TrailStatus.CLOSED
                    }
                    containsOpenKeyword(window) -> {
                        AppLogger.d(context, TAG, "Trail '${names[0]}' → OPEN")
                        return TrailStatus.OPEN
                    }
                }
                index = uppercaseText.indexOf(uppercaseName, index + 1)
            }
        }

        AppLogger.d(context, TAG, "Trail '${names[0]}' → UNKNOWN (not found on this page)")
        return TrailStatus.UNKNOWN
    }

    private fun classifyNetworkError(e: Exception?): ScrapeResult.Failure {
        val reason = when (e) {
            is UnknownHostException -> "No internet connection"
            is SocketTimeoutException -> "Connection timed out"
            is ConnectException -> "Cannot reach samhoustontrails.com"
            is IOException -> "Network error: ${e.message ?: "unknown"}"
            else -> "Unexpected error: ${e?.message ?: "unknown"}"
        }
        return ScrapeResult.Failure(reason)
    }

    private fun containsClosedKeyword(w: String): Boolean =
        w.contains("TEMPORARILY CLOSED") || w.contains("TEMP CLOSED") ||
            w.contains(" CLOSED") || w.contains("\nCLOSED") || w.contains(":CLOSED") ||
            w.contains("IS CLOSED") || w.contains("ARE CLOSED") || w.contains("CLOSURE")

    private fun containsOpenKeyword(w: String): Boolean =
        w.contains(" OPEN") || w.contains("\nOPEN") || w.contains(":OPEN") ||
            w.contains("IS OPEN") || w.contains("ARE OPEN") ||
            w.contains("NOW OPEN") || w.contains("REOPENED")
}
