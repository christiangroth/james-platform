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
import de.chrgroth.james.platform.domain.model.app.ImportProvenance
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.buildAggregationDependencyIndex
import de.chrgroth.james.platform.domain.model.imports.DryRunObject
import de.chrgroth.james.platform.domain.model.imports.DryRunReport
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.FilterSample
import de.chrgroth.james.platform.domain.model.imports.FilterView
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportDefinitionId
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.ImportTrigger
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingSample
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.resolveImportUrl
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDefinitionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
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
  private val importDefinitionRepository: ImportDefinitionRepositoryPort,
  private val importConnectionRepository: ImportConnectionRepositoryPort,
  private val importFetch: ImportFetchPort,
  private val tokenEncryption: TokenEncryptionPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val propertyConstraint: PropertyConstraintPort,
  private val outbox: OutboxPort,
) : ImportPort {

  override fun listAllImportJobs(userId: String): List<ImportJob> =
    importJobRepository.findAllByUserId(userId).sortedByDescending { it.createdAt }

  override fun listAllImportDefinitions(userId: String): List<ImportDefinition> =
    importDefinitionRepository.findAllByUserId(userId)

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
    val definition = ImportDefinition(
      id = ImportDefinitionId(UUID.randomUUID().toString()),
      userId = userId,
      connectionId = connection.id,
      name = "${connection.name}: ${entityDefinition.name}",
      urlPostfix = trimmedUrlPostfix,
      targetEntityDefinitionId = entityDefinition.id,
      selectedDataPath = singleMatch?.path,
      createdAt = now,
      lastChangedAt = now,
    )
    importDefinitionRepository.save(definition)

    val importJob = ImportJob(
      id = ImportJobId(UUID.randomUUID().toString()),
      userId = userId,
      installedAppId = installedApp.id,
      importDefinitionId = definition.id,
      status = if (singleMatch != null) ImportStatus.DATA_IDENTIFIED else ImportStatus.DOWNLOADED,
      payload = rawPayload,
      detectedDataPaths = detectedDataPaths,
      detectedSchema = schema,
      filteredSchema = schema,
      createdAt = now,
      lastChangedAt = now,
    )
    importJobRepository.save(importJob)
    logger.info { "Import job created: installedAppId=$installedAppId connectionId=$connectionId targetEntityDefinitionId=$targetEntityDefinitionId detectedDataPaths=${detectedDataPaths.size}" }
    return importJob.right()
  }

  override fun triggerScheduledImport(definitionId: String): Either<DomainError, ImportJob> {
    val definition = importDefinitionRepository.findById(ImportDefinitionId(definitionId)) ?: run {
      logger.warn { "Trigger scheduled import failed: import definition not found: $definitionId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    var acceptedSchema = definition.lastKnownSchema
    val result = runScheduledImport(definition) { acceptedSchema = it }
    importDefinitionRepository.save(definition.copy(lastKnownSchema = acceptedSchema, lastRunAt = Instant.now(), lastChangedAt = Instant.now()))
    return result
  }

  /**
   * Fetch-to-accept pipeline for an unattended run, reusing [definition]'s already-configured
   * [ImportDefinition.selectedDataPath]/[ImportDefinition.filterRules]/[ImportDefinition.mapping] unchanged instead
   * of running the interactive Filter/Mapping steps. Only ever advances to [acceptDryRun] - invoking [onAccepted]
   * with the newly detected schema so the caller ([triggerScheduledImport]) can update
   * [ImportDefinition.lastKnownSchema] in the single save it always performs afterwards regardless of outcome - when
   * the freshly detected schema does not deviate from the definition's stored baseline and the mapping is still
   * blocking-issue-free; every other outcome returns without creating or accepting any [ImportJob], leaving prior
   * data untouched.
   */
  private fun runScheduledImport(definition: ImportDefinition, onAccepted: (List<SchemaProperty>) -> Unit): Either<DomainError, ImportJob> {
    val mapping = definition.mapping ?: run {
      logger.warn { "Scheduled import skipped: import definition has no mapping configured: ${definition.id.value}" }
      return ImportError.DEFINITION_NOT_CONFIGURED.left()
    }
    val selectedDataPath = definition.selectedDataPath ?: run {
      logger.warn { "Scheduled import skipped: import definition has no data path selected: ${definition.id.value}" }
      return ImportError.DEFINITION_NOT_CONFIGURED.left()
    }
    val installedApp = installedAppFor(definition) ?: run {
      logger.warn { "Scheduled import failed: installed app not found for definitionId: ${definition.id.value}" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val entityDefinitions = entityDefinitionsOf(installedApp)
    val entityDefinition = entityDefinitions.find { it.id == definition.targetEntityDefinitionId } ?: run {
      logger.warn { "Scheduled import failed: target entity definition not found: ${definition.targetEntityDefinitionId.value} for definitionId: ${definition.id.value}" }
      return ImportError.ENTITY_DEFINITION_NOT_FOUND.left()
    }
    val connection = importConnectionRepository.findById(definition.connectionId) ?: run {
      logger.warn { "Scheduled import failed: connection not found: ${definition.connectionId.value} for definitionId: ${definition.id.value}" }
      return ImportError.CONNECTION_NOT_FOUND.left()
    }

    val bearerToken = connection.encryptedBearerToken?.let { tokenEncryption.decrypt(it).fold({ return it.left() }, { it }) }.orEmpty()
    val rawPayload = importFetch.fetch(resolveImportUrl(connection.baseUrl, definition.urlPostfix), bearerToken).fold({ return it.left() }, { it })

    val parsed = try {
      objectMapper.readTree(rawPayload)
    } catch (e: Exception) {
      logger.warn { "Scheduled import failed: invalid JSON response: definitionId=${definition.id.value} connectionId=${connection.id.value}" }
      return ImportError.INVALID_JSON_RESPONSE.left()
    }
    if (!parsed.isObject) {
      logger.warn { "Scheduled import failed: response is not a JSON object: definitionId=${definition.id.value} connectionId=${connection.id.value}" }
      return ImportError.NOT_A_JSON_OBJECT.left()
    }
    val resolved = DataPathDetector.resolve(parsed, selectedDataPath) ?: run {
      logger.warn { "Scheduled import failed: data path no longer resolvable: definitionId=${definition.id.value} path=$selectedDataPath" }
      return ImportError.INVALID_DATA_PATH.left()
    }

    val detectedSchema = SchemaDetector.detect(parsed, resolved.path)
    if (definition.lastKnownSchema.isNotEmpty() && SchemaDrift.detected(definition.lastKnownSchema, detectedSchema)) {
      logger.warn { "Scheduled import aborted: detected schema differs from the last accepted run, no accept performed: definitionId=${definition.id.value}" }
      return ImportError.SCHEMA_DRIFT_DETECTED.left()
    }

    val now = Instant.now()
    val importJob = ImportJob(
      id = ImportJobId(UUID.randomUUID().toString()),
      userId = definition.userId,
      installedAppId = installedApp.id,
      importDefinitionId = definition.id,
      status = ImportStatus.DATA_IDENTIFIED,
      payload = rawPayload,
      detectedDataPaths = listOf(resolved),
      detectedSchema = detectedSchema,
      triggeredBy = ImportTrigger.SYSTEM,
      createdAt = now,
      lastChangedAt = now,
    )
    val filteredSchema = SchemaDetector.detect(recordsAt(importJob, definition))
    val validation = MappingValidator.validate(mapping, entityDefinition, filteredSchema, entityDefinitions, propertyConstraint)
    if (!validation.isReady) {
      logger.warn { "Scheduled import aborted: mapping not ready, no accept performed: definitionId=${definition.id.value} issues=${validation.blockingIssues.size}" }
      return ImportError.IMPORT_JOB_NOT_READY.left()
    }

    val readyJob = importJob.copy(status = ImportStatus.READY, filteredSchema = filteredSchema)
    importJobRepository.save(readyJob)
    onAccepted(detectedSchema)
    logger.info { "Scheduled import job created: definitionId=${definition.id.value} importJobId=${readyJob.id.value}" }
    return acceptDryRun(definition.userId, readyJob.id.value, replaceExisting = false)
  }

  /** [definition]'s owner's installed app whose entity definitions include [ImportDefinition.targetEntityDefinitionId] - not stored on the definition directly, so derived this way. */
  private fun installedAppFor(definition: ImportDefinition): InstalledApp? =
    installedAppRepository.findAllByUserId(definition.userId).find { installedApp ->
      entityDefinitionsOf(installedApp).any { it.id == definition.targetEntityDefinitionId }
    }

  override fun updateSchedule(userId: String, definitionId: String, schedule: String?): Either<DomainError, ImportDefinition> {
    val definition = importDefinitionRepository.findById(ImportDefinitionId(definitionId))?.takeIf { it.userId == userId } ?: run {
      logger.warn { "Update schedule failed: import definition not found: $definitionId for user: $userId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    val trimmedSchedule = schedule?.trim()?.takeIf { it.isNotBlank() }
    if (trimmedSchedule != null) {
      if (definition.selectedDataPath == null || definition.mapping == null) {
        logger.warn { "Update schedule failed: import definition not fully configured yet: $definitionId" }
        return ImportError.DEFINITION_NOT_CONFIGURED.left()
      }
      if (!CronSchedule.isValid(trimmedSchedule)) {
        logger.warn { "Update schedule failed: invalid cron expression for definitionId: $definitionId" }
        return ImportError.INVALID_CRON_SCHEDULE.left()
      }
    }
    val updated = definition.copy(schedule = trimmedSchedule, lastChangedAt = Instant.now())
    importDefinitionRepository.save(updated)
    logger.info { "Schedule updated: definitionId=$definitionId schedule=${trimmedSchedule ?: "none"}" }
    return updated.right()
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
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Select data path failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }

    val parsed = objectMapper.readTree(existing.payload)
    val resolved = DataPathDetector.resolve(parsed, dataPath.trim())
    if (resolved == null) {
      logger.warn { "Select data path failed: invalid data path for importJobId: $importJobId" }
      return ImportError.INVALID_DATA_PATH.left()
    }

    val schema = SchemaDetector.detect(parsed, resolved.path)
    importDefinitionRepository.save(definition.copy(selectedDataPath = resolved.path, lastChangedAt = Instant.now()))
    val updated = existing.copy(
      status = ImportStatus.DATA_IDENTIFIED,
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
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Get filter view failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    return filterView(existing, definition).right()
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
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Update filter failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }

    val filteredRecords = FilterEvaluator.apply(rawRecordsAt(existing, definition), filterRules)
    val updatedDefinition = definition.copy(filterRules = filterRules, lastChangedAt = Instant.now())
    importDefinitionRepository.save(updatedDefinition)
    val updatedJob = existing.copy(
      filteredSchema = SchemaDetector.detect(filteredRecords),
      lastChangedAt = Instant.now(),
    )
    importJobRepository.save(updatedJob)
    logger.info { "Filter updated: importJobId=$importJobId rules=${filterRules.size}" }
    return filterView(updatedJob, updatedDefinition).right()
  }

  private fun filterView(existing: ImportJob, definition: ImportDefinition): FilterView {
    val allRecords = rawRecordsAt(existing, definition)
    return FilterView(existing, definition, allRecords.size, FilterEvaluator.apply(allRecords, definition.filterRules).size)
  }

  override fun resolveFilterFieldValues(userId: String, importJobId: String, sourcePath: String): Either<DomainError, List<String>> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Resolve filter field values failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Resolve filter field values failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    return FilterEvaluator.distinctValues(rawRecordsAt(existing, definition), sourcePath).right()
  }

  override fun resolveFilterSample(userId: String, importJobId: String, matched: Boolean, index: Int): Either<DomainError, FilterSample> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Resolve filter sample failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Resolve filter sample failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    val allRecords = rawRecordsAt(existing, definition)
    val records = if (matched) FilterEvaluator.apply(allRecords, definition.filterRules) else FilterEvaluator.excluded(allRecords, definition.filterRules)
    return FilterSample(records.size, records.getOrNull(index)?.toString()).right()
  }

  override fun getMappingView(userId: String, importJobId: String): Either<DomainError, MappingView> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Get mapping view failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Get mapping view failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Get mapping view failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val targetEntityDefinition = entityDefinitionsOf(installedApp).find { it.id == definition.targetEntityDefinitionId } ?: run {
      logger.warn { "Get mapping view failed: target entity definition not found for importJobId: $importJobId" }
      return ImportError.ENTITY_DEFINITION_NOT_FOUND.left()
    }

    val entityDefinitions = entityDefinitionsOf(installedApp)
    val validation = definition.mapping?.let { mapping ->
      MappingValidator.validate(mapping, targetEntityDefinition, existing.filteredSchema, entityDefinitions, propertyConstraint)
    }
    return MappingView(existing, definition, targetEntityDefinition, entityDefinitions, validation).right()
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
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Update mapping failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
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
    val targetEntityDefinition = entityDefinitions.find { it.id == definition.targetEntityDefinitionId } ?: run {
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
    val updatedDefinition = definition.copy(mapping = mapping, lastChangedAt = Instant.now())
    importDefinitionRepository.save(updatedDefinition)
    val updatedJob = existing.copy(
      status = if (validation.isReady) ImportStatus.READY else ImportStatus.DATA_IDENTIFIED,
      lastChangedAt = Instant.now(),
    )
    importJobRepository.save(updatedJob)
    logger.info { "Mapping updated: importJobId=$importJobId status=${updatedJob.status}" }
    return MappingView(updatedJob, updatedDefinition, targetEntityDefinition, entityDefinitions, validation).right()
  }

  override fun dryRun(userId: String, importJobId: String): Either<DomainError, DryRunReport> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Dry run failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Dry run failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Dry run failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val (mapping, entityDefinition) = mappingAndEntity(definition, installedApp) ?: run {
      logger.warn { "Dry run failed: import job has no mapping: $importJobId" }
      return ImportError.IMPORT_JOB_NOT_READY.left()
    }

    val objects = executeDryRun(existing, definition, mapping, entityDefinition, installedApp)
    logger.info { "Dry run executed: importJobId=$importJobId total=${objects.size} invalid=${objects.count { it.isInvalid }} skipped=${objects.count { it.isSkipped }}" }
    return DryRunReport(existing.id, objects).right()
  }

  override fun resolveMappingSample(userId: String, importJobId: String, index: Int, fieldMappings: List<FieldMapping>): Either<DomainError, MappingSample> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Resolve mapping sample failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Resolve mapping sample failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Resolve mapping sample failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val entityDefinitions = entityDefinitionsOf(installedApp)
    val targetEntityDefinition = entityDefinitions.find { it.id == definition.targetEntityDefinitionId } ?: run {
      logger.warn { "Resolve mapping sample failed: target entity definition not found for importJobId: $importJobId" }
      return ImportError.ENTITY_DEFINITION_NOT_FOUND.left()
    }
    val propertyIds = targetEntityDefinition.properties.map { it.id }.toSet()
    if (fieldMappings.any { it.targetPropertyId !in propertyIds }) {
      logger.warn { "Resolve mapping sample failed: unknown target property for importJobId: $importJobId" }
      return ImportError.MAPPING_PROPERTY_NOT_FOUND.left()
    }

    val records = recordsAt(existing, definition)
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

  override fun acceptDryRun(userId: String, importJobId: String, replaceExisting: Boolean): Either<DomainError, ImportJob> {
    val existing = requireOwnedImportJob(userId, importJobId) ?: run {
      logger.warn { "Accept dry run failed: import job not found: $importJobId for user: $userId" }
      return ImportError.IMPORT_JOB_NOT_FOUND.left()
    }
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Accept dry run failed: import definition not found: ${existing.importDefinitionId} for importJobId: $importJobId" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Accept dry run failed: installed app not found: ${existing.installedAppId} for importJobId: $importJobId" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    readyMappingAndEntity(existing, definition, installedApp) ?: run {
      logger.warn { "Accept dry run failed: import job not ready: $importJobId" }
      return ImportError.IMPORT_JOB_NOT_READY.left()
    }

    val updated = existing.copy(status = ImportStatus.ACCEPTING, lastChangedAt = Instant.now())
    importJobRepository.save(updated)
    outbox.enqueue(DomainOutboxEvent.AcceptDryRun(importJobId = updated.id.value, userId = userId, replaceExisting = replaceExisting))
    logger.info { "Accept dry run enqueued: importJobId=$importJobId replaceExisting=$replaceExisting" }
    return updated.right()
  }

  override fun handle(event: DomainOutboxEvent.AcceptDryRun): Either<DomainError, Unit> {
    val existing = importJobRepository.findById(ImportJobId(event.importJobId)) ?: run {
      logger.info { "Accept dry run handler: import job already gone, treating as already processed: importJobId=${event.importJobId}" }
      return Unit.right()
    }
    val definition = definitionOf(existing) ?: run {
      logger.warn { "Accept dry run handler failed: import definition not found: ${existing.importDefinitionId} for importJobId: ${event.importJobId}" }
      return ImportError.DEFINITION_NOT_FOUND.left()
    }
    val installedApp = installedAppRepository.findById(existing.installedAppId) ?: run {
      logger.warn { "Accept dry run handler failed: installed app not found: ${existing.installedAppId} for importJobId: ${event.importJobId}" }
      return ImportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val (mapping, entityDefinition) = mappingAndEntity(definition, installedApp) ?: run {
      logger.warn { "Accept dry run handler failed: import job has no mapping: ${event.importJobId}" }
      return ImportError.IMPORT_JOB_NOT_READY.left()
    }
    val connection = importConnectionRepository.findById(definition.connectionId) ?: run {
      logger.warn { "Accept dry run handler failed: connection not found: ${definition.connectionId} for importJobId: ${event.importJobId}" }
      return ImportError.CONNECTION_NOT_FOUND.left()
    }
    val importProvenance = ImportProvenance(
      connectionId = connection.id,
      connectionName = connection.name,
      sourceUrl = resolveImportUrl(connection.baseUrl, definition.urlPostfix),
    )

    if (event.replaceExisting) {
      appDataRepository.deleteAllByInstalledAppIdAndEntityType(installedApp.id, entityDefinition.id)
      logger.info { "Accept dry run: cleared existing data before replace import: installedAppId=${installedApp.id} entityType=${entityDefinition.id}" }
    }

    val objects = executeDryRun(existing, definition, mapping, entityDefinition, installedApp)
    val now = Instant.now()
    var savedCount = 0
    for (obj in objects.filter { it.isValid }) {
      val data = entityDefinition.properties.associate { it.id.value to obj.targetData[it.id] }
      appDataRepository.save(
        AppData(
          id = AppDataId(UUID.randomUUID().toString()),
          userId = event.userId,
          installedAppId = installedApp.id,
          appVersion = installedApp.installedVersionNumber,
          lastValidatedWithVersion = installedApp.installedVersionNumber,
          entityType = entityDefinition.id,
          objectVersion = 1,
          createdAt = now,
          lastChangedAt = now,
          data = data,
          importProvenance = importProvenance,
        ),
      )
      savedCount++
    }
    val discardedCount = objects.size - savedCount

    if (savedCount > 0 || event.replaceExisting) {
      enqueueAggregationRecompute(installedApp.id, entityDefinitionsOf(installedApp), entityDefinition.id)
    }

    importJobRepository.delete(existing.id)
    logger.info { "Dry run accepted: importJobId=${event.importJobId} saved=$savedCount discarded=$discardedCount" }
    return Unit.right()
  }

  /**
   * Import writes AppData directly via [appDataRepository], bypassing `AppDataService`'s inline aggregation hook -
   * so once an import job has saved data, every aggregation the dependency index (see #640) maps to
   * [targetEntityId] is enqueued for a full outbox recompute instead (see docs/adr/0020-aggregation-definitions.md).
   */
  private fun enqueueAggregationRecompute(installedAppId: InstalledAppId, entityDefinitions: List<EntityDefinition>, targetEntityId: EntityDefinitionId) {
    val affected = buildAggregationDependencyIndex(entityDefinitions).affectedBy(targetEntityId)
    affected.map { it.aggregationId }.distinct().forEach { aggregationDefinitionId ->
      outbox.enqueue(DomainOutboxEvent.RecomputeAggregation(installedAppId = installedAppId.value, aggregationDefinitionId = aggregationDefinitionId.value))
    }
    if (affected.isNotEmpty()) {
      logger.info { "Aggregation recompute enqueued after import: installedAppId=${installedAppId.value} entityType=${targetEntityId.value} aggregations=${affected.size}" }
    }
  }

  private fun readyMappingAndEntity(existing: ImportJob, definition: ImportDefinition, installedApp: InstalledApp): Pair<Mapping, EntityDefinition>? {
    if (existing.status != ImportStatus.READY) return null
    return mappingAndEntity(definition, installedApp)
  }

  /**
   * Mapping and target entity definition for a definition that merely has a mapping configured, regardless of
   * whether it is free of blocking [de.chrgroth.james.platform.domain.model.imports.MappingIssue]s. Used for
   * [dryRun], which is read-only and should surface mapping issues per record rather than refuse to run, and for
   * [handle], which by the time it runs always finds the job in [ImportStatus.ACCEPTING] rather than
   * [ImportStatus.READY] - [acceptDryRun] itself still requires [ImportStatus.READY] via [readyMappingAndEntity]
   * before enqueueing.
   */
  private fun mappingAndEntity(definition: ImportDefinition, installedApp: InstalledApp): Pair<Mapping, EntityDefinition>? {
    val mapping = definition.mapping ?: return null
    val entityDefinition = entityDefinitionsOf(installedApp).find { it.id == definition.targetEntityDefinitionId } ?: return null
    return mapping to entityDefinition
  }

  private fun executeDryRun(existing: ImportJob, definition: ImportDefinition, mapping: Mapping, entityDefinition: EntityDefinition, installedApp: InstalledApp): List<DryRunObject> =
    DryRunExecutor.execute(
      records = recordsAt(existing, definition),
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

  /** Records at the definition's selected data path, with [ImportDefinition.filterRules] applied - this is what mapping and dry-run operate on. */
  private fun recordsAt(existing: ImportJob, definition: ImportDefinition): List<JsonNode> = FilterEvaluator.apply(rawRecordsAt(existing, definition), definition.filterRules)

  private fun rawRecordsAt(existing: ImportJob, definition: ImportDefinition): List<JsonNode> {
    val path = definition.selectedDataPath ?: return emptyList()
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

  private fun definitionOf(existing: ImportJob): ImportDefinition? = importDefinitionRepository.findById(existing.importDefinitionId)

  companion object : KLogging() {
    private val objectMapper = jacksonObjectMapper()
  }
}
