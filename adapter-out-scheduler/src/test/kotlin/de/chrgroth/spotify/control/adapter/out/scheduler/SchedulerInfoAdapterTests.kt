package de.chrgroth.spotify.control.adapter.out.scheduler

import io.mockk.every
import io.mockk.mockk
import io.quarkus.scheduler.Scheduler
import io.quarkus.scheduler.Trigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.toKotlinInstant

class SchedulerInfoAdapterTests {

  private val scheduler: Scheduler = mockk()

  private val adapter = SchedulerInfoAdapter(scheduler)

  @Test
  fun `active trigger produces running cronjob stats with next execution`() {
    val triggerId = "0_${TestCronjob::class.java.name}#run"
    val methodDescription = "${TestCronjob::class.java.name}#run"
    val nextFireTime = Instant.now().plusSeconds(3600)
    val trigger = mockk<Trigger>()
    every { trigger.id } returns triggerId
    every { trigger.methodDescription } returns methodDescription
    every { trigger.nextFireTime } returns nextFireTime
    every { scheduler.scheduledJobs } returns listOf(trigger)
    every { scheduler.isRunning } returns true
    every { scheduler.isPaused(triggerId) } returns false

    val result = adapter.getCronjobStats()

    assertThat(result).hasSize(1)
    assertThat(result[0].simpleName).isEqualTo("TestCronjob")
    assertThat(result[0].running).isTrue()
    assertThat(result[0].nextExecution).isEqualTo(nextFireTime.toKotlinInstant())
  }

  @Test
  fun `paused trigger produces non-running cronjob stats with null next execution`() {
    val triggerId = "0_${TestCronjob::class.java.name}#run"
    val methodDescription = "${TestCronjob::class.java.name}#run"
    val trigger = mockk<Trigger>()
    every { trigger.id } returns triggerId
    every { trigger.methodDescription } returns methodDescription
    every { trigger.nextFireTime } returns null
    every { scheduler.scheduledJobs } returns listOf(trigger)
    every { scheduler.isRunning } returns true
    every { scheduler.isPaused(triggerId) } returns true

    val result = adapter.getCronjobStats()

    assertThat(result).hasSize(1)
    assertThat(result[0].simpleName).isEqualTo("TestCronjob")
    assertThat(result[0].running).isFalse()
    assertThat(result[0].nextExecution).isNull()
  }

  @Test
  fun `stopped scheduler produces non-running cronjob stats`() {
    val triggerId = "0_${TestCronjob::class.java.name}#run"
    val methodDescription = "${TestCronjob::class.java.name}#run"
    val nextFireTime = Instant.now().plusSeconds(3600)
    val trigger = mockk<Trigger>()
    every { trigger.id } returns triggerId
    every { trigger.methodDescription } returns methodDescription
    every { trigger.nextFireTime } returns nextFireTime
    every { scheduler.scheduledJobs } returns listOf(trigger)
    every { scheduler.isRunning } returns false
    every { scheduler.isPaused(triggerId) } returns false

    val result = adapter.getCronjobStats()

    assertThat(result).hasSize(1)
    assertThat(result[0].running).isFalse()
  }

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
  fun `results are sorted by simpleName`() {
    val triggerIdA = "0_${TestCronjob::class.java.name}#run"
    val methodDescriptionA = "${TestCronjob::class.java.name}#run"
    val triggerA = mockk<Trigger>()
    every { triggerA.id } returns triggerIdA
    every { triggerA.methodDescription } returns methodDescriptionA
    every { triggerA.nextFireTime } returns Instant.now().plusSeconds(3600)

    val triggerIdB = "1_${AnotherTestCronjob::class.java.name}#run"
    val methodDescriptionB = "${AnotherTestCronjob::class.java.name}#run"
    val triggerB = mockk<Trigger>()
    every { triggerB.id } returns triggerIdB
    every { triggerB.methodDescription } returns methodDescriptionB
    every { triggerB.nextFireTime } returns Instant.now().plusSeconds(7200)

    every { scheduler.scheduledJobs } returns listOf(triggerA, triggerB)
    every { scheduler.isRunning } returns true
    every { scheduler.isPaused(any()) } returns false

    val result = adapter.getCronjobStats()

    assertThat(result).hasSize(2)
    assertThat(result[0].simpleName).isEqualTo("AnotherTestCronjob")
    assertThat(result[1].simpleName).isEqualTo("TestCronjob")
  }

  @Test
  fun `empty scheduler returns empty list`() {
    every { scheduler.scheduledJobs } returns emptyList()

    val result = adapter.getCronjobStats()

    assertThat(result).isEmpty()
  }
}
