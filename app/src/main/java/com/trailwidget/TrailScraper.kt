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
 * [Success] carries the parsed statuses.
 * [NetworkFailure] means the host was unreachable (DNS, timeout, no connectivity).
 * [ParseFailure] means the website was reached but trail names could not be found.
 */
sealed class ScrapeResult {
    data class Success(val statuses: TrailStatuses) : ScrapeResult()
    data class NetworkFailure(val reason: String) : ScrapeResult()
    data class ParseFailure(val reason: String) : ScrapeResult()
}

/**
 * Network scraper that reads Sam Houston trail pages and infers current open/closed status
 * for MUT-East and MUT-West.
 *
 * The homepage is fetched first; the secondary URL is only tried when one or both statuses
 * remain unresolved and the first fetch succeeded (parse fallback, not network fallback).
 */
object TrailScraper {

    private const val TAG = "TrailScraper"
    // Max chars to scan forward past the trail name before hitting a boundary.
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

    // Pre-uppercased for use as cross-trail section boundaries.
    private val EAST_NAMES_UPPER = EAST_NAMES.map { it.uppercase() }
    private val WEST_NAMES_UPPER = WEST_NAMES.map { it.uppercase() }

    // Word-boundary regex patterns prevent "CLOSURE" matching inside "DISCLOSURE",
    // "OPEN" inside "OPENING", etc. Longer phrases placed first for clarity (all are found
    // via findAll so alternation order only affects same-position ties, which don't occur here).
    private val CLOSED_REGEX = Regex("""\b(?:TEMPORARILY CLOSED|TEMP CLOSED|IS CLOSED|ARE CLOSED|CLOSURE|CLOSED)\b""")
    private val OPEN_REGEX  = Regex("""\b(?:REOPENED|NOW OPEN|IS OPEN|ARE OPEN|OPEN)\b""")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches and parses trail statuses from the Sam Houston website.
     *
     * Returns [ScrapeResult.Success] on any useful result, or a typed failure with a
     * display-ready reason string when all URLs fail or neither trail can be found.
     */
    fun fetchStatuses(context: Context): ScrapeResult {
        var lastException: Exception? = null
        var fetchedAny = false
        var east = TrailStatus.UNKNOWN
        var west = TrailStatus.UNKNOWN

        for (url in URLS) {
            if (east != TrailStatus.UNKNOWN && west != TrailStatus.UNKNOWN) break
            try {
                val rawText = fetchPage(url)
                val text = rawText.uppercase()   // uppercase once per page, reused for both trails
                fetchedAny = true
                AppLogger.d(context, TAG, "Fetched $url (${rawText.length} chars)")
                if (east == TrailStatus.UNKNOWN) east = parseStatus(context, text, EAST_NAMES_UPPER, WEST_NAMES_UPPER)
                if (west == TrailStatus.UNKNOWN) west = parseStatus(context, text, WEST_NAMES_UPPER, EAST_NAMES_UPPER)
            } catch (e: Exception) {
                AppLogger.e(context, TAG, "Failed to fetch $url", e)
                lastException = e
                // Network-level failure — no point trying remaining URLs.
                if (e is UnknownHostException || e is SocketTimeoutException || e is ConnectException) break
            }
        }

        return when {
            !fetchedAny -> classifyNetworkError(lastException)
            east == TrailStatus.UNKNOWN && west == TrailStatus.UNKNOWN -> {
                AppLogger.e(context, TAG, "Both trails UNKNOWN — website structure may have changed")
                ScrapeResult.ParseFailure("Trail status not found on website")
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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) SHNF-Trails/1.3")
            .build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response body")
        }
        val document = Jsoup.parse(html)
        document.select("script, style").remove()
        return document.body()?.text() ?: throw IOException("Could not parse HTML body")
    }

    /**
     * Scans forward from each occurrence of any name in [namesUpper] and returns the first
     * OPEN/CLOSED keyword found before either [CONTEXT_WINDOW] chars elapse or the next
     * section belonging to the other trail begins (defined by [boundaryNamesUpper]).
     *
     * Using the other trail's names as a section boundary prevents a ±300-char window from
     * spanning both trail descriptions and misattributing West's CLOSED to East (or vice versa).
     *
     * [uppercaseText] must already be uppercased by the caller (done once per page fetch).
     */
    private fun parseStatus(
        context: Context,
        uppercaseText: String,
        namesUpper: List<String>,
        boundaryNamesUpper: List<String>
    ): TrailStatus {
        for (name in namesUpper) {
            var nameIdx = uppercaseText.indexOf(name)
            while (nameIdx >= 0) {
                val scanStart = nameIdx + name.length
                // Stop at the nearest occurrence of the other trail's name to avoid
                // reading that trail's status keywords.
                val nextBoundary = boundaryNamesUpper
                    .mapNotNull { b -> uppercaseText.indexOf(b, scanStart).takeIf { it >= 0 } }
                    .minOrNull() ?: uppercaseText.length
                val scanEnd = minOf(scanStart + CONTEXT_WINDOW, nextBoundary)

                if (scanEnd > scanStart) {
                    val window = uppercaseText.substring(scanStart, scanEnd)
                    val closedMatch = CLOSED_REGEX.find(window)
                    val openMatch  = OPEN_REGEX.find(window)

                    val status = when {
                        closedMatch == null && openMatch == null -> null
                        closedMatch == null -> TrailStatus.OPEN
                        openMatch  == null  -> TrailStatus.CLOSED
                        // First keyword in reading order wins.
                        closedMatch.range.first <= openMatch.range.first -> TrailStatus.CLOSED
                        else -> TrailStatus.OPEN
                    }
                    if (status != null) {
                        AppLogger.d(context, TAG, "Trail '${namesUpper[0]}' → $status")
                        return status
                    }
                }
                nameIdx = uppercaseText.indexOf(name, nameIdx + 1)
            }
        }
        AppLogger.d(context, TAG, "Trail '${namesUpper[0]}' → UNKNOWN (not found on this page)")
        return TrailStatus.UNKNOWN
    }

    private fun classifyNetworkError(e: Exception?): ScrapeResult.NetworkFailure {
        val reason = when (e) {
            is UnknownHostException -> "No internet connection"
            is SocketTimeoutException -> "Connection timed out"
            is ConnectException     -> "Cannot reach samhoustontrails.com"
            is IOException          -> "Network error: ${e.message ?: "unknown"}"
            else                    -> "Unexpected error: ${e?.message ?: "unknown"}"
        }
        return ScrapeResult.NetworkFailure(reason)
    }
}
