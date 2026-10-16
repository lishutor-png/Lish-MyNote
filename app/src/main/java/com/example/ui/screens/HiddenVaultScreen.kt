package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import com.example.ui.components.NoteItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenVaultScreen(
    hiddenNotes: List<NoteEntity>,
    categories: List<CategoryEntity>,
    selectedCategory: String? = null,
    categoryCounts: Map<String, Int> = emptyMap(),
    expandAll: Boolean = true,
    onToggleExpandAll: () -> Unit = {},
    onCategorySelected: (String?) -> Unit = {},
    onLockVault: () -> Unit,
    onAddHiddenNoteClick: () -> Unit,
    onViewNoteClick: (NoteEntity) -> Unit = {},
    onEditNoteClick: (NoteEntity) -> Unit,
    onToggleHiddenClick: (NoteEntity) -> Unit,
    onDeleteNoteClick: (NoteEntity) -> Unit,
    onMoveCategoryClick: (NoteEntity) -> Unit = {}
) {
    // Intercept back navigation to lock vault safely
    BackHandler {
        onLockVault()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Brankas Tersembunyi",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Catatan Rahasia Terproteksi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onLockVault,
                            modifier = Modifier.testTag("vault_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali dan Kunci"
                            )
                        }
                    },
                    actions = {
                        // Toggle Expand / Collapse
                        IconButton(
                            onClick = onToggleExpandAll,
                            modifier = Modifier.testTag("vault_toggle_expand_button")
                        ) {
                            Icon(
                                imageVector = if (expandAll) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = if (expandAll) "Tampilan Ringkas" else "Tampilkan Semua Teks"
                            )
                        }

                        FilledTonalButton(
                            onClick = onLockVault,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("lock_vault_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kunci", fontSize = 12.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Category chips filter for Vault notes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedCategory == null || selectedCategory == "Semua",
                        onClick = { onCategorySelected("Semua") },
                        label = { Text("Semua (${hiddenNotes.size})") }
                    )

                    for (cat in categories) {
                        val count = categoryCounts[cat.name.trim().lowercase()] ?: categoryCounts[cat.name] ?: 0
                        val isSelected = selectedCategory.equals(cat.name, ignoreCase = true)
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        // Show chip if category has secret notes or is selected
                        if (count > 0 || isSelected) {
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategorySelected(cat.name) },
                                label = { Text("${cat.name} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catColor.copy(alpha = 0.2f),
                                    selectedLabelColor = catColor
                                )
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddHiddenNoteClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Tambah Catatan Rahasia") },
                modifier = Modifier.testTag("add_hidden_note_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Security Info Banner
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Catatan di dalam brankas ini terenkripsi AES-256 dan tersembunyi dari daftar utama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (hiddenNotes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Belum Ada Catatan Tersembunyi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Buat catatan baru menggunakan tombol di bawah, atau sembunyikan catatan dari halaman utama menggunakan menu opsi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(hiddenNotes, key = { it.id }) { note ->
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
