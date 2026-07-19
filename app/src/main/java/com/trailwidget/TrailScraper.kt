package com.trailwidget

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Possible availability states reported for a trail.
 */
enum class TrailStatus {
    OPEN,
    CLOSED,
    UNKNOWN
}

/**
 * Pair of scraped statuses for the east and west trails.
 *
 * @property east the parsed status for the east trail
 * @property west the parsed status for the west trail
 */
data class TrailStatuses(
    val east: TrailStatus,
    val west: TrailStatus
)

/**
 * Network scraper that reads Sam Houston trail pages and infers the current trail status text.
 */
object TrailScraper {

    private const val TAG = "TrailScraper"
    private const val CONTEXT_WINDOW = 300

    private val URLS = listOf(
        "https://www.samhoustontrails.com/",
        "https://www.samhoustontrails.com/closed-trail-status"
    )
    private val EAST_NAMES = listOf(
        "MUT-East",
        "MUT East",
        "Multi-Use Trail East",
        "Multi Use Trail East",
        "MUTEAST"
    )
    private val WEST_NAMES = listOf(
        "MUT-West",
        "MUT West",
        "Multi-Use Trail West",
        "Multi Use Trail West",
        "MUTWEST"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches a single URL, strips HTML, and returns the remaining visible text.
     */
    private fun fetchPage(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                response.body?.string()
            } ?: return null

            val document = Jsoup.parse(html)
            document.select("script, style").remove()
            val text = document.body()?.text() ?: return null
            Log.d(TAG, "Fetched $url (${text.length} chars)")
            text
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch $url: ${e.message}")
            null
        }
    }

    private fun parseStatus(text: String, names: List<String>): TrailStatus {
        val uppercaseText = text.uppercase()

        for (name in names) {
            val uppercaseName = name.uppercase()
            var index = uppercaseText.indexOf(uppercaseName)

            while (index >= 0) {
                val start = maxOf(0, index - CONTEXT_WINDOW)
                val end = minOf(
                    uppercaseText.length,
                    index + uppercaseName.length + CONTEXT_WINDOW
                )
                val contextWindow = uppercaseText.substring(start, end)

                when {
                    containsClosedKeyword(contextWindow) -> {
                        Log.d(TAG, "Trail '$name' → CLOSED")
                        return TrailStatus.CLOSED
                    }
                    containsOpenKeyword(contextWindow) -> {
                        Log.d(TAG, "Trail '$name' → OPEN")
                        return TrailStatus.OPEN
                    }
                }

                index = uppercaseText.indexOf(uppercaseName, index + 1)
            }
        }

        Log.d(TAG, "Trail '${names[0]}' → UNKNOWN")
        return TrailStatus.UNKNOWN
    }

    private fun containsClosedKeyword(contextWindow: String): Boolean =
        contextWindow.contains("TEMPORARILY CLOSED") ||
            contextWindow.contains("TEMP CLOSED") ||
            contextWindow.contains(" CLOSED") ||
            contextWindow.contains("\nCLOSED") ||
            contextWindow.contains(":CLOSED") ||
            contextWindow.contains("IS CLOSED") ||
            contextWindow.contains("ARE CLOSED") ||
            contextWindow.contains("CLOSURE")

    private fun containsOpenKeyword(contextWindow: String): Boolean =
        contextWindow.contains(" OPEN") ||
            contextWindow.contains("\nOPEN") ||
            contextWindow.contains(":OPEN") ||
            contextWindow.contains("IS OPEN") ||
            contextWindow.contains("ARE OPEN") ||
            contextWindow.contains("NOW OPEN") ||
            contextWindow.contains("REOPENED")

    /**
     * Fetches trail statuses. The fallback page is only requested when the first page does not
     * resolve both trails, which preserves the existing behavior while reducing unnecessary work.
     */
    fun fetchStatuses(): TrailStatuses {
        var east = TrailStatus.UNKNOWN
        var west = TrailStatus.UNKNOWN

        for (url in URLS) {
            if (east != TrailStatus.UNKNOWN && west != TrailStatus.UNKNOWN) {
                break
            }

            val text = fetchPage(url) ?: continue
            if (east == TrailStatus.UNKNOWN) {
                east = parseStatus(text, EAST_NAMES)
            }
            if (west == TrailStatus.UNKNOWN) {
                west = parseStatus(text, WEST_NAMES)
            }
        }

        return TrailStatuses(east, west)
    }
}
