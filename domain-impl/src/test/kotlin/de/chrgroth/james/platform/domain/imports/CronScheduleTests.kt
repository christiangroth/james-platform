package de.chrgroth.james.platform.domain.imports

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

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
  fun `isDue is true once the next occurrence after since is at or before now`() {
    val since = Instant.parse("2026-01-01T02:00:00Z")
    val now = Instant.parse("2026-01-01T03:00:01Z")

    assertThat(CronSchedule.isDue("0 0 3 * * ?", since, now)).isTrue()
  }

  @Test
  fun `isDue is false when the next occurrence after since is still in the future`() {
    val since = Instant.parse("2026-01-01T02:00:00Z")
    val now = Instant.parse("2026-01-01T02:59:59Z")

    assertThat(CronSchedule.isDue("0 0 3 * * ?", since, now)).isFalse()
  }

  @Test
  fun `isDue is true on the first poll after a long-idle since, self-correcting to the real cadence afterwards`() {
    val longAgo = Instant.parse("2020-01-01T00:00:00Z")
    val now = Instant.parse("2026-01-01T03:00:01Z")

    assertThat(CronSchedule.isDue("0 0 3 * * ?", longAgo, now)).isTrue()
  }
}
