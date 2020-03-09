package de.chrgroth

import java.util.*

data class Localized<T>(val values: Map<Locale, T>)