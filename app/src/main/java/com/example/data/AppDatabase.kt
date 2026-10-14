package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CategoryDao
import com.example.data.dao.NoteDao
import com.example.data.model.CategoryEntity
import com.example.data.model.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [NoteEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lish_mynote.db"
                )
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

            // Initial friendly note
            val now = System.currentTimeMillis()
            val welcomeNote = NoteEntity(
                content = "Selamat datang di Lish MyNote!\n\nSetiap entri memo di sini mencatat tanggal dan jam secara otomatis. Anda dapat mengelompokkan catatan berdasarkan kategori kustom, mencari catatan dengan cepat, serta mencadangkan dan memulihkan data kapan saja.",
                category = "Umum",
                createdAt = now,
                updatedAt = now,
                isHidden = false
            )
            noteDao.insertNote(welcomeNote)
        }
    }
}
