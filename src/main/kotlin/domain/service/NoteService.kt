package domain.service

import bose.ankush.data.model.Note
import domain.model.Result
import domain.repository.NoteRepository

/**
 * Service class for managing admin notes.
 */
class NoteService(private val repository: NoteRepository) {

    suspend fun createNote(userEmail: String, content: String, contentFormat: String): Result<Note> {
        if (content.isBlank()) {
            return Result.error("Note content cannot be empty")
        }
        val note = Note(
            userEmail = userEmail,
            content = content,
            contentFormat = contentFormat
        )
        return repository.createNote(note)
    }

    suspend fun getNotes(userEmail: String, page: Int, limit: Int, search: String?): Result<List<Note>> {
        return repository.getNotes(userEmail, page, limit, search)
    }

    suspend fun getNoteCount(userEmail: String, search: String?): Result<Long> {
        return repository.getNoteCount(userEmail, search)
    }

    suspend fun updateNote(id: String, userEmail: String, content: String, contentFormat: String): Result<Note?> {
        if (content.isBlank()) {
            return Result.error("Note content cannot be empty")
        }
        val note = Note(
            id = org.bson.types.ObjectId(id),
            userEmail = userEmail,
            content = content,
            contentFormat = contentFormat
        )
        return repository.updateNote(id, userEmail, note)
    }
}
