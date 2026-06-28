package de.chrgroth.james.platform.domain.model.app

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Human-readable hint describing the textual format accepted for DURATION property values, shown as input placeholder / help text. */
const val DURATION_FORMAT_HINT = "1d 2h 30m 15s or 02:30:15"

private val COLON_FORMAT = Regex("""^\d+(:\d+){0,2}$""")
private val UNIT_SUFFIX_FORMAT = Regex("""^(?:(\d+)d\s*)?(?:(\d+)h\s*)?(?:(\d+)m\s*)?(?:(\d+)s\s*)?$""")

/**
 * Parses a DURATION property's textual value into a [Duration].
 * Accepts two formats, with [raw] trimmed before matching:
 * - Colon-separated, e.g. "02:30:15", "30:15" or "15", read as `[[hh:]mm:]ss` with leading parts optional.
 * - Unit-suffixed, e.g. "1d 2h 30m 15s", where any subset of the day/hour/minute/second parts may be present.
 * Returns null if [raw] does not match either format.
 */
fun parseDurationValue(raw: String): Duration? {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return null

  if (COLON_FORMAT.matches(trimmed)) {
    val numbers = trimmed.split(":").map { it.toLong() }
    val (hours, minutes, seconds) = List(3 - numbers.size) { 0L } + numbers
    return hours.hours + minutes.minutes + seconds.seconds
  }

  val match = UNIT_SUFFIX_FORMAT.matchEntire(trimmed) ?: return null
  val (days, hours, minutes, seconds) = match.destructured
  if (days.isEmpty() && hours.isEmpty() && minutes.isEmpty() && seconds.isEmpty()) return null
  return days.toDurationPartOrZero().days + hours.toDurationPartOrZero().hours +
    minutes.toDurationPartOrZero().minutes + seconds.toDurationPartOrZero().seconds
}

private fun String.toDurationPartOrZero(): Long = if (isEmpty()) 0L else toLong()
