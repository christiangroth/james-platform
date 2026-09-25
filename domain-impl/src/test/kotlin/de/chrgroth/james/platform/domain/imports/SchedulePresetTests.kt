package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.imports.SchedulePreset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class SchedulePresetTests {

  @Test
  fun `daily preset produces the expected cron expression`() {
    assertThat(SchedulePreset.Daily(6, 30).toCron()).isEqualTo("0 30 6 * * ?")
    assertThat(SchedulePreset.Daily(0, 0).toCron()).isEqualTo("0 0 0 * * ?")
    assertThat(SchedulePreset.Daily(23, 45).toCron()).isEqualTo("0 45 23 * * ?")
  }

  @Test
  fun `every interval preset produces the expected cron expression`() {
    val expected = mapOf(
      15 to "0 0/15 * * * ?",
      30 to "0 0/30 * * * ?",
      60 to "0 0 0/1 * * ?",
      120 to "0 0 0/2 * * ?",
      180 to "0 0 0/3 * * ?",
      240 to "0 0 0/4 * * ?",
      360 to "0 0 0/6 * * ?",
      720 to "0 0 0/12 * * ?",
    )
    assertThat(SchedulePreset.INTERVAL_MINUTES).containsExactlyElementsOf(expected.keys)
    expected.forEach { (minutes, cron) -> assertThat(SchedulePreset.Interval(minutes).toCron()).isEqualTo(cron) }
  }

  @Test
  fun `every generated cron is accepted by CronSchedule and round-trips through fromCron`() {
    val presets = SchedulePreset.INTERVAL_MINUTES.map { SchedulePreset.Interval(it) } +
      (0..23).flatMap { hour -> SchedulePreset.DAILY_MINUTES.map { SchedulePreset.Daily(hour, it) } }

    assertThat(presets).hasSize(8 + 96)
    presets.forEach { preset ->
      assertThat(CronSchedule.isValid(preset.toCron())).describedAs(preset.toCron()).isTrue()
      assertThat(SchedulePreset.fromCron(preset.toCron())).isEqualTo(preset)
    }
  }

  @Test
  fun `fromCron returns null for expressions that are not representable as a preset`() {
    listOf(
      "0 0 3 * * MON",
      "0 0,14,30,45 * * * ?",
      "0 0 0/5 * * ?",
      "0 0/20 * * * ?",
      "0 7 3 * * ?",
      "0 0 24 * * ?",
      "0 0 3 1 * ?",
      "not a cron",
      "",
    ).forEach { assertThat(SchedulePreset.fromCron(it)).describedAs(it).isNull() }
    assertThat(SchedulePreset.fromCron(null)).isNull()
  }

  @Test
  fun `daily and interval factories reject invalid input`() {
    assertThat(SchedulePreset.daily("06:30")).isEqualTo(SchedulePreset.Daily(6, 30))
    assertThat(SchedulePreset.daily("6:45")).isEqualTo(SchedulePreset.Daily(6, 45))
    assertThat(SchedulePreset.daily("06:10")).isNull()
    assertThat(SchedulePreset.daily("24:00")).isNull()
    assertThat(SchedulePreset.daily("0630")).isNull()
    assertThat(SchedulePreset.daily(null)).isNull()
    assertThat(SchedulePreset.interval(120)).isEqualTo(SchedulePreset.Interval(120))
    assertThat(SchedulePreset.interval(5)).isNull()
    assertThat(SchedulePreset.interval(null)).isNull()
  }

  @Test
  fun `timeLabel formats hour and minute with leading zeros`() {
    assertThat(SchedulePreset.Daily(6, 0).timeLabel()).isEqualTo("06:00")
    assertThat(SchedulePreset.Daily(23, 45).timeLabel()).isEqualTo("23:45")
  }

  @Test
  fun `interval presets fire on the wall clock`() {
    val after = Instant.parse("2026-01-01T02:07:00Z")

    assertThat(CronSchedule.nextFireTime(SchedulePreset.Interval(15).toCron(), after, ZoneOffset.UTC)).isEqualTo(Instant.parse("2026-01-01T02:15:00Z"))
    assertThat(CronSchedule.nextFireTime(SchedulePreset.Interval(180).toCron(), after, ZoneOffset.UTC)).isEqualTo(Instant.parse("2026-01-01T03:00:00Z"))
  }
}
