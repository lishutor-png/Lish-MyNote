package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isHidden = 0 ORDER BY createdAt DESC")
    fun getVisibleNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isHidden = 1 ORDER BY createdAt DESC")
    fun getHiddenNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isHidden = 0 AND category = :category ORDER BY createdAt DESC")
    fun getVisibleNotesByCategory(category: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isHidden = 0 AND content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchVisibleNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isHidden = 1 AND content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchHiddenNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getAllNotesDirect(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>): List<Long>

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("UPDATE notes SET isHidden = :isHidden, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun setHiddenStatus(noteId: Long, isHidden: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes")
    suspend fun clearAllNotes()
}
