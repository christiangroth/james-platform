package de.chrgroth.james

interface ErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}

sealed class Maybe<Type> {
    data class Result<Type>(val value: Type) : Maybe<Type>()
    data class Error<Type>(val code: ErrorCode, val details: Any? = null) : Maybe<Type>() {
        fun <R> convert() = this as Error<R>
    }

    fun <R> map(transformer: (Type) -> R) = when(this) {
        is Error -> Error(code, details)
        is Result -> Result(transformer.invoke(value))
    }

    fun <R> transform(transformer: (Type) -> Maybe<R>) = when(this) {
        is Error -> Error(code, details)
        is Result -> transformer.invoke(value)
    }
}
