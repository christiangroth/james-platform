package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
class ImportJobRepositoryTests {

  @Inject
  lateinit var importJobRepository: ImportJobRepositoryPort

  @Inject
  lateinit var importJobDocumentRepository: ImportJobDocumentRepository

  @Test
  fun `migrateLongToDurationFieldMappingConversion rewrites legacy LONG_TO_DURATION conversion to NONE`() {
    val id = UUID.randomUUID().toString()
    val legacyDocument = legacyImportJobDocument(id).append(
      "mapping",
      Document(
        "fieldMappings",
        listOf(
          Document()
            .append("targetPropertyId", "duration")
            .append("sourcePath", "source.duration")
            .append("conversion", "LONG_TO_DURATION")
            .append("importGranularity", null)
            .append("fallbackValue", null)
            .append("referenceLookup", null),
        ),
      ),
    )
    importJobDocumentRepository.mongoCollection().withDocumentClass(Document::class.java).insertOne(legacyDocument)

    importJobRepository.migrateLongToDurationFieldMappingConversion()

    val migrated = importJobRepository.findById(ImportJobId(id))
    assertThat(migrated).isNotNull()
    assertThat(migrated!!.mapping!!.fieldMappings.single().conversion).isEqualTo(FieldMappingConversion.NONE)
  }

  @Test
  fun `migrateLongToDurationFieldMappingConversion leaves other conversions unchanged`() {
    val id = UUID.randomUUID().toString()
    val document = legacyImportJobDocument(id).append(
      "mapping",
      Document(
        "fieldMappings",
        listOf(
          Document()
            .append("targetPropertyId", "amount")
            .append("sourcePath", "source.amount")
            .append("conversion", "STRING_TO_LONG")
            .append("importGranularity", null)
            .append("fallbackValue", null)
            .append("referenceLookup", null),
        ),
      ),
    )
    importJobDocumentRepository.mongoCollection().withDocumentClass(Document::class.java).insertOne(document)

    importJobRepository.migrateLongToDurationFieldMappingConversion()

    val untouched = importJobRepository.findById(ImportJobId(id))
    assertThat(untouched).isNotNull()
    assertThat(untouched!!.mapping!!.fieldMappings.single().conversion).isEqualTo(FieldMappingConversion.STRING_TO_LONG)
  }

  private fun legacyImportJobDocument(id: String) = Document()
    .append("_id", id)
    .append("userId", "user-1")
    .append("installedAppId", "app-1")
    .append("connectionId", "connection-1")
    .append("urlPostfix", null)
    .append("targetEntityDefinitionId", "entity-1")
    .append("status", "READY")
    .append("payload", "[]")
    .append("detectedDataPaths", emptyList<Document>())
    .append("selectedDataPath", null)
    .append("detectedSchema", emptyList<Document>())
    .append("filteredSchema", emptyList<Document>())
    .append("filterRules", emptyList<Document>())
    .append("createdAt", Instant.now())
    .append("lastChangedAt", Instant.now())
}
