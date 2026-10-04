package com.bobcatfr63.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class ProfileKind { CHILD, TEEN, ADULT, CUSTOM }

data class ViewerProfile(val id: String, val name: String, val kind: ProfileKind) {
    fun allowsCategory(categoryName: String): Boolean {
        val value = categoryName.lowercase(Locale.ROOT)
        val adultWords = listOf("adult", "adulte", "xxx", "18+", "porn", "érot", "erot")
        if (adultWords.any(value::contains)) return kind == ProfileKind.ADULT
        return when (kind) {
            ProfileKind.CHILD -> listOf(
                "enfant", "jeunesse", "kids", "kid", "junior", "family", "famille",
                "animation", "animé", "anime", "cartoon", "disney"
            ).any(value::contains)
            ProfileKind.TEEN -> true
            ProfileKind.ADULT, ProfileKind.CUSTOM -> true
        }
    }
}

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("viewer_profiles_v1", Context.MODE_PRIVATE)

    fun profiles(): List<ViewerProfile> {
        prefs.edit().remove("pin_hash").apply()
        val raw = prefs.getString("profiles", null)
        if (raw.isNullOrBlank()) return defaults().also(::save)
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(ViewerProfile(item.getString("id"), item.getString("name"), ProfileKind.valueOf(item.getString("kind"))))
                }
            }
        }.getOrElse { defaults().also(::save) }
    }

    fun add(name: String): ViewerProfile {
        val profile = ViewerProfile("custom_${UUID.randomUUID()}", name.trim(), ProfileKind.CUSTOM)
        save(profiles() + profile)
        return profile
    }

    fun active(): ViewerProfile = profiles().firstOrNull { it.id == prefs.getString("active", null) }
        ?: profiles().first { it.kind == ProfileKind.ADULT }

    fun select(profile: ViewerProfile) = prefs.edit().putString("active", profile.id).apply()

    private fun save(profiles: List<ViewerProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind.name)) }
        prefs.edit().putString("profiles", array.toString()).apply()
    }

    private fun defaults() = listOf(
        ViewerProfile("child", "Enfant", ProfileKind.CHILD),
        ViewerProfile("teen", "Ado", ProfileKind.TEEN),
        ViewerProfile("adult", "Adulte", ProfileKind.ADULT)
    )
}
