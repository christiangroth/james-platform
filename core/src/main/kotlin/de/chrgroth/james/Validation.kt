package de.chrgroth.james

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
