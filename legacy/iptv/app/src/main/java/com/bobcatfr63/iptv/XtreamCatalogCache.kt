package com.bobcatfr63.iptv

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class XtreamCatalogCache(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "xtream_catalog_cache.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE api_cache (
                cache_key TEXT PRIMARY KEY,
                action TEXT NOT NULL,
                body TEXT NOT NULL,
                saved_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_api_cache_action ON api_cache(action)")
        db.execSQL("CREATE INDEX idx_api_cache_saved_at ON api_cache(saved_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun get(key: String, maxAgeMs: Long, allowStale: Boolean = false): String? {
        val now = System.currentTimeMillis()
        readableDatabase.query(
            "api_cache",
            arrayOf("body", "saved_at"),
            "cache_key = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val body = cursor.getString(0)
            val savedAt = cursor.getLong(1)
            return if (allowStale || now - savedAt <= maxAgeMs) body else null
        }
    }

    fun put(key: String, action: String, body: String) {
        writableDatabase.execSQL(
            """
            INSERT INTO api_cache(cache_key, action, body, saved_at)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(cache_key) DO UPDATE SET
                action = excluded.action,
                body = excluded.body,
                saved_at = excluded.saved_at
            """.trimIndent(),
            arrayOf(key, action, body, System.currentTimeMillis())
        )
        prune()
    }

    fun clearAction(action: String) {
        writableDatabase.delete("api_cache", "action = ?", arrayOf(action))
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        writableDatabase.delete("api_cache", "saved_at < ?", arrayOf(cutoff.toString()))
    }

    companion object {
        fun key(server: String, username: String, action: String, extra: Pair<String, String>?): String {
            val raw = buildString {
                append(server.trim().lowercase())
                append('|').append(username)
                append('|').append(action)
                if (extra != null) append('|').append(extra.first).append('=').append(extra.second)
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
