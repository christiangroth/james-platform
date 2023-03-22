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

fun regexParer(domainErrorCodeBlank: DomainErrorCode, pattern: Regex, domainErrorCodeNoMatch: DomainErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { DomainError(domainErrorCodeBlank) }
    .match(pattern) { DomainError(domainErrorCodeNoMatch, "'$it' does not match $pattern") }
    .trim()

fun notBlankParser(domainErrorCode: DomainErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { DomainError(domainErrorCode) }
    .trim()

fun notNegativeLongParser(domainErrorCode: DomainErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { DomainError(domainErrorCode) }
    .long { DomainError(domainErrorCode) }
    .min(0) { DomainError(domainErrorCode) }

fun <T> createValidation(errorCondition: Boolean, domainErrorCode: DomainErrorCode, errorDetails: String?, valueProvider: () -> T): ValidatedNel<DomainError, T> =
    if (errorCondition) {
        Validated.invalidNel(DomainError(domainErrorCode, errorDetails))
    } else {
        Validated.validNel(valueProvider())
    }

fun <T> List<ValidatedNel<DomainError, T>>.reduceWithFirstValue(valueProviderIfEmpty: () -> T): ValidatedNel<DomainError, T> =
    if(isEmpty()) {
        Validated.validNel(valueProviderIfEmpty())
    } else {
        reduceWithFirstValue()
    }

fun <T> List<ValidatedNel<DomainError, T>>.reduceWithFirstValue(): ValidatedNel<DomainError, T> =
    reduce { first, next ->
        first.zip(next) { firstValue, _ ->
            firstValue
        }
    }

fun <T> List<ValidatedNel<DomainError, T>>.reduceWithAllValues(): ValidatedNel<DomainError, List<T>> =
    fold(Validated.validNel(emptyList())) { all, next ->
        all.zip(next) { allValues, nextValue ->
            allValues.plus(nextValue)
        }
    }
