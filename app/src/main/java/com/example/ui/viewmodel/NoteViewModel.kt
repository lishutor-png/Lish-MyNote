package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    MAIN,
    VAULT,
    EXPORT_IMPORT
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = NoteRepository(database.noteDao(), database.categoryDao())

    // Theme Preference State (System, Light, Dark)
    private val prefs = application.getSharedPreferences("lish_settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        when (prefs.getString("theme_mode", "SYSTEM")) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    // App Navigation State
    private val _currentScreen = MutableStateFlow(AppScreen.MAIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Filter and Search for Main Screen
    private val _selectedCategory = MutableStateFlow<String?>("Semua")
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filter for Vault Screen
    private val _vaultSelectedCategory = MutableStateFlow<String?>("Semua")
    val vaultSelectedCategory: StateFlow<String?> = _vaultSelectedCategory.asStateFlow()

    // Categories Flow
    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Visible Notes
    val allVisibleNotes: StateFlow<List<NoteEntity>> = repository.getVisibleNotes(null, "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Notes (Visible + Hidden)
    val allNotes: StateFlow<List<NoteEntity>> = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category Counts for Visible Notes (Normalized lowercase keys to fix count mismatch)
    val categoryCounts: StateFlow<Map<String, Int>> = allVisibleNotes
        .map { list ->
            val counts = mutableMapOf<String, Int>()
            for (note in list) {
                val key = note.category.trim().lowercase()
                counts[key] = (counts[key] ?: 0) + 1
            }
            counts
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Total Category Counts (Visible + Hidden)
    val totalCategoryCounts: StateFlow<Map<String, Int>> = allNotes
        .map { list ->
            val counts = mutableMapOf<String, Int>()
            for (note in list) {
                val key = note.category.trim().lowercase()
                counts[key] = (counts[key] ?: 0) + 1
            }
            counts
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Filtered Visible Notes Flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val visibleNotes: StateFlow<List<NoteEntity>> = combine(
        _selectedCategory,
        _searchQuery
    ) { cat, query -> Pair(cat, query) }
        .flatMapLatest { (cat, query) ->
            repository.getVisibleNotes(if (cat == "Semua") null else cat, query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hidden Notes Flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val hiddenNotes: StateFlow<List<NoteEntity>> = combine(
        _vaultSelectedCategory,
        _searchQuery
    ) { cat, query -> Pair(cat, query) }
        .flatMapLatest { (cat, query) ->
            repository.getHiddenNotes(if (cat == "Semua") null else cat, query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Vault Category Counts (for filtering in Vault)
    val vaultCategoryCounts: StateFlow<Map<String, Int>> = allNotes
        .map { list ->
            val counts = mutableMapOf<String, Int>()
            for (note in list.filter { it.isHidden }) {
                val key = note.category.trim().lowercase()
                counts[key] = (counts[key] ?: 0) + 1
            }
            counts
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Vault Security State
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    // Add / Edit Note Dialog
    private val _showAddEditDialog = MutableStateFlow(false)
    val showAddEditDialog: StateFlow<Boolean> = _showAddEditDialog.asStateFlow()

    private val _editingNote = MutableStateFlow<NoteEntity?>(null)
    val editingNote: StateFlow<NoteEntity?> = _editingNote.asStateFlow()

    // Reader / Viewer Dialog
    private val _viewingNote = MutableStateFlow<NoteEntity?>(null)
    val viewingNote: StateFlow<NoteEntity?> = _viewingNote.asStateFlow()

    // Category Management Dialogs
    private val _showAddCategoryDialog = MutableStateFlow(false)
    val showAddCategoryDialog: StateFlow<Boolean> = _showAddCategoryDialog.asStateFlow()

    private val _showManageCategoriesDialog = MutableStateFlow(false)
    val showManageCategoriesDialog: StateFlow<Boolean> = _showManageCategoriesDialog.asStateFlow()

    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)
    val editingCategory: StateFlow<CategoryEntity?> = _editingCategory.asStateFlow()

    private val _movingCategoryNote = MutableStateFlow<NoteEntity?>(null)
    val movingCategoryNote: StateFlow<NoteEntity?> = _movingCategoryNote.asStateFlow()

    // Full / Compact Content Display Toggle (Defaults to true: show full text without truncation)
    private val _expandAllNotes = MutableStateFlow(true)
    val expandAllNotes: StateFlow<Boolean> = _expandAllNotes.asStateFlow()

    fun toggleExpandAllNotes() {
        _expandAllNotes.value = !_expandAllNotes.value
    }

    // Fallback PIN Dialog
    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    // Notification / Snackbars
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        if (screen == AppScreen.VAULT && !_isVaultUnlocked.value) {
            return
        }
        _currentScreen.value = screen
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category ?: "Semua"
    }

    fun setVaultSelectedCategory(category: String?) {
        _vaultSelectedCategory.value = category ?: "Semua"
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun unlockVault() {
        _isVaultUnlocked.value = true
        _currentScreen.value = AppScreen.VAULT
        showMessage("Akses brankas terbuka")
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        if (_currentScreen.value == AppScreen.VAULT) {
            _currentScreen.value = AppScreen.MAIN
        }
        showMessage("Brankas terkunci")
    }

    fun openViewingNote(note: NoteEntity) {
        _viewingNote.value = note
    }

    fun dismissViewingNote() {
        _viewingNote.value = null
    }

    // Open Add Note Dialog (Same input form for normal and hidden notes)
    fun openAddNoteDialog(preselectedHidden: Boolean = false) {
        val defaultCat = if (preselectedHidden) {
            _vaultSelectedCategory.value?.takeIf { it != "Semua" } ?: "Umum"
        } else {
            _selectedCategory.value?.takeIf { it != "Semua" } ?: "Umum"
        }

        _editingNote.value = if (preselectedHidden) {
            NoteEntity(title = "", content = "", category = defaultCat, isHidden = true)
        } else {
            NoteEntity(title = "", content = "", category = defaultCat, isHidden = false)
        }
        _showAddEditDialog.value = true
    }

    fun openEditNoteDialog(note: NoteEntity) {
        _editingNote.value = note
        _showAddEditDialog.value = true
    }

    fun dismissAddEditDialog() {
        _showAddEditDialog.value = false
        _editingNote.value = null
    }

    fun saveNote(title: String = "", content: String, category: String, isHidden: Boolean) {
        if (content.isBlank() && title.isBlank()) {
            showMessage("Catatan tidak boleh kosong")
            return
        }

        viewModelScope.launch {
            val existing = _editingNote.value
            val catTrimmed = category.trim().ifBlank { "Umum" }

            if (existing != null && existing.id > 0) {
                val updated = existing.copy(
                    title = title.trim(),
                    content = content.trim(),
                    category = catTrimmed,
                    isHidden = isHidden,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateNote(updated)

                if (_viewingNote.value?.id == existing.id) {
                    _viewingNote.value = updated
                }
                showMessage("Catatan berhasil diperbarui")
            } else {
                repository.insertNote(
                    title = title.trim(),
                    content = content.trim(),
                    category = catTrimmed,
                    isHidden = isHidden
                )
                showMessage(if (isHidden) "Catatan rahasia tersimpan di brankas" else "Catatan baru tersimpan aman")
            }
            dismissAddEditDialog()
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
            if (_viewingNote.value?.id == note.id) {
                _viewingNote.value = null
            }
            showMessage("Catatan telah dihapus")
        }
    }

    fun toggleHiddenStatus(note: NoteEntity) {
        viewModelScope.launch {
            val newHidden = !note.isHidden
            repository.toggleHidden(note.id, newHidden)
            if (newHidden) {
                showMessage("Catatan dipindahkan ke Brankas Tersembunyi")
            } else {
                showMessage("Catatan dipindahkan ke Daftar Utama")
            }
        }
    }

    fun openMoveCategoryDialog(note: NoteEntity) {
        _movingCategoryNote.value = note
    }

    fun dismissMoveCategoryDialog() {
        _movingCategoryNote.value = null
    }

    fun moveNoteToCategory(note: NoteEntity, newCategory: String) {
        viewModelScope.launch {
            repository.moveNoteToCategory(note.id, newCategory)
            if (_viewingNote.value?.id == note.id) {
                _viewingNote.value = _viewingNote.value?.copy(category = newCategory)
            }
            _movingCategoryNote.value = null
            showMessage("Catatan dipindahkan ke kategori '$newCategory'")
        }
    }

    // Category Management
    fun openAddCategoryDialog() {
        _showAddCategoryDialog.value = true
    }

    fun dismissAddCategoryDialog() {
        _showAddCategoryDialog.value = false
    }

    fun openManageCategoriesDialog() {
        _showManageCategoriesDialog.value = true
    }

    fun dismissManageCategoriesDialog() {
        _showManageCategoriesDialog.value = false
        _editingCategory.value = null
    }

    fun openEditCategoryDialog(category: CategoryEntity) {
        _editingCategory.value = category
    }

    fun dismissEditCategoryDialog() {
        _editingCategory.value = null
    }

    fun addCategory(name: String, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            showMessage("Nama kategori tidak boleh kosong")
            return
        }
        viewModelScope.launch {
            repository.insertCategory(trimmed, colorHex)
            showMessage("Kategori '$trimmed' berhasil ditambahkan")
            _showAddCategoryDialog.value = false
        }
    }

    fun editCategory(category: CategoryEntity, newName: String, newColorHex: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            showMessage("Nama kategori tidak boleh kosong")
            return
        }
        viewModelScope.launch {
            val updated = category.copy(name = trimmed, colorHex = newColorHex)
            repository.updateCategory(updated, oldName = category.name)
            if (_selectedCategory.value.equals(category.name, ignoreCase = true)) {
                _selectedCategory.value = trimmed
            }
            if (_vaultSelectedCategory.value.equals(category.name, ignoreCase = true)) {
                _vaultSelectedCategory.value = trimmed
            }
            _editingCategory.value = null
            showMessage("Kategori '$trimmed' berhasil diperbarui")
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        if (category.isDefault) {
            showMessage("Kategori bawaan tidak dapat dihapus")
            return
        }
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (_selectedCategory.value.equals(category.name, ignoreCase = true)) {
                _selectedCategory.value = "Semua"
            }
            if (_vaultSelectedCategory.value.equals(category.name, ignoreCase = true)) {
                _vaultSelectedCategory.value = "Semua"
            }
            showMessage("Kategori '${category.name}' dihapus, catatan dialihkan ke 'Umum'")
        }
    }

    fun moveCategoryUp(category: CategoryEntity) {
        viewModelScope.launch {
            val list = categories.value.toMutableList()
            val index = list.indexOfFirst { it.id == category.id }
            if (index > 0) {
                val temp = list[index]
                list[index] = list[index - 1]
                list[index - 1] = temp
                repository.reorderCategories(list)
            }
        }
    }

    fun moveCategoryDown(category: CategoryEntity) {
        viewModelScope.launch {
            val list = categories.value.toMutableList()
            val index = list.indexOfFirst { it.id == category.id }
            if (index != -1 && index < list.size - 1) {
                val temp = list[index]
                list[index] = list[index + 1]
                list[index + 1] = temp
                repository.reorderCategories(list)
            }
        }
    }

    fun moveAllNotesCategory(fromCategory: String, toCategory: String) {
        viewModelScope.launch {
            repository.moveAllNotesCategory(fromCategory, toCategory)
            showMessage("Semua catatan dari '$fromCategory' dipindahkan ke '$toCategory'")
        }
    }

    fun showPinFallback() {
        _showPinDialog.value = true
    }

    fun dismissPinDialog() {
        _showPinDialog.value = false
    }

    suspend fun getExportJson(): String {
        return repository.exportToJson()
    }

    fun importSingleNote(
        title: String,
        content: String,
        category: String,
        isHidden: Boolean = false,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (content.isBlank() && title.isBlank()) {
            val msg = "Isi catatan kosong"
            showMessage(msg)
            onComplete(false, msg)
            return
        }

        viewModelScope.launch {
            try {
                val catTrimmed = category.trim().ifBlank { "Umum" }
                repository.insertNote(
                    title = title.trim(),
                    content = content.trim(),
                    category = catTrimmed,
                    isHidden = isHidden
                )
                val targetDesc = if (isHidden) "brankas tersembunyi" else "daftar utama"
                val msg = "Berhasil mengimpor catatan ke $targetDesc (${content.length} karakter utuh)"
                showMessage(msg)
                onComplete(true, msg)
            } catch (e: Exception) {
                val msg = "Gagal menyimpan: ${e.localizedMessage ?: "Kesalahan penyimpanan"}"
                showMessage(msg)
                onComplete(false, msg)
            }
        }
    }

    fun importBackup(
        rawContent: String,
        password: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = repository.importFromSmartText(rawContent, password)
            result.onSuccess { (cats, notes, desc) ->
                val msg = "Berhasil memulihkan $notes catatan ($desc)"
                showMessage(msg)
                onComplete(true, msg)
            }.onFailure { e ->
                val msg = "Gagal memuat: ${e.localizedMessage ?: "Format tidak dapat dikenali"}"
                showMessage(msg)
                onComplete(false, msg)
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    private fun showMessage(msg: String) {
        _userMessage.value = msg
    }
}
