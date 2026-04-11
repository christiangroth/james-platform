package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.qute.TemplateExtension
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@TemplateExtension
@Suppress("Unused")
object TemplateFormattingExtensions {

  private val DATETIME_FORMATTER by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()) }
  private val DATETIME_SHORT_FORMATTER by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }

  private const val SECONDS_PER_MINUTE = 60L
  private const val SECONDS_PER_HOUR = 3600L

  @JvmStatic
  fun formatted(value: Long): String = String.format(Locale.US, "%,d", value).replace(",", ".")

  @JvmStatic
  fun formatted(value: Int): String = String.format(Locale.US, "%,d", value).replace(",", ".")

  @JvmStatic
  fun formatted(instant: Instant): String = DATETIME_FORMATTER.format(instant.toJavaInstant())

  @JvmStatic
  fun formattedShort(instant: Instant): String = DATETIME_SHORT_FORMATTER.format(instant.toJavaInstant())

  /** Formats a duration given in seconds as `m:ss` (e.g. for a recently-played track). */
  @JvmStatic
  fun formattedDuration(durationSeconds: Long): String {
    val minutes = durationSeconds / SECONDS_PER_MINUTE
    val seconds = durationSeconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
  }
}
