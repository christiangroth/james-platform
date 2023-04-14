package de.chrgroth.james

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.zip
import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.collections.asList
import com.sksamuel.tribune.core.long
import com.sksamuel.tribune.core.min
import com.sksamuel.tribune.core.strings.match
import com.sksamuel.tribune.core.strings.notNullOrBlank
import com.sksamuel.tribune.core.strings.trim

fun regexParer(domainErrorCodeBlank: DomainErrorCode, pattern: Regex, domainErrorCodeNoMatch: DomainErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { DomainError(code = domainErrorCodeBlank) }
    .match(pattern) { DomainError(code = domainErrorCodeNoMatch, errorMessage = "'$it' does not match $pattern") }
    .trim()

fun notBlankParser(domainErrorCode: DomainErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { DomainError(code = domainErrorCode) }
    .trim()

fun notNegativeLongParser(domainErrorCode: DomainErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { DomainError(code = domainErrorCode) }
    .long { DomainError(code = domainErrorCode) }
    .min(0) { DomainError(code = domainErrorCode) }

fun notEmptyListParser(domainErrorCode: DomainErrorCode) = Parser
    .from<String>()
    .asList(min = 1, max = Int.MAX_VALUE) { DomainError(code = domainErrorCode) }

fun <T> createValidation(errorCondition: Boolean, domainErrorCode: DomainErrorCode, errorMessage: String?, valueProvider: () -> T): ValidatedNel<DomainError, T> =
    if (errorCondition) {
        Validated.invalidNel(DomainError(code = domainErrorCode, errorMessage = errorMessage))
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
