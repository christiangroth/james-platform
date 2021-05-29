package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors

interface ErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}

sealed class Maybe<Type> {
    data class Result<Type>(val value: Type) : Maybe<Type>()
    // TODO #17 would maybe be great to have a json pointer here??
    data class Error<Type>(val code: ErrorCode, val details: String? = null) : Maybe<Type>()
    data class Errors<Type>(val errors: List<Error<Type>>) : Maybe<Type>()

    @Suppress("UNCHECKED_CAST")
    fun <R> map(transformer: (Type) -> R) = when(this) {
        is Errors -> this as Maybe<R> // make the compiler happy
        is Error -> this as Maybe<R> // make the compiler happy
        is Result -> Result(transformer.invoke(value))
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> transform(transformer: (Type) -> Maybe<R>) = when(this) {
        is Errors -> this as Maybe<R> // make the compiler happy
        is Error -> this as Maybe<R> // make the compiler happy
        is Result -> transformer.invoke(value)
    }
}

// TODO #17 tests
fun <Type> List<Errors<Type>?>.combine() =
    if(this.filterNotNull().isEmpty()) {
        null
    } else {
        Errors(errors = this.filterNotNull().flatMap { it.errors })
    }


fun <Type> Error<Type>?.combine(other: Error<Type>?) =
    when {
        this != null && other != null -> Errors(errors = listOf(this, other))
        this != null && other == null -> Errors(errors = listOf(this))
        this == null && other != null -> Errors(errors = listOf(other))
        else -> null
    }

// TODO #17 tests
fun <Type> Errors<Type>?.combine(other: Error<Type>?) =
    when {
        this != null && other != null -> Errors(errors = this.errors.plus(other))
        this != null && other == null -> this
        this == null && other != null -> Errors(errors = listOf(other))
        else -> null
    }

// TODO #17 tests
fun <Type> Errors<Type>?.combine(other: Errors<Type>?) =
    when {
        this != null && other != null -> Errors(errors = this.errors.plus(other.errors))
        this != null && other == null -> this
        this == null && other != null -> other
        else -> null
    }
