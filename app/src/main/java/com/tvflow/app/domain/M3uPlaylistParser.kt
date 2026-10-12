package com.tvflow.app.domain

/** A station from an extended M3U playlist (not an HLS media manifest). */
data class M3uStation(
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val tvgId: String?
)

/** Parses #EXTINF entries, ignoring unsupported or malformed stations. */
object M3uPlaylistParser {
    private val attribute = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(playlist: String): List<M3uStation> {
        require(playlist.trimStart('\uFEFF', ' ', '\n', '\r').startsWith("#EXTM3U", true)) {
            "Le fichier sélectionné n'est pas une playlist M3U."
        }
        val result = ArrayList<M3uStation>()
        var attributes: Map<String, String>? = null
        var pendingName: String? = null
        for (raw in playlist.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:", true) -> {
                    attributes = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pendingName = line.substringAfterLast(',', "").trim()
                }
                line.isBlank() || line.startsWith("#") -> Unit
                attributes != null -> {
                    if ((line.startsWith("http://") || line.startsWith("https://")) && result.size < 40_000) {
                        val attr = attributes
                        val title = pendingName?.takeIf { it.isNotBlank() } ?: attr["tvg-name"] ?: "Chaîne"
                        result += M3uStation(title, line, attr["group-title"].orEmpty().ifBlank { "Autres chaînes" },
                            attr["tvg-logo"]?.takeIf { it.isNotBlank() }, attr["tvg-id"]?.takeIf { it.isNotBlank() })
                    }
                    attributes = null
                    pendingName = null
                }
            }
        }
        require(result.isNotEmpty()) { "La playlist M3U ne contient aucune chaîne HTTP valide." }
        return result
    }
}
