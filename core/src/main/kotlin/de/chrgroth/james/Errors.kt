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
    data class Error<Type>(val code: ErrorCode, val details: Any? = null) : Maybe<Type>()
    data class Errors<Type>(val errors: List<Error<Type>>) : Maybe<Type>()

    fun <R> map(transformer: (Type) -> R) = when(this) {
        is Errors -> this as Maybe<R> // make the compiler happy
        is Error -> this as Maybe<R> // make the compiler happy
        is Result -> Result(transformer.invoke(value))
    }

    fun <R> transform(transformer: (Type) -> Maybe<R>) = when(this) {
        is Errors -> this as Maybe<R> // make the compiler happy
        is Error -> this as Maybe<R> // make the compiler happy
        is Result -> transformer.invoke(value)
    }
}

fun <Type> List<Maybe.Errors<Type>?>.combine() =
    Maybe.Errors(errors = this.filterNotNull().flatMap { it.errors })
