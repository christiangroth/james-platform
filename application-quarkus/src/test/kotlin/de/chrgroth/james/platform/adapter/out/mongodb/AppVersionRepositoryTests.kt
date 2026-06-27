package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
class AppVersionRepositoryTests {

  @Inject
  lateinit var appVersionRepository: AppVersionRepositoryPort

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
}
