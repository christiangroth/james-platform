package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppVersionMigrationScriptFailedError
import de.chrgroth.james.platform.domain.error.AppVersionMigrationStepFailedError
import de.chrgroth.james.platform.domain.error.AppVersionMigrationValidationFailedError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.MigrationStep
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.ValueConversion
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.infra.ScriptType
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionMigrationPort
import de.chrgroth.james.platform.domain.port.`in`.app.MigrationScriptResult
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.script.ScriptEngineManager

/**
 * Executes Developer-authored Entity migration scripts as part of the installation upgrade flow, reusing the same JSR-223 Kotlin script
 * sandbox as [ComputedPropertyService]. See docs/adr/0018-app-version-migration-execution-trigger.md.
 */
@ApplicationScoped
@Suppress("Unused")
class AppVersionMigrationService(
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val appData: AppDataPort,
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val scriptMetrics: ScriptMetrics,
  @param:ConfigProperty(name = "app.script.timeout-ms", defaultValue = "500")
  private val scriptTimeoutMs: Long,
) : AppVersionMigrationPort {

  private val scriptExecutor = Executors.newVirtualThreadPerTaskExecutor()

  private val scriptEngineManager: ScriptEngineManager by lazy {
    ScriptEngineManager(Thread.currentThread().contextClassLoader)
  }

  private val scriptEngine: javax.script.ScriptEngine? by lazy {
    scriptEngineManager.getEngineByExtension("kts").also {
      if (it == null) logger.warn { "Kotlin scripting engine not available – migrations will fail" }
    }
  }

  override fun runScript(previousEntity: EntityDefinition, newEntity: EntityDefinition, data: Map<String, String?>): MigrationScriptResult {
    val script = newEntity.migrationScript ?: return MigrationScriptResult.Success(data)
    val engine = scriptEngine ?: return MigrationScriptResult.Failure("Kotlin scripting engine not available")

    val startNs = System.nanoTime()
    var success = true
    try {
      val bindings = engine.createBindings()
      bindings[BINDING_DATA] = data
      bindings[BINDING_PREVIOUS] = previousEntity
      bindings[BINDING_NEW] = newEntity
      val future = scriptExecutor.submit(Callable { engine.eval(buildWrappedScript(script), bindings) })
      val value = future.get(scriptTimeoutMs, TimeUnit.MILLISECONDS)
      val migrated = (value as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v?.toString() }
      return if (migrated != null) {
        MigrationScriptResult.Success(migrated)
      } else {
        success = false
        MigrationScriptResult.Failure("Migration script for entity ${newEntity.name} did not return a Map<String, String?>")
      }
    } catch (e: TimeoutException) {
      success = false
      logger.warn { "Migration script timed out after ${scriptTimeoutMs}ms for entity ${newEntity.name}" }
      return MigrationScriptResult.Failure("Migration script timed out after ${scriptTimeoutMs}ms")
    } catch (e: ExecutionException) {
      success = false
      logger.warn { "Migration script failed for entity ${newEntity.name}: ${e.cause?.message}" }
      return MigrationScriptResult.Failure(e.cause?.message ?: "Migration script failed")
    } catch (e: Exception) {
      success = false
      logger.warn { "Migration script failed for entity ${newEntity.name}: ${e.message}" }
      return MigrationScriptResult.Failure(e.message ?: "Migration script failed")
    } finally {
      scriptMetrics.record(ScriptType.MIGRATION, newEntity.name, MIGRATION_METRIC_LABEL, (System.nanoTime() - startNs) / 1_000_000L, success)
    }
  }

  override fun migrateInstallation(installedAppId: InstalledAppId, appId: AppId, fromVersion: VersionNumber, toVersion: VersionNumber): Either<DomainError, Unit> {
    val publishedVersions = appVersionRepository.findAllByAppId(appId)
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .sortedBy { it.createdAt }

    val fromIndex = publishedVersions.indexOfFirst { it.versionNumber == fromVersion }
    val toIndex = publishedVersions.indexOfFirst { it.versionNumber == toVersion }
    if (fromIndex < 0 || toIndex < 0 || toIndex <= fromIndex) return Unit.right()

    val pendingVersions = publishedVersions.subList(fromIndex + 1, toIndex + 1)
    val entityMigrations = pendingVersions.flatMap { version ->
      version.entityDefinitions.filter { it.migrationScript != null || it.migrationSteps.isNotEmpty() }.map { version to it }
    }
    if (entityMigrations.isEmpty()) return Unit.right()

    // Held in memory until every pending migration for this installation succeeds — nothing is persisted on failure (see class doc).
    val pendingSaves = mutableMapOf<AppDataId, AppData>()

    for ((version, entity) in entityMigrations) {
      val versionIndex = publishedVersions.indexOfFirst { it.versionNumber == version.versionNumber }
      val previousEntity = previousEntityDefinition(publishedVersions, versionIndex, entity)

      val pendingAppData = appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entity.id)
        .map { pendingSaves[it.id] ?: it }
        .filter { publishedVersions.indexOfFirst { v -> v.versionNumber == it.lastValidatedWithVersion } < versionIndex }

      for (existingAppData in pendingAppData) {
        val migrated = runAndValidate(
          previousEntity,
          entity,
          existingAppData,
          installedAppId,
          version.versionNumber?.value ?: "",
          "installedAppId=${installedAppId.value}",
        ).fold({ return it.left() }, { it })
        pendingSaves[existingAppData.id] = existingAppData.copy(
          data = migrated,
          lastValidatedWithVersion = version.versionNumber!!,
          objectVersion = existingAppData.objectVersion + 1,
        )
      }
    }

    pendingSaves.values.forEach { appDataRepository.save(it) }
    if (pendingSaves.isNotEmpty()) {
      logger.info { "Migrated ${pendingSaves.size} app data object(s) for installedAppId=${installedAppId.value} from ${fromVersion.value} to ${toVersion.value}" }
    }
    return Unit.right()
  }

  override fun dryRunMigration(appId: AppId, entityMigrations: List<Pair<EntityDefinition, EntityDefinition>>): Either<DomainError, Unit> {
    if (entityMigrations.isEmpty()) return Unit.right()
    val installations = installedAppRepository.findAllByAppId(appId)
    for ((previousEntity, newEntity) in entityMigrations) {
      for (installedApp in installations) {
        val existingAppData = appDataRepository.findAllByInstalledAppIdAndEntityType(installedApp.id, newEntity.id)
        for (existingAppDataRow in existingAppData) {
          runAndValidate(previousEntity, newEntity, existingAppDataRow, installedApp.id, "", "dry-run for appId=${appId.value}")
            .fold({ return it.left() }, {})
        }
      }
    }
    return Unit.right()
  }

  /**
   * Applies [newEntity]'s migration building blocks (in order) and then its migration script against [existingAppData], re-validating
   * the result, logging and returning a [DomainError] on failure at either stage.
   */
  private fun runAndValidate(
    previousEntity: EntityDefinition,
    newEntity: EntityDefinition,
    existingAppData: AppData,
    installedAppId: InstalledAppId,
    versionNumber: String,
    logContext: String,
  ): Either<DomainError, Map<String, String?>> {
    val afterSteps = applyMigrationSteps(previousEntity, newEntity, existingAppData.data).fold(
      { reason ->
        logger.warn { "Migration aborted for $logContext: entity=${newEntity.name} appDataId=${existingAppData.id.value}: $reason" }
        return AppVersionMigrationStepFailedError(newEntity.name, existingAppData.id.value, versionNumber, reason).left()
      },
      { it },
    )
    when (val scriptResult = runScript(previousEntity, newEntity, afterSteps)) {
      is MigrationScriptResult.Failure -> {
        logger.warn { "Migration aborted for $logContext: entity=${newEntity.name} appDataId=${existingAppData.id.value}: ${scriptResult.reason}" }
        return AppVersionMigrationScriptFailedError(newEntity.name, existingAppData.id.value, versionNumber, scriptResult.reason).left()
      }
      is MigrationScriptResult.Success -> {
        val validation = appData.validateEntityData(newEntity, scriptResult.data, installedAppId.value, excludingDataId = existingAppData.id.value)
        return when (validation) {
          is Either.Left -> {
            val propertyViolations = (validation.value as? AppDataConstraintViolationError)?.propertyViolations ?: emptyMap()
            logger.warn { "Migration aborted for $logContext: entity=${newEntity.name} appDataId=${existingAppData.id.value} failed re-validation: $propertyViolations" }
            AppVersionMigrationValidationFailedError(newEntity.name, existingAppData.id.value, versionNumber, propertyViolations).left()
          }
          is Either.Right -> scriptResult.data.right()
        }
      }
    }
  }

  /**
   * Applies [newEntity]'s [MigrationStep]s to [data], in order: [MigrationStep.ConvertType] converts a property's value in place using
   * its type/unit in [previousEntity] as the source and in [newEntity] as the target; [MigrationStep.CopyValue] copies a value from a
   * property that only exists in [previousEntity] into one that only exists in [newEntity]; [MigrationStep.ConvertUnit] converts a
   * property's value in place from its declared [MigrationStep.ConvertUnit.sourceGranularity] to its new unit's `storageGranularity`;
   * [MigrationStep.FillEmptyValue] fills a `null`/blank value with a fixed value or the property's own default;
   * [MigrationStep.AdjustToConstraints] clamps/truncates an out-of-range value to the property's current constraints. Returns a failure
   * reason if a referenced property is missing (e.g. because the version chain has diverged) or [ValueConversion.convert] fails,
   * otherwise the resulting data. [MigrationStep.AdjustToConstraints] never fails here - a value it cannot meaningfully adjust is left
   * unchanged and instead fails the shared re-validation that follows step execution (see `runAndValidate`), by design (no fallback to
   * `null`).
   */
  private fun applyMigrationSteps(previousEntity: EntityDefinition, newEntity: EntityDefinition, data: Map<String, String?>): Either<String, Map<String, String?>> {
    if (newEntity.migrationSteps.isEmpty()) return data.right()
    var result = data
    for (step in newEntity.migrationSteps) {
      result = when (step) {
        is MigrationStep.ConvertType -> {
          val sourceProp = previousEntity.properties.find { it.id == step.propertyId }
            ?: return "Property ${step.propertyId.value} not found in previous entity definition".left()
          val targetProp = newEntity.properties.find { it.id == step.propertyId }
            ?: return "Property ${step.propertyId.value} not found in entity definition".left()
          convertValue(sourceProp, targetProp, result[step.propertyId.value]).fold({ return it.left() }, { converted -> result + (step.propertyId.value to converted) })
        }
        is MigrationStep.CopyValue -> {
          val sourceProp = previousEntity.properties.find { it.id == step.sourcePropertyId }
            ?: return "Property ${step.sourcePropertyId.value} not found in previous entity definition".left()
          val targetProp = newEntity.properties.find { it.id == step.targetPropertyId }
            ?: return "Property ${step.targetPropertyId.value} not found in entity definition".left()
          convertValue(sourceProp, targetProp, result[step.sourcePropertyId.value]).fold(
            { return it.left() },
            { converted -> result + (step.targetPropertyId.value to converted) },
          )
        }
        is MigrationStep.ConvertUnit -> {
          val targetProp = newEntity.properties.find { it.id == step.propertyId }
            ?: return "Property ${step.propertyId.value} not found in entity definition".left()
          val targetUnit = targetProp.unit ?: return "Property ${targetProp.name} has no unit".left()
          val raw = result[step.propertyId.value]
          if (raw.isNullOrBlank()) result else result + (step.propertyId.value to ValueConversion.convertGranularity(targetUnit, step.sourceGranularity, raw))
        }
        is MigrationStep.FillEmptyValue -> {
          val targetProp = newEntity.properties.find { it.id == step.propertyId }
            ?: return "Property ${step.propertyId.value} not found in entity definition".left()
          val raw = result[step.propertyId.value]
          if (!raw.isNullOrBlank()) {
            result
          } else {
            val fillValue = step.value ?: targetProp.default
            if (fillValue == null) result else result + (step.propertyId.value to fillValue)
          }
        }
        is MigrationStep.AdjustToConstraints -> {
          val targetProp = newEntity.properties.find { it.id == step.propertyId }
            ?: return "Property ${step.propertyId.value} not found in entity definition".left()
          val raw = result[step.propertyId.value]
          if (raw.isNullOrBlank()) result else result + (step.propertyId.value to adjustToConstraints(targetProp, raw))
        }
      }
    }
    return result.right()
  }

  /**
   * Clamps [rawValue] (already in [property]'s storage format) to its current numeric/date/time min/max constraints, or truncates it to
   * its `maxLength` if it is a `STRING`. A value that cannot be parsed as [property]'s type, or a constraint this cannot express as a
   * clamp/truncation (e.g. `Pattern`), is returned unchanged - left to fail the shared re-validation that follows step execution.
   */
  private fun adjustToConstraints(property: Property, rawValue: String): String = when (property.type) {
    PropertyType.LONG -> {
      val value = rawValue.toLongOrNull()
      if (value == null) {
        rawValue
      } else {
        val min = property.constraints.filterIsInstance<PropertyConstraint.MinLong>().firstOrNull()?.min
        val max = property.constraints.filterIsInstance<PropertyConstraint.MaxLong>().firstOrNull()?.max
        value.coerceIn(min ?: Long.MIN_VALUE, max ?: Long.MAX_VALUE).toString()
      }
    }
    PropertyType.DOUBLE -> {
      val value = rawValue.toDoubleOrNull()
      if (value == null) {
        rawValue
      } else {
        val min = property.constraints.filterIsInstance<PropertyConstraint.MinDouble>().firstOrNull()?.min
        val max = property.constraints.filterIsInstance<PropertyConstraint.MaxDouble>().firstOrNull()?.max
        formatDouble(value.coerceIn(min ?: -Double.MAX_VALUE, max ?: Double.MAX_VALUE))
      }
    }
    PropertyType.STRING -> {
      val maxLength = property.constraints.filterIsInstance<PropertyConstraint.MaxLength>().firstOrNull()?.max
      if (maxLength != null && rawValue.length > maxLength) rawValue.substring(0, maxLength) else rawValue
    }
    PropertyType.DATE -> {
      val min = property.constraints.filterIsInstance<PropertyConstraint.MinDate>().firstOrNull()?.min
      val max = property.constraints.filterIsInstance<PropertyConstraint.MaxDate>().firstOrNull()?.max
      adjustToRange(rawValue, { LocalDate.parse(it) }, min, max)
    }
    PropertyType.TIME -> {
      val min = property.constraints.filterIsInstance<PropertyConstraint.MinTime>().firstOrNull()?.min
      val max = property.constraints.filterIsInstance<PropertyConstraint.MaxTime>().firstOrNull()?.max
      adjustToRange(rawValue, { LocalTime.parse(it) }, min, max)
    }
    PropertyType.DATETIME -> {
      val min = property.constraints.filterIsInstance<PropertyConstraint.MinDatetime>().firstOrNull()?.min
      val max = property.constraints.filterIsInstance<PropertyConstraint.MaxDatetime>().firstOrNull()?.max
      adjustToRange(rawValue, { LocalDateTime.parse(it) }, min, max)
    }
    else -> rawValue
  }

  private fun <T : Comparable<T>> adjustToRange(rawValue: String, parse: (String) -> T, min: T?, max: T?): String {
    val value = runCatching { parse(rawValue) }.getOrNull() ?: return rawValue
    val clamped = when {
      min != null && value < min -> min
      max != null && value > max -> max
      else -> value
    }
    return clamped.toString()
  }

  private fun formatDouble(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()

  /** Converts [rawValue] from [source]'s type/unit to [target]'s, via [ValueConversion.convert] and, if both carry a unit, [ValueConversion.convertGranularity]. */
  private fun convertValue(source: Property, target: Property, rawValue: String?): Either<String, String?> {
    val converted = ValueConversion.convert(source.type, target.type, rawValue).getOrElse {
      return "Value '$rawValue' of property ${source.name} could not be converted to ${target.type}".left()
    }
    val sourceUnit = source.unit
    val targetUnit = target.unit
    if (converted.isNullOrBlank() || sourceUnit == null || targetUnit == null) return converted.right()
    return ValueConversion.convertGranularity(targetUnit, sourceUnit.storageGranularity, converted).right()
  }

  /** The entity definition [entity] is migrating from: its own shape in the published Version immediately preceding [versionIndex], if any. */
  private fun previousEntityDefinition(publishedVersions: List<AppVersion>, versionIndex: Int, entity: EntityDefinition): EntityDefinition =
    publishedVersions.getOrNull(versionIndex - 1)?.entityDefinitions?.find { it.id == entity.id } ?: entity

  private fun buildWrappedScript(script: String): String = buildString {
    appendLine("@file:Suppress(\"UNCHECKED_CAST\")")
    appendLine()
    appendLine("val it: Map<String, String?> = ($BINDING_DATA ?: emptyMap<String, String?>()) as Map<String, String?>")
    appendLine(
      "val previousEntity: de.chrgroth.james.platform.domain.model.app.EntityDefinition = " +
        "$BINDING_PREVIOUS as de.chrgroth.james.platform.domain.model.app.EntityDefinition",
    )
    appendLine(
      "val newEntity: de.chrgroth.james.platform.domain.model.app.EntityDefinition = " +
        "$BINDING_NEW as de.chrgroth.james.platform.domain.model.app.EntityDefinition",
    )
    appendLine()
    append(script)
  }

  companion object : KLogging() {
    private const val BINDING_DATA = "_migrationData"
    private const val BINDING_PREVIOUS = "_migrationPreviousEntity"
    private const val BINDING_NEW = "_migrationNewEntity"
    private const val MIGRATION_METRIC_LABEL = "migration"
  }
}
