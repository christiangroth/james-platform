package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppVersionMigrationScriptFailedError
import de.chrgroth.james.platform.domain.error.AppVersionMigrationValidationFailedError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.infra.ScriptType
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionMigrationPort
import de.chrgroth.james.platform.domain.port.`in`.app.MigrationScriptResult
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.script.ScriptEngineManager

/**
 * Executes Developer-authored Entity migration scripts as part of the installation upgrade flow, reusing the same JSR-223 Kotlin script
 * sandbox as [ComputedPropertyService]. See docs/app-version-migration.md and docs/adr/0018-app-version-migration-execution-trigger.md.
 */
@ApplicationScoped
@Suppress("Unused")
class AppVersionMigrationService(
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val appData: AppDataPort,
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
    val entityMigrations = pendingVersions.flatMap { version -> version.entityDefinitions.filter { it.migrationScript != null }.map { version to it } }
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
        when (val scriptResult = runScript(previousEntity, entity, existingAppData.data)) {
          is MigrationScriptResult.Failure -> {
            logger.warn {
              "Migration aborted for installedAppId=${installedAppId.value}: entity=${entity.name} appDataId=${existingAppData.id.value} " +
                "version=${version.versionNumber?.value}: ${scriptResult.reason}"
            }
            return AppVersionMigrationScriptFailedError(entity.name, existingAppData.id.value, version.versionNumber?.value ?: "", scriptResult.reason).left()
          }
          is MigrationScriptResult.Success -> {
            val validation = appData.validateEntityData(entity, scriptResult.data, installedAppId.value, excludingDataId = existingAppData.id.value)
            when (validation) {
              is Either.Left -> {
                val propertyViolations = (validation.value as? AppDataConstraintViolationError)?.propertyViolations ?: emptyMap()
                logger.warn {
                  "Migration aborted for installedAppId=${installedAppId.value}: entity=${entity.name} appDataId=${existingAppData.id.value} " +
                    "version=${version.versionNumber?.value} failed re-validation: $propertyViolations"
                }
                return AppVersionMigrationValidationFailedError(entity.name, existingAppData.id.value, version.versionNumber?.value ?: "", propertyViolations).left()
              }
              is Either.Right -> {
                pendingSaves[existingAppData.id] = existingAppData.copy(
                  data = scriptResult.data,
                  lastValidatedWithVersion = version.versionNumber!!,
                  objectVersion = existingAppData.objectVersion + 1,
                )
              }
            }
          }
        }
      }
    }

    pendingSaves.values.forEach { appDataRepository.save(it) }
    if (pendingSaves.isNotEmpty()) {
      logger.info { "Migrated ${pendingSaves.size} app data object(s) for installedAppId=${installedAppId.value} from ${fromVersion.value} to ${toVersion.value}" }
    }
    return Unit.right()
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
