package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.ui.components.AddCategoryDialog
import com.example.ui.components.AddEditNoteDialog
import com.example.ui.components.FallbackPinDialog
import com.example.ui.components.NoteDetailDialog
import com.example.ui.screens.ExportImportScreen
import com.example.ui.screens.HiddenVaultScreen
import com.example.ui.screens.MainNotesScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.NoteViewModel
import com.example.ui.viewmodel.ThemeMode
import com.example.util.BiometricHelper

class MainActivity : FragmentActivity() {
    private val viewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                LishMyNoteApp(
                    activity = this,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun LishMyNoteApp(
    activity: FragmentActivity,
    viewModel: NoteViewModel
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val visibleNotes by viewModel.visibleNotes.collectAsState()
    val allVisibleNotes by viewModel.allVisibleNotes.collectAsState()
    val hiddenNotes by viewModel.hiddenNotes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val showAddEditDialog by viewModel.showAddEditDialog.collectAsState()
    val editingNote by viewModel.editingNote.collectAsState()
    val viewingNote by viewModel.viewingNote.collectAsState()
    val showAddCategoryDialog by viewModel.showAddCategoryDialog.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle user notifications
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Secret entry triggered exclusively on TRIPLE TAP of the app logo
    val onTriggerSecretVault = {
        if (BiometricHelper.isBiometricAvailable(activity)) {
            BiometricHelper.authenticate(
                activity = activity,
                title = "Verifikasi Keamanan",
                subtitle = "Pindai sidik jari atau gunakan kunci perangkat",
                onSuccess = {
                    viewModel.unlockVault()
                },
                onError = { _ ->
                    viewModel.showPinFallback()
                }
            )
        } else {
            viewModel.showPinFallback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.MAIN -> {
                    MainNotesScreen(
                        notes = visibleNotes,
                        allNotesCount = allVisibleNotes.size,
                        categories = categories,
                        categoryCounts = categoryCounts,
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        themeMode = themeMode,
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        onCategorySelected = { viewModel.setSelectedCategory(it) },
                        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                        onTripleLogoClick = onTriggerSecretVault,
                        onAddNoteClick = { viewModel.openAddNoteDialog(preselectedHidden = false) },
                        onViewNoteClick = { viewModel.openViewingNote(it) },
                        onEditNoteClick = { viewModel.openEditNoteDialog(it) },
                        onToggleHiddenClick = { viewModel.toggleHiddenStatus(it) },
                        onDeleteNoteClick = { viewModel.deleteNote(it) },
                        onAddCategoryClick = { viewModel.openAddCategoryDialog() },
                        onNavigateExportImport = { viewModel.navigateTo(AppScreen.EXPORT_IMPORT) }
                    )
                }

                AppScreen.VAULT -> {
                    HiddenVaultScreen(
                        hiddenNotes = hiddenNotes,
                        categories = categories,
                        onLockVault = { viewModel.lockVault() },
                        onAddHiddenNoteClick = { viewModel.openAddNoteDialog(preselectedHidden = true) },
                        onViewNoteClick = { viewModel.openViewingNote(it) },
                        onEditNoteClick = { viewModel.openEditNoteDialog(it) },
                        onToggleHiddenClick = { viewModel.toggleHiddenStatus(it) },
                        onDeleteNoteClick = { viewModel.deleteNote(it) }
                    )
                }

                AppScreen.EXPORT_IMPORT -> {
                    ExportImportScreen(
                        totalNotesCount = allVisibleNotes.size + hiddenNotes.size,
                        totalCategoriesCount = categories.size,
                        onBack = { viewModel.navigateTo(AppScreen.MAIN) },
                        getExportData = { viewModel.getExportJson() },
                        onImportData = { json ->
                            viewModel.importBackup(json) { _, _ -> }
                        }
                    )
                }
            }

            // Reader / Viewer Dialog for comfortable reading of notes and novel chapters
            viewingNote?.let { noteToView ->
                val catColorHex = categories.find { it.name == noteToView.category }?.colorHex
                NoteDetailDialog(
                    note = noteToView,
                    categoryColorHex = catColorHex,
                    onDismiss = { viewModel.dismissViewingNote() },
                    onEditClick = { viewModel.openEditNoteDialog(noteToView) },
                    onDeleteClick = { viewModel.deleteNote(noteToView) }
                )
            }

            // Dialog: Add / Edit Note (Asking solely for content & category as requested)
            if (showAddEditDialog) {
                AddEditNoteDialog(
                    initialNote = editingNote,
                    categories = categories,
                    onSave = { content, category, isHidden ->
                        viewModel.saveNote(content, category, isHidden)
                    },
                    onDismiss = { viewModel.dismissAddEditDialog() },
                    onAddNewCategoryClick = { viewModel.openAddCategoryDialog() }
                )
            }

            // Dialog: Add Custom Category
            if (showAddCategoryDialog) {
                AddCategoryDialog(
                    onDismiss = { viewModel.dismissAddCategoryDialog() },
                    onAdd = { name, colorHex ->
                        viewModel.addCategory(name, colorHex)
                    }
                )
            }

            // Dialog: Fallback PIN
            if (showPinDialog) {
                FallbackPinDialog(
                    onDismiss = { viewModel.dismissPinDialog() },
                    onSuccess = {
                        viewModel.dismissPinDialog()
                        viewModel.unlockVault()
                    }
                )
            }
        }
    }
}
