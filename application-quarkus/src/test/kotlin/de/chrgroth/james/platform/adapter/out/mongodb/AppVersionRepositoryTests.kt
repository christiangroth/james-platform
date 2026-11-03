package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date
import java.util.UUID

@QuarkusTest
class AppVersionRepositoryTests {

  @Inject
  lateinit var appVersionRepository: AppVersionRepositoryPort

  @Inject
  lateinit var appVersionDocumentRepository: AppVersionDocumentRepository

  @Test
  fun `save and findById round-trip an OBJECT property nested five levels deep`() {
    // level 5 (innermost) is a plain scalar, levels 1-4 are nested OBJECT properties wrapping the level below
    val level5 = Property(id = PropertyId("level-5"), name = "Level5", type = PropertyType.STRING, nullable = true)
    val level4 = Property(id = PropertyId("level-4"), name = "Level4", type = PropertyType.OBJECT, nullable = true, nestedProperties = listOf(level5))
    val level3 = Property(id = PropertyId("level-3"), name = "Level3", type = PropertyType.OBJECT, nullable = true, nestedProperties = listOf(level4))
    val level2 = Property(id = PropertyId("level-2"), name = "Level2", type = PropertyType.OBJECT, nullable = true, nestedProperties = listOf(level3))
    val level1 = Property(id = PropertyId("level-1"), name = "Level1", type = PropertyType.OBJECT, nullable = true, nestedProperties = listOf(level2))

    val entityDefinition = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "DeepEntity", properties = listOf(level1))
    val version = AppVersion(
      id = AppVersionId(UUID.randomUUID().toString()),
      appId = AppId(UUID.randomUUID().toString()),
      versionNumber = null,
      releaseNotes = null,
      entityDefinitions = listOf(entityDefinition),
      reports = emptyList(),
      status = AppVersionStatus.DRAFT,
      createdAt = Instant.now(),
    )

    appVersionRepository.save(version)

    val loaded = appVersionRepository.findById(version.id)
    assertThat(loaded).isNotNull()
    val loadedLevel1 = loaded!!.entityDefinitions.single().properties.single()
    assertThat(loadedLevel1.id).isEqualTo(level1.id)

    val loadedLevel2 = loadedLevel1.nestedProperties.single()
    assertThat(loadedLevel2.id).isEqualTo(level2.id)

    val loadedLevel3 = loadedLevel2.nestedProperties.single()
    assertThat(loadedLevel3.id).isEqualTo(level3.id)

    val loadedLevel4 = loadedLevel3.nestedProperties.single()
    assertThat(loadedLevel4.id).isEqualTo(level4.id)

    val loadedLevel5 = loadedLevel4.nestedProperties.single()
    assertThat(loadedLevel5).isEqualTo(level5)
  }

  /** A version stored before ADR 0029, with Kotlin scripts on a computed property, a smart default and the entity (migration script). */
  private fun legacyVersion(id: String): Document = Document()
    .append("_id", id)
    .append("appId", "app-${UUID.randomUUID()}")
    .append("versionNumber", "1.0.0")
    .append("releaseNotes", "notes")
    .append("status", "PUBLISHED")
    .append("createdAt", Date())
    .append("reports", emptyList<Document>())
    .append(
      "entityDefinitions",
      listOf(
        Document()
          .append("_id", "e-1")
          .append("name", "Lauf")
          .append("migrationScript", "it + (\"x\" to \"1\")")
          .append(
            "properties",
            listOf(
              Document().append("_id", "p-datum").append("name", "datum").append("type", "DATE").append("nullable", true)
                .append("smartDefault", "java.time.LocalDate.now().toString()"),
            ),
          )
          .append("computedProperties", listOf(Document().append("_id", "cp-1").append("name", "pace").append("type", "DOUBLE").append("script", "42.0"))),
      ),
    )

  @Test
  fun `a version with Kotlin scripts loads, the scripts become legacy values that are no longer evaluated`() {
    val id = UUID.randomUUID().toString()
    appVersionDocumentRepository.mongoCollection().withDocumentClass(Document::class.java).insertOne(legacyVersion(id))

    val entity = appVersionRepository.findById(AppVersionId(id))!!.entityDefinitions.single()

    assertThat(entity.properties.single().smartDefault).isNull()
    assertThat(entity.properties.single().legacySmartDefaultScript).isEqualTo("java.time.LocalDate.now().toString()")
    assertThat(entity.computedProperties.single().expression).isNull()
    assertThat(entity.computedProperties.single().legacyScript).isEqualTo("42.0")
  }

  @Test
  fun `expressions, units and legacy scripts round-trip`() {
    val id = UUID.randomUUID().toString()
    appVersionDocumentRepository.mongoCollection().withDocumentClass(Document::class.java).insertOne(legacyVersion(id))
    val loaded = appVersionRepository.findById(AppVersionId(id))!!
    val kilometers = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.KILOMETERS, DistanceGranularity.KILOMETERS)
    val entity = loaded.entityDefinitions.single()
    val added = ComputedProperty(ComputedPropertyId("cp-2"), "strecke", PropertyType.DOUBLE, expression = "1 + 2", unit = kilometers)
    val updated = loaded.copy(
      entityDefinitions = listOf(
        entity.copy(properties = entity.properties.map { it.copy(smartDefault = "today()") }, computedProperties = entity.computedProperties + added),
      ),
    )

    appVersionRepository.save(updated)
    val reloaded = appVersionRepository.findById(AppVersionId(id))!!.entityDefinitions.single()

    assertThat(reloaded.properties.single().smartDefault).isEqualTo("today()")
    assertThat(reloaded.properties.single().legacySmartDefaultScript).isEqualTo("java.time.LocalDate.now().toString()")
    assertThat(reloaded.computedProperties.map { it.legacyScript }).containsExactly("42.0", null)
    assertThat(reloaded.computedProperties.last().expression).isEqualTo("1 + 2")
    assertThat(reloaded.computedProperties.last().unit).isEqualTo(kilometers)
  }
}
