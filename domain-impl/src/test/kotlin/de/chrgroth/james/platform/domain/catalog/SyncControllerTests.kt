package de.chrgroth.james.platform.domain.catalog

import de.chrgroth.james.platform.domain.model.catalog.AppArtist
import de.chrgroth.james.platform.domain.model.catalog.AppTrack
import de.chrgroth.james.platform.domain.model.catalog.ArtistId
import de.chrgroth.james.platform.domain.model.catalog.TrackId
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.james.platform.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class SyncControllerTests {

  private val appTrackRepository: AppTrackRepositoryPort = mockk(relaxed = true)
  private val appArtistRepository: AppArtistRepositoryPort = mockk(relaxed = true)
  private val outboxPort: OutboxPort = mockk(relaxed = true)

  private val controller = SyncController(
    appTrackRepository,
    appArtistRepository,
    outboxPort,
  )

  private val userId = UserId("user-1")
  private val syncTime = Instant.fromEpochSeconds(1)

  private fun track(id: String) = AppTrack(
    id = TrackId(id),
    title = "Track $id",
    artistId = ArtistId("artist-$id"),
    lastSync = syncTime,
  )

  private fun artist(id: String) = AppArtist(
    id = ArtistId(id),
    artistName = "Artist $id",
    lastSync = syncTime,
  )

  // --- syncForTracks ---

  @Test
  fun `syncForTracks with empty list does nothing`() {
    controller.syncForTracks(emptyList(), userId)

    verify(exactly = 0) { appTrackRepository.findByTrackIds(any()) }
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `syncForTracks skips tracks already present in catalog`() {
    every { appTrackRepository.findByTrackIds(any()) } returns listOf(track("t1"))

    controller.syncForTracks(
      listOf(CatalogSyncRequest("t1", listOf("artist-1"))),
      userId,
    )

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `syncForTracks enqueues SyncArtistDetails for missing artist`() {
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    controller.syncForTracks(
      listOf(CatalogSyncRequest("t1", listOf("artist-1"))),
      userId,
    )

    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
  }

  @Test
  fun `syncForTracks does not enqueue SyncArtistDetails when artist already in catalog`() {
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns listOf(artist("artist-1"))

    controller.syncForTracks(
      listOf(CatalogSyncRequest("t1", listOf("artist-1"))),
      userId,
    )

    verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncArtistDetails }) }
  }

  @Test
  fun `syncForTracks does nothing when all tracks are in catalog`() {
    every { appTrackRepository.findByTrackIds(any()) } returns listOf(track("t1"), track("t2"))

    controller.syncForTracks(
      listOf(
        CatalogSyncRequest("t1", listOf("artist-1")),
        CatalogSyncRequest("t2", listOf("artist-2")),
      ),
      userId,
    )

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `syncForTracks deduplicates artists across multiple missing tracks`() {
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    controller.syncForTracks(
      listOf(
        CatalogSyncRequest("t1", listOf("artist-shared")),
        CatalogSyncRequest("t2", listOf("artist-shared")),
      ),
      userId,
    )

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-shared", userId)) }
  }

  // --- syncArtists ---

  @Test
  fun `syncArtists enqueues SyncArtistDetails for missing artists`() {
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    controller.syncArtists(listOf("artist-1", "artist-2"), userId)

    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-1", userId)) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails("artist-2", userId)) }
  }

  @Test
  fun `syncArtists skips artists already in catalog`() {
    every { appArtistRepository.findByArtistIds(any()) } returns listOf(artist("artist-1"))

    controller.syncArtists(listOf("artist-1"), userId)

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `syncArtists with empty list does nothing`() {
    controller.syncArtists(emptyList(), userId)

    verify(exactly = 0) { appArtistRepository.findByArtistIds(any()) }
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }
}
