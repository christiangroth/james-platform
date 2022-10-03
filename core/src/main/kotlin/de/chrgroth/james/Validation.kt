package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result

fun validateMatches(value: String, pattern: Regex, codeBlank: ErrorCode, codeNoMatch: ErrorCode): Maybe<String> =
    value.trim().let {
        when {
            it.isBlank() -> Error(code = codeBlank, details = null)
            it.matches(pattern) -> Result(it)
            else -> Error(code = codeNoMatch, details = "'$it' does not match $pattern")
        }
    }

fun validateNotBlank(value: String, codeBlank: ErrorCode): Maybe<String> =
    value.trim().let {
        if (it.isBlank()) {
            Error(code = codeBlank, details = null)
        } else {
            Result(it)
        }
    }

fun validateNotNegative(value: Long, codeNegative: ErrorCode): Maybe<Long> =
    if (value >= 0) {
        Result(value)
    } else {
        Error(code = codeNegative, details = value.toString())
    }
