package de.chrgroth.james.platform.domain.report

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.app.resolveInstalledAppVersion
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.InvalidReportDefinitionError
import de.chrgroth.james.platform.domain.error.ReportError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionError
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.infra.AppTimeZone
import de.chrgroth.james.platform.domain.port.`in`.app.ComputedPropertyPort
import de.chrgroth.james.platform.domain.port.`in`.app.ReportPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Clock as KotlinClock

@ApplicationScoped
class ReportService(
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val computedProperty: ComputedPropertyPort,
) : ReportPort {

  /** Source of "today" for relative time ranges; replaceable in tests. */
  internal var clock: Clock = Clock.system(AppTimeZone.zone)

  override fun parseAndValidate(version: AppVersion, definition: String): Either<List<ReportDefinitionError>, ReportDefinition> {
    val result = ReportDefinitionParser.parse(definition, version.entityDefinitions)
    return result.definition?.right() ?: result.errors.left()
  }

  override fun renderReport(userId: String, installedAppId: String, reportId: String): Either<DomainError, ReportView> {
    val (installedApp, version) = ownedInstallation(userId, installedAppId).fold({ return it.left() }, { it })
    val report = version.reports.find { it.id.value == reportId } ?: run {
      logger.warn { "Render report failed: report $reportId not found in version ${version.id.value}" }
      return ReportError.REPORT_NOT_FOUND.left()
    }
    return execute(installedApp, version, report.id.value, report.name, report.definition)
  }

  override fun previewReport(userId: String, installedAppId: String, name: String, definition: String): Either<DomainError, ReportView> {
    val (installedApp, version) = ownedInstallation(userId, installedAppId).fold({ return it.left() }, { it })
    return execute(installedApp, version, "preview", name, definition)
  }

  private fun ownedInstallation(userId: String, installedAppId: String): Either<DomainError, Pair<InstalledApp, AppVersion>> {
    val installedApp = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (installedApp == null || installedApp.userId != userId) {
      logger.warn { "Report failed: installed app $installedAppId not found for user $userId" }
      return ReportError.INSTALLED_APP_NOT_FOUND.left()
    }
    val version = resolveInstalledAppVersion(installedApp, appVersionRepository) ?: run {
      logger.warn { "Report failed: version of installed app $installedAppId not found" }
      return ReportError.INSTALLED_APP_NOT_FOUND.left()
    }
    return (installedApp to version).right()
  }

  private fun execute(installedApp: InstalledApp, version: AppVersion, reportId: String, name: String, definition: String): Either<DomainError, ReportView> {
    val parsed = parseAndValidate(version, definition).fold(
      { errors ->
        logger.warn { "Report $reportId of installation ${installedApp.id.value} is invalid: ${errors.size} error(s)" }
        return InvalidReportDefinitionError(errors).left()
      },
      { it },
    )
    // Sandbox boundary: only this installation's objects are ever loaded.
    val appData = appDataRepository.findAllByInstalledAppId(installedApp.id)
    val now = KotlinClock.System.now()
    val executor = ReportExecutor(version.entityDefinitions, appData, LocalDate.now(clock)) { entity, data ->
      computedProperty.computeValues(entity, data, now)
    }
    return executor.execute(reportId, name, parsed).right()
  }

  companion object : KLogging()
}
