package com.example.data.repository

import com.example.data.dao.CategoryDao
import com.example.data.dao.NoteDao
import com.example.data.model.BackupData
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import com.example.util.Aes256Crypt
import com.example.util.SmartTextDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository enforcing Advanced Local Encryption:
 * Note titles and contents are encrypted using field-level AES-256 standard
 * cryptography before storing them locally in a secure SQLite Room database.
 * Decryption occurs only in-memory.
 */
class NoteRepository(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao
) {
    private fun decryptNote(note: NoteEntity): NoteEntity {
        return note.copy(
            title = Aes256Crypt.decrypt(note.title),
            content = Aes256Crypt.decrypt(note.content)
        )
    }

    private fun encryptNote(note: NoteEntity): NoteEntity {
        return note.copy(
            title = Aes256Crypt.encrypt(note.title),
            content = Aes256Crypt.encrypt(note.content)
        )
    }

    fun getVisibleNotes(category: String? = null, query: String = ""): Flow<List<NoteEntity>> {
        return noteDao.getVisibleNotes().map { rawList ->
            // In-memory decryption
            val decryptedList = rawList.map { decryptNote(it) }

            // Filter in-memory for category and query
            decryptedList.filter { note ->
                val matchesCategory = category.isNullOrBlank() || category == "Semua" || note.category.equals(category, ignoreCase = true)
                val matchesQuery = query.isBlank() ||
                        note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true) ||
                        note.category.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }
        }
    }

    fun getHiddenNotes(query: String = ""): Flow<List<NoteEntity>> {
        return noteDao.getHiddenNotes().map { rawList ->
            // In-memory decryption
            val decryptedList = rawList.map { decryptNote(it) }

            decryptedList.filter { note ->
                query.isBlank() ||
                        note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true) ||
                        note.category.contains(query, ignoreCase = true)
            }
        }
    }

    fun getCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    suspend fun insertNote(
        title: String = "",
        content: String,
        category: String,
        isHidden: Boolean = false,
        colorHex: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        // Encrypt title and content using field-level AES-256 before storing in Room DB
        val encryptedTitle = Aes256Crypt.encrypt(title.trim())
        val encryptedContent = Aes256Crypt.encrypt(content.trim())

        val note = NoteEntity(
            title = encryptedTitle,
            content = encryptedContent,
            category = category.trim().ifBlank { "Umum" },
            createdAt = now,
            updatedAt = now,
            isHidden = isHidden,
            colorHex = colorHex
        )
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: NoteEntity) {
        // Encrypt title and content using field-level AES-256 before updating
        val updated = note.copy(
            title = Aes256Crypt.encrypt(note.title.trim()),
            content = Aes256Crypt.encrypt(note.content.trim()),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.updateNote(updated)
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    suspend fun toggleHidden(noteId: Long, isHidden: Boolean) {
        noteDao.setHiddenStatus(noteId, isHidden, System.currentTimeMillis())
    }

    suspend fun insertCategory(name: String, colorHex: String): Long {
        val trimmed = name.trim()
        val existing = categoryDao.getCategoryByName(trimmed)
        if (existing != null) {
            return existing.id
        }
        val category = CategoryEntity(name = trimmed, colorHex = colorHex, isDefault = false)
        return categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        categoryDao.deleteCategory(category)
    }

    suspend fun exportToJson(): String {
        // Read and decrypt in-memory for export
        val allEncryptedNotes = noteDao.getAllNotesDirect()
        val decryptedNotes = allEncryptedNotes.map { decryptNote(it) }
        val allCategories = categoryDao.getAllCategoriesDirect()

        val backup = BackupData(
            categories = allCategories,
            notes = decryptedNotes
        )
        return backup.toJsonString()
    }

    suspend fun importFromJson(jsonString: String): Result<Pair<Int, Int>> {
        return runCatching {
            val backup = BackupData.fromJsonString(jsonString)
            var catCount = 0
            var noteCount = 0

            // Import categories first
            for (cat in backup.categories) {
                val existing = categoryDao.getCategoryByName(cat.name)
                if (existing == null) {
                    categoryDao.insertCategory(cat.copy(id = 0L))
                    catCount++
                }
            }

            // Import notes with AES-256 encryption at rest
            for (note in backup.notes) {
                val toInsert = encryptNote(note.copy(id = 0L))
                noteDao.insertNote(toInsert)
                noteCount++
            }

            Pair(catCount, noteCount)
        }
    }

    suspend fun importFromSmartText(
        rawInput: String,
        userPassword: String? = null
    ): Result<Triple<Int, Int, String>> {
        return runCatching {
            val decoded = SmartTextDecoder.decodeAndParse(rawInput, userPassword)
            if (!decoded.success || decoded.notes.isEmpty()) {
                throw IllegalArgumentException(decoded.description.ifBlank { "Format file tidak dapat diproses" })
            }

            var catCount = 0
            var noteCount = 0

            // Import categories
            for (cat in decoded.categories) {
                val existing = categoryDao.getCategoryByName(cat.name)
                if (existing == null) {
                    categoryDao.insertCategory(cat.copy(id = 0L))
                    catCount++
                }
            }

            // Import notes with field-level AES-256 encryption at rest
            for (note in decoded.notes) {
                val toInsert = encryptNote(note.copy(id = 0L))
                noteDao.insertNote(toInsert)
                noteCount++
            }

            Triple(catCount, noteCount, decoded.description)
        }
    }
}
