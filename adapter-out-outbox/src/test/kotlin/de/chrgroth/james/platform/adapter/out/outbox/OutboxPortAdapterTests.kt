package de.chrgroth.james.platform.adapter.out.outbox

import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.quarkus.outbox.domain.OutboxEventPriority
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionInfo
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionStatus
import de.chrgroth.quarkus.outbox.domain.OutboxTask as LibraryOutboxTask
import de.chrgroth.quarkus.outbox.domain.OutboxTaskStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

// DomainOutboxEvent is a sealed interface with no concrete subtypes yet (added by follow-up tickets in #543),
// so enqueue() cannot be exercised with a real or mocked instance until one exists.
class OutboxPortAdapterTests {

  private val outbox: ApplicationOutboxClient = mockk()

  private val adapter = OutboxPortAdapter(outbox)

  @Test
  fun `partition stats fall back to active with zero counts when no info exists`() {
    every { outbox.partitionInfos() } returns emptyList()

    val stats = adapter.getPartitionStats()

    assertThat(stats).hasSize(1)
    assertThat(stats.single().name).isEqualTo("domain")
    assertThat(stats.single().status).isEqualTo("ACTIVE")
    assertThat(stats.single().documentCount).isZero()
    assertThat(stats.single().blockedUntil).isNull()
    assertThat(stats.single().eventTypeCounts).isEmpty()
  }

  @Test
  fun `partition stats map library info sorted by count descending`() {
    every { outbox.partitionInfos() } returns listOf(
      OutboxPartitionInfo(
        key = "domain",
        status = OutboxPartitionStatus.PAUSED,
        statusReason = "manual pause",
        pausedUntil = Instant.parse("2026-08-18T00:00:00Z"),
        eventCount = 5L,
        eventPerTypeCount = mapOf("low-count-event" to 1L, "high-count-event" to 4L),
      ),
    )

    val stats = adapter.getPartitionStats().single()

    assertThat(stats.status).isEqualTo("PAUSED")
    assertThat(stats.documentCount).isEqualTo(5L)
    assertThat(stats.blockedUntil).isNotNull()
    assertThat(stats.eventTypeCounts).extracting("eventType").containsExactly("high-count-event", "low-count-event")
  }

  @Test
  fun `tasks by partition are mapped from library tasks`() {
    every { outbox.eventsForPartition(DomainOutboxPartition.Domain) } returns listOf(
      LibraryOutboxTask(
        id = "task-1",
        partition = "domain",
        eventType = "test-event",
        payload = "{}",
        deduplicationKey = "dedup-1",
        status = OutboxTaskStatus.PENDING,
        attempts = 0,
        createdAt = Instant.parse("2026-08-17T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-17T00:00:00Z"),
        nextRetryAt = null,
        priority = OutboxEventPriority.MEDIUM,
        lastError = null,
      ),
    )

    val tasks = adapter.getTasksByPartition("domain")

    assertThat(tasks).hasSize(1)
    assertThat(tasks.single().eventType).isEqualTo("test-event")
    assertThat(tasks.single().deduplicationKey).isEqualTo("dedup-1")
    assertThat(tasks.single().status).isEqualTo("PENDING")
  }
}
