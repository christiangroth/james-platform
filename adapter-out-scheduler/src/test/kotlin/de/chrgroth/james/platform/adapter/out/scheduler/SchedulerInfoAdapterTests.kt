package de.chrgroth.james.platform.adapter.out.scheduler

import io.mockk.every
import io.mockk.mockk
import io.quarkus.scheduler.Scheduler
import io.quarkus.scheduler.Trigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchedulerInfoAdapterTests {

  private val scheduler: Scheduler = mockk()

  private val adapter = SchedulerInfoAdapter(scheduler)

  @Test
  fun `trigger with null method description is skipped`() {
    val trigger = mockk<Trigger>()
    every { trigger.id } returns "0_some-id"
    every { trigger.methodDescription } returns null
    every { scheduler.scheduledJobs } returns listOf(trigger)

    val result = adapter.getCronjobStats()

    assertThat(result).isEmpty()
  }

  @Test
  fun `trigger with method description without hash separator is skipped`() {
    val trigger = mockk<Trigger>()
    every { trigger.id } returns "some-id"
    every { trigger.methodDescription } returns "invalid-method-description"
    every { scheduler.scheduledJobs } returns listOf(trigger)

    val result = adapter.getCronjobStats()

    assertThat(result).isEmpty()
  }

  @Test
  fun `trigger for unknown class is skipped`() {
    val trigger = mockk<Trigger>()
    every { trigger.id } returns "some-id"
    every { trigger.methodDescription } returns "com.example.NonExistentJob#run"
    every { scheduler.scheduledJobs } returns listOf(trigger)

    val result = adapter.getCronjobStats()

    assertThat(result).isEmpty()
  }

  @Test
  fun `empty scheduler returns empty list`() {
    every { scheduler.scheduledJobs } returns emptyList()

    val result = adapter.getCronjobStats()

    assertThat(result).isEmpty()
  }
}
