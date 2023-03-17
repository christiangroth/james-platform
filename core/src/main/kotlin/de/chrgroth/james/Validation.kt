package de.chrgroth.james

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.zip
import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.long
import com.sksamuel.tribune.core.min
import com.sksamuel.tribune.core.strings.match
import com.sksamuel.tribune.core.strings.notNullOrBlank
import com.sksamuel.tribune.core.strings.trim

fun regexParer(errorCodeBlank: ErrorCode, pattern: Regex, errorCodeNoMatch: ErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { Error(errorCodeBlank) }
    .match(pattern) { Error(errorCodeNoMatch, "'$it' does not match $pattern") }
    .trim()

fun notBlankParser(errorCode: ErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { Error(errorCode) }
    .trim()

fun notNegativeLongParser(errorCode: ErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { Error(errorCode) }
    .long { Error(errorCode) }
    .min(0) { Error(errorCode) }

// TODO #29 tests
fun <T> createValidation(errorCondition: Boolean, errorCode: ErrorCode, errorDetails: String?, valueProvider: () -> T): ValidatedNel<Error, T> =
    if (errorCondition) {
        Validated.invalidNel(Error(errorCode, errorDetails))
    } else {
        Validated.validNel(valueProvider())
    }

// TODO #29 add tests
fun <T> List<ValidatedNel<Error, T>>.reduceWithFirstValue(): ValidatedNel<Error, T> =
    reduce { first, next ->
        first.zip(next) { firstValue, _ ->
            firstValue
        }
    }

// TODO #29 add tests
fun <T> List<ValidatedNel<Error, T>>.reduceWithAllValues(): ValidatedNel<Error, List<T>> =
    fold(Validated.validNel(emptyList())) { all, next ->
        all.zip(next) { allValues, nextValue ->
            allValues.plus(nextValue)
        }
    }
