package de.chrgroth.james

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.zip

// TODO #29 rename to DomainError?
// TODO #29 refactor to return all errors and not error by error
// TODO #29 really need details?? won't be usable for the frontend, maybe just return the code?? or maybe use as Map instead of String?
data class Error(val code: ErrorCode, val details: String? = null)

interface ErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}

// TODO #29 introduce everywhere
// TODO #29 tests / move
fun <T> createValidation(errorCondition: Boolean, errorCode: ErrorCode, errorDetails: String?, valueProvider: () -> T): ValidatedNel<Error, T> =
    if (errorCondition) {
        Validated.invalidNel(Error(errorCode, errorDetails))
    } else {
        Validated.validNel(valueProvider())
    }

// TODO #29 add tests / move
fun <T> List<ValidatedNel<Error, T>>.reduceWithFirstValue(): ValidatedNel<Error, T> =
    reduce { first, next ->
        first.zip(next) { firstValue, _ ->
            firstValue
        }
    }

// TODO #29 add tests / move
fun <T> List<ValidatedNel<Error, T>>.reduceWithAllValues(): ValidatedNel<Error, List<T>> =
    fold(Validated.validNel(emptyList())) { all, next ->
        all.zip(next) { allValues, nextValue ->
            allValues.plus(nextValue)
        }
    }
