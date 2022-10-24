package de.chrgroth.james

import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.long
import com.sksamuel.tribune.core.min
import com.sksamuel.tribune.core.strings.match
import com.sksamuel.tribune.core.strings.notNullOrBlank
import com.sksamuel.tribune.core.strings.trim
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result

fun regexParer(errorCodeBlank: ErrorCode, pattern: Regex, errorCodeNoMatch: ErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { de.chrgroth.james.Error(errorCodeBlank) }
    .match(pattern) { de.chrgroth.james.Error(errorCodeNoMatch, "'$it' does not match $pattern") }
    .trim()

fun notBlankParser(errorCode: ErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { de.chrgroth.james.Error(errorCode) }
    .trim()

fun notNegativeLongParser(errorCode: ErrorCode) = Parser
    .fromNullableString()
    .notNullOrBlank { de.chrgroth.james.Error(errorCode) }
    .long { de.chrgroth.james.Error(errorCode) }
    .min(0) { de.chrgroth.james.Error(errorCode) }

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
