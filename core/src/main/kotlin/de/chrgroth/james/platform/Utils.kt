package de.chrgroth.james.platform

fun String?.trimToNull(): String? =
    if (this.isNullOrBlank()) {
        null
    } else {
        this.trim()
    }
