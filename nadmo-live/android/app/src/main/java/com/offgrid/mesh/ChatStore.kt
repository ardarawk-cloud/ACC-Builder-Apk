package com.offgrid.mesh

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    enum class IdentityState { NEW, MATCH, CHANGED }

    data class PeerIdentityCheck(
        val state: IdentityState,
        val verified: Boolean,
        val previousFingerprint: String?
    )

    data class StoredPeer(
        val peerId: String,
        val address: String?,
        val lastSeen: Long,
        val identityFingerprint: String?,
        val verified: Boolean
    )

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
                last_seen INTEGER NOT NULL,
                identity_fingerprint TEXT,
                verified INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_peers_address ON peers(address)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE peers ADD COLUMN identity_fingerprint TEXT")
            db.execSQL("ALTER TABLE peers ADD COLUMN verified INTEGER NOT NULL DEFAULT 0")
        }
    }

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

    fun observePeerIdentity(peerId: String, address: String?, fingerprint: String): PeerIdentityCheck {
        val existing = peerById(peerId)
        val now = System.currentTimeMillis()

        if (existing == null) {
            val values = ContentValues().apply {
                put("peer_id", peerId)
                put("address", address)
                put("last_seen", now)
                put("identity_fingerprint", fingerprint)
                put("verified", 0)
            }
            writableDatabase.insertWithOnConflict("peers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            return PeerIdentityCheck(IdentityState.NEW, false, null)
        }

        val previous = existing.identityFingerprint
        if (previous != null && previous != fingerprint) {
            writableDatabase.update(
                "peers",
                ContentValues().apply { put("last_seen", now) },
                "peer_id = ?",
                arrayOf(peerId)
            )
            return PeerIdentityCheck(IdentityState.CHANGED, false, previous)
        }

        val values = ContentValues().apply {
            put("address", address)
            put("last_seen", now)
            if (previous == null) {
                put("identity_fingerprint", fingerprint)
                put("verified", 0)
            }
        }
        writableDatabase.update("peers", values, "peer_id = ?", arrayOf(peerId))
        return PeerIdentityCheck(IdentityState.MATCH, if (previous == null) false else existing.verified, previous)
    }

    fun markPeerVerified(peerId: String, fingerprint: String): Boolean {
        val values = ContentValues().apply { put("verified", 1) }
        return writableDatabase.update(
            "peers",
            values,
            "peer_id = ? AND identity_fingerprint = ?",
            arrayOf(peerId, fingerprint)
        ) > 0
    }

    fun peerForAddress(address: String): StoredPeer? {
        readableDatabase.query(
            "peers",
            arrayOf("peer_id", "address", "last_seen", "identity_fingerprint", "verified"),
            "address = ?",
            arrayOf(address),
            null,
            null,
            "last_seen DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) peerFromCursor(cursor) else null
        }
    }

    fun peerIdForAddress(address: String): String? = peerForAddress(address)?.peerId

    private fun peerById(peerId: String): StoredPeer? {
        readableDatabase.query(
            "peers",
            arrayOf("peer_id", "address", "last_seen", "identity_fingerprint", "verified"),
            "peer_id = ?",
            arrayOf(peerId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) peerFromCursor(cursor) else null
        }
    }

    private fun peerFromCursor(cursor: android.database.Cursor): StoredPeer {
        return StoredPeer(
            peerId = cursor.getString(0),
            address = if (cursor.isNull(1)) null else cursor.getString(1),
            lastSeen = cursor.getLong(2),
            identityFingerprint = if (cursor.isNull(3)) null else cursor.getString(3),
            verified = cursor.getInt(4) == 1
        )
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
        private const val DB_VERSION = 2
    }
}
