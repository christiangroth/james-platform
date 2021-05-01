package de.chrgroth.james

interface ErrorCodeProvider {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation() = "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}"
}

sealed class Maybe<Type> {
    class Result<Type>(val result: Type) : Maybe<Type>()
    class Error<Type>(val errorCodeProvider: ErrorCodeProvider) : Maybe<Type>()

    fun isError() = this is Error
    fun <R> map(transformer: (Type) -> R) = when(this) {
        is Error -> Error(errorCodeProvider)
        is Result -> Result(transformer.invoke(result))
    }
}
