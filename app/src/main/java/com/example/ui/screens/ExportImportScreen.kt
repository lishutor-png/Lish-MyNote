package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CategoryEntity
import com.example.util.DecodedResult
import com.example.util.SmartTextDecoder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    totalNotesCount: Int,
    totalCategoriesCount: Int,
    categories: List<CategoryEntity> = emptyList(),
    onBack: () -> Unit,
    getExportData: suspend () -> String,
    onImportData: (rawContent: String, password: String?) -> Unit,
    onImportSingleNote: (title: String, content: String, category: String, isHidden: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var rawInputText by remember { mutableStateOf("") }
    var decryptionPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loadedFileName by remember { mutableStateOf<String?>(null) }

    // Custom import target settings (for single note import to guarantee no content is dropped)
    var customTitle by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Umum") }
    var saveToHiddenVault by remember { mutableStateOf(false) }
    var importAsSingleNote by remember { mutableStateOf(true) }
    var showFullTextDialog by remember { mutableStateOf(false) }

    // Real-time decoding and format detection using SmartTextDecoder
    val decodedResult: DecodedResult? = remember(rawInputText, decryptionPassword) {
        if (rawInputText.isNotBlank()) {
            SmartTextDecoder.decodeAndParse(rawInputText, decryptionPassword.ifBlank { null })
        } else {
            null
        }
    }

    // Auto-detect secret note status and category from decoded payload
    androidx.compose.runtime.LaunchedEffect(decodedResult) {
        decodedResult?.let { res ->
            if (res.notes.isNotEmpty()) {
                val first = res.notes.first()
                if (first.isHidden) {
                    saveToHiddenVault = true
                }
                if (first.category.isNotBlank() && (selectedCategory == "Umum" || selectedCategory.isBlank())) {
                    selectedCategory = first.category
                }
            }
        }
    }

    // Auto-fill title from decoded result or filename if user hasn't typed a custom title
    val effectiveTitle = remember(decodedResult, loadedFileName, customTitle) {
        if (customTitle.isNotBlank()) {
            customTitle
        } else if (decodedResult != null && decodedResult.fullDocumentTitle.isNotBlank()) {
            decodedResult.fullDocumentTitle
        } else if (!loadedFileName.isNullOrBlank()) {
            loadedFileName!!.substringBeforeLast(".")
        } else {
            "Catatan Baru"
        }
    }

    BackHandler {
        onBack()
    }

    // Export Document Launcher (SAF CreateDocument)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonData = getExportData()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonData.toByteArray(Charsets.UTF_8))
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun getFileName(uri: Uri): String {
        var name = "file.txt"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }

    // Import Document Launcher (SAF OpenDocument) - Supports .txt, .json, .sql, and all text files
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fName = getFileName(uri)
                loadedFileName = fName
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    rawInputText = content
                    customTitle = fName.substringBeforeLast(".")
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cadangan & Impor File",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("export_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Advanced Local Encryption Information Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Advanced Local Encryption (AES-256)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Judul dan isi catatan Anda dienkripsi menggunakan standar kriptografi tingkat field AES-256 sebelum disimpan di database lokal SQLite Room. Dekripsi hanya terjadi di memori (in-memory).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Saat ini: $totalNotesCount catatan tersimpan dalam $totalCategoriesCount kategori.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Import Section: Upload .txt / .json and Decrypt
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Unggah & Impor File (.txt / .json / .sql)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Pilih file .txt atau tempel teks dokumen Anda. Aplikasi menjamin seluruh isi catatan masuk secara utuh tanpa terpotong.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // File picker buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                openDocumentLauncher.launch(
                                    arrayOf(
                                        "text/plain",
                                        "application/json",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_open_file_button")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih File .txt")
                        }

                        OutlinedButton(
                            onClick = {
                                openDocumentLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_open_json_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih .json / .sql")
                        }
                    }

                    if (loadedFileName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "File terpilih: $loadedFileName (${rawInputText.length} karakter)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text Field for manual text / ciphertext paste
                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = {
                            rawInputText = it
                            loadedFileName = null
                        },
                        label = { Text("Atau Tempel Teks Catatan / Dokumen di Sini") },
                        placeholder = { Text("Tempel teks .txt, enkripsi AES-256, Base64, atau JSON...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .testTag("import_raw_input_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password input (if note was encrypted with a custom password)
                    OutlinedTextField(
                        value = decryptionPassword,
                        onValueChange = { decryptionPassword = it },
                        label = { Text("Kata Sandi Dekripsi (Opsional)") },
                        placeholder = { Text("Masukkan sandi jika file dilindungi password") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Sembunyikan" else "Tampilkan"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_password_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Detected Info & Decryption Status
                    if (decodedResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val isSuccess = decodedResult.success && decodedResult.notes.isNotEmpty()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSuccess) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isSuccess) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = decodedResult.description,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSuccess) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (decodedResult.detectedMethod.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Metode: ${decodedResult.detectedMethod} • ${decodedResult.totalCharacters} Karakter • ${decodedResult.totalWords} Kata",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Button to view entire text
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { showFullTextDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Periksa Seluruh Teks (${decodedResult.totalCharacters} Karakter)")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Import Mode Selection: 1 Catatan Lengkap vs Multi Catatan
                        Text(
                            text = "Pilih Mode Impor:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = importAsSingleNote,
                                onClick = { importAsSingleNote = true },
                                label = { Text("📄 1 Catatan Utuh (Lengkap)") },
                                leadingIcon = if (importAsSingleNote) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )

                            if (decodedResult.notes.size > 1) {
                                FilterChip(
                                    selected = !importAsSingleNote,
                                    onClick = { importAsSingleNote = false },
                                    label = { Text("📚 Pisah (${decodedResult.notes.size} Catatan)") },
                                    leadingIcon = if (!importAsSingleNote) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Single note settings (Category & Vault destination)
                        if (importAsSingleNote) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Kategori:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val dynamicCats = (categories.map { it.name } + (decodedResult?.categories?.map { it.name } ?: emptyList())).distinct()
                                val catOptions = if (dynamicCats.isNotEmpty()) dynamicCats else listOf("Umum", "Pekerjaan", "Pribadi", "Ide", "Penting")
                                for (catName in catOptions) {
                                    val isSel = selectedCategory.equals(catName, ignoreCase = true)
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedCategory = catName },
                                        label = { Text(catName) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Secret / Hidden Note Toggle
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (saveToHiddenVault) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { saveToHiddenVault = !saveToHiddenVault }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (saveToHiddenVault) Icons.Default.Shield else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = if (saveToHiddenVault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (saveToHiddenVault) "Simpan ke Brankas Tersembunyi (Catatan Rahasia)" else "Simpan ke Daftar Utama",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = if (saveToHiddenVault) "Hanya tampil setelah verifikasi brankas" else "Tampil di halaman utama catatan",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = saveToHiddenVault,
                                        onCheckedChange = { saveToHiddenVault = it }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val canImport = decodedResult != null && decodedResult.success && decodedResult.notes.isNotEmpty()

                    // Primary Import Action Button
                    Button(
                        onClick = {
                            if (decodedResult != null && importAsSingleNote) {
                                // Import 100% of content as one complete note!
                                val contentToSave = decodedResult.fullDocumentContent.ifBlank { rawInputText }
                                onImportSingleNote(
                                    "",
                                    contentToSave,
                                    selectedCategory,
                                    saveToHiddenVault
                                )
                                rawInputText = ""
                                decryptionPassword = ""
                                loadedFileName = null
                                customTitle = ""
                                saveToHiddenVault = false
                            } else if (rawInputText.isNotBlank()) {
                                onImportData(rawInputText, decryptionPassword.ifBlank { null })
                                rawInputText = ""
                                decryptionPassword = ""
                                loadedFileName = null
                                customTitle = ""
                                saveToHiddenVault = false
                            }
                        },
                        enabled = canImport || rawInputText.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_submit_text_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (decodedResult != null) {
                                if (importAsSingleNote) {
                                    "Simpan Sebagai 1 Catatan Lengkap (${decodedResult.totalCharacters} Karakter)"
                                } else {
                                    "Impor Semua (${decodedResult.notes.size} Catatan Terpisah)"
                                }
                            } else {
                                "Proses & Simpan Catatan"
                            }
                        )
                    }
                }
            }

            // Export Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Ekspor Cadangan Catatan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Simpan seluruh catatan dan kategori ke file JSON agar aman dan dapat dipindahkan ke perangkat lain kapan saja.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val filename = "lish_mynote_backup_${System.currentTimeMillis()}.json"
                            createDocumentLauncher.launch(filename)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_save_file_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan File Cadangan (.json)")
                    }
                }
            }
        }
    }

    // Full Decoded Text Verification Dialog
    if (showFullTextDialog && decodedResult != null) {
        Dialog(
            onDismissRequest = { showFullTextDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Pratinjau Seluruh Isi Teks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { showFullTextDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Teks Lengkap", decodedResult.fullDocumentContent))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Salin")
                            }
                        }
                    )
                }
            ) { padding ->
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Total ${decodedResult.totalCharacters} karakter • ${decodedResult.totalWords} kata • ${decodedResult.notes.size} catatan terdeteksi",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = decodedResult.fullDocumentContent,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
