package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteEntity
import com.example.util.DateUtils

/**
 * Note Card with comprehensive content display (no cut-offs), AES-256 badge,
 * category styling, time indicator, and options menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItemCard(
    note: NoteEntity,
    categoryColorHex: String? = null,
    expandAll: Boolean = true,
    onNoteClick: () -> Unit,
    onEditClick: () -> Unit,
    onToggleHiddenClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveCategoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember(expandAll) { mutableStateOf(expandAll) }

    val categoryColor = remember(categoryColorHex) {
        if (!categoryColorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(categoryColorHex))
            } catch (e: Exception) {
                Color(0xFF2563EB)
            }
        } else {
            Color(0xFF2563EB)
        }
    }

    val wordCount = remember(note.content) {
        note.content.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    val displayBody = remember(note.content) {
        note.content.trim()
    }

    val isLongContent = remember(displayBody) {
        displayBody.lines().size > 4 || displayBody.length > 180
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("note_card_${note.id}")
            .combinedClickable(
                onClick = onNoteClick,
                onLongClick = { showMenu = true }
            )
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Category Badge, Secret Shield / AES-256 Lock Indicator, Word count & Timestamp & Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.12f),
                        contentColor = categoryColor
                    ) {
                        Text(
                            text = note.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // Vault / AES-256 Lock Indicator
                    if (note.isHidden) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Catatan Rahasia Brankas",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Rahasia",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Terenkripsi AES-256",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    if (wordCount > 30) {
                        Text(
                            text = "$wordCount kata",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Waktu otomatis",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = DateUtils.formatNoteDateTime(note.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Three-dot Options Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("note_menu_button_${note.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu Opsi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Catatan") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onEditClick()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Pindah Kategori") },
                                leadingIcon = {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onMoveCategoryClick()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (note.isHidden) "Pindah ke Daftar Utama"
                                        else "Sembunyikan ke Brankas"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (note.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleHiddenClick()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Salin Seluruh Teks") },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Catatan Lish MyNote", note.content))
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Bagikan") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, note.content)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Catatan"))
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body display (Whole text shown without cutting off when expanded)
            if (displayBody.isNotBlank()) {
                Text(
                    text = displayBody,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 21.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                )

                // Inline Expand / Collapse Toggle if content is long
                if (isLongContent) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isExpanded) "Sembunyikan Sebagian ▲" else "Tampilkan Selengkapnya (Semua) ▼",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}
