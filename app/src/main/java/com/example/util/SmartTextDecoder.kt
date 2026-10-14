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
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DecodedResult(
    val success: Boolean,
    val description: String,
    val notes: List<NoteEntity>,
    val categories: List<CategoryEntity>,
    val fullDocumentContent: String = "",
    val fullDocumentTitle: String = "",
    val rawTextPreview: String = "",
    val detectedMethod: String = "",
    val requiresPassword: Boolean = false,
    val totalCharacters: Int = 0,
    val totalWords: Int = 0
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
     * Decodes and parses any input string (.txt, .sql, .csv, .json, or encrypted formats).
     * Ensures NO content is dropped or truncated.
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

        // 1. Direct AES-256 field / envelope check
        if (trimmed.startsWith("ENC:AES256:")) {
            val decrypted = Aes256Crypt.decrypt(trimmed)
            if (decrypted.isNotBlank() && decrypted != trimmed) {
                return parsePlainTextAsNotes(decrypted, "Enkripsi AES-256 (Terenkripsi Lokal)")
            }
        }

        // 2. Direct JSON check
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonResult = tryParseJson(trimmed, passwordsToTry)
            if (jsonResult != null) {
                return jsonResult
            }
        }

        // 3. SQL Dump file check (INSERT INTO notes ...)
        if (trimmed.contains("INSERT INTO", ignoreCase = true) &&
            (trimmed.contains("notes", ignoreCase = true) || trimmed.contains("memo", ignoreCase = true))) {
            val sqlResult = tryParseSqlDump(trimmed)
            if (sqlResult != null && sqlResult.notes.isNotEmpty()) {
                return sqlResult
            }
        }

        // 4. Header-formatted single/multi notes (Judul: ... Isi: ...)
        val headerResult = tryParseHeaderFormattedText(trimmed)
        if (headerResult != null && headerResult.notes.isNotEmpty()) {
            return headerResult
        }

        // 5. Try Base64 decoding
        val b64Decoded = tryBase64Decode(trimmed)
        if (b64Decoded != null) {
            val fromB64 = tryProcessBytes(b64Decoded, "Base64", passwordsToTry, allowPlainText = false)
            if (fromB64 != null) {
                return fromB64
            }
            // If base64 decoded directly to clean UTF8 plain text
            val b64Utf8 = tryDecodeUtf8Strict(b64Decoded)
            if (b64Utf8 != null && isLikelyReadableText(b64Utf8)) {
                return parsePlainTextAsNotes(b64Utf8, "Base64 -> Teks")
            }
        }

        // 6. Try Hex decoding
        val hexDecoded = tryHexDecode(trimmed)
        if (hexDecoded != null) {
            val fromHex = tryProcessBytes(hexDecoded, "Hex", passwordsToTry, allowPlainText = false)
            if (fromHex != null) {
                return fromHex
            }
        }

        // 7. If it starts with OpenSSL "Salted__" header as raw string
        if (trimmed.startsWith("Salted__")) {
            val rawBytes = trimmed.toByteArray(StandardCharsets.ISO_8859_1)
            val fromRaw = tryProcessBytes(rawBytes, "OpenSSL Salted", passwordsToTry, allowPlainText = true)
            if (fromRaw != null) {
                return fromRaw
            }
        }

        // 8. If password was provided, try decrypting raw bytes
        if (!userPassword.isNullOrBlank()) {
            val rawBytes = trimmed.toByteArray(StandardCharsets.UTF_8)
            val fromRaw = tryProcessBytes(rawBytes, "Teks Terenkripsi", listOf(userPassword.trim()), allowPlainText = true)
            if (fromRaw != null) {
                return fromRaw
            }
        }

        // 9. Standard / Comprehensive Plain Text Note (.txt document, markdown, novel chapter, or notes)
        return parsePlainTextAsNotes(trimmed, "Teks Plain / Dokumen")
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
                    val fullDoc = backup.notes.joinToString("\n\n---\n\n") {
                        if (it.title.isNotBlank()) "${it.title}\n\n${it.content}" else it.content
                    }
                    val words = countWords(fullDoc)
                    return DecodedResult(
                        success = true,
                        description = "Cadangan JSON (${backup.notes.size} Catatan, ${backup.categories.size} Kategori)",
                        notes = backup.notes,
                        categories = backup.categories,
                        fullDocumentContent = fullDoc,
                        fullDocumentTitle = backup.notes.firstOrNull()?.title ?: "Cadangan Catatan",
                        rawTextPreview = if (backup.notes.isNotEmpty()) backup.notes[0].content.take(300) else "",
                        detectedMethod = "Format JSON Cadangan",
                        totalCharacters = fullDoc.length,
                        totalWords = words
                    )
                }

                // Generic single note object {"title": "...", "content": "..."}
                val note = parseSingleJsonNote(root)
                if (note != null) {
                    val words = countWords(note.content)
                    return DecodedResult(
                        success = true,
                        description = "Catatan Tunggal JSON",
                        notes = listOf(note),
                        categories = listOf(CategoryEntity(name = note.category, colorHex = "#2563EB", isDefault = false)),
                        fullDocumentContent = note.content,
                        fullDocumentTitle = note.title.ifBlank { note.content.lines().firstOrNull()?.take(60) ?: "Catatan" },
                        rawTextPreview = note.content.take(300),
                        detectedMethod = "Objek JSON",
                        totalCharacters = note.content.length,
                        totalWords = words
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
                        var str = array.optString(i)
                        str = Aes256Crypt.decrypt(str)
                        if (str.isNotBlank()) {
                            val n = createNote(title = "", content = str, category = "Umum")
                            notes.add(n)
                            if (!categories.containsKey("Umum")) {
                                categories["Umum"] = CategoryEntity(name = "Umum", colorHex = "#2563EB", isDefault = false)
                            }
                        }
                    }
                }

                if (notes.isNotEmpty()) {
                    val fullDoc = notes.joinToString("\n\n---\n\n") {
                        if (it.title.isNotBlank()) "${it.title}\n\n${it.content}" else it.content
                    }
                    val words = countWords(fullDoc)
                    return DecodedResult(
                        success = true,
                        description = "Daftar Catatan JSON (${notes.size} catatan)",
                        notes = notes,
                        categories = categories.values.toList(),
                        fullDocumentContent = fullDoc,
                        fullDocumentTitle = notes.firstOrNull()?.title ?: "Daftar Catatan",
                        rawTextPreview = notes[0].content.take(300),
                        detectedMethod = "Array JSON",
                        totalCharacters = fullDoc.length,
                        totalWords = words
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSingleJsonNote(obj: JSONObject): NoteEntity? {
        var content = when {
            obj.has("content") -> obj.optString("content")
            obj.has("isi") -> obj.optString("isi")
            obj.has("body") -> obj.optString("body")
            obj.has("text") -> obj.optString("text")
            obj.has("description") -> obj.optString("description")
            obj.has("memo") -> obj.optString("memo")
            obj.has("note") -> obj.optString("note")
            else -> ""
        }
        var title = when {
            obj.has("title") -> obj.optString("title")
            obj.has("judul") -> obj.optString("judul")
            obj.has("name") -> obj.optString("name")
            else -> ""
        }

        // In-memory decryption if field-level encrypted with AES-256
        title = Aes256Crypt.decrypt(title.trim())
        content = Aes256Crypt.decrypt(content.trim())

        val fullContent = when {
            title.isNotBlank() && content.isNotBlank() -> {
                if (content.startsWith(title)) content else "$title\n\n$content"
            }
            title.isNotBlank() -> title
            content.isNotBlank() -> content
            else -> ""
        }

        if (fullContent.isBlank()) return null

        val category = obj.optString("category", obj.optString("kategori", "Umum")).ifBlank { "Umum" }
        val now = System.currentTimeMillis()
        val createdAt = obj.optLong("createdAt", obj.optLong("created_at", now))
        val updatedAt = obj.optLong("updatedAt", obj.optLong("updated_at", now))
        val isHidden = obj.optBoolean("isHidden", obj.optBoolean("is_hidden", false))

        return NoteEntity(
            id = 0L,
            title = title,
            content = fullContent,
            category = category,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isHidden = isHidden
        )
    }

    /**
     * Parses SQLite dump statements: INSERT INTO notes (...) VALUES (...)
     */
    private fun tryParseSqlDump(sql: String): DecodedResult? {
        val notes = mutableListOf<NoteEntity>()
        val categories = mutableMapOf<String, CategoryEntity>()
        val insertPattern = Pattern.compile("INSERT\\s+INTO\\s+[`\"']?(\\w+)[`\"']?\\s*(?:\\(([^)]+)\\))?\\s*VALUES\\s*\\((.+)\\);?", Pattern.CASE_INSENSITIVE)

        for (line in sql.lines()) {
            val matcher = insertPattern.matcher(line.trim())
            if (matcher.find()) {
                val valuesStr = matcher.group(3) ?: continue
                val values = parseSqlValues(valuesStr)
                if (values.isNotEmpty()) {
                    var title = ""
                    var content = ""
                    var category = "Umum"

                    // Try to guess values from types
                    val textValues = values.filter { it.isNotBlank() }
                    if (textValues.size >= 2) {
                        if (textValues[0].length < 80 && textValues[1].length > 10) {
                            title = textValues[0]
                            content = textValues[1]
                        } else {
                            content = textValues[0]
                        }
                    } else if (textValues.isNotEmpty()) {
                        content = textValues[0]
                    }

                    // Decrypt AES-256 fields if present
                    title = Aes256Crypt.decrypt(title)
                    content = Aes256Crypt.decrypt(content)

                    if (content.isNotBlank()) {
                        val note = createNote(title = title, content = content, category = category)
                        notes.add(note)
                        categories[category] = CategoryEntity(name = category, colorHex = "#2563EB", isDefault = false)
                    }
                }
            }
        }

        if (notes.isEmpty()) return null

        val fullDoc = notes.joinToString("\n\n---\n\n") { it.content }
        return DecodedResult(
            success = true,
            description = "Format SQL Dump (${notes.size} catatan)",
            notes = notes,
            categories = categories.values.toList(),
            fullDocumentContent = fullDoc,
            fullDocumentTitle = notes.firstOrNull()?.title ?: "Catatan SQL",
            rawTextPreview = notes[0].content.take(300),
            detectedMethod = "SQL Insert Dump",
            totalCharacters = fullDoc.length,
            totalWords = countWords(fullDoc)
        )
    }

    private fun parseSqlValues(valuesStr: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var quoteChar = '\''
        var escape = false

        for (ch in valuesStr) {
            if (escape) {
                sb.append(ch)
                escape = false
                continue
            }
            if (ch == '\\') {
                escape = true
                continue
            }
            if (!inQuotes && (ch == '\'' || ch == '"')) {
                inQuotes = true
                quoteChar = ch
                continue
            }
            if (inQuotes && ch == quoteChar) {
                inQuotes = false
                continue
            }
            if (!inQuotes && ch == ',') {
                result.add(sb.toString().trim())
                sb.clear()
                continue
            }
            sb.append(ch)
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString().trim())
        }
        return result
    }

    /**
     * Parses text with explicit key-value headers:
     * Judul: ... / Title: ...
     * Kategori: ... / Category: ...
     * Isi: ... / Content: ...
     */
    private fun tryParseHeaderFormattedText(text: String): DecodedResult? {
        val lines = text.lines()
        var hasJudul = false
        var hasIsi = false

        var title = ""
        var category = "Umum"
        val bodyBuilder = StringBuilder()
        var capturingBody = false

        for (line in lines) {
            val trimmedLine = line.trim()
            val lower = trimmedLine.lowercase()

            if (!capturingBody) {
                when {
                    lower.startsWith("judul:") || lower.startsWith("title:") -> {
                        hasJudul = true
                        title = trimmedLine.substringAfter(":").trim()
                    }
                    lower.startsWith("kategori:") || lower.startsWith("category:") -> {
                        category = trimmedLine.substringAfter(":").trim().ifBlank { "Umum" }
                    }
                    lower.startsWith("isi:") || lower.startsWith("content:") || lower.startsWith("catatan:") -> {
                        hasIsi = true
                        capturingBody = true
                        val inlineBody = trimmedLine.substringAfter(":").trim()
                        if (inlineBody.isNotBlank()) {
                            bodyBuilder.appendLine(inlineBody)
                        }
                    }
                }
            } else {
                bodyBuilder.appendLine(line)
            }
        }

        if (hasIsi || (hasJudul && bodyBuilder.isNotEmpty())) {
            val content = bodyBuilder.toString().trim()
            val fullContent = if (title.isNotBlank() && !content.startsWith(title)) {
                "$title\n\n$content"
            } else {
                content.ifBlank { title }
            }

            val note = createNote(title = title, content = fullContent, category = category)
            val words = countWords(fullContent)
            return DecodedResult(
                success = true,
                description = "Format Dokumen Berstruktur (Judul & Isi Lengkap)",
                notes = listOf(note),
                categories = listOf(CategoryEntity(name = category, colorHex = "#2563EB", isDefault = false)),
                fullDocumentContent = fullContent,
                fullDocumentTitle = title.ifBlank { "Catatan" },
                rawTextPreview = fullContent.take(300),
                detectedMethod = "Format Header Judul & Isi",
                totalCharacters = fullContent.length,
                totalWords = words
            )
        }

        return null
    }

    /**
     * Parses plain text notes from a .txt file.
     * Guarantees that ALL content is kept:
     * - fullDocumentContent contains 100% of the text.
     * - If distinct explicit dividers exist (e.g. "=== Catatan 2 ===" or "[Catatan 2]"), notes list contains separated parts,
     *   while fullDocumentContent preserves the whole document as a single complete note.
     */
    fun parsePlainTextAsNotes(text: String, detectedLabel: String = "Teks Plain"): DecodedResult {
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

        // Look for clean first line as potential title
        val nonBlankLines = lines.filter { it.isNotBlank() }
        val firstLine = nonBlankLines.firstOrNull()?.trim() ?: ""
        val candidateTitle = if (firstLine.length in 1..90 && !firstLine.startsWith("http")) {
            firstLine.removePrefix("# ").removePrefix("## ").trim()
        } else {
            "Catatan ${DateUtils.formatDateShort(System.currentTimeMillis())}"
        }

        // Only split if there are clear, intentional multiple-note dividers
        // (e.g. === Catatan 1 ===, --- Catatan 2 ---, or [Catatan 3])
        val explicitDividerRegex = Regex("^(?:={3,}|-{3,})\\s*(?:Catatan|Bab|Bagian|Note|Chapter|Entry|No\\.?)\\s*\\d*\\s*(?:={3,}|-{3,})?$|^\\[(?:Catatan|Bab|Note|Chapter)\\s*\\d*\\]", RegexOption.IGNORE_CASE)
        val hasExplicitDividers = lines.count { explicitDividerRegex.containsMatchIn(it.trim()) } >= 1

        if (hasExplicitDividers) {
            var currentBuffer = StringBuilder()
            var currentCategory = "Umum"
            var currentTitle = ""

            for (line in lines) {
                val tLine = line.trim()
                if (explicitDividerRegex.containsMatchIn(tLine)) {
                    val content = currentBuffer.toString().trim()
                    if (content.isNotBlank()) {
                        notes.add(createNote(title = currentTitle, content = content, category = currentCategory))
                        currentBuffer = StringBuilder()
                    }
                    currentTitle = tLine.removePrefix("===").removeSuffix("===").removePrefix("---").removeSuffix("---").removeSurrounding("[", "]").trim()
                } else {
                    currentBuffer.appendLine(line)
                }
            }

            val remaining = currentBuffer.toString().trim()
            if (remaining.isNotBlank()) {
                notes.add(createNote(title = currentTitle, content = remaining, category = currentCategory))
            }
        }

        // Fallback: If no explicit dividers were matched, keep the ENTIRE text as ONE complete note!
        // This ensures NO CONTENT IS DROPPED (solving "Saat di masukkan filenya isi catatan tidak masuk semua")!
        if (notes.isEmpty()) {
            notes.add(createNote(title = candidateTitle, content = trimmed, category = "Umum"))
        }

        for (n in notes) {
            if (!categories.containsKey(n.category)) {
                categories[n.category] = CategoryEntity(name = n.category, colorHex = "#2563EB", isDefault = false)
            }
        }

        val totalChars = trimmed.length
        val totalWords = countWords(trimmed)

        return DecodedResult(
            success = true,
            description = if (notes.size > 1) "Dokumen Teks ($totalChars karakter, ${notes.size} bagian terdeteksi)" else "Dokumen Teks Lengkap ($totalChars karakter, $totalWords kata)",
            notes = notes,
            categories = categories.values.toList(),
            fullDocumentContent = trimmed,
            fullDocumentTitle = candidateTitle,
            rawTextPreview = trimmed.take(300),
            detectedMethod = detectedLabel,
            totalCharacters = totalChars,
            totalWords = totalWords
        )
    }

    private fun tryProcessBytes(
        bytes: ByteArray,
        sourceDesc: String,
        passwordsToTry: List<String>,
        allowPlainText: Boolean
    ): DecodedResult? {
        val decompressed = tryDecompressGzip(bytes)
        val activeBytes = decompressed ?: bytes

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

        // Try AES decryption with candidates
        for (pass in passwordsToTry) {
            val decrypted = tryDecryptAes(activeBytes, pass)
            if (decrypted != null) {
                val decString = tryDecodeUtf8Strict(decrypted)
                if (decString != null && isLikelyReadableText(decString)) {
                    val trimmed = decString.trim()
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        val res = tryParseJson(trimmed, passwordsToTry)
                        if (res != null) {
                            return res.copy(
                                description = "Berhasil didekripsi (AES) -> ${res.notes.size} catatan",
                                detectedMethod = "AES ($sourceDesc)"
                            )
                        }
                    }
                    val textRes = parsePlainTextAsNotes(trimmed, "AES ($sourceDesc)")
                    if (textRes.success && textRes.notes.isNotEmpty()) {
                        return textRes.copy(
                            description = "Berhasil didekripsi (AES) -> Dokumen Lengkap",
                            detectedMethod = "AES ($sourceDesc)"
                        )
                    }
                }
            }
        }

        return null
    }

    private fun tryDecryptAes(bytes: ByteArray, pass: String): ByteArray? {
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

        // 2. Try AES-256 GCM (12-byte IV)
        try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(pass.toByteArray(StandardCharsets.UTF_8))
            val key = SecretKeySpec(keyBytes, "AES")
            if (bytes.size > 12) {
                val iv = bytes.copyOfRange(0, 12)
                val ciphertext = bytes.copyOfRange(12, bytes.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                return cipher.doFinal(ciphertext)
            }
        } catch (_: Exception) {}

        // 3. Try AES-256 CBC with SHA-256(pass)
        try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(pass.toByteArray(StandardCharsets.UTF_8))
            val key = SecretKeySpec(keyBytes, "AES")
            if (bytes.size > 16) {
                val iv = bytes.copyOfRange(0, 16)
                val ciphertext = bytes.copyOfRange(16, bytes.size)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                return cipher.doFinal(ciphertext)
            }
        } catch (_: Exception) {}

        // 4. Try AES ECB with MD5(pass)
        val md5Key = md5(pass)
        val md5KeySpec = SecretKeySpec(md5Key, "AES")
        try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, md5KeySpec)
            return cipher.doFinal(bytes)
        } catch (_: Exception) {}

        // 5. Try AES CBC with MD5(pass) and first 16 bytes as IV
        if (bytes.size > 16) {
            try {
                val iv = bytes.copyOfRange(0, 16)
                val ciphertext = bytes.copyOfRange(16, bytes.size)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, md5KeySpec, IvParameterSpec(iv))
                return cipher.doFinal(ciphertext)
            } catch (_: Exception) {}
        }

        return null
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
        if (trimmed.contains(" ") && trimmed.split(" ").size > 3) return null

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
                return false
            }
        }
        return (readable.toDouble() / text.length) >= 0.80
    }

    private fun countWords(text: String): Int {
        return text.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    fun createNote(title: String, content: String, category: String): NoteEntity {
        val now = System.currentTimeMillis()
        val finalTitle = title.trim()
        val finalContent = content.trim()
        return NoteEntity(
            id = 0L,
            title = finalTitle,
            content = finalContent,
            category = category.ifBlank { "Umum" },
            createdAt = now,
            updatedAt = now,
            isHidden = false
        )
    }
}
