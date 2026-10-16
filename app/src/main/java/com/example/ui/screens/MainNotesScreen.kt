package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import com.example.ui.components.NoteItemCard
import com.example.ui.viewmodel.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNotesScreen(
    notes: List<NoteEntity>,
    allNotesCount: Int,
    categories: List<CategoryEntity>,
    categoryCounts: Map<String, Int>,
    selectedCategory: String?,
    searchQuery: String,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    expandAll: Boolean = true,
    onToggleExpandAll: () -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTripleLogoClick: () -> Unit,
    onAddNoteClick: () -> Unit,
    onViewNoteClick: (NoteEntity) -> Unit,
    onEditNoteClick: (NoteEntity) -> Unit,
    onToggleHiddenClick: (NoteEntity) -> Unit,
    onDeleteNoteClick: (NoteEntity) -> Unit,
    onMoveCategoryClick: (NoteEntity) -> Unit = {},
    onAddCategoryClick: () -> Unit,
    onOpenManageCategories: () -> Unit = {},
    onNavigateExportImport: () -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    // Triple-click detector on logo to open secret vault without any hints or clues
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val handleLogoTap = {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 800L) {
            tapCount++
        } else {
            tapCount = 1
        }
        lastTapTime = now
        if (tapCount >= 3) {
            tapCount = 0
            onTripleLogoClick()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxWidth()
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Flat Design Logo (Triple click to trigger secret entrance)
                            Image(
                                painter = painterResource(id = R.drawable.lish_mynote_logo),
                                contentDescription = "Logo Lish MyNote",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { handleLogoTap() }
                                    .testTag("app_logo_main")
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lish MyNote",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    )
                                )
                                Text(
                                    text = "Catatan & Memo Terenkripsi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Toggle Full View / Compact View ("Semua bisa ditampilkan")
                        IconButton(
                            onClick = onToggleExpandAll,
                            modifier = Modifier.testTag("toggle_expand_all_button")
                        ) {
                            Icon(
                                imageVector = if (expandAll) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = if (expandAll) "Tampilan Ringkas" else "Tampilkan Semua Teks Catatan"
                            )
                        }

                        // Search Toggle
                        IconButton(
                            onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) onSearchQueryChanged("")
                            },
                            modifier = Modifier.testTag("toggle_search_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Clear else Icons.Default.Search,
                                contentDescription = "Cari Catatan"
                            )
                        }

                        // Manage Categories
                        IconButton(
                            onClick = onOpenManageCategories,
                            modifier = Modifier.testTag("manage_categories_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Kelola Kategori"
                            )
                        }

                        // Theme Mode Selector (Dark, Light, System)
                        Box {
                            IconButton(
                                onClick = { showThemeMenu = true },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = when (themeMode) {
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    },
                                    contentDescription = "Pilih Tema Tampilan"
                                )
                            }

                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tema Terang") },
                                    leadingIcon = {
                                        Icon(Icons.Default.LightMode, contentDescription = null)
                                    },
                                    trailingIcon = if (themeMode == ThemeMode.LIGHT) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        onSelectThemeMode(ThemeMode.LIGHT)
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tema Gelap") },
                                    leadingIcon = {
                                        Icon(Icons.Default.DarkMode, contentDescription = null)
                                    },
                                    trailingIcon = if (themeMode == ThemeMode.DARK) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        onSelectThemeMode(ThemeMode.DARK)
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ikuti Sistem") },
                                    leadingIcon = {
                                        Icon(Icons.Default.BrightnessAuto, contentDescription = null)
                                    },
                                    trailingIcon = if (themeMode == ThemeMode.SYSTEM) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        onSelectThemeMode(ThemeMode.SYSTEM)
                                        showThemeMenu = false
                                    }
                                )
                            }
                        }

                        // Export / Import
                        IconButton(
                            onClick = onNavigateExportImport,
                            modifier = Modifier.testTag("export_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ImportExport,
                                contentDescription = "Cadangan Data"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search Bar (Expanded)
                if (isSearchExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Cari isi catatan...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Hapus teks")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                // Category Chips with Note Counts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "Semua" Chip
                    FilterChip(
                        selected = selectedCategory == null || selectedCategory == "Semua",
                        onClick = { onCategorySelected("Semua") },
                        label = { Text("Semua ($allNotesCount)") },
                        modifier = Modifier.testTag("category_filter_Semua")
                    )

                    // User categories with accurate case-insensitive count
                    for (cat in categories) {
                        val isSelected = selectedCategory.equals(cat.name, ignoreCase = true)
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        // Robust case-insensitive and trimmed lookup
                        val count = categoryCounts[cat.name.trim().lowercase()] ?: categoryCounts[cat.name] ?: 0

                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelected(cat.name) },
                            label = { Text("${cat.name} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = catColor.copy(alpha = 0.16f),
                                selectedLabelColor = catColor
                            ),
                            modifier = Modifier.testTag("category_filter_${cat.name}")
                        )
                    }

                    // "+ Kategori" Chip to add new custom category
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAddCategoryClick() }
                            .testTag("add_category_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Kategori Baru",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // "Kelola Kategori" Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenManageCategories() }
                            .testTag("open_manage_categories_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Kelola",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNoteClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Tambah Catatan") },
                modifier = Modifier.testTag("add_note_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (notes.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NoteAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (searchQuery.isNotEmpty()) "Catatan tidak ditemukan"
                        else "Belum ada catatan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (searchQuery.isNotEmpty()) "Coba kata kunci pencarian yang lain."
                        else "Tekan tombol + di bawah untuk membuat memo baru, atau muat file cadangan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        val catColorHex = categories.find { it.name.equals(note.category, ignoreCase = true) }?.colorHex
                        NoteItemCard(
                            note = note,
                            categoryColorHex = catColorHex,
                            expandAll = expandAll,
                            onNoteClick = { onViewNoteClick(note) },
                            onEditClick = { onEditNoteClick(note) },
                            onToggleHiddenClick = { onToggleHiddenClick(note) },
                            onDeleteClick = { onDeleteNoteClick(note) },
                            onMoveCategoryClick = { onMoveCategoryClick(note) }
                        )
                    }
                }
            }
        }
    }
}
