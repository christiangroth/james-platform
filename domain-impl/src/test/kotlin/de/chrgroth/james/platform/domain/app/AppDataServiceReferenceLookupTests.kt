package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.ReferenceLookup
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/** Reference lookups (ADR 0026): entering a REF property by its target's lookup text, optionally auto-creating the target on save. */
class AppDataServiceReferenceLookupTests {

  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val aggregationInlineUpdate: AggregationInlineUpdateService = mockk(relaxed = true)
  private val service = AppDataService(installedAppRepository, appVersionRepository, appDataRepository, PropertyConstraintService(), aggregationInlineUpdate)

  private val installedAppId = InstalledAppId("installed-1")
  private val appId = AppId("app-1")
  private val userId = "user-1"

  private val locationId = EntityDefinitionId("location")
  private val locationName = Property(PropertyId("location-name"), "name", PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey))
  private val locationCity = Property(PropertyId("location-city"), "stadt", PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.MaxLength(20)))
  private val location = EntityDefinition(locationId, "Location", properties = listOf(locationName, locationCity))

  private val artistId = EntityDefinitionId("artist")
  private val artistName = Property(PropertyId("artist-name"), "name", PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey))
  private val artist = EntityDefinition(artistId, "Künstler", properties = listOf(artistName))

  private val gigId = EntityDefinitionId("gig")
  private val gigDate = Property(PropertyId("gig-date"), "datum", PropertyType.DATE, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey))
  private val gigArtist = Property(
    PropertyId("gig-artist"), "künstler", PropertyType.REF, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey),
    targetEntityId = artistId, referenceLookup = ReferenceLookup(artistName.id, autoCreate = true),
  )
  private val gigLocation = Property(
    PropertyId("gig-location"), "location", PropertyType.REF, nullable = false,
    targetEntityId = locationId, referenceLookup = ReferenceLookup(locationName.id, autoCreate = true),
  )
  private val gig = EntityDefinition(gigId, "Auftritt", properties = listOf(gigDate, gigArtist, gigLocation))

  private val stored = mutableListOf<AppData>()

  private fun version(vararg entities: EntityDefinition) = AppVersion(
    id = AppVersionId("ver-1"),
    appId = appId,
    versionNumber = VersionNumber("1.0.0"),
    releaseNotes = "test",
    entityDefinitions = entities.toList(),
    reports = emptyList(),
    status = AppVersionStatus.PUBLISHED,
    createdAt = Instant.now(),
  )

  private fun givenVersion(vararg entities: EntityDefinition) {
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns version(*entities)
  }

  private fun storedObject(id: String, entity: EntityDefinitionId, data: Map<String, String?>) = AppData(
    id = AppDataId(id),
    userId = userId,
    installedAppId = installedAppId,
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = entity,
    objectVersion = 1,
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
    data = data,
  ).also { stored += it }

  private fun storedOf(entity: EntityDefinitionId) = stored.filter { it.entityType == entity }

  @BeforeEach
  fun setUp() {
    every { installedAppRepository.findById(installedAppId) } returns
      InstalledApp(id = installedAppId, userId = userId, appId = appId, installedVersionNumber = VersionNumber("1.0.0"), installedAt = Instant.now())
    // mockk hands inline value class arguments to answers either boxed or unboxed, so compare against both forms
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, any()) } answers {
      val entity = secondArg<Any>()
      stored.filter { it.entityType == entity || it.entityType.value == entity }
    }
    every { appDataRepository.findById(any()) } answers {
      val id = firstArg<Any>()
      stored.find { it.id == id || it.id.value == id }
    }
    every { appDataRepository.save(any()) } answers {
      val appData = firstArg<AppData>()
      stored.removeIf { it.id == appData.id }
      stored += appData
    }
    givenVersion(gig, artist, location)
    storedObject("loc-1", locationId, mapOf(locationName.id.value to "Palladium", locationCity.id.value to "Köln"))
  }

  private fun gigForm(date: String = "2026-11-02", artistText: String? = "Emil Bulls", locationText: String? = "Palladium", extra: Map<String, List<String>> = emptyMap()) =
    buildMap<String, List<String>> {
      put("prop_${gigDate.id.value}", listOf(date))
      artistText?.let { put("reflookup_${gigArtist.id.value}", listOf(it)) }
      locationText?.let { put("reflookup_${gigLocation.id.value}", listOf(it)) }
      putAll(extra)
    }

  @Test
  fun `resolves lookup text to the existing target object`() {
    storedObject("artist-1", artistId, mapOf(artistName.id.value to "Emil Bulls"))

    val created = service.createAppData(userId, installedAppId.value, gigId.value, gigForm()).getOrNull()!!

    assertThat(created.data[gigArtist.id.value]).isEqualTo("artist-1")
    assertThat(created.data[gigLocation.id.value]).isEqualTo("loc-1")
    assertThat(storedOf(artistId)).hasSize(1)
  }

  @Test
  fun `resolves lookup text case-insensitively when exactly one target matches`() {
    storedObject("artist-1", artistId, mapOf(artistName.id.value to "Emil Bulls"))

    val created = service.createAppData(userId, installedAppId.value, gigId.value, gigForm(artistText = "  emil bulls ", locationText = "PALLADIUM")).getOrNull()!!

    assertThat(created.data[gigArtist.id.value]).isEqualTo("artist-1")
    assertThat(created.data[gigLocation.id.value]).isEqualTo("loc-1")
  }

  @Test
  fun `auto-creates an unknown target from the lookup text and saves it before the referencing object`() {
    val created = service.createAppData(userId, installedAppId.value, gigId.value, gigForm(artistText = "Opeth")).getOrNull()!!

    val newArtist = storedOf(artistId).single()
    assertThat(newArtist.data[artistName.id.value]).isEqualTo("Opeth")
    assertThat(created.data[gigArtist.id.value]).isEqualTo(newArtist.id.value)
    assertThat(stored.indexOf(newArtist)).isLessThan(stored.indexOfFirst { it.id == created.id })
  }

  @Test
  fun `auto-creates a target with further required values from the new-object fields`() {
    val form = gigForm(locationText = "Zeche", extra = mapOf("refnew_${gigLocation.id.value}.${locationCity.id.value}" to listOf("Bochum")))

    val created = service.createAppData(userId, installedAppId.value, gigId.value, form).getOrNull()!!

    val zeche = storedOf(locationId).single { it.data[locationName.id.value] == "Zeche" }
    assertThat(zeche.data[locationCity.id.value]).isEqualTo("Bochum")
    assertThat(created.data[gigLocation.id.value]).isEqualTo(zeche.id.value)
  }

  @Test
  fun `reports the new target's constraint violations under the REF property and saves nothing`() {
    val form = gigForm(artistText = "Opeth", locationText = "Zeche", extra = mapOf("refnew_${gigLocation.id.value}.${locationCity.id.value}" to listOf("B".repeat(21))))
    val result = service.createAppData(userId, installedAppId.value, gigId.value, form)

    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations.keys).containsExactly(gigLocation.id.value)
    assertThat(error.propertyViolations[gigLocation.id.value]).containsExactly(PropertyConstraintViolation.MaxLengthViolation(20))
    assertThat(error.pathedViolations.map { it.path }).containsExactly("location › stadt")
    assertThat(storedOf(locationId)).hasSize(1)
    assertThat(storedOf(artistId)).isEmpty()
    assertThat(storedOf(gigId)).isEmpty()
  }

  @Test
  fun `rejects an auto-created target missing a required value`() {
    val result = service.createAppData(userId, installedAppId.value, gigId.value, gigForm(locationText = "Zeche"))

    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations[gigLocation.id.value]).containsExactly(PropertyConstraintViolation.RequiredValueViolation)
    assertThat(error.pathedViolations.map { it.path }).containsExactly("location › stadt")
    assertThat(storedOf(locationId)).hasSize(1)
    assertThat(storedOf(gigId)).isEmpty()
  }

  @Test
  fun `does not create the target when the referencing object itself is invalid`() {
    storedObject("artist-1", artistId, mapOf(artistName.id.value to "Emil Bulls"))
    storedObject("gig-1", gigId, mapOf(gigDate.id.value to "2026-11-02", gigArtist.id.value to "artist-1", gigLocation.id.value to "loc-1"))

    val result = service.createAppData(userId, installedAppId.value, gigId.value, gigForm(locationText = "Zeche", extra = mapOf(
      "refnew_${gigLocation.id.value}.${locationCity.id.value}" to listOf("Bochum"),
    )))

    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations[gigArtist.id.value]).contains(PropertyConstraintViolation.UniqueKeyViolation)
    assertThat(storedOf(locationId)).hasSize(1)
    assertThat(storedOf(gigId)).hasSize(1)
  }

  @Test
  fun `rejects an unknown lookup text when the property does not auto-create`() {
    givenVersion(gig.copy(properties = listOf(gigDate, gigArtist.copy(referenceLookup = ReferenceLookup(artistName.id, autoCreate = false)), gigLocation)), artist, location)

    val result = service.createAppData(userId, installedAppId.value, gigId.value, gigForm(artistText = "Opeth"))

    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations[gigArtist.id.value]).containsExactly(PropertyConstraintViolation.ReferenceLookupNotFoundViolation)
    assertThat(storedOf(artistId)).isEmpty()
  }

  @Test
  fun `creates one target only when two lookups name the same new target`() {
    val second = gigLocation.copy(id = PropertyId("gig-support-location"), name = "support", nullable = true)
    givenVersion(gig.copy(properties = gig.properties + second), artist, location)
    val form = gigForm(locationText = "Zeche", extra = mapOf(
      "refnew_${gigLocation.id.value}.${locationCity.id.value}" to listOf("Bochum"),
      "reflookup_${second.id.value}" to listOf("zeche"),
    ))

    val created = service.createAppData(userId, installedAppId.value, gigId.value, form).getOrNull()!!

    assertThat(storedOf(locationId).filter { it.data[locationName.id.value] == "Zeche" }).hasSize(1)
    assertThat(created.data[second.id.value]).isEqualTo(created.data[gigLocation.id.value])
  }

  @Test
  fun `blank lookup text clears the reference, which a required REF then reports as missing`() {
    val result = service.createAppData(userId, installedAppId.value, gigId.value, gigForm(artistText = " ", extra = mapOf("prop_${gigArtist.id.value}" to listOf("stale-id"))))

    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations[gigArtist.id.value]).contains(PropertyConstraintViolation.RequiredValueViolation)
    assertThat(storedOf(artistId)).isEmpty()
  }

  @Test
  fun `keeps a submitted REF id when no lookup text is submitted (API clients)`() {
    storedObject("artist-1", artistId, mapOf(artistName.id.value to "Emil Bulls"))
    val form = mapOf(
      "prop_${gigDate.id.value}" to listOf("2026-11-02"),
      "prop_${gigArtist.id.value}" to listOf("artist-1"),
      "prop_${gigLocation.id.value}" to listOf("loc-1"),
    )

    val created = service.createAppData(userId, installedAppId.value, gigId.value, form).getOrNull()!!

    assertThat(created.data[gigArtist.id.value]).isEqualTo("artist-1")
  }

  @Test
  fun `updateAppData resolves and auto-creates lookups as well`() {
    storedObject("artist-1", artistId, mapOf(artistName.id.value to "Emil Bulls"))
    storedObject("gig-1", gigId, mapOf(gigDate.id.value to "2026-11-02", gigArtist.id.value to "artist-1", gigLocation.id.value to "loc-1"))

    val updated = service.updateAppData(userId, installedAppId.value, "gig-1", gigForm(artistText = "Imminence")).getOrNull()!!

    val imminence = storedOf(artistId).single { it.data[artistName.id.value] == "Imminence" }
    assertThat(updated.data[gigArtist.id.value]).isEqualTo(imminence.id.value)
    assertThat(updated.objectVersion).isEqualTo(2)
  }

  @Test
  fun `ignores a lookup that is no longer valid and keeps the regular REF value`() {
    val compositeLocation = location.copy(properties = listOf(locationName, locationCity.copy(constraints = setOf(PropertyConstraint.UniqueKey))))
    givenVersion(gig, artist, compositeLocation)
    storedObject("artist-1", artistId, mapOf(artistName.id.value to "Emil Bulls"))
    val form = gigForm(extra = mapOf("prop_${gigLocation.id.value}" to listOf("loc-1")))

    val created = service.createAppData(userId, installedAppId.value, gigId.value, form).getOrNull()!!

    assertThat(created.data[gigLocation.id.value]).isEqualTo("loc-1")
  }
}
