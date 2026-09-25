package de.chrgroth.james.platform.domain.model.imports

/**
 * The fixed set of user-selectable schedules for an [ImportDefinition] (issue #698). The stored representation stays
 * the Quartz cron expression in [ImportDefinition.schedule]; this type is the single place converting between the two
 * ([toCron] / [fromCron]). A stored cron that is not exactly one of the expressions generated here (e.g. a legacy
 * custom expression) maps to `null` in [fromCron] and is shown as "benutzerdefiniert" by the UI.
 *
 * Wall-clock times ([Daily]) are interpreted in the same time zone the UI renders instants in
 * ([de.chrgroth.james.platform.domain.model.infra.AppTimeZone]), see `CronSchedule.nextFireTime`.
 */
sealed interface SchedulePreset {

  fun toCron(): String

  /** Fires once per day at [hour]:[minute], where [minute] lies on the 15-minute raster. */
  data class Daily(val hour: Int, val minute: Int) : SchedulePreset {
    init {
      require(hour in 0..23) { "hour out of range: $hour" }
      require(minute in DAILY_MINUTES) { "minute not on the 15-minute raster: $minute" }
    }

    override fun toCron(): String = "0 $minute $hour * * ?"

    /** `HH:mm`, as used by the schedule modal's time select. */
    fun timeLabel(): String = "%02d:%02d".format(hour, minute)
  }

  /** Fires every [minutes] minutes (one of [INTERVAL_MINUTES]), aligned to the wall clock. */
  data class Interval(val minutes: Int) : SchedulePreset {
    init {
      require(minutes in INTERVAL_MINUTES) { "unsupported interval: $minutes" }
    }

    override fun toCron(): String = when {
      minutes < 60 -> "0 0/$minutes * * * ?"
      else -> "0 0 0/${minutes / 60} * * ?"
    }
  }

  companion object {
    val DAILY_MINUTES: List<Int> = listOf(0, 15, 30, 45)
    val INTERVAL_MINUTES: List<Int> = listOf(15, 30, 60, 120, 180, 240, 360, 720)

    private val DAILY_CRON = Regex("""^0 (\d{1,2}) (\d{1,2}) \* \* \?$""")
    private val MINUTE_INTERVAL_CRON = Regex("""^0 0/(\d{1,2}) \* \* \* \?$""")
    private val HOUR_INTERVAL_CRON = Regex("""^0 0 0/(\d{1,2}) \* \* \?$""")

    /** Parses a `HH:mm` string on the 15-minute raster; null if malformed or off the raster. */
    fun daily(time: String?): Daily? {
      val parts = time?.trim()?.split(":") ?: return null
      if (parts.size != 2) return null
      val hour = parts[0].toIntOrNull() ?: return null
      val minute = parts[1].toIntOrNull() ?: return null
      return runCatching { Daily(hour, minute) }.getOrNull()
    }

    fun interval(minutes: Int?): Interval? = minutes?.takeIf { it in INTERVAL_MINUTES }?.let { Interval(it) }

    /** The preset whose [toCron] is exactly [expression], or null if [expression] is not representable as a preset. */
    fun fromCron(expression: String?): SchedulePreset? {
      val expr = expression?.trim() ?: return null
      val candidate: SchedulePreset? = DAILY_CRON.matchEntire(expr)?.let { match ->
        val (hour, minute) = match.destructured
        runCatching { Daily(hour.toInt(), minute.toInt()) }.getOrNull()
      } ?: MINUTE_INTERVAL_CRON.matchEntire(expr)?.let { interval(it.groupValues[1].toInt()) }
        ?: HOUR_INTERVAL_CRON.matchEntire(expr)?.let { interval(it.groupValues[1].toInt() * 60) }
      return candidate?.takeIf { it.toCron() == expr }
    }
  }
}
