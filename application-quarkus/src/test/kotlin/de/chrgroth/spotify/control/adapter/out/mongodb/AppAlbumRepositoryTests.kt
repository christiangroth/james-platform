package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppAlbumRepositoryTests {

  @Inject
  lateinit var appAlbumRepository: AppAlbumRepositoryPort

  private fun album(suffix: String) = AppAlbum(
    id = AlbumId("album-$suffix-${UUID.randomUUID()}"),
    totalTracks = 10,
    title = "Album $suffix",
    imageLink = "https://img.example.com/cover-$suffix.jpg",
    releaseDate = "2023-01-01",
    releaseDatePrecision = "day",
    type = "album",
    artistId = ArtistId("artist-$suffix"),
    artistName = "Artist $suffix",
    lastSync = kotlin.time.Instant.fromEpochSeconds(1),
  )

  @Test
  fun `upsertAll persists new items and findByAlbumIds returns them`() {
    val item = album("new")
    appAlbumRepository.upsertAll(listOf(item))

    val result = appAlbumRepository.findByAlbumIds(setOf(item.id))

    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(item.id)
    assertThat(result[0].title).isEqualTo(item.title)
    assertThat(result[0].artistId).isEqualTo(item.artistId)
    assertThat(result[0].artistName).isEqualTo(item.artistName)
    assertThat(result[0].type).isEqualTo(item.type)
    assertThat(result[0].totalTracks).isEqualTo(item.totalTracks)
    assertThat(result[0].releaseDate).isEqualTo(item.releaseDate)
    assertThat(result[0].lastSync).isNotNull()
  }

  @Test
  fun `upsertAll overwrites existing fields on re-upsert`() {
    val albumId = "album-overwrite-${UUID.randomUUID()}"
    appAlbumRepository.upsertAll(listOf(AppAlbum(id = AlbumId(albumId), title = "Original Title", lastSync = kotlin.time.Instant.fromEpochSeconds(1))))

    appAlbumRepository.upsertAll(listOf(AppAlbum(id = AlbumId(albumId), title = "Updated Title", lastSync = kotlin.time.Instant.fromEpochSeconds(1))))

    val result = appAlbumRepository.findByAlbumIds(setOf(AlbumId(albumId)))
    assertThat(result).hasSize(1)
    assertThat(result[0].title).isEqualTo("Updated Title")
  }

  @Test
  fun `upsertAll persists additional artist fields`() {
    val albumId = "album-multi-artist-${UUID.randomUUID()}"
    val item = AppAlbum(
      id = AlbumId(albumId),
      title = "Collab Album",
      artistId = ArtistId("artist-1"),
      artistName = "Artist One",
      additionalArtistIds = listOf(ArtistId("artist-2")),
      additionalArtistNames = listOf("Artist Two"),
      lastSync = kotlin.time.Instant.fromEpochSeconds(1),
    )
    appAlbumRepository.upsertAll(listOf(item))

    val result = appAlbumRepository.findByAlbumIds(setOf(AlbumId(albumId)))
    assertThat(result).hasSize(1)
    assertThat(result[0].additionalArtistIds).containsExactly(ArtistId("artist-2"))
    assertThat(result[0].additionalArtistNames).containsExactly("Artist Two")
  }

  @Test
  fun `findByAlbumIds returns empty list for unknown albumIds`() {
    val result = appAlbumRepository.findByAlbumIds(setOf(AlbumId("unknown-album-${UUID.randomUUID()}")))
    assertThat(result).isEmpty()
  }

  @Test
  fun `findByAlbumIds returns empty list for empty input`() {
    val result = appAlbumRepository.findByAlbumIds(emptySet())
    assertThat(result).isEmpty()
  }

  @Test
  fun `findByArtistId returns albums for the given artist`() {
    val artistId = ArtistId("artist-find-${UUID.randomUUID()}")
    val album1 = album("a1").copy(artistId = artistId)
    val album2 = album("a2").copy(artistId = artistId)
    val other = album("other").copy(artistId = ArtistId("other-artist-${UUID.randomUUID()}"))
    appAlbumRepository.upsertAll(listOf(album1, album2, other))

    val result = appAlbumRepository.findByArtistId(artistId)

    assertThat(result.map { it.id }).containsExactlyInAnyOrder(album1.id, album2.id)
  }

  @Test
  fun `findByArtistId returns empty list when no albums match`() {
    val artistId = ArtistId("artist-none-${UUID.randomUUID()}")

    val result = appAlbumRepository.findByArtistId(artistId)

    assertThat(result).isEmpty()
  }

  @Test
  fun `countAll returns total number of albums`() {
    val before = appAlbumRepository.countAll()
    val album1 = album("count1")
    val album2 = album("count2")
    appAlbumRepository.upsertAll(listOf(album1, album2))

    val after = appAlbumRepository.countAll()

    assertThat(after).isEqualTo(before + 2)
  }
}
