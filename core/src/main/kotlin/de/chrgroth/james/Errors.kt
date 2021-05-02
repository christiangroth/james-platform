package de.chrgroth.james

interface ErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}

sealed class Maybe<Type> {
    class Result<Type>(val value: Type) : Maybe<Type>()
    class Error<Type>(val code: ErrorCode) : Maybe<Type>()

    fun <R> map(transformer: (Type) -> R) = when(this) {
        is Error -> Error(code)
        is Result -> Result(transformer.invoke(value))
    }
}
