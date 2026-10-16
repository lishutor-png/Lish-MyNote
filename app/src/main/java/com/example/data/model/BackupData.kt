package com.example.data.model

import com.example.util.Aes256Crypt
import com.example.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val version: Int = 1,
    val appName: String = "Lish MyNote",
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<CategoryEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("version", version)
        root.put("appName", appName)
        root.put("exportedAt", exportedAt)

        val categoriesArray = JSONArray()
        for (category in categories) {
            val catObj = JSONObject()
            catObj.put("name", category.name)
            catObj.put("colorHex", category.colorHex)
            catObj.put("isDefault", category.isDefault)
            catObj.put("displayOrder", category.displayOrder)
            categoriesArray.put(catObj)
        }
        root.put("categories", categoriesArray)

        val notesArray = JSONArray()
        for (note in notes) {
            val noteObj = JSONObject()
            noteObj.put("title", note.title)
            noteObj.put("content", note.content)
            noteObj.put("category", note.category)
            noteObj.put("createdAt", note.createdAt)
            noteObj.put("updatedAt", note.updatedAt)
            noteObj.put("isHidden", note.isHidden)
            noteObj.put("isSecret", note.isHidden)
            note.colorHex?.let { noteObj.put("colorHex", it) }
            notesArray.put(noteObj)
        }
        root.put("notes", notesArray)

        return root.toString(2)
    }

    companion object {
        fun detectBackupFormat(rawContent: String): String {
            return try {
                val trimmed = rawContent.trim()
                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    "Format Daftar Catatan JSON (${array.length()} catatan terdeteksi)"
                } else if (trimmed.startsWith("{")) {
                    val root = JSONObject(trimmed)
                    if (root.has("notes")) {
                        val notes = root.optJSONArray("notes")?.length() ?: 0
                        val cats = root.optJSONArray("categories")?.length() ?: 0
                        "Cadangan Lish MyNote ($notes Catatan, $cats Kategori)"
                    } else {
                        // Check for array fields
                        val keys = root.keys()
                        var foundCount = 0
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val arr = root.optJSONArray(key)
                            if (arr != null && arr.length() > 0) {
                                foundCount = arr.length()
                                break
                            }
                        }
                        if (foundCount > 0) {
                            "Format Cadangan Catatan ($foundCount catatan terdeteksi)"
                        } else {
                            "File Cadangan JSON Terdeteksi"
                        }
                    }
                } else {
                    "Format teks belum teridentifikasi"
                }
            } catch (e: Exception) {
                "Format JSON tidak valid"
            }
        }

        fun fromJsonString(jsonString: String): BackupData {
            val trimmed = jsonString.trim()
            val categoriesMap = mutableMapOf<String, CategoryEntity>()
            val notesList = mutableListOf<NoteEntity>()

            if (trimmed.startsWith("[")) {
                // Root is a JSON Array of notes
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.get(i)
                    parseNoteItem(item, notesList, categoriesMap)
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)

                // 1. Explicit categories
                val categoriesArray = root.optJSONArray("categories") ?: root.optJSONArray("kategori")
                if (categoriesArray != null) {
                    for (i in 0 until categoriesArray.length()) {
                        val obj = categoriesArray.optJSONObject(i)
                        if (obj != null) {
                            val name = obj.optString("name", obj.optString("nama", "")).trim()
                            if (name.isNotBlank()) {
                                categoriesMap[name.lowercase()] = CategoryEntity(
                                    id = 0L,
                                    name = name,
                                    colorHex = obj.optString("colorHex", obj.optString("color", "#2563EB")),
                                    isDefault = obj.optBoolean("isDefault", false),
                                    displayOrder = obj.optInt("displayOrder", i)
                                )
                            }
                        }
                    }
                }

                // 2. Look for notes array in common keys
                val notesKeys = listOf("notes", "catatan", "memos", "data", "items", "records", "list", "entries")
                var foundNotesArray: JSONArray? = null
                for (key in notesKeys) {
                    val arr = root.optJSONArray(key)
                    if (arr != null) {
                        foundNotesArray = arr
                        break
                    }
                }

                if (foundNotesArray != null) {
                    for (i in 0 until foundNotesArray.length()) {
                        val item = foundNotesArray.get(i)
                        parseNoteItem(item, notesList, categoriesMap)
                    }
                } else {
                    // Try parsing root directly as a note if it has content/text
                    parseNoteItem(root, notesList, categoriesMap)
                }
            }

            // Ensure every note's category exists in categoriesMap
            for (note in notesList) {
                val catKey = note.category.trim().lowercase()
                if (note.category.isNotBlank() && !categoriesMap.containsKey(catKey)) {
                    categoriesMap[catKey] = CategoryEntity(
                        id = 0L,
                        name = note.category,
                        colorHex = note.colorHex ?: "#EC4899",
                        isDefault = false
                    )
                }
            }

            return BackupData(
                version = 1,
                appName = "Lish MyNote",
                exportedAt = System.currentTimeMillis(),
                categories = categoriesMap.values.toList(),
                notes = notesList
            )
        }

        private fun parseNoteItem(
            rawItem: Any,
            notesList: MutableList<NoteEntity>,
            categoriesMap: MutableMap<String, CategoryEntity>
        ) {
            when (rawItem) {
                is JSONObject -> {
                    // Extract title and body (safely ignoring empty string keys like "")
                    var rawTitle = rawItem.optString("title", rawItem.optString("judul", "")).trim()
                    var rawBody = rawItem.optString(
                        "content",
                        rawItem.optString(
                            "text",
                            rawItem.optString(
                                "body",
                                rawItem.optString(
                                    "note",
                                    rawItem.optString(
                                        "memo",
                                        rawItem.optString(
                                            "catatan",
                                            rawItem.optString("isi", rawItem.optString("description", ""))
                                        )
                                    )
                                )
                            )
                        )
                    ).trim()

                    // If encrypted with AES-256, decrypt in-memory
                    rawTitle = Aes256Crypt.decrypt(rawTitle)
                    rawBody = Aes256Crypt.decrypt(rawBody)

                    val finalContent = rawBody.ifBlank { rawTitle }

                    if (finalContent.isBlank()) return

                    // Extract category
                    val category = rawItem.optString(
                        "category",
                        rawItem.optString(
                            "kategori",
                            rawItem.optString(
                                "folder",
                                rawItem.optString("tag", rawItem.optString("label", "Umum"))
                            )
                        )
                    ).trim().ifBlank { "Umum" }

                    // Extract date/time
                    val rawDate = rawItem.opt("createdAt")
                        ?: rawItem.opt("created_at")
                        ?: rawItem.opt("timestamp")
                        ?: rawItem.opt("date")
                        ?: rawItem.opt("time")
                        ?: rawItem.opt("tanggal")
                        ?: rawItem.opt("updatedAt")
                        ?: rawItem.opt("updated_at")

                    val createdAt = DateUtils.parseDateToTimestamp(rawDate)
                    val updatedAt = DateUtils.parseDateToTimestamp(
                        rawItem.opt("updatedAt") ?: rawItem.opt("updated_at") ?: createdAt
                    )

                    // Extract secret / hidden flag (isSecret: true, is_secret: true, isLocked: true, isHidden: true)
                    val isHidden = rawItem.optBoolean("isSecret", false) ||
                            rawItem.optBoolean("is_secret", false) ||
                            rawItem.optBoolean("isLocked", false) ||
                            rawItem.optBoolean("is_locked", false) ||
                            rawItem.optBoolean("secret", false) ||
                            rawItem.optBoolean("tersembunyi", false) ||
                            rawItem.optBoolean("isHidden", false) ||
                            rawItem.optBoolean("is_hidden", false)

                    val colorHex = if (rawItem.has("colorHex")) rawItem.optString("colorHex")
                    else if (rawItem.has("color")) rawItem.optString("color")
                    else null

                    notesList.add(
                        NoteEntity(
                            id = 0L,
                            title = rawTitle,
                            content = finalContent,
                            category = category,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            isHidden = isHidden,
                            colorHex = colorHex
                        )
                    )
                }
                is String -> {
                    var str = rawItem.trim()
                    str = Aes256Crypt.decrypt(str)
                    if (str.isNotBlank()) {
                        notesList.add(
                            NoteEntity(
                                id = 0L,
                                title = "",
                                content = str,
                                category = "Umum",
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                isHidden = false
                            )
                        )
                    }
                }
            }
        }
    }
}
