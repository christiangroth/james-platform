package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.catalog.AppArtist
import de.chrgroth.james.platform.domain.model.catalog.ArtistId
import de.chrgroth.james.platform.domain.port.out.catalog.AppArtistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppArtistRepositoryTests {

  @Inject
  lateinit var appArtistRepository: AppArtistRepositoryPort

  private fun artist(suffix: String) = AppArtist(
    id = ArtistId("artist-$suffix-${UUID.randomUUID()}"),
    artistName = "Artist $suffix",
    lastSync = kotlin.time.Instant.fromEpochSeconds(1),
  )

  @Test
  fun `upsertAll persists new items and findByArtistIds returns them`() {
    val item = artist("new")
    appArtistRepository.upsertAll(listOf(item))

    val result = appArtistRepository.findByArtistIds(setOf(item.id))

    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(item.id)
    assertThat(result[0].artistName).isEqualTo(item.artistName)
  }

  @Test
  fun `upsertAll updates existing items when artistId matches`() {
    val original = artist("update")
    appArtistRepository.upsertAll(listOf(original))

    val updated = original.copy(artistName = "Updated Name")
    appArtistRepository.upsertAll(listOf(updated))

    val result = appArtistRepository.findByArtistIds(setOf(original.id))

    assertThat(result).hasSize(1)
    assertThat(result[0].artistName).isEqualTo("Updated Name")
  }

  @Test
  fun `findByArtistIds returns empty list for unknown artistIds`() {
    val result = appArtistRepository.findByArtistIds(setOf(ArtistId("unknown-artist-${UUID.randomUUID()}")))
    assertThat(result).isEmpty()
  }

  @Test
  fun `findByArtistIds returns empty list for empty input`() {
    val result = appArtistRepository.findByArtistIds(emptySet())
    assertThat(result).isEmpty()
  }

  @Test
  fun `findByArtistIds returns all matching items in a single batch`() {
    val item1 = artist("batch1")
    val item2 = artist("batch2")
    val item3 = artist("batch3")
    appArtistRepository.upsertAll(listOf(item1, item2, item3))

    val result = appArtistRepository.findByArtistIds(setOf(item1.id, item2.id, item3.id))

    assertThat(result).hasSize(3)
    assertThat(result.map { it.id }).containsExactlyInAnyOrder(item1.id, item2.id, item3.id)
  }

  @Test
  fun `upsertAll stores all sync fields`() {
    val item = artist("sync").copy(
      imageLink = "https://example.com/image.jpg",
      type = "artist",
    )
    appArtistRepository.upsertAll(listOf(item))

    val result = appArtistRepository.findByArtistIds(setOf(item.id))
    assertThat(result).hasSize(1)
    assertThat(result[0].artistName).isEqualTo(item.artistName)
    assertThat(result[0].imageLink).isEqualTo("https://example.com/image.jpg")
    assertThat(result[0].type).isEqualTo("artist")
    assertThat(result[0].lastSync).isNotEqualTo(kotlin.time.Instant.DISTANT_PAST)
  }

  @Test
  fun `findWithImageLinkAndBlankName returns only artists with imageLink and blank artistName`() {
    val withImageAndBlankName = artist("blank-name").copy(artistName = "", imageLink = "https://img.example.com/1.jpg")
    val withImageAndName = artist("has-name").copy(imageLink = "https://img.example.com/2.jpg")
    val withoutImage = artist("no-image").copy(artistName = "")
    appArtistRepository.upsertAll(listOf(withImageAndBlankName, withImageAndName, withoutImage))
    val result = appArtistRepository.findWithImageLinkAndBlankName()

    assertThat(result.map { it.id }).contains(withImageAndBlankName.id)
    assertThat(result.map { it.id }).doesNotContain(withImageAndName.id, withoutImage.id)
  }

  @Test
  fun `countAll returns total number of artists`() {
    val before = appArtistRepository.countAll()
    val artist1 = artist("count1")
    val artist2 = artist("count2")
    appArtistRepository.upsertAll(listOf(artist1, artist2))

    val after = appArtistRepository.countAll()

    assertThat(after).isEqualTo(before + 2)
  }
}
