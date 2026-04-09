package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaylistRepositoryTests {

  @Inject
  lateinit var playlistRepository: PlaylistRepositoryPort

  private fun buildPlaylistInfo(id: String, syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.PASSIVE): PlaylistInfo {
    val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
    return PlaylistInfo(
      spotifyPlaylistId = id,
      snapshotId = "snap-1",
      lastSnapshotIdSyncTime = now - 1.hours,
      name = "Playlist $id",
      syncStatus = syncStatus,
    )
  }

  @Test
  fun `findByUserId returns empty list when no playlists exist`() {
    val userId = UserId("no-playlists-${UUID.randomUUID()}")

    assertThat(playlistRepository.findByUserId(userId)).isEmpty()
  }

  @Test
  fun `saveAll and findByUserId round-trips playlists correctly`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    val playlists = listOf(
      buildPlaylistInfo("p1", PlaylistSyncStatus.ACTIVE),
      buildPlaylistInfo("p2", PlaylistSyncStatus.PASSIVE),
    )

    playlistRepository.replaceAll(userId, playlists)

    val found = playlistRepository.findByUserId(userId)
    assertThat(found).hasSize(2)
    val byId = found.associateBy { it.spotifyPlaylistId }
    assertThat(byId["p1"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
    assertThat(byId["p2"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
  }

  @Test
  fun `saveAll replaces previous playlists for the same user`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    playlistRepository.replaceAll(userId, listOf(buildPlaylistInfo("p1"), buildPlaylistInfo("p2")))

    playlistRepository.replaceAll(userId, listOf(buildPlaylistInfo("p3")))

    val found = playlistRepository.findByUserId(userId)
    assertThat(found).hasSize(1)
    assertThat(found[0].spotifyPlaylistId).isEqualTo("p3")
  }

  @Test
  fun `saveAll with empty list removes all playlists for user`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    playlistRepository.replaceAll(userId, listOf(buildPlaylistInfo("p1")))

    playlistRepository.replaceAll(userId, emptyList())

    assertThat(playlistRepository.findByUserId(userId)).isEmpty()
  }

  @Test
  fun `saveAll does not affect playlists of other users`() {
    val userId1 = UserId("test-${UUID.randomUUID()}")
    val userId2 = UserId("test-${UUID.randomUUID()}")
    playlistRepository.replaceAll(userId1, listOf(buildPlaylistInfo("p1")))
    playlistRepository.replaceAll(userId2, listOf(buildPlaylistInfo("p2")))

    playlistRepository.replaceAll(userId1, listOf(buildPlaylistInfo("p3")))

    assertThat(playlistRepository.findByUserId(userId1).map { it.spotifyPlaylistId }).containsExactly("p3")
    assertThat(playlistRepository.findByUserId(userId2).map { it.spotifyPlaylistId }).containsExactly("p2")
  }

  @Test
  fun `updateLastSyncTime sets lastSyncTime on existing playlist metadata`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    playlistRepository.replaceAll(userId, listOf(buildPlaylistInfo("p1")))
    val syncTime = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

    playlistRepository.updateLastSyncTime(userId, "p1", syncTime)

    val found = playlistRepository.findByUserId(userId)
    assertThat(found).hasSize(1)
    assertThat(found[0].lastSyncTime).isEqualTo(syncTime)
  }

  @Test
  fun `updateLastSyncTime is a no-op for unknown playlist`() {
    val userId = UserId("test-${UUID.randomUUID()}")

    playlistRepository.updateLastSyncTime(userId, "unknown", Clock.System.now())

    assertThat(playlistRepository.findByUserId(userId)).isEmpty()
  }

  @Test
  fun `saveAll and findByUserId round-trips lastSyncTime correctly`() {
    val userId = UserId("test-${UUID.randomUUID()}")
    val syncTime = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
    val playlist = buildPlaylistInfo("p1").copy(lastSyncTime = syncTime)

    playlistRepository.replaceAll(userId, listOf(playlist))

    val found = playlistRepository.findByUserId(userId)
    assertThat(found).hasSize(1)
    assertThat(found[0].lastSyncTime).isEqualTo(syncTime)
  }
}
