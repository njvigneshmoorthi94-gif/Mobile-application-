package com.heypapu.ai.news

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class NewsArticle(
    val title: String,
    val source: String,
    val publishedDate: String,
    val link: String,
    val category: String,
    val isVerifiedMajorOutlet: Boolean
)

class WorldwideNewsRepository {

    private val client = OkHttpClient()

    private val verifiedOutlets = listOf(
        "The Hindu", "BBC", "Reuters", "Associated Press", "NDTV",
        "The Indian Express", "Times of India", "TechCrunch", "ESPN",
        "Dinamalar", "Dinamani", "Puthiya Thalaimurai", "The Verge", "Al Jazeera"
    )

    private val categoryFeeds = mapOf(
        "India" to "https://news.google.com/rss/headlines/section/geo/India?hl=en-IN&gl=IN&ceid=IN:en",
        "Tamil Nadu" to "https://news.google.com/rss/search?q=Tamil+Nadu+Chennai&hl=en-IN&gl=IN&ceid=IN:en",
        "Business" to "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=en-IN&gl=IN&ceid=IN:en",
        "Technology" to "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=en-IN&gl=IN&ceid=IN:en",
        "AI" to "https://news.google.com/rss/search?q=Artificial+Intelligence+LLM+Google+OpenAI&hl=en-IN&gl=IN&ceid=IN:en",
        "Sports" to "https://news.google.com/rss/headlines/section/topic/SPORTS?hl=en-IN&gl=IN&ceid=IN:en",
        "International" to "https://feeds.bbci.co.uk/news/world/rss.xml"
    )

    suspend fun fetchTopHeadlines(category: String): List<NewsArticle> = withContext(Dispatchers.IO) {
        val url = categoryFeeds[category] ?: categoryFeeds["India"]!!
        val articles = mutableListOf<NewsArticle>()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HeyPapuAI-Android/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val xml = response.body?.string().orEmpty()
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(xml))

                var insideItem = false
                var title = ""
                var link = ""
                var pubDate = ""
                var source = if (url.contains("bbci")) "BBC World News" else "News Wire"

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT && articles.size < 10) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "item" -> {
                                    insideItem = true
                                    title = ""
                                    link = ""
                                    pubDate = ""
                                    source = if (url.contains("bbci")) "BBC World News" else "News Wire"
                                }
                                "title" -> if (insideItem) title = parser.nextText().trim()
                                "link" -> if (insideItem) link = parser.nextText().trim()
                                "pubDate" -> if (insideItem) pubDate = parser.nextText().trim().take(22)
                                "source" -> if (insideItem) source = parser.nextText().trim()
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "item" && insideItem) {
                                insideItem = false
                                if (title.isNotBlank() && link.isNotBlank()) {
                                    val isVerified = verifiedOutlets.any {
                                        source.contains(it, ignoreCase = true) ||
                                            title.contains(it, ignoreCase = true)
                                    }
                                    articles.add(
                                        NewsArticle(
                                            title = title,
                                            source = source,
                                            publishedDate = pubDate.ifBlank { "Today" },
                                            link = link,
                                            category = category,
                                            isVerifiedMajorOutlet = isVerified
                                        )
                                    )
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (_: Exception) {
        }
        articles.take(10)
    }
}
