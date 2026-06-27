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
  fun `save and findById preserves recursively nested OBJECT properties`() {
    val innermost = Property(
      id = PropertyId(UUID.randomUUID().toString()),
      name = "street",
      type = PropertyType.STRING,
    )
    val nested = Property(
      id = PropertyId(UUID.randomUUID().toString()),
      name = "address",
      type = PropertyType.OBJECT,
      nestedProperties = listOf(innermost),
    )
    val top = Property(
      id = PropertyId(UUID.randomUUID().toString()),
      name = "contact",
      type = PropertyType.OBJECT,
      nestedProperties = listOf(nested),
    )
    val entityDefinition = EntityDefinition(
      id = EntityDefinitionId(UUID.randomUUID().toString()),
      name = "Person",
      properties = listOf(top),
    )
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

    val found = appVersionRepository.findById(version.id)
    assertThat(found).isNotNull()
    val foundTop = found!!.entityDefinitions.single().properties.single()
    assertThat(foundTop.name).isEqualTo("contact")
    val foundNested = foundTop.nestedProperties.single()
    assertThat(foundNested.name).isEqualTo("address")
    val foundInnermost = foundNested.nestedProperties.single()
    assertThat(foundInnermost.name).isEqualTo("street")
    assertThat(foundInnermost.type).isEqualTo(PropertyType.STRING)
  }
}
