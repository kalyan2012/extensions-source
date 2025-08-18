package eu.kanade.tachiyomi.multisrc.kemono

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.lang.Thread.sleep

open class Kemono(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "all",
) : HttpSource(), ConfigurableSource {
    override val supportsLatest = true

    override val client = network.client.newBuilder().rateLimit(10).build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/css")

    private val json: Json by injectLazy()

    private val preferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    private val apiPath = "api/v1"

    private val imgCdnUrl = baseUrl.replace("//", "//img.")

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.fromCallable {
            searchMangas(page, sortBy = "pop" to "desc", url = "posts/popular")
        }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.fromCallable {
            searchMangas(page, sortBy = "lat" to "desc", url = "posts")
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.fromCallable { searchMangas(page, query, filters) }

    private fun searchMangas(
        page: Int = 1,
        title: String = "",
        filters: FilterList? = null,
        sortBy: Pair<String, String> = "" to "",
        url: String = "posts",
    ): MangasPage {
        var hasNextPage = true
        val result = ArrayList<SManga>()
        var finalUrl = "$baseUrl/$apiPath/$url?o=${(page - 1) * 50}"
        if (title.isNotEmpty()) finalUrl += "&q=$title"

        if (url == "posts") {
            val request = GET(finalUrl, headers)
            val mainPage = retry(request).parseAs<PostsDto>()
            mainPage.retrievePosts().forEach { post ->
                if (post.images.isNotEmpty()) result.add(post.toSManga(imgCdnUrl))
            }
            val limit = mainPage.getCount()
            hasNextPage = (page * 50 < limit)
            return MangasPage(result, hasNextPage)
        } else if (url == "posts/popular") {
            finalUrl += "&period=recent"
            val request = GET(finalUrl, headers)
            val mainPage = retry(request).parseAs<PopularDto>()
            mainPage.retrievePosts().forEach { post ->
                if (post.images.isNotEmpty()) result.add(post.toSManga(imgCdnUrl))
            }
            val limit = mainPage.getCount()
            hasNextPage = (page * 50 < limit)
            return MangasPage(result, hasNextPage)
        }
        throw UnsupportedOperationException()
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        // thumbnails already built in toSManga, no extra formatting
        return Observable.just(manga)
    }

    override fun getChapterUrl(chapter: SChapter) =
        "$baseUrl${chapter.url.replace("$apiPath/", "")}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.fromCallable {
            val result = ArrayList<SChapter>()
            val request = GET("$baseUrl/$apiPath${manga.url}", headers)
            val post: PostDto = retry(request).parseAs()
            if (post.getCurrentPost().images.isNotEmpty()) {
                result.add(post.getCurrentPost().toSChapter())
            }
            result
        }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl/$apiPath${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val post: PostDto = response.parseAs()
        return post.getCurrentPost().images.mapIndexed { i, path ->
            Page(i, imageUrl = baseUrl + path)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl!!
        if (!preferences.getBoolean(USE_LOW_RES_IMG, false)) return GET(imageUrl, headers)

        val index = imageUrl.indexOf('/', 8)
        val url = buildString {
            append(imageUrl, 0, index)
            append("/thumbnail/data")
            append(imageUrl.substring(index))
        }
        return GET(url, headers)
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun retry(request: Request): Response {
        var code = 0
        repeat(5) {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) return response
            response.close()
            code = response.code
            if (code == 429) sleep(10000)
        }
        throw Exception("HTTP error $code")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = POST_PAGES_PREF
            title = "Maximum posts to load"
            summary = "Loading more posts costs more time and network traffic.\nCurrently: %s"
            entryValues = Array(POST_PAGES_MAX) { (it + 1).toString() }
            entries = Array(POST_PAGES_MAX) { "${(it + 1)} pages (${(it + 1) * PAGE_POST_LIMIT} posts)" }
            setDefaultValue(POST_PAGES_DEFAULT)
        }.let { screen.addPreference(it) }

        SwitchPreferenceCompat(screen.context).apply {
            key = USE_LOW_RES_IMG
            title = "Use low resolution images"
            summary = "Reduce load time significantly. When turning off, clear chapter cache to remove cached low resolution images."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    override fun getFilterList(): FilterList =
        FilterList(
            SortFilter("Sort by", Filter.Sort.Selection(0, false), getSortsList),
            TypeFilter("Types", getTypes),
            FavouritesFilter(),
        )

    open val getTypes: List<String> = emptyList()

    open val getSortsList: List<Pair<String, String>> = listOf(
        "Popularity" to "pop",
        "Date Indexed" to "new",
        "Date Updated" to "lat",
        "Alphabetical Order" to "tit",
        "Service" to "serv",
        "Date Favourited" to "fav",
    )

    internal open class TypeFilter(name: String, vals: List<String>) :
        Filter.Group<TriFilter>(name, vals.map { TriFilter(it, it.lowercase()) })

    internal class FavouritesFilter :
        Filter.Group<TriFilter>("Favourites", listOf(TriFilter("Favourites Only", "fav")))

    internal open class TriFilter(name: String, val value: String) : Filter.TriState(name)

    internal open class SortFilter(
        name: String,
        selection: Selection,
        private val vals: List<Pair<String, String>>,
    ) : Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
        fun getValue() = vals[state!!.index].second
    }

    companion object {
        private const val PAGE_POST_LIMIT = 50
        const val PROMPT = "You can change how many posts to load in the extension preferences."
        private const val POST_PAGES_PREF = "POST_PAGES"
        private const val POST_PAGES_DEFAULT = "1"
        private const val POST_PAGES_MAX = 75
        private const val USE_LOW_RES_IMG = "USE_LOW_RES_IMG"
    }
}
