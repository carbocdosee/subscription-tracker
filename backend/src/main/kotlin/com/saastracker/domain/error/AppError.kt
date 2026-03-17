package com.saastracker.domain.error

sealed class AppError(open val message: String) {
    data class Validation(override val message: String) : AppError(message)
    data class Unauthorized(override val message: String) : AppError(message)
    data class Forbidden(override val message: String) : AppError(message)
    data class NotFound(override val message: String) : AppError(message)
    data class Conflict(override val message: String) : AppError(message)
    data class RateLimited(override val message: String) : AppError(message)
    data class ExternalService(override val message: String, val cause: Throwable? = null) : AppError(message)
    data class Internal(override val message: String, val cause: Throwable? = null) : AppError(message)
}

sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}

inline fun <T> appResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (ex: IllegalArgumentException) {
    AppResult.Failure(AppError.Validation(ex.message ?: "Invalid input"))
} catch (ex: Exception) {
    AppResult.Failure(AppError.Internal(ex.message ?: "Unexpected error", ex))
}

