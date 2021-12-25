package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result

// TODO #25 remove code duplications on invocation sides (init vs methods)
// TODO #25 tests

internal fun validateMatches(value: String, pattern: Regex, codeBlank: ErrorCode, codeNoMatch: ErrorCode): Maybe<String> =
    value.trim().let {
        when {
            it.isBlank() -> Error(code = codeBlank, details = null)
            it.matches(pattern) -> Result(it)
            else -> Error(code = codeNoMatch, details = it)
        }
    }

internal fun validateNotBlank(value: String, codeBlank: ErrorCode): Maybe<String> =
    value.trim().let {
        if (it.isBlank()) {
            Error(code = codeBlank, details = null)
        } else {
            Result(it)
        }
    }

internal fun validateNotNegative(value: Long, codeNegative: ErrorCode): Maybe<Long> =
    if (value >= 0) {
        Result(value)
    } else {
        Error(code = codeNegative, details = value.toString())
    }

internal fun <Type> Maybe.Errors<Type>?.throwOnError(type: String): Unit {
    if(this != null) {
        throw InvalidInstanceException(type, errors)
    }
}
