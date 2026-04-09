package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.adapter.`in`.web.DashboardSseAdapter
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
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
class DashboardSseTests {

  @Inject
  lateinit var dashboardSseService: DashboardSseAdapter

  @Inject
  lateinit var dashboardRefreshPort: DashboardRefreshPort

  @Test
  fun `sse endpoint delivers refresh-playback-data event when user is notified`() {
    val userId = UserId("test-user-sse")
    val received = CopyOnWriteArrayList<String>()
    val latch = CountDownLatch(1)

    val cancellable: Cancellable = dashboardSseService.stream(userId)
      .subscribe().with(
        { event: String -> received.add(event); latch.countDown() },
        { _: Throwable -> /* ignore errors */ },
      )

    // Mock a change: notify the user via the refresh port
    dashboardRefreshPort.notifyUserPlaybackData(userId)

    assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
    assertEquals(listOf("refresh-playback-data"), received.toList())

    cancellable.cancel()
  }

  @Test
  fun `sse endpoint delivers refresh-playlist-metadata event when playlist metadata is notified`() {
    val userId = UserId("test-user-sse-playlist")
    val received = CopyOnWriteArrayList<String>()
    val latch = CountDownLatch(1)

    val cancellable: Cancellable = dashboardSseService.stream(userId)
      .subscribe().with(
        { event: String -> received.add(event); latch.countDown() },
        { _: Throwable -> /* ignore errors */ },
      )

    dashboardRefreshPort.notifyUserPlaylistMetadata(userId)

    assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
    assertEquals(listOf("refresh-playlist-metadata"), received.toList())

    cancellable.cancel()
  }

  @Test
  fun `sse endpoint delivers refresh-catalog-data event when catalog data is notified`() {
    val userId = UserId("test-user-sse-catalog")
    val received = CopyOnWriteArrayList<String>()
    val latch = CountDownLatch(1)

    val cancellable: Cancellable = dashboardSseService.stream(userId)
      .subscribe().with(
        { event: String -> received.add(event); latch.countDown() },
        { _: Throwable -> /* ignore errors */ },
      )

    dashboardRefreshPort.notifyCatalogData()

    assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE refresh event should be received within 5 seconds")
    assertEquals(listOf("refresh-catalog-data"), received.toList())

    cancellable.cancel()
  }
}
