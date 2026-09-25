package de.chrgroth.james.platform.domain.model.infra

import java.time.ZoneId

/**
 * The single time zone the application shows and interprets wall-clock times in. All timestamps are stored and
 * passed around as UTC instants (`Instant`, BSON dates); this zone is applied only at the edges - rendering an instant
 * for the user (`TemplateFormattingExtensions.formatted`) and interpreting user-chosen wall-clock times such as an
 * import schedule's "daily at 06:30" (`CronSchedule.nextFireTime`). Never use the JVM/browser default zone for either.
 *
 * Defaults to [DEFAULT_ZONE_ID]; override with the `app.time-zone` system property or the `APP_TIME_ZONE`
 * environment variable (an IANA zone id such as `Europe/Berlin`). An unknown id falls back to the default.
 */
object AppTimeZone {

  const val DEFAULT_ZONE_ID = "Europe/Berlin"

  val zone: ZoneId by lazy {
    val configured = System.getProperty("app.time-zone")?.takeIf { it.isNotBlank() } ?: System.getenv("APP_TIME_ZONE")?.takeIf { it.isNotBlank() }
    configured?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of(DEFAULT_ZONE_ID)
  }
}
