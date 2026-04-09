package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.adapter.`in`.web.HealthSseAdapter
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxTaskCountObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsObserver
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.subscription.Cancellable
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@QuarkusTest
class HealthSseTests {

  @Inject
  lateinit var healthSseService: HealthSseAdapter

  @Inject
  lateinit var outgoingRequestStatsObserver: OutgoingRequestStatsObserver

  @Inject
  lateinit var outboxTaskCountObserver: OutboxTaskCountObserver

  @Test
  fun `sse endpoint delivers refresh-outgoing-http-calls event when outgoing request is recorded`() {
    val userId = UserId("test-user-health-sse-http")
    val received = CopyOnWriteArrayList<String>()
    val latch = CountDownLatch(1)

    val cancellable: Cancellable = healthSseService.stream(userId)
      .subscribe().with(
        { event: String -> received.add(event); latch.countDown() },
        { _: Throwable -> /* ignore errors */ },
      )

    outgoingRequestStatsObserver.onRequestRecorded()

    assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
    assertEquals(listOf("refresh-outgoing-http-calls"), received.toList())

    cancellable.cancel()
  }

  @Test
  fun `sse endpoint delivers refresh-outbox-partitions event when partition is activated`() {
    val userId = UserId("test-user-health-sse-outbox")
    val received = CopyOnWriteArrayList<String>()
    val latch = CountDownLatch(1)

    val cancellable: Cancellable = healthSseService.stream(userId)
      .subscribe().with(
        { event: String -> received.add(event); latch.countDown() },
        { _: Throwable -> /* ignore errors */ },
      )

    healthSseService.onPartitionActivated("to-spotify")

    assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
    assertEquals(listOf("refresh-outbox-partitions"), received.toList())

    cancellable.cancel()
  }

  @Test
  fun `sse endpoint delivers refresh-outbox-partitions event when outbox task count changes`() {
    val userId = UserId("test-user-health-sse-outbox-count")
    val received = CopyOnWriteArrayList<String>()
    val latch = CountDownLatch(1)

    val cancellable: Cancellable = healthSseService.stream(userId)
      .subscribe().with(
        { event: String -> received.add(event); latch.countDown() },
        { _: Throwable -> /* ignore errors */ },
      )

    outboxTaskCountObserver.onOutboxTaskCountChanged()

    assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
    assertEquals(listOf("refresh-outbox-partitions"), received.toList())

    cancellable.cancel()
  }

  @Test
  fun `sse endpoint delivers refresh-playback-state event when playback is detected`() {
    val userId = UserId("test-user-health-sse-playback")
    val received = CopyOnWriteArrayList<String>()
    val latch = CountDownLatch(1)

    val cancellable: Cancellable = healthSseService.stream(userId)
      .subscribe().with(
        { event: String -> received.add(event); latch.countDown() },
        { _: Throwable -> /* ignore errors */ },
      )

    healthSseService.onPlaybackDetected()

    assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
    assertEquals(listOf("refresh-playback-state"), received.toList())

    cancellable.cancel()
  }
}
