package com.collecter.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

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
                .filter { it.length >= 2 }
                .distinct()
                .map { VideoEntry(title = it, page = page) }

            if (cleaned.isNotEmpty()) dao.insertAll(cleaned)
            onProgress(page, cleaned.size)
            delay(250)
        }
    }

    private fun heuristicTitles(document: org.jsoup.nodes.Document): List<String> {
        val selectors = listOf(
            "article h1, article h2, article h3, article h4",
            ".post-title, .entry-title, .video-title, .item-title",
            "h2 a, h3 a, h4 a"
        )
        for (selector in selectors) {
            val result = document.select(selector).map { it.text().trim() }.filter { it.isNotBlank() }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
}
