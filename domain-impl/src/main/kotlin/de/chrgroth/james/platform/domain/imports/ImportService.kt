package de.chrgroth.james.platform.domain.imports

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.imports.DryRunAcceptResult
import de.chrgroth.james.platform.domain.model.imports.DryRunObject
import de.chrgroth.james.platform.domain.model.imports.DryRunReport
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.FilterSample
import de.chrgroth.james.platform.domain.model.imports.FilterView
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingSample
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.model.imports.resolveImportUrl
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.TokenEncryptionPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class ImportService(
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val importJobRepository: ImportJobRepositoryPort,
  private val importConnectionRepository: ImportConnectionRepositoryPort,
  private val importFetch: ImportFetchPort,
  private val tokenEncryption: TokenEncryptionPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val propertyConstraint: PropertyConstraintPort,
) : ImportPort {

  override fun listAllImportJobs(userId: String): List<ImportJob> =
    importJobRepository.findAllByUserId(userId).sortedByDescending { it.createdAt }

  override fun triggerImport(
    userId: String,
    installedAppId: String,
    connectionId: String,
    targetEntityDefinitionId: String,
    urlPostfix: String?,
  ): Either<DomainError, ImportJob> {
    val installedApp = requireOwnedInstalledApp(userId, installedAppId) ?: run {
      logger.warn { "Trigger import failed: installed app not found: $installedAppId for user: $userId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val entityDefinition = entityDefinitionsOf(installedApp).find { it.id.value == targetEntityDefinitionId } ?: run {
      logger.warn { "Trigger import failed: entity definition not found: $targetEntityDefinitionId for installedAppId: $installedAppId" }
      return ImportError.ENTITY_DEFINITION_NOT_FOUND.left()
    }
    val connection = importConnectionRepository.findById(ImportConnectionId(connectionId))?.takeIf { it.userId == userId } ?: run {
      logger.warn { "Trigger import failed: connection not found: $connectionId for user: $userId" }
      return ImportError.CONNECTION_NOT_FOUND.left()
    }
    val trimmedUrlPostfix = urlPostfix?.trim()?.takeIf { it.isNotBlank() }

    val bearerToken = connection.encryptedBearerToken?.let { tokenEncryption.decrypt(it).fold({ return it.left() }, { it }) }.orEmpty()
    val rawPayload = importFetch.fetch(resolveImportUrl(connection.baseUrl, trimmedUrlPostfix), bearerToken).fold({ return it.left() }, { it })

    val parsed = try {
      objectMapper.readTree(rawPayload)
    } catch (e: Exception) {
      logger.warn { "Trigger import failed: invalid JSON response from connection: $connectionId" }
      return ImportError.INVALID_JSON_RESPONSE.left()
    }
    if (!parsed.isObject) {
      logger.warn { "Trigger import failed: response is not a JSON object from connection: $connectionId" }
      return ImportError.NOT_A_JSON_OBJECT.left()
    }

    val detectedDataPaths = DataPathDetector.detect(parsed)
    val singleMatch = detectedDataPaths.singleOrNull()
    val schema = singleMatch?.let { SchemaDetector.detect(parsed, it.path) }.orEmpty()

    val now = Instant.now()
    val importJob = ImportJob(
      id = ImportJobId(UUID.randomUUID().toString()),
      userId = userId,
      installedAppId = installedApp.id,
      connectionId = connection.id,
      urlPostfix = trimmedUrlPostfix,
      targetEntityDefinitionId = entityDefinition.id,
      status = if (singleMatch != null) ImportStatus.DATA_IDENTIFIED else ImportStatus.DOWNLOADED,
      payload = rawPayload,
      detectedDataPaths = detectedDataPaths,
      selectedDataPath = singleMatch?.path,
      detectedSchema = schema,
      filteredSchema = schema,
      createdAt = now,
      lastChangedAt = now,
    )
    importJobRepository.save(importJob)
    logger.info { "Import job created: installedAppId=$installedAppId connectionId=$connectionId targetEntityDefinitionId=$targetEntityDefinitionId detectedDataPaths=${detectedDataPaths.size}" }
    return importJob.right()
  }

  override fun selectDataPath(userId: String, importJobId: String, dataPath: String): Either<DomainError, ImportJob> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Select data path failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    if (existing.status != ImportStatus.DOWNLOADED) {
      logger.warn { "Select data path failed: import job not in DOWNLOADED status: $importJobId" }
      return ImportError.IMPORT_JOB_NOT_DOWNLOADED.left()
    }
    if (dataPath.isBlank()) {
      logger.warn { "Select data path failed: blank data path for importJobId: $importJobId" }
      return ImportError.BLANK_DATA_PATH.left()
    }

    val parsed = objectMapper.readTree(existing.payload)
    val resolved = DataPathDetector.resolve(parsed, dataPath.trim())
    if (resolved == null) {
      logger.warn { "Select data path failed: invalid data path for importJobId: $importJobId" }
      return ImportError.INVALID_DATA_PATH.left()
    }

    val schema = SchemaDetector.detect(parsed, resolved.path)
    val updated = existing.copy(
      status = ImportStatus.DATA_IDENTIFIED,
      selectedDataPath = resolved.path,
      detectedSchema = schema,
      filteredSchema = schema,
      lastChangedAt = Instant.now(),
    )
    importJobRepository.save(updated)
    logger.info { "Data path selected: importJobId=$importJobId path=${resolved.path}" }
    return updated.right()
  }

  override fun getFilterView(userId: String, importJobId: String): Either<DomainError, FilterView> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Get filter view failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    return filterView(existing).right()
  }

  override fun updateFilter(userId: String, importJobId: String, filterRules: List<FilterRule>): Either<DomainError, FilterView> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Update filter failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    if (existing.status != ImportStatus.DATA_IDENTIFIED && existing.status != ImportStatus.READY) {
      logger.warn { "Update filter failed: import job not filterable: $importJobId" }
      return ImportError.IMPORT_JOB_NOT_FILTERABLE.left()
    }

    val filteredRecords = FilterEvaluator.apply(rawRecordsAt(existing), filterRules)
    val updated = existing.copy(
      filterRules = filterRules,
      filteredSchema = SchemaDetector.detect(filteredRecords),
      lastChangedAt = Instant.now(),
    )
    importJobRepository.save(updated)
    logger.info { "Filter updated: importJobId=$importJobId rules=${filterRules.size}" }
    return filterView(updated).right()
  }

  private fun filterView(existing: ImportJob): FilterView {
    val allRecords = rawRecordsAt(existing)
    return FilterView(existing, allRecords.size, FilterEvaluator.apply(allRecords, existing.filterRules).size)
  }

  override fun resolveFilterFieldValues(userId: String, importJobId: String, sourcePath: String): Either<DomainError, List<String>> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Resolve filter field values failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    return FilterEvaluator.distinctValues(rawRecordsAt(existing), sourcePath).right()
  }

  override fun resolveFilterSample(userId: String, importJobId: String, matched: Boolean, index: Int): Either<DomainError, FilterSample> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Resolve filter sample failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val allRecords = rawRecordsAt(existing)
    val records = if (matched) FilterEvaluator.apply(allRecords, existing.filterRules) else FilterEvaluator.excluded(allRecords, existing.filterRules)
    return FilterSample(records.size, records.getOrNull(index)?.toString()).right()
  }

  override fun getMappingView(userId: String, importJobId: String): Either<DomainError, MappingView> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Get mapping view failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Get mapping view failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val targetEntityDefinition = entityDefinitionsOf(installedApp).find { it.id == existing.targetEntityDefinitionId } ?: run {
      logger.warn { "Get mapping view failed: target entity definition not found for importJobId: $importJobId" }
      return ImportError.ENTITY_DEFINITION_NOT_FOUND.left()
    }

    val entityDefinitions = entityDefinitionsOf(installedApp)
    val validation = existing.mapping?.let { mapping ->
      MappingValidator.validate(mapping, targetEntityDefinition, existing.filteredSchema, entityDefinitions, propertyConstraint)
    }
    return MappingView(existing, targetEntityDefinition, entityDefinitions, validation).right()
  }

  override fun updateMapping(
    userId: String,
    importJobId: String,
    fieldMappings: List<FieldMapping>,
  ): Either<DomainError, MappingView> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Update mapping failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Update mapping failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    if (existing.status != ImportStatus.DATA_IDENTIFIED && existing.status != ImportStatus.READY) {
      logger.warn { "Update mapping failed: import job not mappable: $importJobId" }
      return ImportError.IMPORT_JOB_NOT_MAPPABLE.left()
    }

    val entityDefinitions = entityDefinitionsOf(installedApp)
    val targetEntityDefinition = entityDefinitions.find { it.id == existing.targetEntityDefinitionId } ?: run {
      logger.warn { "Update mapping failed: target entity definition not found for importJobId: $importJobId" }
      return ImportError.ENTITY_DEFINITION_NOT_FOUND.left()
    }
    val propertyIds = targetEntityDefinition.properties.map { it.id }.toSet()
    if (fieldMappings.any { it.targetPropertyId !in propertyIds }) {
      logger.warn { "Update mapping failed: unknown target property for importJobId: $importJobId" }
      return ImportError.MAPPING_PROPERTY_NOT_FOUND.left()
    }

    val mapping = Mapping(
      fieldMappings = fieldMappings,
    )
    val validation = MappingValidator.validate(mapping, targetEntityDefinition, existing.filteredSchema, entityDefinitions, propertyConstraint)
    val updated = existing.copy(
      status = if (validation.isReady) ImportStatus.READY else ImportStatus.DATA_IDENTIFIED,
      mapping = mapping,
      lastChangedAt = Instant.now(),
    )
    importJobRepository.save(updated)
    logger.info { "Mapping updated: importJobId=$importJobId status=${updated.status}" }
    return MappingView(updated, targetEntityDefinition, entityDefinitions, validation).right()
  }

  override fun dryRun(userId: String, importJobId: String): Either<DomainError, DryRunReport> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Dry run failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Dry run failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val (mapping, entityDefinition) = mappingAndEntity(existing, installedApp) ?: run {
      logger.warn { "Dry run failed: import job has no mapping: $importJobId" }
      return ImportError.IMPORT_JOB_NOT_READY.left()
    }

    val objects = executeDryRun(existing, mapping, entityDefinition, installedApp)
    logger.info { "Dry run executed: importJobId=$importJobId total=${objects.size} invalid=${objects.count { it.isInvalid }} skipped=${objects.count { it.isSkipped }}" }
    return DryRunReport(existing.id, objects).right()
  }

  override fun resolveMappingSample(userId: String, importJobId: String, index: Int, fieldMappings: List<FieldMapping>): Either<DomainError, MappingSample> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Resolve mapping sample failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Resolve mapping sample failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val entityDefinitions = entityDefinitionsOf(installedApp)
    val targetEntityDefinition = entityDefinitions.find { it.id == existing.targetEntityDefinitionId } ?: run {
      logger.warn { "Resolve mapping sample failed: target entity definition not found for importJobId: $importJobId" }
      return ImportError.ENTITY_DEFINITION_NOT_FOUND.left()
    }
    val propertyIds = targetEntityDefinition.properties.map { it.id }.toSet()
    if (fieldMappings.any { it.targetPropertyId !in propertyIds }) {
      logger.warn { "Resolve mapping sample failed: unknown target property for importJobId: $importJobId" }
      return ImportError.MAPPING_PROPERTY_NOT_FOUND.left()
    }

    val records = recordsAt(existing)
    val record = records.getOrNull(index) ?: return MappingSample(records.size, null).right()

    val dryRunObject = DryRunExecutor.executeSingle(
      record = record,
      mapping = Mapping(fieldMappings),
      entityDefinition = targetEntityDefinition,
      existingAppData = appDataRepository.findAllByInstalledAppIdAndEntityType(installedApp.id, targetEntityDefinition.id),
      entityDefinitionsById = entityDefinitions.associateBy { it.id },
      referencedAppDataByEntityId = referencedAppData(targetEntityDefinition, installedApp),
      propertyConstraint = propertyConstraint,
    )
    return MappingSample(records.size, dryRunObject).right()
  }

  override fun acceptDryRun(userId: String, importJobId: String, replaceExisting: Boolean): Either<DomainError, DryRunAcceptResult> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Accept dry run failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Accept dry run failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val (mapping, entityDefinition) = readyMappingAndEntity(existing, installedApp) ?: run {
      logger.warn { "Accept dry run failed: import job not ready: $importJobId" }
      return ImportError.IMPORT_JOB_NOT_READY.left()
    }

    if (replaceExisting) {
      appDataRepository.deleteAllByInstalledAppIdAndEntityType(installedApp.id, entityDefinition.id)
      logger.info { "Accept dry run: cleared existing data before replace import: installedAppId=${installedApp.id} entityType=${entityDefinition.id}" }
    }

    val objects = executeDryRun(existing, mapping, entityDefinition, installedApp)
    val now = Instant.now()
    var savedCount = 0
    for (obj in objects.filter { it.isValid }) {
      val data = entityDefinition.properties.associate { it.id.value to obj.targetData[it.id] }
      appDataRepository.save(
        AppData(
          id = AppDataId(UUID.randomUUID().toString()),
          userId = userId,
          installedAppId = installedApp.id,
          appVersion = installedApp.installedVersionNumber,
          entityType = entityDefinition.id,
          objectVersion = 1,
          createdAt = now,
          lastChangedAt = now,
          data = data,
        ),
      )
      savedCount++
    }
    val discardedCount = objects.size - savedCount

    importJobRepository.delete(existing.id)
    logger.info { "Dry run accepted: importJobId=$importJobId saved=$savedCount discarded=$discardedCount" }
    return DryRunAcceptResult(installedApp.id, savedCount, discardedCount).right()
  }

  private fun readyMappingAndEntity(existing: ImportJob, installedApp: InstalledApp): Pair<Mapping, EntityDefinition>? {
    if (existing.status != ImportStatus.READY) return null
    return mappingAndEntity(existing, installedApp)
  }

  /**
   * Mapping and target entity definition for a job that merely has a mapping configured, regardless of whether
   * it is free of blocking [de.chrgroth.james.platform.domain.model.imports.MappingIssue]s. Used for [dryRun],
   * which is read-only and should surface mapping issues per record rather than refuse to run - unlike
   * [acceptDryRun], which still requires [ImportStatus.READY] via [readyMappingAndEntity].
   */
  private fun mappingAndEntity(existing: ImportJob, installedApp: InstalledApp): Pair<Mapping, EntityDefinition>? {
    val mapping = existing.mapping ?: return null
    val entityDefinition = entityDefinitionsOf(installedApp).find { it.id == existing.targetEntityDefinitionId } ?: return null
    return mapping to entityDefinition
  }

  private fun executeDryRun(existing: ImportJob, mapping: Mapping, entityDefinition: EntityDefinition, installedApp: InstalledApp): List<DryRunObject> =
    DryRunExecutor.execute(
      records = recordsAt(existing),
      mapping = mapping,
      entityDefinition = entityDefinition,
      existingAppData = appDataRepository.findAllByInstalledAppIdAndEntityType(installedApp.id, entityDefinition.id),
      entityDefinitionsById = entityDefinitionsOf(installedApp).associateBy { it.id },
      referencedAppDataByEntityId = referencedAppData(entityDefinition, installedApp),
      propertyConstraint = propertyConstraint,
    )

  /** Loaded for every REF property (not just lookup-configured ones) so [DryRunExecutor] can also verify existence of directly mapped or fallback values. */
  private fun referencedAppData(entityDefinition: EntityDefinition, installedApp: InstalledApp): Map<EntityDefinitionId, List<AppData>> {
    val referencedEntityIds = entityDefinition.properties
      .filter { it.type == PropertyType.REF }
      .mapNotNull { it.targetEntityId }
      .toSet()
    return referencedEntityIds.associateWith { appDataRepository.findAllByInstalledAppIdAndEntityType(installedApp.id, it) }
  }

  /** Records at the job's selected data path, with [ImportJob.filterRules] applied - this is what mapping and dry-run operate on. */
  private fun recordsAt(existing: ImportJob): List<JsonNode> = FilterEvaluator.apply(rawRecordsAt(existing), existing.filterRules)

  private fun rawRecordsAt(existing: ImportJob): List<JsonNode> {
    val path = existing.selectedDataPath ?: return emptyList()
    var current = objectMapper.readTree(existing.payload)
    for (segment in path.split(".")) {
      current = current.get(segment) ?: return emptyList()
    }
    return current.toList()
  }

  private fun entityDefinitionsOf(installedApp: InstalledApp): List<EntityDefinition> =
    appVersionRepository.findByAppIdAndVersionNumber(installedApp.appId, installedApp.installedVersionNumber)?.entityDefinitions.orEmpty()

  override fun deleteImportJob(userId: String, importJobId: String): Either<DomainError, Unit> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Delete import job failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    importJobRepository.delete(existing.id)
    logger.info { "Import job deleted: $importJobId for user: $userId" }
    return Unit.right()
  }

  private fun requireOwnedInstalledApp(userId: String, installedAppId: String): InstalledApp? {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    return if (installedApp != null && installedApp.userId == userId) installedApp else null
  }

  private fun requireOwnedImportJob(userId: String, importJobId: String): ImportJob? {
    val existing = importJobRepository.findById(ImportJobId(importJobId))
    return if (existing != null && existing.userId == userId) existing else null
  }

  companion object : KLogging() {
    private val objectMapper = jacksonObjectMapper()
  }
}
