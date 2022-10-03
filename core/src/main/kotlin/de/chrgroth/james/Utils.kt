package de.chrgroth.james

fun String?.trimToNull(): String? =
    if (this.isNullOrBlank()) {
        null
    } else {
        this.trim()
    }
