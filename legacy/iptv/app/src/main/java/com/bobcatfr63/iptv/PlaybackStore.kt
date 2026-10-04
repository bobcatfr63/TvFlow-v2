package com.bobcatfr63.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackRecord(
    val key: String,
    val title: String,
    val url: String,
    val type: ContentType,
    val categoryId: String,
    val rating: String?,
    val image: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    fun toPlayable() = Playable(title, url, type, key, categoryId, rating)
}

class PlaybackStore(context: Context, profileId: String = "adult") {
    private val prefs = context.getSharedPreferences("playback_history_v2", Context.MODE_PRIVATE)
    private val key = "records_$profileId"

    @Synchronized
    fun records(): List<PlaybackRecord> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val type = runCatching { ContentType.valueOf(o.optString("type")) }.getOrNull() ?: continue
                    add(
                        PlaybackRecord(
                            key = o.optString("key"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            type = type,
                            categoryId = o.optString("categoryId"),
                            rating = o.optString("rating").takeIf { it.isNotBlank() },
                            image = o.optString("image").takeIf { it.isNotBlank() },
                            positionMs = o.optLong("positionMs", 0L),
                            durationMs = o.optLong("durationMs", 0L),
                            updatedAt = o.optLong("updatedAt", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun recordStart(playable: Playable, image: String? = null) {
        val existing = records().firstOrNull { it.key == playable.contentKey }
        val updated = PlaybackRecord(
            key = playable.contentKey.ifBlank { playable.url },
            title = playable.title,
            url = playable.url,
            type = playable.type,
            categoryId = playable.categoryId,
            rating = playable.rating,
            image = image ?: existing?.image,
            positionMs = existing?.positionMs ?: 0L,
            durationMs = existing?.durationMs ?: 0L,
            updatedAt = System.currentTimeMillis()
        )
        write(listOf(updated) + records().filterNot { it.key == updated.key })
    }

    @Synchronized
    fun saveProgress(playable: Playable, positionMs: Long, durationMs: Long) {
        val recordKey = playable.contentKey.ifBlank { playable.url }
        val old = records().firstOrNull { it.key == recordKey }
        val updated = PlaybackRecord(
            key = recordKey,
            title = playable.title,
            url = playable.url,
            type = playable.type,
            categoryId = playable.categoryId,
            rating = playable.rating,
            image = old?.image,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis()
        )
        write(listOf(updated) + records().filterNot { it.key == recordKey })
    }

    fun resumePosition(playable: Playable): Long {
        val recordKey = playable.contentKey.ifBlank { playable.url }
        val r = records().firstOrNull { it.key == recordKey } ?: return 0L
        if (r.positionMs < 30_000L || r.durationMs <= 0L) return 0L
        if (r.progress >= 0.95f) return 0L
        return r.positionMs
    }

    @Synchronized
    fun clearFinished() {
        write(records().filterNot { it.durationMs > 0L && it.progress >= 0.95f })
    }

    private fun write(records: List<PlaybackRecord>) {
        val array = JSONArray()
        records.take(80).forEach { r ->
            array.put(JSONObject().apply {
                put("key", r.key)
                put("title", r.title)
                put("url", r.url)
                put("type", r.type.name)
                put("categoryId", r.categoryId)
                put("rating", r.rating ?: "")
                put("image", r.image ?: "")
                put("positionMs", r.positionMs)
                put("durationMs", r.durationMs)
                put("updatedAt", r.updatedAt)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
