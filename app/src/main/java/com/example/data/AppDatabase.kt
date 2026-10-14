package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CategoryDao
import com.example.data.dao.NoteDao
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import com.example.util.Aes256Crypt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [NoteEntity::class, CategoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lish_mynote.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database.categoryDao(), database.noteDao())
                    }
                }
            }
        }

        suspend fun populateInitialData(categoryDao: CategoryDao, noteDao: NoteDao) {
            val defaults = listOf(
                CategoryEntity(name = "Umum", colorHex = "#2563EB", isDefault = true),
                CategoryEntity(name = "Pekerjaan", colorHex = "#10B981", isDefault = true),
                CategoryEntity(name = "Pribadi", colorHex = "#F59E0B", isDefault = true),
                CategoryEntity(name = "Ide", colorHex = "#8B5CF6", isDefault = true),
                CategoryEntity(name = "Penting", colorHex = "#EF4444", isDefault = true)
            )
            for (category in defaults) {
                categoryDao.insertCategory(category)
            }

            // Initial friendly note with field-level AES-256 encryption
            val now = System.currentTimeMillis()
            val welcomeTitle = "Selamat Datang di Lish MyNote"
            val welcomeBody = "Setiap entri memo di sini mencatat tanggal dan jam secara otomatis.\n\nKeamanan Tingkat Tinggi (Advanced Local Encryption):\nJudul dan isi catatan Anda dienkripsi menggunakan standar kriptografi AES-256 sebelum disimpan di database lokal SQLite Room. Dekripsi hanya terjadi di memori (in-memory) saat aplikasi berjalan."
            
            val welcomeNote = NoteEntity(
                title = Aes256Crypt.encrypt(welcomeTitle),
                content = Aes256Crypt.encrypt(welcomeBody),
                category = "Umum",
                createdAt = now,
                updatedAt = now,
                isHidden = false
            )
            noteDao.insertNote(welcomeNote)
        }
    }
}
