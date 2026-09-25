package de.chrgroth.james.platform.domain.imports

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CronScheduleTests {

  @Test
  fun `isValid accepts a well-formed quartz cron expression`() {
    assertThat(CronSchedule.isValid("0 0 3 * * ?")).isTrue()
  }

  @Test
  fun `isValid rejects a malformed cron expression`() {
    assertThat(CronSchedule.isValid("not a cron")).isFalse()
  }

  @Test
  fun `isValid rejects a blank expression`() {
    assertThat(CronSchedule.isValid(" ")).isFalse()
  }

  @Test
  fun `isValid accepts an expression firing exactly every 15 minutes`() {
    assertThat(CronSchedule.isValid("0 0,15,30,45 * * * ?")).isTrue()
  }

  @Test
  fun `isValid accepts an expression firing less often than every 15 minutes`() {
    assertThat(CronSchedule.isValid("0 0 * * * ?")).isTrue()
  }

  @Test
  fun `isValid rejects an expression firing every minute`() {
    assertThat(CronSchedule.isValid("0 * * * * ?")).isFalse()
  }

  @Test
  fun `isValid rejects an expression firing every 5 minutes`() {
    assertThat(CronSchedule.isValid("0 0,5,10,15,20,25,30,35,40,45,50,55 * * * ?")).isFalse()
  }

  @Test
  fun `isValid rejects an expression with an uneven cadence where only one gap is below 15 minutes`() {
    assertThat(CronSchedule.isValid("0 0,14,30,45 * * * ?")).isFalse()
  }

  @Test
  fun `nextFireTime returns the next occurrence strictly after the given instant`() {
    val after = Instant.parse("2026-01-01T02:00:00Z")

    assertThat(CronSchedule.nextFireTime("0 0 3 * * ?", after, ZoneOffset.UTC)).isEqualTo(Instant.parse("2026-01-01T03:00:00Z"))
  }

  @Test
  fun `nextFireTime evaluates the expression in the given zone including DST`() {
    val berlin = ZoneId.of("Europe/Berlin")

    // winter time (UTC+1): 06:30 local == 05:30Z
    assertThat(CronSchedule.nextFireTime("0 30 6 * * ?", Instant.parse("2026-01-15T00:00:00Z"), berlin)).isEqualTo(Instant.parse("2026-01-15T05:30:00Z"))
    // summer time (UTC+2): 06:30 local == 04:30Z
    assertThat(CronSchedule.nextFireTime("0 30 6 * * ?", Instant.parse("2026-07-15T00:00:00Z"), berlin)).isEqualTo(Instant.parse("2026-07-15T04:30:00Z"))
    // day after the spring-forward change (2026-03-29): still 06:30 local == 04:30Z
    assertThat(CronSchedule.nextFireTime("0 30 6 * * ?", Instant.parse("2026-03-29T05:00:00Z"), berlin)).isEqualTo(Instant.parse("2026-03-30T04:30:00Z"))
  }

  @Test
  fun `nextFireTime returns null for an invalid expression`() {
    assertThat(CronSchedule.nextFireTime("not a cron", Instant.now())).isNull()
  }
}
