package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors

class InvalidInstanceException(type: String, val errors: List<Error<*>>) :
    RuntimeException("Attempted to create invalid $type instance: $errors")

interface ErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}

sealed class Maybe<Type> {
    data class Result<Type>(val value: Type) : Maybe<Type>()
    data class Error<Type>(val code: ErrorCode, val details: String?) : Maybe<Type>()
    data class Errors<Type>(val errors: List<Error<Type>>) : Maybe<Type>()

    @Suppress("UNCHECKED_CAST")
    fun <R> map(transformer: (Type) -> R): Maybe<R> = when (this) {
        is Errors -> this as Errors<R> // make the compiler happy
        is Error -> this as Error<R> // make the compiler happy
        is Result -> Result(transformer.invoke(value))
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> flatMap(transformer: (Type) -> Maybe<R>): Maybe<R> = when (this) {
        is Errors -> this as Errors<R> // make the compiler happy
        is Error -> this as Error<R> // make the compiler happy
        is Result -> transformer.invoke(value)
    }
}

fun <Type> List<Maybe<*>?>.foldErrors() =
    this.filterIsInstance<Error<Type>>().fold()

fun <Type> List<Error<Type>?>.fold() =
    if (this.filterNotNull().isEmpty()) {
        null
    } else {
        Errors(errors = this.filterNotNull())
    }

fun <Type> List<Errors<Type>?>.combine() =
    if (this.filterNotNull().isEmpty()) {
        null
    } else {
        Errors(errors = this.filterNotNull().flatMap { it.errors })
    }

fun <Type> Error<Type>?.combine(other: Error<Type>?) =
    when {
        this != null && other != null -> Errors(errors = listOf(this, other))
        this != null -> Errors(errors = listOf(this))
        other != null -> Errors(errors = listOf(other))
        else -> null
    }

fun <Type> Errors<Type>?.combine(other: Error<Type>?) =
    when {
        this != null && other != null -> Errors(errors = this.errors.plus(other))
        this != null -> this
        other != null -> Errors(errors = listOf(other))
        else -> null
    }

fun <Type> Errors<Type>?.combine(other: Errors<Type>?) =
    when {
        this != null && other != null -> Errors(errors = this.errors.plus(other.errors))
        this != null -> this
        other != null -> other
        else -> null
    }

fun <Type> List<Maybe<*>?>.foldAndShrink() =
    foldErrors<Type>().foldAndShrink()

fun <Type> Errors<Type>?.foldAndShrink(): Maybe<Type>? =
    when {
        this == null -> null
        this.errors.size == 1 -> this.errors.single()
        else -> this
    }
