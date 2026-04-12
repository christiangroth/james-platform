package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.user.User
import io.quarkus.qute.TemplateExtension
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import java.time.Instant as JavaInstant

@TemplateExtension
@Suppress("Unused")
object TemplateFormattingExtensions {

  /**
   * Returns the username string value. Used because [Username] is a [JvmInline] value class,
   * whose JVM getter is name-mangled, preventing Qute from resolving it via reflection.
   */
  @JvmStatic
  fun username(user: User): String = user.username.value

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
  fun formatted(instant: JavaInstant): String = DATETIME_FORMATTER.format(instant)

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
