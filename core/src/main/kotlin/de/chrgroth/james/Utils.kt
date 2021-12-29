package de.chrgroth.james

internal fun String?.trimToNull(): String? =
    if (this.isNullOrBlank()) {
        null
    } else {
        this.trim()
    }
