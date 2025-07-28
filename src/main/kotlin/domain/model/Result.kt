package domain.model

/**
 * A generic class that holds a value or an error.
 * This is used to represent the result of an operation that can fail.
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()

    companion object {
        /**
         * Creates a Success result with the given data.
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * Creates an Error result with the given message and optional exception.
         */
        fun error(message: String, exception: Exception? = null): Result<Nothing> =
            Error(message, exception)
    }

    /**
     * Returns true if this is a Success, false otherwise.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns true if this is an Error, false otherwise.
     */
    val isError: Boolean get() = this is Error

    /**
     * Returns the data if this is a Success, null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    /**
     * Maps the success value using the given transform function.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> success(transform(data))
        is Error -> this
    }

    /**
     * Maps the error message and exception using the given transform function.
     */
    inline fun mapError(transform: (String, Exception?) -> Pair<String, Exception?>): Result<T> =
        when (this) {
            is Success -> this
            is Error -> {
                val (newMessage, newException) = transform(message, exception)
                error(newMessage, newException)
            }
        }
}