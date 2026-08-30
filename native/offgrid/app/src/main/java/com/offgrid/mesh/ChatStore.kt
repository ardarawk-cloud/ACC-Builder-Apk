package com.offgrid.mesh

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                peer_id TEXT NOT NULL,
                mine INTEGER NOT NULL,
                text TEXT NOT NULL,
                delivered INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_peer_time ON messages(peer_id, created_at)")
        db.execSQL(
            """
            CREATE TABLE peers (
                peer_id TEXT PRIMARY KEY,
                address TEXT,
                last_seen INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_peers_address ON peers(address)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun saveMessage(message: StoredMessage) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("peer_id", message.peerId)
            put("mine", if (message.mine) 1 else 0)
            put("text", message.text)
            put("delivered", if (message.delivered) 1 else 0)
            put("created_at", message.createdAt)
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun markDelivered(id: String) {
        val values = ContentValues().apply { put("delivered", 1) }
        writableDatabase.update("messages", values, "id = ?", arrayOf(id))
    }

    fun loadMessages(peerId: String, limit: Int = 200): List<StoredMessage> {
        val result = mutableListOf<StoredMessage>()
        readableDatabase.query(
            "messages",
            arrayOf("id", "peer_id", "mine", "text", "delivered", "created_at"),
            "peer_id = ?",
            arrayOf(peerId),
            null,
            null,
            "created_at DESC",
            limit.coerceIn(1, 500).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += StoredMessage(
                    id = cursor.getString(0),
                    peerId = cursor.getString(1),
                    mine = cursor.getInt(2) == 1,
                    text = cursor.getString(3),
                    delivered = cursor.getInt(4) == 1,
                    createdAt = cursor.getLong(5)
                )
            }
        }
        result.reverse()
        return result
    }

    fun rememberPeer(peerId: String, address: String?) {
        val values = ContentValues().apply {
            put("peer_id", peerId)
            put("address", address)
            put("last_seen", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("peers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun peerIdForAddress(address: String): String? {
        readableDatabase.query(
            "peers",
            arrayOf("peer_id"),
            "address = ?",
            arrayOf(address),
            null,
            null,
            "last_seen DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    data class StoredMessage(
        val id: String,
        val peerId: String,
        val mine: Boolean,
        val text: String,
        val delivered: Boolean,
        val createdAt: Long
    )

    companion object {
        private const val DB_NAME = "offgrid-chat.db"
        private const val DB_VERSION = 1
    }
}
