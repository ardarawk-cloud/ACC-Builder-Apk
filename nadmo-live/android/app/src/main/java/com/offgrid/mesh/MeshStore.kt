package com.offgrid.mesh

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MeshStore(context: Context, private val localDeviceId: String) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    data class GroupConfig(val name: String, val groupId: String)

    data class Envelope(
        val id: String,
        val groupId: String,
        val senderId: String,
        val createdAt: Long,
        val expiresAt: Long,
        val hopCount: Int,
        val maxHops: Int,
        val cipherText: String
    )

    data class ReadableMessage(
        val id: String,
        val senderId: String,
        val text: String,
        val createdAt: Long,
        val hopCount: Int,
        val mine: Boolean,
        val syncedPeers: Int
    )

    data class ReceiveResult(
        val accepted: Boolean,
        val isNew: Boolean,
        val readable: ReadableMessage?
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE mesh_messages (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                hop_count INTEGER NOT NULL,
                max_hops INTEGER NOT NULL,
                cipher_text TEXT NOT NULL,
                received_from TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_mesh_group_time ON mesh_messages(group_id, created_at)")
        db.execSQL("CREATE INDEX idx_mesh_expiry ON mesh_messages(expires_at)")
        db.execSQL(
            """
            CREATE TABLE mesh_acks (
                message_id TEXT NOT NULL,
                peer_id TEXT NOT NULL,
                acked_at INTEGER NOT NULL,
                PRIMARY KEY(message_id, peer_id)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun currentGroup(): GroupConfig? {
        val name = prefs.getString(KEY_GROUP_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val groupId = prefs.getString(KEY_GROUP_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val key = prefs.getString(KEY_GROUP_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        if (runCatching { Base64.decode(key, Base64.NO_WRAP) }.getOrNull()?.size != 32) return null
        return GroupConfig(name, groupId)
    }

    fun configureGroup(name: String, code: String): GroupConfig? {
        val cleanName = name.trim().take(40)
        val cleanCode = code.trim()
        if (cleanName.isBlank() || cleanCode.length < 6) return null

        val normalized = cleanName.lowercase(Locale.ROOT)
        val groupDigest = sha256("$GROUP_CONTEXT|$normalized|$cleanCode".toByteArray(Charsets.UTF_8))
        val groupId = groupDigest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val salt = sha256("$SALT_CONTEXT|$groupId".toByteArray(Charsets.UTF_8)).copyOf(16)
        val spec = PBEKeySpec(cleanCode.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()

        prefs.edit()
            .putString(KEY_GROUP_NAME, cleanName)
            .putString(KEY_GROUP_ID, groupId)
            .putString(KEY_GROUP_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
            .apply()

        return GroupConfig(cleanName, groupId)
    }

    fun createMessage(text: String): Envelope? {
        val config = currentGroup() ?: return null
        val clean = text.trim()
        if (clean.isBlank() || clean.length > MAX_TEXT_LENGTH) return null
        val key = currentKey() ?: return null
        val now = System.currentTimeMillis()
        val envelope = Envelope(
            id = UUID.randomUUID().toString(),
            groupId = config.groupId,
            senderId = localDeviceId,
            createdAt = now,
            expiresAt = now + DEFAULT_TTL_MS,
            hopCount = 0,
            maxHops = DEFAULT_MAX_HOPS,
            cipherText = ""
        )
        val cipher = encrypt(key, clean.toByteArray(Charsets.UTF_8), aad(envelope))
        val complete = envelope.copy(cipherText = Base64.encodeToString(cipher, Base64.NO_WRAP))
        insertEnvelope(complete, null)
        cleanupExpired()
        return complete
    }

    fun receiveEnvelope(envelope: Envelope, fromPeerId: String): ReceiveResult {
        if (!validEnvelope(envelope)) return ReceiveResult(false, false, null)
        val now = System.currentTimeMillis()
        if (envelope.expiresAt <= now) return ReceiveResult(true, false, null)
        if (messageExists(envelope.id)) return ReceiveResult(true, false, null)

        val storedHop = (envelope.hopCount + 1).coerceAtMost(envelope.maxHops)
        val stored = envelope.copy(hopCount = storedHop)
        insertEnvelope(stored, fromPeerId)
        cleanupExpired()
        return ReceiveResult(true, true, decryptReadable(stored))
    }

    fun pendingForPeer(peerId: String, limit: Int = 12): List<Envelope> {
        cleanupExpired()
        val result = mutableListOf<Envelope>()
        val sql = """
            SELECT m.id, m.group_id, m.sender_id, m.created_at, m.expires_at,
                   m.hop_count, m.max_hops, m.cipher_text
            FROM mesh_messages m
            WHERE m.expires_at > ?
              AND m.hop_count < m.max_hops
              AND m.sender_id != ?
              AND (m.received_from IS NULL OR m.received_from != ?)
              AND NOT EXISTS (
                  SELECT 1 FROM mesh_acks a
                  WHERE a.message_id = m.id AND a.peer_id = ?
              )
            ORDER BY m.created_at ASC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(
            sql,
            arrayOf(
                System.currentTimeMillis().toString(),
                peerId,
                peerId,
                peerId,
                limit.coerceIn(1, 30).toString()
            )
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Envelope(
                    id = cursor.getString(0),
                    groupId = cursor.getString(1),
                    senderId = cursor.getString(2),
                    createdAt = cursor.getLong(3),
                    expiresAt = cursor.getLong(4),
                    hopCount = cursor.getInt(5),
                    maxHops = cursor.getInt(6),
                    cipherText = cursor.getString(7)
                )
            }
        }
        return result
    }

    fun markAck(peerId: String, messageId: String) {
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("peer_id", peerId)
            put("acked_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("mesh_acks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun readableMessages(limit: Int = 100): List<ReadableMessage> {
        val config = currentGroup() ?: return emptyList()
        cleanupExpired()
        val envelopes = mutableListOf<Envelope>()
        readableDatabase.query(
            "mesh_messages",
            arrayOf("id", "group_id", "sender_id", "created_at", "expires_at", "hop_count", "max_hops", "cipher_text"),
            "group_id = ?",
            arrayOf(config.groupId),
            null,
            null,
            "created_at DESC",
            limit.coerceIn(1, 200).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                envelopes += Envelope(
                    id = cursor.getString(0),
                    groupId = cursor.getString(1),
                    senderId = cursor.getString(2),
                    createdAt = cursor.getLong(3),
                    expiresAt = cursor.getLong(4),
                    hopCount = cursor.getInt(5),
                    maxHops = cursor.getInt(6),
                    cipherText = cursor.getString(7)
                )
            }
        }
        envelopes.reverse()
        return envelopes.mapNotNull { decryptReadable(it) }
    }

    fun queueCount(): Int {
        cleanupExpired()
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM mesh_messages WHERE expires_at > ? AND hop_count < max_hops",
            arrayOf(System.currentTimeMillis().toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun decryptReadable(envelope: Envelope): ReadableMessage? {
        val config = currentGroup() ?: return null
        if (config.groupId != envelope.groupId) return null
        val key = currentKey() ?: return null
        val encrypted = runCatching { Base64.decode(envelope.cipherText, Base64.NO_WRAP) }.getOrNull() ?: return null
        val plain = runCatching { decrypt(key, encrypted, aad(envelope)) }.getOrNull() ?: return null
        return ReadableMessage(
            id = envelope.id,
            senderId = envelope.senderId,
            text = plain.toString(Charsets.UTF_8),
            createdAt = envelope.createdAt,
            hopCount = envelope.hopCount,
            mine = envelope.senderId == localDeviceId,
            syncedPeers = ackCount(envelope.id)
        )
    }

    private fun insertEnvelope(envelope: Envelope, receivedFrom: String?) {
        val values = ContentValues().apply {
            put("id", envelope.id)
            put("group_id", envelope.groupId)
            put("sender_id", envelope.senderId)
            put("created_at", envelope.createdAt)
            put("expires_at", envelope.expiresAt)
            put("hop_count", envelope.hopCount)
            put("max_hops", envelope.maxHops)
            put("cipher_text", envelope.cipherText)
            if (receivedFrom == null) putNull("received_from") else put("received_from", receivedFrom)
        }
        writableDatabase.insertWithOnConflict("mesh_messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun messageExists(id: String): Boolean {
        readableDatabase.query(
            "mesh_messages",
            arrayOf("id"),
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1"
        ).use { cursor -> return cursor.moveToFirst() }
    }

    private fun ackCount(messageId: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM mesh_acks WHERE message_id = ?",
            arrayOf(messageId)
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    private fun cleanupExpired() {
        val cutoff = System.currentTimeMillis()
        writableDatabase.delete("mesh_messages", "expires_at <= ?", arrayOf(cutoff.toString()))
        writableDatabase.execSQL(
            "DELETE FROM mesh_acks WHERE message_id NOT IN (SELECT id FROM mesh_messages)"
        )
    }

    private fun currentKey(): SecretKeySpec? {
        val encoded = prefs.getString(KEY_GROUP_KEY, null) ?: return null
        val bytes = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (bytes.size != 32) return null
        return SecretKeySpec(bytes, "AES")
    }

    private fun validEnvelope(e: Envelope): Boolean {
        if (e.id.length !in 8..80) return false
        if (e.groupId.length !in 8..64) return false
        if (e.senderId.length !in 6..64) return false
        if (e.cipherText.length !in 20..3200) return false
        if (e.createdAt <= 0L || e.expiresAt <= e.createdAt) return false
        if (e.maxHops !in 1..12 || e.hopCount !in 0..e.maxHops) return false
        return true
    }

    private fun aad(e: Envelope): ByteArray =
        "$AAD_CONTEXT|${e.id}|${e.groupId}|${e.senderId}|${e.createdAt}|${e.expiresAt}|${e.maxHops}"
            .toByteArray(Charsets.UTF_8)

    private fun encrypt(key: SecretKeySpec, plain: ByteArray, aad: ByteArray): ByteArray {
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
    }

    private fun decrypt(key: SecretKeySpec, payload: ByteArray, aad: ByteArray): ByteArray {
        require(payload.size > 12)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(encrypted)
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    companion object {
        private const val DB_NAME = "offgrid-mesh.db"
        private const val DB_VERSION = 1
        private const val PREFS_NAME = "offgrid_mesh_group"
        private const val KEY_GROUP_NAME = "group_name"
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_GROUP_KEY = "group_key"
        private const val GROUP_CONTEXT = "OFFGRID-MESH-GROUP1"
        private const val SALT_CONTEXT = "OFFGRID-MESH-SALT1"
        private const val AAD_CONTEXT = "OFFGRID-MESH-AAD1"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val MAX_TEXT_LENGTH = 600
        private const val DEFAULT_MAX_HOPS = 6
        private const val DEFAULT_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
