package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppTrackDataRepositoryTests {

  @Inject
  lateinit var appTrackRepository: AppTrackRepositoryPort

  private fun trackData(suffix: String) = AppTrack(
    id = TrackId("track-$suffix-${UUID.randomUUID()}"),
    title = "Track $suffix",
    artistId = ArtistId("artist-$suffix"),
    lastSync = kotlin.time.Instant.fromEpochSeconds(1),
  )

  @Test
  fun `upsertAll persists new items and findByTrackIds returns them`() {
    val item = trackData("new")
    appTrackRepository.upsertAll(listOf(item))

    val result = appTrackRepository.findByTrackIds(setOf(item.id))

    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(item.id)
    assertThat(result[0].title).isEqualTo(item.title)
    assertThat(result[0].artistId).isEqualTo(item.artistId)
  }

  @Test
  fun `upsertAll updates existing items when id matches`() {
    val original = trackData("update")
    appTrackRepository.upsertAll(listOf(original))

    val updated = original.copy(title = "Updated Title")
    appTrackRepository.upsertAll(listOf(updated))

    val result = appTrackRepository.findByTrackIds(setOf(original.id))

    assertThat(result).hasSize(1)
    assertThat(result[0].title).isEqualTo("Updated Title")
  }

  @Test
  fun `findByTrackIds returns empty list for unknown trackIds`() {
    val result = appTrackRepository.findByTrackIds(setOf(TrackId("unknown-track-id-${UUID.randomUUID()}")))
    assertThat(result).isEmpty()
  }

  @Test
  fun `findByTrackIds returns empty list for empty input`() {
    val result = appTrackRepository.findByTrackIds(emptySet())
    assertThat(result).isEmpty()
  }

  @Test
  fun `findByTrackIds returns all matching items in a single batch`() {
    val item1 = trackData("batch1")
    val item2 = trackData("batch2")
    val item3 = trackData("batch3")
    appTrackRepository.upsertAll(listOf(item1, item2, item3))

    val result = appTrackRepository.findByTrackIds(setOf(item1.id, item2.id, item3.id))

    assertThat(result).hasSize(3)
    assertThat(result.map { it.id }).containsExactlyInAnyOrder(item1.id, item2.id, item3.id)
  }

  @Test
  fun `upsertAll stores all sync fields`() {
    val item = trackData("sync").copy(
      albumId = AlbumId("album-1"),
      albumName = "Album One",
      artistName = "Artist Name",
      additionalArtistIds = listOf(ArtistId("artist-2")),
      additionalArtistNames = listOf("Artist Two"),
      discNumber = 1,
      durationMs = 210000,
      trackNumber = 5,
      type = "track",
    )
    appTrackRepository.upsertAll(listOf(item))

    val result = appTrackRepository.findByTrackIds(setOf(item.id))
    assertThat(result).hasSize(1)
    assertThat(result[0].albumId).isEqualTo(AlbumId("album-1"))
    assertThat(result[0].albumName).isEqualTo("Album One")
    assertThat(result[0].artistName).isEqualTo("Artist Name")
    assertThat(result[0].additionalArtistIds).containsExactly(ArtistId("artist-2"))
    assertThat(result[0].additionalArtistNames).containsExactly("Artist Two")
    assertThat(result[0].discNumber).isEqualTo(1)
    assertThat(result[0].durationMs).isEqualTo(210000)
    assertThat(result[0].trackNumber).isEqualTo(5)
    assertThat(result[0].type).isEqualTo("track")
    assertThat(result[0].lastSync).isNotEqualTo(kotlin.time.Instant.DISTANT_PAST)
  }

  @Test
  fun `upsertAll overwrites albumId and albumName on re-upsert`() {
    val withAlbum = trackData("album-set").copy(albumId = AlbumId("album-x"), albumName = "Album X")
    appTrackRepository.upsertAll(listOf(withAlbum))

    val updated = withAlbum.copy(albumId = AlbumId("album-y"), albumName = "Album Y")
    appTrackRepository.upsertAll(listOf(updated))

    val result = appTrackRepository.findByTrackIds(setOf(withAlbum.id))
    assertThat(result).hasSize(1)
    assertThat(result[0].albumId).isEqualTo(AlbumId("album-y"))
    assertThat(result[0].albumName).isEqualTo("Album Y")
  }

  @Test
  fun `findByArtistId returns tracks for the given artist`() {
    val artistId = ArtistId("artist-find-${UUID.randomUUID()}")
    val track1 = trackData("t1").copy(artistId = artistId)
    val track2 = trackData("t2").copy(artistId = artistId)
    val other = trackData("other").copy(artistId = ArtistId("other-artist-${UUID.randomUUID()}"))
    appTrackRepository.upsertAll(listOf(track1, track2, other))

    val result = appTrackRepository.findByArtistId(artistId)

    assertThat(result.map { it.id }).containsExactlyInAnyOrder(track1.id, track2.id)
  }

  @Test
  fun `findByArtistId returns empty list when no tracks match`() {
    val artistId = ArtistId("artist-none-${UUID.randomUUID()}")

    val result = appTrackRepository.findByArtistId(artistId)

    assertThat(result).isEmpty()
  }

  @Test
  fun `findByAlbumId returns tracks for the given album`() {
    val albumId = AlbumId("album-find-${UUID.randomUUID()}")
    val track1 = trackData("ta1").copy(albumId = albumId)
    val track2 = trackData("ta2").copy(albumId = albumId)
    val other = trackData("other-album").copy(albumId = AlbumId("other-album-${UUID.randomUUID()}"))
    appTrackRepository.upsertAll(listOf(track1, track2, other))

    val result = appTrackRepository.findByAlbumId(albumId)

    assertThat(result.map { it.id }).containsExactlyInAnyOrder(track1.id, track2.id)
  }

  @Test
  fun `findByAlbumId returns empty list when no tracks match`() {
    val albumId = AlbumId("album-none-${UUID.randomUUID()}")

    val result = appTrackRepository.findByAlbumId(albumId)

    assertThat(result).isEmpty()
  }

  @Test
  fun `countAll returns total number of tracks`() {
    val before = appTrackRepository.countAll()
    val track1 = trackData("count1")
    val track2 = trackData("count2")
    appTrackRepository.upsertAll(listOf(track1, track2))

    val after = appTrackRepository.countAll()

    assertThat(after).isEqualTo(before + 2)
  }
}
