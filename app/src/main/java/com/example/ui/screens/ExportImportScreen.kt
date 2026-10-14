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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.util.DecodedResult
import com.example.util.SmartTextDecoder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    totalNotesCount: Int,
    totalCategoriesCount: Int,
    onBack: () -> Unit,
    getExportData: suspend () -> String,
    onImportData: (rawContent: String, password: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var rawInputText by remember { mutableStateOf("") }
    var decryptionPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loadedFileName by remember { mutableStateOf<String?>(null) }

    // Real-time decoding and format detection using SmartTextDecoder
    val decodedResult: DecodedResult? = remember(rawInputText, decryptionPassword) {
        if (rawInputText.isNotBlank()) {
            SmartTextDecoder.decodeAndParse(rawInputText, decryptionPassword.ifBlank { null })
        } else {
            null
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
                } catch (_: Exception) {
                    // Handled
                }
            }
        }
    }

    // Helper to query file name
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

    // Import Document Launcher (SAF OpenDocument) - Supports .txt, .json, and all text files
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                loadedFileName = getFileName(uri)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    rawInputText = content
                }
            } catch (_: Exception) {
                // Handled
            }
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
            // Status & Information Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cadangan & Pemulihan Fleksibel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mendukung impor file .txt, teks terenkripsi (otomatis dekripsi MD5/AES/Base64), dokumen bab/memo, serta format cadangan .json resmi.",
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
                            text = "Unggah & Impor File (.txt / .json)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Pilih file .txt (teks biasa / terenkripsi MD5/AES/Base64) atau file .json cadangan Anda.",
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
                                        "text/plain",
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
                            Text("Pilih File .json")
                        }
                    }

                    // Display loaded file name if any
                    if (loadedFileName != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "File dimuat: $loadedFileName",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Isi Teks / Hasil Unggahan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Tempel Clipboard",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)
                                    val text = item?.text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        rawInputText = text
                                        loadedFileName = null
                                    }
                                }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        placeholder = {
                            Text("Tempel teks atau pilih file .txt di atas (otomatis didekripsi jika dienkripsi)...")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("import_text_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Optional password input for encrypted files
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = decryptionPassword,
                        onValueChange = { decryptionPassword = it },
                        label = { Text("Kunci / Password Dekripsi (Opsional)") },
                        placeholder = { Text("Kosongkan untuk mencoba otomatis (MD5/AES default)") },
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
                        Spacer(modifier = Modifier.height(10.dp))
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
                                        text = "Metode: ${decodedResult.detectedMethod}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (decodedResult.rawTextPreview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Pratinjau: \"${decodedResult.rawTextPreview}...\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val canImport = decodedResult != null && decodedResult.success && decodedResult.notes.isNotEmpty()

                    Button(
                        onClick = {
                            if (rawInputText.isNotBlank()) {
                                onImportData(rawInputText, decryptionPassword.ifBlank { null })
                                rawInputText = ""
                                decryptionPassword = ""
                                loadedFileName = null
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
                            if (decodedResult != null && decodedResult.notes.isNotEmpty()) {
                                "Impor Sekarang (${decodedResult.notes.size} Catatan)"
                            } else {
                                "Proses & Impor Catatan"
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
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan ke File JSON")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val data = getExportData()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_SUBJECT, "Lish MyNote Backup")
                                        putExtra(Intent.EXTRA_TEXT, data)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Cadangan"))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan")
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val data = getExportData()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Lish MyNote Backup", data))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_copy_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Teks")
                        }
                    }
                }
            }
        }
    }
}
