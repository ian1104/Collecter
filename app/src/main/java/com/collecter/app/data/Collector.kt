package com.collecter.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class Collector(private val dao: VideoDao) {
    suspend fun collect(
        baseUrl: String,
        startPage: Int,
        endPage: Int,
        titleSelector: String,
        onProgress: (page: Int, found: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        require(startPage > 0 && endPage >= startPage) { "Invalid page range" }
        val base = baseUrl.trimEnd('/')
        val selector = titleSelector.trim()

        for (page in startPage..endPage) {
            val url = "$base/page/$page"
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) Collector/0.1")
                .timeout(20_000)
                .get()

            val titles = if (selector.isNotEmpty()) {
                document.select(selector).map { it.text().trim() }
            } else {
                heuristicTitles(document)
            }

            val cleaned = titles
                .map(::cleanTitle)
                .filter { it.length >= 2 }
                .filterNot(::looksLikeNavigation)
                .distinct()
                .map { VideoEntry(title = it, page = page) }

            if (cleaned.isNotEmpty()) dao.insertAll(cleaned)
            onProgress(page, cleaned.size)
            delay(250)
        }
    }

    private fun heuristicTitles(document: Document): List<String> {
        val selectors = listOf(
            ".video-title, .video-title a, .post-title, .post-title a, .entry-title, .entry-title a, .item-title, .item-title a",
            "article h1, article h2, article h3, article h4",
            "h2 a, h3 a, h4 a"
        )
        for (selector in selectors) {
            val result = document.select(selector)
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
            if (result.isNotEmpty()) return result
        }
        return document.select("a:has(img)")
            .mapNotNull { link -> link.ownText().trim().takeIf { it.isNotBlank() } }
    }

    private fun cleanTitle(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()

    private fun looksLikeNavigation(value: String): Boolean {
        return value.lowercase() in setOf(
            "next", "previous", "prev", "home", "login", "register",
            "menu", "search", "more", "read more"
        )
    }
}
