package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportDefinitionId
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.port.out.imports.ImportDefinitionRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

@QuarkusTest
class ImportDefinitionRepositoryTests {

  @Inject
  lateinit var importDefinitionRepository: ImportDefinitionRepositoryPort

  @Test
  fun `save persists filter rules and mapping and find returns them unchanged`() {
    val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    val definition = ImportDefinition(
      id = ImportDefinitionId("def-${now.toEpochMilli()}"),
      userId = "user-1",
      connectionId = ImportConnectionId("conn-1"),
      name = "My API: Contacts",
      urlPostfix = "/latest",
      targetEntityDefinitionId = EntityDefinitionId("entity-1"),
      selectedDataPath = "items",
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE")),
      mapping = Mapping(fieldMappings = listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "name"))),
      createdAt = now,
      lastChangedAt = now,
    )

    importDefinitionRepository.save(definition)

    val found = importDefinitionRepository.findById(definition.id)
    assertThat(found).isEqualTo(definition)
  }

  @Test
  fun `findAllByUserId returns only the requesting user's definitions`() {
    val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    val owned = ImportDefinition(
      id = ImportDefinitionId("def-owned-${now.toEpochMilli()}"),
      userId = "user-1",
      connectionId = ImportConnectionId("conn-1"),
      name = "Owned",
      targetEntityDefinitionId = EntityDefinitionId("entity-1"),
      createdAt = now,
      lastChangedAt = now,
    )
    val other = owned.copy(id = ImportDefinitionId("def-other-${now.toEpochMilli()}"), userId = "someone-else", name = "Other")
    importDefinitionRepository.save(owned)
    importDefinitionRepository.save(other)

    val result = importDefinitionRepository.findAllByUserId("user-1")

    assertThat(result).contains(owned)
    assertThat(result).noneMatch { it.userId == "someone-else" }
  }

  @Test
  fun `delete removes the definition`() {
    val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    val definition = ImportDefinition(
      id = ImportDefinitionId("def-delete-${now.toEpochMilli()}"),
      userId = "user-1",
      connectionId = ImportConnectionId("conn-1"),
      name = "To delete",
      targetEntityDefinitionId = EntityDefinitionId("entity-1"),
      createdAt = now,
      lastChangedAt = now,
    )
    importDefinitionRepository.save(definition)

    importDefinitionRepository.delete(definition.id)

    assertThat(importDefinitionRepository.findById(definition.id)).isNull()
  }
}
