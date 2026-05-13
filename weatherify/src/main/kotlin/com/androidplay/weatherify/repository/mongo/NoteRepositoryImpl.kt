package com.androidplay.weatherify.repository.mongo

import com.androidplay.weatherify.domain.Note
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.androidplay.weatherify.db.WeatherifyDb
import com.androidplay.core.common.Result
import com.androidplay.weatherify.repository.NoteRepository
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId

import java.time.Instant

/**
 * Implementation of NoteRepository that uses MongoDB for data storage.
 */
class NoteRepositoryImpl(private val databaseModule: WeatherifyDb) : NoteRepository {

    override suspend fun createNote(note: Note): Result<Note> {
        return try {
            // Validate content is not empty
            if (note.content.isBlank()) {
                return Result.error("Note content cannot be empty")
            }

            val result = databaseModule.getNotesCollection().insertOne(note)
            if (result.wasAcknowledged()) {
                Result.success(note)
            } else {
                Result.error("Failed to create note")
            }
        } catch (e: Exception) {
            Result.error("${"Failed to create note"}: ${e.message}", e)
        }
    }

    override suspend fun getNotes(
        userEmail: String,
        page: Int,
        limit: Int,
        search: String?
    ): Result<List<Note>> {
        return try {
            val filter = if (search != null && search.isNotBlank()) {
                Filters.and(
                    Filters.eq("userEmail", userEmail),
                    Filters.text(search)
                )
            } else {
                Filters.eq("userEmail", userEmail)
            }

            val offset = (page - 1) * limit
            val notes = databaseModule.getNotesCollection()
                .find(filter)
                .sort(Sorts.descending("createdAt"))
                .skip(offset)
                .limit(limit)
                .toList()

            Result.success(notes)
        } catch (e: Exception) {
            Result.error("Failed to get notes: ${e.message}", e)
        }
    }

    override suspend fun getNoteCount(userEmail: String, search: String?): Result<Long> {
        return try {
            val filter = if (search != null && search.isNotBlank()) {
                Filters.and(
                    Filters.eq("userEmail", userEmail),
                    Filters.text(search)
                )
            } else {
                Filters.eq("userEmail", userEmail)
            }

            val count = databaseModule.getNotesCollection().countDocuments(filter)
            Result.success(count)
        } catch (e: Exception) {
            Result.error("Failed to count notes: ${e.message}", e)
        }
    }

    override suspend fun updateNote(
        id: String,
        userEmail: String,
        note: Note
    ): Result<Note?> {
        return try {
            // Validate content is not empty
            if (note.content.isBlank()) {
                return Result.error("Note content cannot be empty")
            }

            // Enforce ownership: can only update own notes
            val filter = Filters.and(
                Filters.eq("_id", ObjectId(id)),
                Filters.eq("userEmail", userEmail)
            )

            val updatedNote = note.copy(
                updatedAt = Instant.now().toString()
            )

            val result = databaseModule.getNotesCollection().findOneAndReplace(filter, updatedNote)
            if (result != null) {
                Result.success(updatedNote)
            } else {
                Result.error("Note not found")
            }
        } catch (e: Exception) {
            Result.error("${"Failed to update note"}: ${e.message}", e)
        }
    }
}
