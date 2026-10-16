package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryEntity

private val PRESET_COLORS = listOf(
    "#2563EB", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6",
    "#EC4899", "#06B6D4", "#F97316", "#64748B", "#14B8A6"
)

@Composable
fun ManageCategoriesDialog(
    categories: List<CategoryEntity>,
    categoryCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onAddClick: () -> Unit,
    onEditCategory: (category: CategoryEntity, newName: String, newColor: String) -> Unit,
    onDeleteCategory: (category: CategoryEntity) -> Unit,
    onMoveUp: (category: CategoryEntity) -> Unit,
    onMoveDown: (category: CategoryEntity) -> Unit,
    onMoveAllNotes: (fromCategory: String, toCategory: String) -> Unit = { _, _ -> }
) {
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editColor by remember { mutableStateOf("#2563EB") }

    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var transferCategoryFrom by remember { mutableStateOf<CategoryEntity?>(null) }
    var transferCategoryTo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kelola Kategori",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
            ) {
                Text(
                    text = "Atur urutan (panah atas/bawah), ubah nama, ganti warna, pindahkan catatan, atau hapus kategori kustom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(categories, key = { _, item -> item.id }) { index, cat ->
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        // Robust case-insensitive and trimmed category count lookup
                        val count = categoryCounts[cat.name.trim().lowercase()] ?: categoryCounts[cat.name] ?: 0

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Color Dot
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        if (cat.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "Bawaan",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "$count catatan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Reorder Buttons: Up & Down
                                IconButton(
                                    onClick = { onMoveUp(cat) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Pindah Ke Atas",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onMoveDown(cat) },
                                    enabled = index < categories.size - 1,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Pindah Ke Bawah",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Transfer notes button
                                IconButton(
                                    onClick = {
                                        transferCategoryFrom = cat
                                        val other = categories.firstOrNull { it.id != cat.id }
                                        transferCategoryTo = other?.name ?: "Umum"
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DriveFileMove,
                                        contentDescription = "Pindahkan Catatan Kategori",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                // Edit Button
                                IconButton(
                                    onClick = {
                                        editingCategory = cat
                                        editName = cat.name
                                        editColor = cat.colorHex
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Kategori",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Delete Button (Disabled for default categories)
                                if (!cat.isDefault) {
                                    IconButton(
                                        onClick = { categoryToDelete = cat },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus Kategori",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(30.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAddClick,
                modifier = Modifier.testTag("dialog_add_category_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah Kategori")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Selesai")
            }
        }
    )

    // Edit Category Sub-Dialog
    editingCategory?.let { targetCat ->
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = {
                Text(
                    text = "Edit Kategori",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Nama Kategori",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Pilih Warna Kategori",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in PRESET_COLORS.take(5)) {
                            val isSel = editColor.equals(col, ignoreCase = true)
                            val c = Color(android.graphics.Color.parseColor(col))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { editColor = col }
                                    .border(
                                        width = if (isSel) 2.dp else 0.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in PRESET_COLORS.drop(5)) {
                            val isSel = editColor.equals(col, ignoreCase = true)
                            val c = Color(android.graphics.Color.parseColor(col))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { editColor = col }
                                    .border(
                                        width = if (isSel) 2.dp else 0.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditCategory(targetCat, editName.trim(), editColor)
                        editingCategory = null
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text("Simpan Perubahan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingCategory = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Move All Notes from Category to Another Sub-Dialog
    transferCategoryFrom?.let { fromCat ->
        AlertDialog(
            onDismissRequest = { transferCategoryFrom = null },
            title = {
                Text("Pindahkan Semua Catatan")
            },
            text = {
                Column {
                    Text("Pindahkan semua catatan dari '${fromCat.name}' ke kategori tujuan:")
                    Spacer(modifier = Modifier.height(10.dp))
                    val destinationOptions = categories.filter { it.id != fromCat.id }
                    for (dest in destinationOptions) {
                        val isSel = transferCategoryTo.equals(dest.name, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { transferCategoryTo = dest.name }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dest.name, modifier = Modifier.weight(1f), fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMoveAllNotes(fromCat.name, transferCategoryTo)
                        transferCategoryFrom = null
                    },
                    enabled = transferCategoryTo.isNotBlank() && !transferCategoryTo.equals(fromCat.name, ignoreCase = true)
                ) {
                    Text("Pindahkan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { transferCategoryFrom = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    categoryToDelete?.let { delCat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = {
                Text("Hapus Kategori '${delCat.name}'?")
            },
            text = {
                Text("Kategori akan dihapus. Semua catatan di dalam kategori ini akan otomatis dialihkan ke kategori 'Umum' sehingga tidak ada catatan yang hilang.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(delCat)
                        categoryToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { categoryToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
