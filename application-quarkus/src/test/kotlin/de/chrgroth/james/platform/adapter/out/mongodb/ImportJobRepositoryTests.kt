package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
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

  /**
   * This migration (ADR 0017) already ran to completion against production data before ADR 0021 moved `mapping` off
   * `ImportJobDocument` onto `ImportDefinitionDocument` - it is kept operating on the legacy `import_job.mapping`
   * subdocument path as dead-but-harmless cleanup code (see [ImportJobRepositoryAdapter]), so this test reads back
   * the raw BSON rather than the domain model, which no longer exposes `mapping` on `ImportJob`.
   */
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

    assertThat(migratedConversion(id)).isEqualTo("NONE")
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

    assertThat(migratedConversion(id)).isEqualTo("STRING_TO_LONG")
  }

  @Suppress("UNCHECKED_CAST")
  private fun migratedConversion(id: String): String? {
    val document = importJobDocumentRepository.mongoCollection().withDocumentClass(Document::class.java).find(Filters.eq("_id", id)).first()!!
    val fieldMapping = (document.get("mapping", Document::class.java).get("fieldMappings") as List<Document>).single()
    return fieldMapping.getString("conversion")
  }

  private fun legacyImportJobDocument(id: String) = Document()
    .append("_id", id)
    .append("userId", "user-1")
    .append("installedAppId", "app-1")
    .append("importDefinitionId", "definition-1")
    .append("status", "READY")
    .append("payload", "[]")
    .append("detectedDataPaths", emptyList<Document>())
    .append("detectedSchema", emptyList<Document>())
    .append("filteredSchema", emptyList<Document>())
    .append("createdAt", Instant.now())
    .append("lastChangedAt", Instant.now())
}
