package com.example.ui.viewmodel

import android.app.Application
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
import android.content.Context
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

    // Filter and Search
    private val _selectedCategory = MutableStateFlow<String?>("Semua")
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Categories
    val categories: StateFlow<List<CategoryEntity>> = repository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Visible Notes (unfiltered by category for global count)
    val allVisibleNotes: StateFlow<List<NoteEntity>> = repository.getVisibleNotes(null, "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Counts per category
    val categoryCounts: StateFlow<Map<String, Int>> = allVisibleNotes
        .map { list -> list.groupBy { it.category }.mapValues { it.value.size } }
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
    val hiddenNotes: StateFlow<List<NoteEntity>> = _searchQuery
        .flatMapLatest { query -> repository.getHiddenNotes(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Add Category Dialog
    private val _showAddCategoryDialog = MutableStateFlow(false)
    val showAddCategoryDialog: StateFlow<Boolean> = _showAddCategoryDialog.asStateFlow()

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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun unlockVault() {
        _isVaultUnlocked.value = true
        _currentScreen.value = AppScreen.VAULT
        showMessage("Akses terbuka")
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        if (_currentScreen.value == AppScreen.VAULT) {
            _currentScreen.value = AppScreen.MAIN
        }
        showMessage("Terkunci")
    }

    fun openViewingNote(note: NoteEntity) {
        _viewingNote.value = note
    }

    fun dismissViewingNote() {
        _viewingNote.value = null
    }

    fun openAddNoteDialog(preselectedHidden: Boolean = false) {
        _editingNote.value = if (preselectedHidden) {
            NoteEntity(content = "", category = "Pribadi", isHidden = true)
        } else {
            null
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

    fun saveNote(content: String, category: String, isHidden: Boolean) {
        if (content.isBlank()) {
            showMessage("Catatan tidak boleh kosong")
            return
        }

        viewModelScope.launch {
            val existing = _editingNote.value
            if (existing != null && existing.id > 0) {
                repository.updateNote(
                    existing.copy(
                        content = content.trim(),
                        category = category.trim().ifBlank { "Umum" },
                        isHidden = isHidden
                    )
                )
                // If currently viewing this note, update viewer too
                if (_viewingNote.value?.id == existing.id) {
                    _viewingNote.value = existing.copy(
                        content = content.trim(),
                        category = category.trim().ifBlank { "Umum" },
                        isHidden = isHidden,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                showMessage("Catatan berhasil diperbarui")
            } else {
                repository.insertNote(
                    content = content.trim(),
                    category = category.trim().ifBlank { "Umum" },
                    isHidden = isHidden
                )
                showMessage("Catatan baru tersimpan")
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
                showMessage("Catatan disembunyikan")
            } else {
                showMessage("Catatan ditampilkan kembali")
            }
        }
    }

    fun openAddCategoryDialog() {
        _showAddCategoryDialog.value = true
    }

    fun dismissAddCategoryDialog() {
        _showAddCategoryDialog.value = false
    }

    fun addCategory(name: String, colorHex: String) {
        if (name.isBlank()) {
            showMessage("Nama kategori tidak boleh kosong")
            return
        }
        viewModelScope.launch {
            repository.insertCategory(name.trim(), colorHex)
            showMessage("Kategori '${name.trim()}' berhasil ditambahkan")
            _showAddCategoryDialog.value = false
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        if (category.isDefault) {
            showMessage("Kategori bawaan tidak dapat dihapus")
            return
        }
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (_selectedCategory.value == category.name) {
                _selectedCategory.value = "Semua"
            }
            showMessage("Kategori '${category.name}' dihapus")
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

    fun importBackup(jsonString: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.importFromJson(jsonString)
            result.onSuccess { (cats, notes) ->
                val msg = "Berhasil memuat $notes catatan dan $cats kategori baru!"
                showMessage(msg)
                onComplete(true, msg)
            }.onFailure { e ->
                val msg = "Gagal memuat: ${e.localizedMessage ?: "Format tidak dikenali"}"
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
