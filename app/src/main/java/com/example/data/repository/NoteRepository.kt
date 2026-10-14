package com.example.data.repository

import com.example.data.dao.CategoryDao
import com.example.data.dao.NoteDao
import com.example.data.model.BackupData
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao
) {
    fun getVisibleNotes(category: String? = null, query: String = ""): Flow<List<NoteEntity>> {
        return when {
            query.isNotBlank() -> noteDao.searchVisibleNotes(query.trim())
            !category.isNullOrBlank() && category != "Semua" -> noteDao.getVisibleNotesByCategory(category)
            else -> noteDao.getVisibleNotes()
        }
    }

    fun getHiddenNotes(query: String = ""): Flow<List<NoteEntity>> {
        return if (query.isNotBlank()) {
            noteDao.searchHiddenNotes(query.trim())
        } else {
            noteDao.getHiddenNotes()
        }
    }

    fun getCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    suspend fun insertNote(
        content: String,
        category: String,
        isHidden: Boolean = false,
        colorHex: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            content = content.trim(),
            category = category.trim().ifBlank { "Umum" },
            createdAt = now,
            updatedAt = now,
            isHidden = isHidden,
            colorHex = colorHex
        )
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: NoteEntity) {
        val updated = note.copy(updatedAt = System.currentTimeMillis())
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
        val allNotes = noteDao.getAllNotesDirect()
        val allCategories = categoryDao.getAllCategoriesDirect()
        val backup = BackupData(
            categories = allCategories,
            notes = allNotes
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

            // Import notes
            for (note in backup.notes) {
                noteDao.insertNote(note.copy(id = 0L))
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
            val decoded = com.example.util.SmartTextDecoder.decodeAndParse(rawInput, userPassword)
            if (!decoded.success || decoded.notes.isEmpty()) {
                throw IllegalArgumentException(decoded.description.ifBlank { "Format file tidak dapat diproses" })
            }

            var catCount = 0
            var noteCount = 0

            // Import categories first
            for (cat in decoded.categories) {
                val existing = categoryDao.getCategoryByName(cat.name)
                if (existing == null) {
                    categoryDao.insertCategory(cat.copy(id = 0L))
                    catCount++
                }
            }

            // Import notes
            for (note in decoded.notes) {
                noteDao.insertNote(note.copy(id = 0L))
                noteCount++
            }

            Triple(catCount, noteCount, decoded.description)
        }
    }
}
