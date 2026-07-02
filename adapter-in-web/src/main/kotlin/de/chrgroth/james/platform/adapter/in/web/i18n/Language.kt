package de.chrgroth.james.platform.adapter.`in`.web.i18n

import java.util.Locale

/**
 * Supported UI languages. [UNDERSCORE] is an artificial pseudo-locale generated at build time from the German properties
 * (see `generatePseudoLocaleMessages` in `adapter-in-web/build.gradle.kts`) - every letter/digit is replaced with `_`
 * while whitespace, punctuation and other special characters are preserved. It exists purely to make missing i18n
 * placeholders in templates visually obvious during UI testing.
 */
enum class Language(val code: String, val locale: Locale) {
  GERMAN("de", Locale.GERMAN),
  UNDERSCORE("xx", Locale.forLanguageTag("xx")),
  ;

  companion object {
    const val COOKIE_NAME = "lang"

    fun fromCode(code: String?): Language = entries.find { it.code == code } ?: GERMAN
  }
}
