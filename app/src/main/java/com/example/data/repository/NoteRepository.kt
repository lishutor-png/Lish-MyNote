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

class NoteRepository(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao
) {

    val categories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    /**
     * Decrypts note title and content in-memory so they can be safely displayed and searched.
     */
    private fun decryptNote(entity: NoteEntity): NoteEntity {
        return entity.copy(
            title = Aes256Crypt.decrypt(entity.title),
            content = Aes256Crypt.decrypt(entity.content)
        )
    }

    /**
     * Encrypts note title and content using field-level AES-256 before writing to SQLite Room.
     */
    private fun encryptNote(note: NoteEntity): NoteEntity {
        return note.copy(
            title = Aes256Crypt.encrypt(note.title.trim()),
            content = Aes256Crypt.encrypt(note.content.trim())
        )
    }

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotesFlow().map { list ->
        list.map { decryptNote(it) }
    }

    fun getVisibleNotes(category: String? = null, query: String = ""): Flow<List<NoteEntity>> {
        val baseFlow = if (category.isNullOrBlank() || category == "Semua") {
            noteDao.getVisibleNotes()
        } else {
            noteDao.getVisibleNotesByCategory(category)
        }

        return baseFlow.map { list ->
            val decryptedList = list.map { decryptNote(it) }
            if (query.isBlank()) {
                decryptedList
            } else {
                decryptedList.filter { note ->
                    note.content.contains(query, ignoreCase = true) ||
                            note.title.contains(query, ignoreCase = true) ||
                            note.category.contains(query, ignoreCase = true)
                }
            }
        }
    }

    fun getHiddenNotes(category: String? = null, query: String = ""): Flow<List<NoteEntity>> {
        return noteDao.getHiddenNotes().map { list ->
            val decryptedList = list.map { decryptNote(it) }
            val filteredByCategory = if (category.isNullOrBlank() || category == "Semua") {
                decryptedList
            } else {
                decryptedList.filter { it.category.equals(category.trim(), ignoreCase = true) }
            }

            if (query.isBlank()) {
                filteredByCategory
            } else {
                filteredByCategory.filter { note ->
                    note.content.contains(query, ignoreCase = true) ||
                            note.title.contains(query, ignoreCase = true) ||
                            note.category.contains(query, ignoreCase = true)
                }
            }
        }
    }

    suspend fun insertNote(
        title: String = "",
        content: String,
        category: String,
        isHidden: Boolean = false,
        colorHex: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val catTrimmed = category.trim().ifBlank { "Umum" }

        // Ensure category exists and normalize case
        val existingCat = categoryDao.getCategoryByName(catTrimmed)
        val finalCatName = if (existingCat != null) {
            existingCat.name
        } else {
            categoryDao.insertCategory(CategoryEntity(name = catTrimmed, colorHex = "#EC4899", isDefault = false))
            catTrimmed
        }

        // Encrypt title and content using field-level AES-256 before storing in Room DB
        val encryptedTitle = Aes256Crypt.encrypt(title.trim())
        val encryptedContent = Aes256Crypt.encrypt(content.trim())

        val note = NoteEntity(
            title = encryptedTitle,
            content = encryptedContent,
            category = finalCatName,
            createdAt = now,
            updatedAt = now,
            isHidden = isHidden,
            colorHex = colorHex
        )
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: NoteEntity) {
        val catTrimmed = note.category.trim().ifBlank { "Umum" }
        val existingCat = categoryDao.getCategoryByName(catTrimmed)
        val finalCatName = if (existingCat != null) {
            existingCat.name
        } else {
            categoryDao.insertCategory(CategoryEntity(name = catTrimmed, colorHex = "#EC4899", isDefault = false))
            catTrimmed
        }

        val updated = note.copy(
            title = Aes256Crypt.encrypt(note.title.trim()),
            content = Aes256Crypt.encrypt(note.content.trim()),
            category = finalCatName,
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

    suspend fun moveNoteToCategory(noteId: Long, newCategory: String) {
        val catTrimmed = newCategory.trim().ifBlank { "Umum" }
        val existingCat = categoryDao.getCategoryByName(catTrimmed)
        val finalCatName = if (existingCat != null) {
            existingCat.name
        } else {
            categoryDao.insertCategory(CategoryEntity(name = catTrimmed, colorHex = "#EC4899", isDefault = false))
            catTrimmed
        }
        noteDao.updateNoteCategory(noteId, finalCatName)
    }

    suspend fun moveAllNotesCategory(fromCategory: String, toCategory: String) {
        val fromTrimmed = fromCategory.trim()
        val toTrimmed = toCategory.trim().ifBlank { "Umum" }
        val existingTo = categoryDao.getCategoryByName(toTrimmed)
        val finalToName = if (existingTo != null) {
            existingTo.name
        } else {
            categoryDao.insertCategory(CategoryEntity(name = toTrimmed, colorHex = "#EC4899", isDefault = false))
            toTrimmed
        }
        noteDao.updateCategoryForNotes(fromTrimmed, finalToName)
    }

    suspend fun insertCategory(name: String, colorHex: String): Long {
        val trimmed = name.trim()
        val existing = categoryDao.getCategoryByName(trimmed)
        if (existing != null) {
            return existing.id
        }
        val allCats = categoryDao.getAllCategoriesDirect()
        val nextOrder = (allCats.maxOfOrNull { it.displayOrder } ?: 0) + 1
        val category = CategoryEntity(name = trimmed, colorHex = colorHex, isDefault = false, displayOrder = nextOrder)
        return categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity, oldName: String? = null) {
        categoryDao.updateCategory(category)
        if (!oldName.isNullOrBlank() && !oldName.equals(category.name, ignoreCase = true)) {
            // Update all notes belonging to the old category name to the new name
            noteDao.updateCategoryForNotes(oldName.trim(), category.name.trim())
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        if (category.isDefault) return
        categoryDao.deleteCategory(category)
        // Safely reassign orphaned notes to "Umum"
        noteDao.reassignNotesToDefaultCategory(category.name.trim())
    }

    suspend fun reorderCategories(categories: List<CategoryEntity>) {
        for ((index, cat) in categories.withIndex()) {
            categoryDao.updateCategoryOrder(cat.id, index)
        }
    }

    suspend fun exportToJson(): String {
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

            // Import categories
            for (cat in backup.categories) {
                val existing = categoryDao.getCategoryByName(cat.name)
                if (existing == null) {
                    categoryDao.insertCategory(cat.copy(id = 0L))
                    catCount++
                }
            }

            // Import notes with AES-256 encryption at rest
            for (note in backup.notes) {
                val catName = note.category.trim().ifBlank { "Umum" }
                val existingCat = categoryDao.getCategoryByName(catName)
                val finalCatName = if (existingCat != null) {
                    existingCat.name
                } else {
                    categoryDao.insertCategory(CategoryEntity(name = catName, colorHex = "#EC4899", isDefault = false))
                    catCount++
                    catName
                }
                val toInsert = encryptNote(note.copy(id = 0L, category = finalCatName))
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

            // Import categories from decoded result
            for (cat in decoded.categories) {
                val existing = categoryDao.getCategoryByName(cat.name)
                if (existing == null) {
                    categoryDao.insertCategory(cat.copy(id = 0L))
                    catCount++
                }
            }

            // Import notes with field-level AES-256 encryption at rest
            for (note in decoded.notes) {
                val catName = note.category.trim().ifBlank { "Umum" }
                val existingCat = categoryDao.getCategoryByName(catName)
                val finalCatName = if (existingCat != null) {
                    existingCat.name
                } else {
                    categoryDao.insertCategory(CategoryEntity(name = catName, colorHex = "#EC4899", isDefault = false))
                    catCount++
                    catName
                }
                // Note that note.isHidden is strictly preserved!
                val toInsert = encryptNote(note.copy(id = 0L, category = finalCatName, isHidden = note.isHidden))
                noteDao.insertNote(toInsert)
                noteCount++
            }

            Triple(catCount, noteCount, decoded.description)
        }
    }
}
