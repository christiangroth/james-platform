package de.chrgroth.james.platform.adapter.out.mongodb

import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
class ImportConnectionRepositoryTests {

  @Inject
  lateinit var importConnectionRepository: ImportConnectionRepositoryPort

  @Inject
  lateinit var importConnectionDocumentRepository: ImportConnectionDocumentRepository

  @Test
  fun `renameUrlFieldToBaseUrl migrates legacy url field to baseUrl`() {
    val id = UUID.randomUUID().toString()
    val legacyDocument = Document()
      .append("_id", id)
      .append("userId", "user-1")
      .append("name", "legacy connection")
      .append("url", "https://legacy.example.com")
      .append("createdAt", Instant.now())
      .append("lastChangedAt", Instant.now())
    importConnectionDocumentRepository.mongoCollection().withDocumentClass(Document::class.java).insertOne(legacyDocument)

    importConnectionRepository.renameUrlFieldToBaseUrl()

    val migrated = importConnectionRepository.findById(ImportConnectionId(id))
    assertThat(migrated).isNotNull()
    assertThat(migrated!!.baseUrl).isEqualTo("https://legacy.example.com")
  }

  @Test
  fun `renameUrlFieldToBaseUrl leaves documents that already have baseUrl unchanged`() {
    val id = UUID.randomUUID().toString()
    val document = Document()
      .append("_id", id)
      .append("userId", "user-1")
      .append("name", "current connection")
      .append("baseUrl", "https://current.example.com")
      .append("createdAt", Instant.now())
      .append("lastChangedAt", Instant.now())
    importConnectionDocumentRepository.mongoCollection().withDocumentClass(Document::class.java).insertOne(document)

    importConnectionRepository.renameUrlFieldToBaseUrl()

    val untouched = importConnectionRepository.findById(ImportConnectionId(id))
    assertThat(untouched).isNotNull()
    assertThat(untouched!!.baseUrl).isEqualTo("https://current.example.com")
  }
}
