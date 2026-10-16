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
import com.example.ui.components.ManageCategoriesDialog
import com.example.ui.components.MoveNoteCategoryDialog
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
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
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
    val vaultCategoryCounts by viewModel.vaultCategoryCounts.collectAsState()
    val totalCategoryCounts by viewModel.totalCategoryCounts.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val vaultSelectedCategory by viewModel.vaultSelectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val expandAllNotes by viewModel.expandAllNotes.collectAsState()

    val showAddEditDialog by viewModel.showAddEditDialog.collectAsState()
    val editingNote by viewModel.editingNote.collectAsState()
    val viewingNote by viewModel.viewingNote.collectAsState()
    val showAddCategoryDialog by viewModel.showAddCategoryDialog.collectAsState()
    val showManageCategoriesDialog by viewModel.showManageCategoriesDialog.collectAsState()
    val movingCategoryNote by viewModel.movingCategoryNote.collectAsState()
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
                        expandAll = expandAllNotes,
                        onToggleExpandAll = { viewModel.toggleExpandAllNotes() },
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        onCategorySelected = { viewModel.setSelectedCategory(it) },
                        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                        onTripleLogoClick = onTriggerSecretVault,
                        onAddNoteClick = { viewModel.openAddNoteDialog(preselectedHidden = false) },
                        onViewNoteClick = { viewModel.openViewingNote(it) },
                        onEditNoteClick = { viewModel.openEditNoteDialog(it) },
                        onToggleHiddenClick = { viewModel.toggleHiddenStatus(it) },
                        onDeleteNoteClick = { viewModel.deleteNote(it) },
                        onMoveCategoryClick = { viewModel.openMoveCategoryDialog(it) },
                        onAddCategoryClick = { viewModel.openAddCategoryDialog() },
                        onOpenManageCategories = { viewModel.openManageCategoriesDialog() },
                        onNavigateExportImport = { viewModel.navigateTo(AppScreen.EXPORT_IMPORT) }
                    )
                }

                AppScreen.VAULT -> {
                    HiddenVaultScreen(
                        hiddenNotes = hiddenNotes,
                        categories = categories,
                        selectedCategory = vaultSelectedCategory,
                        categoryCounts = vaultCategoryCounts,
                        expandAll = expandAllNotes,
                        onToggleExpandAll = { viewModel.toggleExpandAllNotes() },
                        onCategorySelected = { viewModel.setVaultSelectedCategory(it) },
                        onLockVault = { viewModel.lockVault() },
                        onAddHiddenNoteClick = { viewModel.openAddNoteDialog(preselectedHidden = true) },
                        onViewNoteClick = { viewModel.openViewingNote(it) },
                        onEditNoteClick = { viewModel.openEditNoteDialog(it) },
                        onToggleHiddenClick = { viewModel.toggleHiddenStatus(it) },
                        onDeleteNoteClick = { viewModel.deleteNote(it) },
                        onMoveCategoryClick = { viewModel.openMoveCategoryDialog(it) }
                    )
                }

                AppScreen.EXPORT_IMPORT -> {
                    ExportImportScreen(
                        totalNotesCount = allVisibleNotes.size + hiddenNotes.size,
                        totalCategoriesCount = categories.size,
                        categories = categories,
                        onBack = { viewModel.navigateTo(AppScreen.MAIN) },
                        getExportData = { viewModel.getExportJson() },
                        onImportData = { content, password ->
                            viewModel.importBackup(content, password)
                        },
                        onImportSingleNote = { title, content, category, isHidden ->
                            viewModel.importSingleNote(title, content, category, isHidden)
                        }
                    )
                }
            }

            // Reader / Viewer Dialog for comfortable reading of notes
            viewingNote?.let { noteToView ->
                val catColorHex = categories.find { it.name.equals(noteToView.category, ignoreCase = true) }?.colorHex
                NoteDetailDialog(
                    note = noteToView,
                    categoryColorHex = catColorHex,
                    onDismiss = { viewModel.dismissViewingNote() },
                    onEditClick = { viewModel.openEditNoteDialog(noteToView) },
                    onDeleteClick = { viewModel.deleteNote(noteToView) }
                )
            }

            // Dialog: Add / Edit Note (Identical rich form for normal and secret notes)
            if (showAddEditDialog) {
                AddEditNoteDialog(
                    initialNote = editingNote,
                    categories = categories,
                    onSave = { title, content, category, isHidden ->
                        viewModel.saveNote(title, content, category, isHidden)
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

            // Dialog: Manage Categories (Move order, add, edit, delete, transfer notes)
            if (showManageCategoriesDialog) {
                ManageCategoriesDialog(
                    categories = categories,
                    categoryCounts = totalCategoryCounts,
                    onDismiss = { viewModel.dismissManageCategoriesDialog() },
                    onAddClick = { viewModel.openAddCategoryDialog() },
                    onEditCategory = { cat, newName, newColor ->
                        viewModel.editCategory(cat, newName, newColor)
                    },
                    onDeleteCategory = { cat ->
                        viewModel.deleteCategory(cat)
                    },
                    onMoveUp = { cat ->
                        viewModel.moveCategoryUp(cat)
                    },
                    onMoveDown = { cat ->
                        viewModel.moveCategoryDown(cat)
                    },
                    onMoveAllNotes = { fromCat, toCat ->
                        viewModel.moveAllNotesCategory(fromCat, toCat)
                    }
                )
            }

            // Dialog: Move Note Category
            movingCategoryNote?.let { noteToMove ->
                MoveNoteCategoryDialog(
                    note = noteToMove,
                    categories = categories,
                    onDismiss = { viewModel.dismissMoveCategoryDialog() },
                    onCategorySelected = { newCat ->
                        viewModel.moveNoteToCategory(noteToMove, newCat)
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
