package com.androidplay.core.common

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(message: String, exception: Exception? = null): Result<Nothing> =
            Error(message, exception)
    }

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> success(transform(data))
        is Error -> this
    }

    inline fun mapError(transform: (String, Exception?) -> Pair<String, Exception?>): Result<T> =
        when (this) {
            is Success -> this
            is Error -> {
                val (newMessage, newException) = transform(message, exception)
                error(newMessage, newException)
            }
        }
}
