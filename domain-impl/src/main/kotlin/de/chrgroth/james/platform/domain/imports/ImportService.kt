package de.chrgroth.james.platform.domain.imports

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.imports.ImportDocument
import de.chrgroth.james.platform.domain.model.imports.ImportDocumentId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDocumentRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.user.TokenEncryptionPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class ImportService(
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val importDocumentRepository: ImportDocumentRepositoryPort,
  private val importFetch: ImportFetchPort,
  private val tokenEncryption: TokenEncryptionPort,
) : ImportPort {

  override fun listImportDocuments(userId: String, installedAppId: String): Either<DomainError, List<ImportDocument>> {
    val installedApp = requireOwnedInstalledApp(userId, installedAppId) ?: run {
      logger.warn { "List import documents failed: installed app not found: $installedAppId for user: $userId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    return importDocumentRepository.findAllByInstalledAppId(installedApp.id).sortedByDescending { it.createdAt }.right()
  }

  override fun triggerImport(userId: String, installedAppId: String, sourceUrl: String, bearerToken: String): Either<DomainError, ImportDocument> {
    val installedApp = requireOwnedInstalledApp(userId, installedAppId) ?: run {
      logger.warn { "Trigger import failed: installed app not found: $installedAppId for user: $userId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    if (sourceUrl.isBlank()) {
      logger.warn { "Trigger import failed: blank source URL for installedAppId: $installedAppId" }
      return ImportError.BLANK_URL.left()
    }
    if (bearerToken.isBlank()) {
      logger.warn { "Trigger import failed: blank bearer token for installedAppId: $installedAppId" }
      return ImportError.BLANK_BEARER_TOKEN.left()
    }

    val trimmedUrl = sourceUrl.trim()
    val trimmedToken = bearerToken.trim()

    val rawPayload = importFetch.fetch(trimmedUrl, trimmedToken).fold({ return it.left() }, { it })

    val parsed = try {
      objectMapper.readTree(rawPayload)
    } catch (e: Exception) {
      logger.warn { "Trigger import failed: invalid JSON response from $trimmedUrl" }
      return ImportError.INVALID_JSON_RESPONSE.left()
    }
    if (!parsed.isObject) {
      logger.warn { "Trigger import failed: response is not a JSON object from $trimmedUrl" }
      return ImportError.NOT_A_JSON_OBJECT.left()
    }

    val encryptedToken = tokenEncryption.encrypt(trimmedToken).fold({ return it.left() }, { it })

    val detectedDataPaths = DataPathDetector.detect(parsed)
    val singleMatch = detectedDataPaths.singleOrNull()

    val now = Instant.now()
    val importDocument = ImportDocument(
      id = ImportDocumentId(UUID.randomUUID().toString()),
      userId = userId,
      installedAppId = installedApp.id,
      sourceUrl = trimmedUrl,
      encryptedBearerToken = encryptedToken,
      status = if (singleMatch != null) ImportStatus.DATA_IDENTIFIED else ImportStatus.DOWNLOADED,
      payload = rawPayload,
      detectedDataPaths = detectedDataPaths,
      selectedDataPath = singleMatch?.path,
      createdAt = now,
      lastChangedAt = now,
    )
    importDocumentRepository.save(importDocument)
    logger.info { "Import document created: installedAppId=$installedAppId sourceUrl=$trimmedUrl detectedDataPaths=${detectedDataPaths.size}" }
    return importDocument.right()
  }

  override fun selectDataPath(userId: String, installedAppId: String, importDocumentId: String, dataPath: String): Either<DomainError, ImportDocument> {
    val installedApp = requireOwnedInstalledApp(userId, installedAppId) ?: run {
      logger.warn { "Select data path failed: installed app not found: $installedAppId for user: $userId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val existing = importDocumentRepository.findById(ImportDocumentId(importDocumentId))
    if (existing == null || existing.installedAppId != installedApp.id) {
      logger.warn { "Select data path failed: import document not found: $importDocumentId for installedAppId: $installedAppId" }
      return ImportError.IMPORT_DOCUMENT_NOT_FOUND.left()
    }
    if (existing.status != ImportStatus.DOWNLOADED) {
      logger.warn { "Select data path failed: import document not in DOWNLOADED status: $importDocumentId" }
      return ImportError.IMPORT_DOCUMENT_NOT_DOWNLOADED.left()
    }
    if (dataPath.isBlank()) {
      logger.warn { "Select data path failed: blank data path for importDocumentId: $importDocumentId" }
      return ImportError.BLANK_DATA_PATH.left()
    }

    val resolved = DataPathDetector.resolve(objectMapper.readTree(existing.payload), dataPath.trim())
    if (resolved == null) {
      logger.warn { "Select data path failed: invalid data path for importDocumentId: $importDocumentId" }
      return ImportError.INVALID_DATA_PATH.left()
    }

    val updated = existing.copy(
      status = ImportStatus.DATA_IDENTIFIED,
      selectedDataPath = resolved.path,
      lastChangedAt = Instant.now(),
    )
    importDocumentRepository.save(updated)
    logger.info { "Data path selected: importDocumentId=$importDocumentId path=${resolved.path}" }
    return updated.right()
  }

  override fun deleteImportDocument(userId: String, installedAppId: String, importDocumentId: String): Either<DomainError, Unit> {
    val installedApp = requireOwnedInstalledApp(userId, installedAppId) ?: run {
      logger.warn { "Delete import document failed: installed app not found: $installedAppId for user: $userId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val existing = importDocumentRepository.findById(ImportDocumentId(importDocumentId))
    if (existing == null || existing.installedAppId != installedApp.id) {
      logger.warn { "Delete import document failed: import document not found: $importDocumentId for installedAppId: $installedAppId" }
      return ImportError.IMPORT_DOCUMENT_NOT_FOUND.left()
    }
    importDocumentRepository.delete(existing.id)
    logger.info { "Import document deleted: $importDocumentId for installedAppId: $installedAppId" }
    return Unit.right()
  }

  private fun requireOwnedInstalledApp(userId: String, installedAppId: String): InstalledApp? {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    return if (installedApp != null && installedApp.userId == userId) installedApp else null
  }

  companion object : KLogging() {
    private val objectMapper = jacksonObjectMapper()
  }
}
