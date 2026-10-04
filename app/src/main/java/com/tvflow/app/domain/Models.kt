package com.tvflow.app.domain

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val outputFormat: String = "ts"
)

data class ServerProfile(
    val id: String,
    val name: String,
    val credentials: XtreamCredentials
)

data class ConnectionProbe(
    val label: String,
    val ok: Boolean,
    val statusCode: Int? = null,
    val message: String
)

data class ConnectionDiagnostics(
    val api: ConnectionProbe,
    val live: ConnectionProbe,
    val vod: ConnectionProbe
) {
    val ipLikelyBlocked: Boolean
        get() = listOf(api, live, vod).any { it.statusCode == 403 }
}

data class LiveSubFilter(
    val id: String,
    val name: String
)

data class LiveCategory(
    val id: String,
    val name: String,
    val order: Int = 0,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val subFilters: List<LiveSubFilter> = emptyList()
)

enum class StreamHealth { UNKNOWN, CHECKING, ONLINE, UNSTABLE, OFFLINE, UNPLAYABLE }

data class LiveChannel(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val logoUrl: String?,
    val epgChannelId: String?,
    val streamType: String = "live",
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long? = null,
    val isHidden: Boolean = false,
    val customOrder: Int? = null,
    val health: StreamHealth = StreamHealth.UNKNOWN,
    val healthCheckedAt: Long? = null,
    val healthFailureCount: Int = 0,
    val lastHttpCode: Int? = null,
    val lastPlaybackError: String? = null,
    val logicalNumber: Int? = null,
    val tvArchive: Boolean = false,
    val tvArchiveDuration: Int = 0
) {
    val isSectionMarker: Boolean
        get() = name.trim().matches(Regex("^#{3,}\\s*.+?\\s*#{3,}$"))

    val sectionTitle: String?
        get() = if (isSectionMarker) name.trim().trim('#').trim().ifBlank { null } else null
}

data class EpgProgram(
    val streamId: Int,
    val startTimestamp: Long,
    val stopTimestamp: Long,
    val title: String,
    val description: String?,
    val hasArchive: Boolean = false
) {
    fun isLiveAt(nowSeconds: Long): Boolean = nowSeconds in startTimestamp until stopTimestamp
}
