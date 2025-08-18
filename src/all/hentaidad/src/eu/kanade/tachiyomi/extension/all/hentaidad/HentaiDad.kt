package eu.kanade.tachiyomi.extension.all.hentaidad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HentaiDad() : ParsedHttpSource() {
    override val baseUrl = "https://hentaidad.com"
    override val lang = "all"
    override val name = "HentaiDad"
    override val supportsLatest = true

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img .entry-image").attr("abs:src")
        manga.title = element.select("img .entry-image").attr("abs:title")
        manga.setUrlWithoutDomain(element.select("a").attr("abs:href"))
        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".pagination-next:not([disabled])"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$page")
    }

    override fun latestUpdatesSelector() = ".row > article"

    // Popular
    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/$page")
    }
    override fun popularMangaSelector() = latestUpdatesSelector()

    // Search

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.findInstance<TagFilter>()!!
        return when {
            query.isNotEmpty() -> GET("$baseUrl/search?tag=$query&page=$page")
            else -> popularMangaRequest(page)
        }
    }
    override fun searchMangaSelector() = latestUpdatesSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".video-title h1").text()
        manga.description = document.select(".video-title h1").text().trim()
        val genres = mutableListOf<String>()
        document.select(".description-box").first()!!.select("p > .video-tag").forEach {
            genres.add(it.text().substringAfter("#"))
        }
        manga.genre = genres.joinToString(", ")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.chapter_number = 0F
        chapter.name = element.select(".video-title .text-capitalize").text()
        return chapter
    }

    override fun chapterListSelector() = "html"

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".lightgallery img").forEach {
            val itUrl = it.attr("abs:src")
            pages.add(Page(pages.size, "", itUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        TagFilter(),
    )

    class TagFilter : Filter.Text("Tag ID")

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
