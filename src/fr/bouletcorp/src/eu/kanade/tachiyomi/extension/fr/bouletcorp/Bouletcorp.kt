package eu.kanade.tachiyomi.extension.fr.bouletcorp

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Bouletcorp : HttpSource() {

    override val name = "Bouletcorp"
    override val baseUrl = "https://www.bouletcorp.com"
    override val lang = "fr"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH)

    // -- Popular (single manga) --

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage = MangasPage(listOf(createManga()), false)

    // -- Latest (unsupported, single manga) --

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // -- Search --

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(baseUrl, headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // -- Manga Details --

    override fun getMangaUrl(manga: SManga): String = baseUrl

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl, headers)

    override fun mangaDetailsParse(response: Response): SManga = createManga()

    // -- Chapter List --

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/archives", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        document.select("li a[href]").forEach { element ->
            val href = element.attr("href")
            if (!href.startsWith("/rogatons/") && !href.startsWith("/notes/")) return@forEach

            val title = element.text().trim()
            val date = extractDate(href)

            chapters.add(
                SChapter.create().apply {
                    name = title.replace(Regex("\\s+\\d{4}-\\d{2}-\\d{2}$"), "")
                    setUrlWithoutDomain(href)
                    date_upload = date
                    chapter_number = -1f
                },
            )
        }

        return chapters
    }

    // -- Pages --

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        // Main comic images
        document.select("#comic-strip .img-container img").forEachIndexed { index, img ->
            val imageUrl = img.absUrl("src")
            if (imageUrl.isNotBlank()) {
                pages.add(Page(index, imageUrl = imageUrl))
            }
        }

        // Bonus image (inside <details> element)
        document.select("details img").forEach { img ->
            val imageUrl = img.absUrl("src")
            if (imageUrl.isNotBlank()) {
                pages.add(Page(pages.size, imageUrl = imageUrl))
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // -- Helpers --

    private fun createManga(): SManga = SManga.create().apply {
        title = "Bouletcorp"
        url = "/"
        artist = "Boulet"
        author = "Boulet"
        description = "Le blog BD de Boulet"
        status = SManga.ONGOING
        thumbnail_url = "https://cdna.artstation.com/p/users/avatars/000/616/756/large/95f8699bc6b48e40fa766ee74e275721.jpg"
    }

    private fun extractDate(path: String): Long {
        val match = Regex("/(\\d{4})/(\\d{2})/(\\d{2})$").find(path) ?: return 0L
        val (year, month, day) = match.destructured
        return try {
            dateFormat.parse("$year-$month-$day")?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
