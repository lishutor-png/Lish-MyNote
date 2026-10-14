package com.example.util

import android.util.Base64
import com.example.data.model.BackupData
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DecodedResult(
    val success: Boolean,
    val description: String,
    val notes: List<NoteEntity>,
    val categories: List<CategoryEntity>,
    val rawTextPreview: String = "",
    val detectedMethod: String = "",
    val requiresPassword: Boolean = false
)

object SmartTextDecoder {

    private val DEFAULT_PASSWORDS = listOf(
        "Lish MyNote",
        "LishMyNote",
        "mylish",
        "Mylish",
        "MylishNote",
        "lish",
        "note",
        "notes",
        "secret",
        "password",
        "123456",
        "admin",
        "lutfianshori81@guru.smk.belajar.id"
    )

    /**
     * Attempts to decode and parse any input string (.txt or .json content).
     * Handles:
     * 1. Plain Lish MyNote JSON
     * 2. JSON wrapper with {"type":"secure_export", "payload":"..."} or {"payload":"..."}
     * 3. Base64 encoded JSON / text / ciphertext
     * 4. AES Decryption with MD5 key derivation (ECB, CBC with IV, OpenSSL Salted__)
     * 5. MD5 XOR obfuscation
     * 6. Hex encoded text / bytes
     * 7. Plain text / Markdown notes (single note or multi-note separated by dividers)
     */
    fun decodeAndParse(input: String, userPassword: String? = null): DecodedResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return DecodedResult(
                success = false,
                description = "File atau teks kosong",
                notes = emptyList(),
                categories = emptyList()
            )
        }

        val passwordsToTry = mutableListOf<String>()
        if (!userPassword.isNullOrBlank()) {
            passwordsToTry.add(userPassword.trim())
        }
        for (p in DEFAULT_PASSWORDS) {
            if (!passwordsToTry.contains(p)) {
                passwordsToTry.add(p)
            }
        }

        // 1. Check if it's direct JSON first
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonResult = tryParseJson(trimmed, passwordsToTry)
            if (jsonResult != null) {
                return jsonResult
            }
        }

        // 2. Try Base64 decoding (only if it matches strict Base64 format without spaces between words)
        val b64Decoded = tryBase64Decode(trimmed)
        if (b64Decoded != null) {
            val fromB64 = tryProcessBytes(b64Decoded, "Base64", passwordsToTry, allowPlainText = false)
            if (fromB64 != null) {
                return fromB64
            }
            // If base64 decoded directly to clean UTF8 plain text
            val b64Utf8 = tryDecodeUtf8Strict(b64Decoded)
            if (b64Utf8 != null && isLikelyReadableText(b64Utf8)) {
                val textRes = parsePlainTextAsNotes(b64Utf8)
                if (textRes.success && textRes.notes.isNotEmpty()) {
                    return textRes.copy(
                        description = "Berhasil membaca Base64 -> Dokumen (${textRes.notes.size} catatan)",
                        detectedMethod = "Base64 -> Teks"
                    )
                }
            }
        }

        // 3. Try Hex decoding (if input is clean hex digits)
        val hexDecoded = tryHexDecode(trimmed)
        if (hexDecoded != null) {
            val fromHex = tryProcessBytes(hexDecoded, "Hex", passwordsToTry, allowPlainText = false)
            if (fromHex != null) {
                return fromHex
            }
        }

        // 4. If it starts with OpenSSL "Salted__" header as raw string
        if (trimmed.startsWith("Salted__")) {
            val rawBytes = trimmed.toByteArray(StandardCharsets.ISO_8859_1)
            val fromRaw = tryProcessBytes(rawBytes, "OpenSSL Salted", passwordsToTry, allowPlainText = true)
            if (fromRaw != null) {
                return fromRaw
            }
        }

        // 5. If user explicitly provided a password, try decrypting raw bytes
        if (!userPassword.isNullOrBlank()) {
            val rawBytes = trimmed.toByteArray(StandardCharsets.UTF_8)
            val fromRaw = tryProcessBytes(rawBytes, "Teks Terenkripsi", listOf(userPassword.trim()), allowPlainText = true)
            if (fromRaw != null) {
                return fromRaw
            }
        }

        // 6. Default / Fallback: Parse as Plain Text Notes (.txt document, markdown chapters, or notes)
        return parsePlainTextAsNotes(trimmed)
    }

    private fun tryParseJson(jsonString: String, passwordsToTry: List<String>): DecodedResult? {
        return try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)

                // Check for secure export / encrypted payload wrapper
                val payloadKeys = listOf("payload", "secure_export", "data", "ciphertext", "encrypted_data", "isi_enkripsi")
                for (key in payloadKeys) {
                    if (root.has(key)) {
                        val payloadStr = root.optString(key)
                        if (payloadStr.isNotBlank()) {
                            val decoded = decodeAndParse(payloadStr, passwordsToTry.firstOrNull())
                            if (decoded.success && decoded.notes.isNotEmpty()) {
                                return decoded.copy(
                                    description = "Dekripsi berhasil dari paket aman (${decoded.notes.size} catatan)",
                                    detectedMethod = "JSON Payload Enkripsi"
                                )
                            }
                        }
                    }
                }

                // Standard BackupData format
                val backup = BackupData.fromJsonString(trimmed)
                if (backup.notes.isNotEmpty() || backup.categories.isNotEmpty()) {
                    return DecodedResult(
                        success = true,
                        description = "Cadangan JSON (${backup.notes.size} Catatan, ${backup.categories.size} Kategori)",
                        notes = backup.notes,
                        categories = backup.categories,
                        rawTextPreview = if (backup.notes.isNotEmpty()) backup.notes[0].content.take(150) else "",
                        detectedMethod = "Format JSON Cadangan"
                    )
                }

                // Generic single note object {"title": "...", "content": "..."}
                val note = parseSingleJsonNote(root)
                if (note != null) {
                    return DecodedResult(
                        success = true,
                        description = "Catatan Tunggal JSON",
                        notes = listOf(note),
                        categories = listOf(CategoryEntity(name = note.category, colorHex = "#2563EB", isDefault = false)),
                        rawTextPreview = note.content.take(150),
                        detectedMethod = "Objek JSON"
                    )
                }
            } else if (trimmed.startsWith("[")) {
                // Array of notes or items
                val array = JSONArray(trimmed)
                val notes = mutableListOf<NoteEntity>()
                val categories = mutableMapOf<String, CategoryEntity>()

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i)
                    if (item != null) {
                        val note = parseSingleJsonNote(item)
                        if (note != null) {
                            notes.add(note)
                            if (!categories.containsKey(note.category)) {
                                categories[note.category] = CategoryEntity(name = note.category, colorHex = "#2563EB", isDefault = false)
                            }
                        }
                    } else {
                        // Array of strings
                        val str = array.optString(i)
                        if (str.isNotBlank()) {
                            val n = createNote(str, "Umum")
                            notes.add(n)
                            if (!categories.containsKey("Umum")) {
                                categories["Umum"] = CategoryEntity(name = "Umum", colorHex = "#2563EB", isDefault = false)
                            }
                        }
                    }
                }

                if (notes.isNotEmpty()) {
                    return DecodedResult(
                        success = true,
                        description = "Daftar Catatan JSON (${notes.size} catatan)",
                        notes = notes,
                        categories = categories.values.toList(),
                        rawTextPreview = notes[0].content.take(150),
                        detectedMethod = "Array JSON"
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSingleJsonNote(obj: JSONObject): NoteEntity? {
        val content = when {
            obj.has("content") -> obj.optString("content")
            obj.has("isi") -> obj.optString("isi")
            obj.has("body") -> obj.optString("body")
            obj.has("text") -> obj.optString("text")
            obj.has("description") -> obj.optString("description")
            else -> ""
        }
        val title = when {
            obj.has("title") -> obj.optString("title")
            obj.has("judul") -> obj.optString("judul")
            else -> ""
        }
        val fullContent = if (title.isNotBlank() && !content.startsWith(title)) {
            if (content.isNotBlank()) "$title\n$content" else title
        } else {
            content.ifBlank { title }
        }

        if (fullContent.isBlank()) return null

        val category = obj.optString("category", obj.optString("kategori", "Umum")).ifBlank { "Umum" }
        val now = System.currentTimeMillis()
        val createdAt = obj.optLong("createdAt", obj.optLong("created_at", now))
        val updatedAt = obj.optLong("updatedAt", obj.optLong("updated_at", now))
        val isHidden = obj.optBoolean("isHidden", obj.optBoolean("is_hidden", false))

        return NoteEntity(
            id = 0L,
            content = fullContent,
            category = category,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isHidden = isHidden
        )
    }

    private fun tryProcessBytes(
        bytes: ByteArray,
        sourceDesc: String,
        passwordsToTry: List<String>,
        allowPlainText: Boolean
    ): DecodedResult? {
        // A. Is it GZIP compressed?
        val decompressed = tryDecompressGzip(bytes)
        val activeBytes = decompressed ?: bytes

        // B. Try interpreting directly as UTF-8 string
        val utf8String = tryDecodeUtf8Strict(activeBytes)
        if (utf8String != null && isLikelyReadableText(utf8String)) {
            val trimmed = utf8String.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val res = tryParseJson(trimmed, passwordsToTry)
                if (res != null) {
                    return res.copy(
                        description = "Berhasil membaca $sourceDesc -> JSON (${res.notes.size} catatan)",
                        detectedMethod = "$sourceDesc -> JSON"
                    )
                }
            }
        }

        // C. Try MD5-derived AES Decryption
        for (pass in passwordsToTry) {
            val decrypted = tryDecryptAesMd5(activeBytes, pass)
            if (decrypted != null) {
                val decString = tryDecodeUtf8Strict(decrypted)
                if (decString != null && isLikelyReadableText(decString)) {
                    val trimmed = decString.trim()
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        val res = tryParseJson(trimmed, passwordsToTry)
                        if (res != null) {
                            return res.copy(
                                description = "Berhasil didekripsi (AES-MD5) -> ${res.notes.size} catatan",
                                detectedMethod = "AES-MD5 ($sourceDesc)"
                            )
                        }
                    }
                    val textRes = parsePlainTextAsNotes(trimmed)
                    if (textRes.success && textRes.notes.isNotEmpty()) {
                        return textRes.copy(
                            description = "Berhasil didekripsi (AES-MD5) -> ${textRes.notes.size} catatan teks",
                            detectedMethod = "AES-MD5 ($sourceDesc)"
                        )
                    }
                }
            }
        }

        // D. Try MD5 XOR Decryption
        for (pass in passwordsToTry) {
            val xorDecrypted = tryXorMd5(activeBytes, pass)
            val decString = tryDecodeUtf8Strict(xorDecrypted)
            if (decString != null && isLikelyReadableText(decString)) {
                val trimmed = decString.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    val res = tryParseJson(trimmed, passwordsToTry)
                    if (res != null) {
                        return res.copy(
                            description = "Berhasil didekripsi (XOR-MD5) -> ${res.notes.size} catatan",
                            detectedMethod = "XOR-MD5"
                        )
                    }
                }
                if (allowPlainText && hasNoteIndicators(trimmed)) {
                    val textRes = parsePlainTextAsNotes(trimmed)
                    if (textRes.success && textRes.notes.isNotEmpty()) {
                        return textRes.copy(
                            description = "Berhasil didekripsi (XOR-MD5) -> ${textRes.notes.size} catatan teks",
                            detectedMethod = "XOR-MD5"
                        )
                    }
                }
            }
        }

        return null
    }

    private fun tryDecryptAesMd5(bytes: ByteArray, pass: String): ByteArray? {
        val md5Key = md5(pass)
        val keySpec = SecretKeySpec(md5Key, "AES")

        // 1. Check OpenSSL format "Salted__"
        if (bytes.size > 16 && bytes.take(8).toByteArray().contentEquals("Salted__".toByteArray(StandardCharsets.US_ASCII))) {
            try {
                val salt = bytes.copyOfRange(8, 16)
                val ciphertext = bytes.copyOfRange(16, bytes.size)
                val (derivedKey, iv) = evpBytesToKey(pass, salt, 16, 16)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(iv))
                return cipher.doFinal(ciphertext)
            } catch (_: Exception) {}
        }

        // 2. Try AES/ECB/PKCS5Padding with MD5(pass) directly
        try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            return cipher.doFinal(bytes)
        } catch (_: Exception) {}

        // 3. Try AES/CBC with MD5(pass) as key and first 16 bytes as IV
        if (bytes.size > 16) {
            try {
                val iv = bytes.copyOfRange(0, 16)
                val ciphertext = bytes.copyOfRange(16, bytes.size)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                return cipher.doFinal(ciphertext)
            } catch (_: Exception) {}
        }

        // 4. Try AES/CBC with zero IV
        try {
            val iv = ByteArray(16) { 0 }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            return cipher.doFinal(bytes)
        } catch (_: Exception) {}

        return null
    }

    private fun tryXorMd5(bytes: ByteArray, pass: String): ByteArray {
        val md5Key = md5(pass)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            out[i] = (bytes[i].toInt() xor md5Key[i % md5Key.size].toInt()).toByte()
        }
        return out
    }

    fun md5(input: String): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(StandardCharsets.UTF_8))
    }

    private fun evpBytesToKey(password: String, salt: ByteArray, keyLen: Int, ivLen: Int): Pair<ByteArray, ByteArray> {
        val md = MessageDigest.getInstance("MD5")
        val passBytes = password.toByteArray(StandardCharsets.UTF_8)
        var both = ByteArray(0)
        var d = ByteArray(0)
        while (both.size < keyLen + ivLen) {
            md.reset()
            if (d.isNotEmpty()) md.update(d)
            md.update(passBytes)
            md.update(salt)
            d = md.digest()
            both += d
        }
        val key = both.copyOfRange(0, keyLen)
        val iv = both.copyOfRange(keyLen, keyLen + ivLen)
        return Pair(key, iv)
    }

    private fun tryBase64Decode(str: String): ByteArray? {
        val trimmed = str.trim()
        val noWhitespace = trimmed.filterNot { it.isWhitespace() }
        if (noWhitespace.length < 8) return null

        // If string contains multiple distinct words with spaces (not base64 chunks), skip
        if (trimmed.contains(" ") && trimmed.split(" ").size > 3) {
            return null
        }

        // Base64 only contains A-Z, a-z, 0-9, +, /, =, and URL safe -, _
        val isBase64CharsOnly = noWhitespace.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' || it == '-' || it == '_'
        }
        if (!isBase64CharsOnly) return null

        return try {
            Base64.decode(noWhitespace, Base64.NO_WRAP)
        } catch (_: Exception) {
            try {
                Base64.decode(noWhitespace, Base64.URL_SAFE)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun tryHexDecode(str: String): ByteArray? {
        val cleaned = str.filterNot { it.isWhitespace() }
        if (cleaned.length < 8 || cleaned.length % 2 != 0) return null
        if (!cleaned.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return try {
            val len = cleaned.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(cleaned[i], 16) shl 4) + Character.digit(cleaned[i + 1], 16)).toByte()
                i += 2
            }
            data
        } catch (_: Exception) {
            null
        }
    }

    private fun tryDecompressGzip(bytes: ByteArray): ByteArray? {
        if (bytes.size < 2) return null
        // GZIP magic header: 0x1f, 0x8b
        if (bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return try {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun tryDecodeUtf8Strict(bytes: ByteArray): String? {
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
            decoder.onMalformedInput(CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            charBuffer.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isLikelyReadableText(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.contains('\uFFFD')) return false
        var readable = 0
        for (ch in text) {
            if (ch.isLetterOrDigit() || ch.isWhitespace() || ch in ",.!?;:'\"-_()[]{}/@#%&*+=<>~|\\") {
                readable++
            } else if (ch.code < 32 && ch != '\n' && ch != '\r' && ch != '\t') {
                return false // Binary control char
            }
        }
        return (readable.toDouble() / text.length) >= 0.80
    }

    private fun hasNoteIndicators(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("catatan") ||
                lower.contains("note") ||
                lower.contains("judul") ||
                lower.contains("title") ||
                lower.contains("bab ") ||
                lower.contains("chapter") ||
                text.contains("---") ||
                text.contains("===") ||
                text.lines().size > 1
    }

    /**
     * Parses plain text notes from a .txt file.
     * Supports:
     * - Multi-note text separated by "---", "===", "###", or empty lines
     * - Single note with title on the first line
     */
    fun parsePlainTextAsNotes(text: String): DecodedResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return DecodedResult(
                success = false,
                description = "Teks kosong",
                notes = emptyList(),
                categories = emptyList()
            )
        }

        val lines = trimmed.lines()
        val notes = mutableListOf<NoteEntity>()
        val categories = mutableMapOf<String, CategoryEntity>()

        // Check if there are explicit note dividers
        val dividerRegex = Regex("^[-=_*]{3,}$|^---+|^===+|^\\[Catatan\\s*\\d*\\]|^##\\s+")
        val hasDividers = lines.count { dividerRegex.containsMatchIn(it.trim()) } >= 1

        if (hasDividers) {
            var currentBuffer = StringBuilder()
            var currentCategory = "Umum"

            for (line in lines) {
                val tLine = line.trim()
                if (dividerRegex.containsMatchIn(tLine)) {
                    val content = currentBuffer.toString().trim()
                    if (content.isNotBlank()) {
                        notes.add(createNote(content, currentCategory))
                        currentBuffer = StringBuilder()
                    }
                    if (tLine.contains("Kategori:", ignoreCase = true)) {
                        currentCategory = tLine.substringAfter("Kategori:").trim()
                    }
                } else {
                    currentBuffer.appendLine(line)
                }
            }

            val remaining = currentBuffer.toString().trim()
            if (remaining.isNotBlank()) {
                notes.add(createNote(remaining, currentCategory))
            }
        } else {
            // Check if separated by double blank lines (multiple paragraphs/memos)
            val paragraphs = trimmed.split(Regex("\n\\s*\n\\s*\n"))
            if (paragraphs.size > 1) {
                for (para in paragraphs) {
                    val p = para.trim()
                    if (p.isNotBlank()) {
                        notes.add(createNote(p, "Umum"))
                    }
                }
            } else {
                notes.add(createNote(trimmed, "Umum"))
            }
        }

        for (n in notes) {
            if (!categories.containsKey(n.category)) {
                categories[n.category] = CategoryEntity(name = n.category, colorHex = "#2563EB", isDefault = false)
            }
        }

        return DecodedResult(
            success = true,
            description = "Format Dokumen Teks (${notes.size} catatan)",
            notes = notes,
            categories = categories.values.toList(),
            rawTextPreview = if (notes.isNotEmpty()) notes[0].content.take(150) else "",
            detectedMethod = "Teks Plain / Dokumen"
        )
    }

    private fun createNote(content: String, category: String): NoteEntity {
        val now = System.currentTimeMillis()
        return NoteEntity(
            id = 0L,
            content = content,
            category = category.ifBlank { "Umum" },
            createdAt = now,
            updatedAt = now,
            isHidden = false
        )
    }
}
