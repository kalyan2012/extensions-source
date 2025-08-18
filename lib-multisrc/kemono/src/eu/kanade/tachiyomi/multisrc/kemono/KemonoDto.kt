package eu.kanade.tachiyomi.multisrc.kemono

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class KemonoFileDto(
    val name: String? = null,
    val path: String? = null,
)

@Serializable
data class KemonoAttachmentDto(
    val name: String? = null,
    val path: String? = null,
    val server: String? = null,
    val type: String? = null,
    val extension: String? = null,
)

@Serializable
data class KemonoPostDto(
    val id: String,
    val user: String,
    val service: String,
    val title: String? = null,
    @SerialName("content") val content: String? = null,
    val published: String? = null,
    val file: KemonoFileDto? = null,
    val attachments: List<KemonoAttachmentDto> = emptyList(),
    @SerialName("fav_count") val favCount: Int? = null,
) {
    val images: List<String>
        get() {
            val list = mutableListOf<String>()
            file?.path?.let { list.add(it) }
            attachments.forEach { att -> att.path?.let { list.add(it) } }
            return list
        }

    fun toSManga(imgCdnUrl: String): SManga {
        val manga = SManga.create().apply {
            title = this@KemonoPostDto.title ?: "Untitled"
            url = "/$service/user/$user/post/$id"
            thumbnail_url = when {
                file?.path != null -> "$imgCdnUrl/thumbnail/data${file.path}"
                attachments.isNotEmpty() -> "$imgCdnUrl/thumbnail/data${attachments[0].path}"
                else -> null
            }
            description = buildString {
                if (!content.isNullOrBlank()) appendLine(content)
                if (favCount != null) appendLine("❤️ Favorites: $favCount")
                if (!published.isNullOrBlank()) appendLine("📅 Published: $published")
            }.trim()
        }
        return manga
    }

    fun toSChapter(): SChapter {
        val chapter = SChapter.create()
        chapter.name = title ?: "Post $id"
        chapter.url = "/$service/user/$user/post/$id"
        chapter.date_upload = published.toDateMillis()
        return chapter
    }
}

@Serializable
data class PostsDto(
    val posts: List<KemonoPostDto> = emptyList(),
    val props: PropsDto? = null,
) {
    fun retrievePosts(): List<KemonoPostDto> = posts
    fun getCount(): Int = props?.count ?: posts.size
}

@Serializable
data class PopularDto(
    val posts: List<KemonoPostDto> = emptyList(),
    val props: PropsDto? = null,
) {
    fun retrievePosts(): List<KemonoPostDto> = posts
    fun getCount(): Int = props?.count ?: posts.size
}

@Serializable
data class PostDto(
    val post: KemonoPostDto,
    val attachments: List<KemonoAttachmentDto> = emptyList(),
    val previews: List<KemonoAttachmentDto> = emptyList(),
    val videos: List<KemonoAttachmentDto> = emptyList(),
) {
    fun getCurrentPost(): KemonoPostDto = post
}

@Serializable
data class PropsDto(
    val count: Int? = null,
)

/**
 * Helper extension to parse ISO date strings (e.g. "2025-08-18T00:00:00").
 */
private fun String?.toDateMillis(): Long {
    if (this.isNullOrBlank()) return 0L
    return try {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        df.parse(this)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}
