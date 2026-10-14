package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BackupData
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import com.example.util.Aes256Crypt
import com.example.util.DateUtils
import com.example.util.SmartTextDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Lish MyNote", appName)
  }

  @Test
  fun `test AES-256 field level encryption and in-memory decryption`() {
    val sampleTitle = "Rencana Keamanan Siber 2026"
    val sampleContent = "Ini adalah dokumen rahasia perusahaan yang harus dienkripsi menggunakan AES-256 tingkat field sebelum disimpan ke database SQLite Room lokal."

    // Encrypt fields
    val encryptedTitle = Aes256Crypt.encrypt(sampleTitle)
    val encryptedContent = Aes256Crypt.encrypt(sampleContent)

    // Verify ciphertext is encrypted (not plain text)
    assertNotEquals(sampleTitle, encryptedTitle)
    assertNotEquals(sampleContent, encryptedContent)
    assertTrue(Aes256Crypt.isEncrypted(encryptedTitle))
    assertTrue(Aes256Crypt.isEncrypted(encryptedContent))

    // In-memory decryption
    val decryptedTitle = Aes256Crypt.decrypt(encryptedTitle)
    val decryptedContent = Aes256Crypt.decrypt(encryptedContent)

    assertEquals(sampleTitle, decryptedTitle)
    assertEquals(sampleContent, decryptedContent)
  }

  @Test
  fun `test full document text preservation when importing file`() {
    val longNoteDocument = """
      # Catatan Pertemuan dan Rencana Strategis
      Tanggal: 14 Oktober 2026
      
      Bagian 1: Analisis Kebutuhan
      - Poin A: Memperbaiki sistem impor file .txt agar isi catatan tidak terpotong.
      - Poin B: Menggunakan Advanced Local Encryption AES-256 pada judul dan isi catatan.
      
      Bagian 2: Tindak Lanjut
      Semua tim sepakat untuk memastikan seluruh paragraf, tanda baca, dan karakter tetap utuh saat dimasukkan.
      
      Penutup: Selesai dengan sukses.
    """.trimIndent()

    val decoded = SmartTextDecoder.decodeAndParse(longNoteDocument)
    assertTrue(decoded.success)
    // Full document content must retain 100% of characters
    assertEquals(longNoteDocument, decoded.fullDocumentContent)
    assertTrue(decoded.notes.isNotEmpty())
    assertTrue(decoded.totalCharacters > 200)
    assertTrue(decoded.notes[0].content.contains("Penutup: Selesai dengan sukses."))
  }

  @Test
  fun `test header formatted note parsing with Judul and Isi`() {
    val headerDoc = """
      Judul: Ringkasan Kuliah Algoritma
      Kategori: Pendidikan
      Isi:
      Algoritma enkripsi AES-256 menggunakan kunci berukuran 256-bit.
      Keamanannya sangat tinggi dan standar industri global.
      Semua penjelasan ini harus masuk secara lengkap.
    """.trimIndent()

    val decoded = SmartTextDecoder.decodeAndParse(headerDoc)
    assertTrue(decoded.success)
    assertEquals(1, decoded.notes.size)
    assertEquals("Ringkasan Kuliah Algoritma", decoded.notes[0].title)
    assertTrue(decoded.notes[0].content.contains("Semua penjelasan ini harus masuk secara lengkap."))
    assertEquals("Pendidikan", decoded.notes[0].category)
  }

  @Test
  fun `test backup data json serialization and deserialization`() {
    val testNotes = listOf(
        NoteEntity(
            id = 1L,
            title = "Rapat Proyek",
            content = "Catatan Penting Rapat Proyek",
            category = "Pekerjaan",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            isHidden = true
        )
    )
    val testCategories = listOf(
        CategoryEntity(id = 1L, name = "Pekerjaan", colorHex = "#10B981", isDefault = true)
    )

    val backup = BackupData(
        categories = testCategories,
        notes = testNotes
    )

    val jsonString = backup.toJsonString()
    assertNotNull(jsonString)
    assertTrue(jsonString.contains("Rapat Proyek"))
    assertTrue(jsonString.contains("Catatan Penting Rapat Proyek"))
    assertTrue(jsonString.contains("Pekerjaan"))

    val restored = BackupData.fromJsonString(jsonString)
    assertEquals(1, restored.notes.size)
    assertEquals("Rapat Proyek", restored.notes[0].title)
    assertEquals("Catatan Penting Rapat Proyek", restored.notes[0].content)
    assertEquals(true, restored.notes[0].isHidden)
    assertEquals(1, restored.categories.size)
    assertEquals("Pekerjaan", restored.categories[0].name)
  }

  @Test
  fun `test import generic array of notes json`() {
    val genericJson = """
      [
        {
          "title": "Ide Bisnis Kreatif",
          "content": "Rencana pembukaan toko online berbasis memo kustom.",
          "category": "Bisnis",
          "createdAt": 1700000000000
        },
        {
          "title": "Daftar Belanja",
          "text": "Beli kopi, kertas binder, dan pena.",
          "category": "Pribadi",
          "date": "2024-05-10 10:30:00"
        }
      ]
    """.trimIndent()

    val detection = BackupData.detectFormatInfo(genericJson)
    assertTrue(detection.contains("2 catatan terdeteksi"))

    val imported = BackupData.fromJsonString(genericJson)
    assertEquals("Lish MyNote", imported.appName)
    assertEquals(2, imported.notes.size)
    assertEquals(2, imported.categories.size)
    assertEquals("Ide Bisnis Kreatif", imported.notes[0].title)
    assertTrue(imported.notes[0].content.contains("Rencana pembukaan toko online"))
    assertEquals("Bisnis", imported.notes[0].category)
    assertEquals("Daftar Belanja", imported.notes[1].title)
    assertTrue(imported.notes[1].content.contains("Beli kopi, kertas binder"))
    assertEquals("Pribadi", imported.notes[1].category)
  }

  @Test
  fun `test smart text decoder with base64 encoded json`() {
    val rawJson = """[{"title":"Catatan Rahasia","content":"Informasi terenkripsi penting"}]"""
    val b64 = Base64.encodeToString(rawJson.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    val decoded = SmartTextDecoder.decodeAndParse(b64)
    assertTrue(decoded.success)
    assertEquals(1, decoded.notes.size)
    assertEquals("Catatan Rahasia", decoded.notes[0].title)
    assertTrue(decoded.notes[0].content.contains("Informasi terenkripsi penting"))
  }

  @Test
  fun `test smart text decoder with AES MD5 encrypted payload`() {
    val originalText = "Catatan Penting dengan Kunci MD5"
    val md5Key = SmartTextDecoder.md5("Lish MyNote")
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(md5Key, "AES"))
    val encryptedBytes = cipher.doFinal(originalText.toByteArray(StandardCharsets.UTF_8))
    val encryptedB64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

    // Automatically decrypts using default candidate keys
    val decoded = SmartTextDecoder.decodeAndParse(encryptedB64)
    assertTrue(decoded.success)
    assertEquals(1, decoded.notes.size)
    assertTrue(decoded.notes[0].content.contains(originalText))
  }

  @Test
  fun `test date formatting`() {
    val timestamp = System.currentTimeMillis()
    val formatted = DateUtils.formatNoteDateTime(timestamp)
    assertTrue(formatted.startsWith("Hari ini"))
  }
}
