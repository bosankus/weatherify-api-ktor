package domain.repository

import bose.ankush.data.model.Note
import domain.model.Result

/**
 * Repository interface for managing admin notes.
 */
interface NoteRepository {
    suspend fun createNote(note: Note): Result<Note>
    suspend fun getNotes(userEmail: String, page: Int, limit: Int, search: String?): Result<List<Note>>
    suspend fun getNoteCount(userEmail: String, search: String?): Result<Long>
    suspend fun updateNote(id: String, userEmail: String, note: Note): Result<Note?>
}
