package de.chrgroth.james

// TODO #25 check test coverage

internal fun String?.trimToNull(): String? =
    if (this.isNullOrBlank()) {
        null
    } else {
        this.trim()
    }
