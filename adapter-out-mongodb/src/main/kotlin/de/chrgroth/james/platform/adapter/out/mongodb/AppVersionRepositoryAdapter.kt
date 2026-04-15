package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.ReportEntityFilter
import de.chrgroth.james.platform.domain.model.app.ReportId
import de.chrgroth.james.platform.domain.model.app.ReportPage
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AppVersionRepositoryAdapter(
  private val appVersionDocumentRepository: AppVersionDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppVersionRepositoryPort {

  override fun findById(versionId: AppVersionId): AppVersion? =
    mongoQueryMetrics.timed("app_version.findById") {
      appVersionDocumentRepository.findById(versionId.value)?.toDomain()
    }

  override fun findByAppIdAndVersionNumber(appId: AppId, versionNumber: VersionNumber): AppVersion? =
    mongoQueryMetrics.timed("app_version.findByAppIdAndVersionNumber") {
      appVersionDocumentRepository
        .find("$APP_ID_FIELD = ?1 and $VERSION_NUMBER_FIELD = ?2", appId.value, versionNumber.value)
        .firstResult()
        ?.toDomain()
    }

  override fun findAllByAppId(appId: AppId): List<AppVersion> =
    mongoQueryMetrics.timed("app_version.findAllByAppId") {
      appVersionDocumentRepository.find(APP_ID_FIELD, appId.value).list().map { it.toDomain() }
    }

  override fun save(version: AppVersion) {
    mongoQueryMetrics.timed("app_version.save") {
      val doc = version.toDocument()
      appVersionDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, version.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  private fun AppVersionDocument.toDomain() = AppVersion(
    id = AppVersionId(id),
    appId = AppId(appId),
    versionNumber = VersionNumber(versionNumber),
    releaseNotes = releaseNotes,
    entityDefinitions = entityDefinitions.map { it.toDomain() },
    reports = reports.map { it.toDomain() },
    status = AppVersionStatus.valueOf(status),
    createdAt = createdAt,
  )

  private fun EntityDefinitionDocument.toDomain() = EntityDefinition(
    id = EntityDefinitionId(id),
    name = name,
    properties = properties.map { it.toDomain() },
  )

  private fun PropertyDocument.toDomain() = Property(
    id = PropertyId(id),
    name = name,
    type = PropertyType.valueOf(type),
    nullable = nullable,
    constraints = constraints.mapNotNull { it.toDomain() }.toSet(),
  )

  private fun ConstraintDocument.toDomain(): PropertyConstraint? = when (constraintType) {
    "UniqueKey" -> PropertyConstraint.UniqueKey
    "MinLong" -> longValue?.let { PropertyConstraint.MinLong(it) }
    "MaxLong" -> longValue?.let { PropertyConstraint.MaxLong(it) }
    "MinDouble" -> doubleValue?.let { PropertyConstraint.MinDouble(it) }
    "MaxDouble" -> doubleValue?.let { PropertyConstraint.MaxDouble(it) }
    "MinLength" -> intValue?.let { PropertyConstraint.MinLength(it) }
    "MaxLength" -> intValue?.let { PropertyConstraint.MaxLength(it) }
    "Pattern" -> stringValue?.let { PropertyConstraint.Pattern(it) }
    "MinSize" -> intValue?.let { PropertyConstraint.MinSize(it) }
    "MaxSize" -> intValue?.let { PropertyConstraint.MaxSize(it) }
    else -> null
  }

  private fun ReportDocument.toDomain() = Report(
    id = ReportId(id),
    name = name,
    pages = pages.map { it.toDomain() },
  )

  private fun ReportPageDocument.toDomain() = ReportPage(
    html = html,
    script = script,
    entityFilters = entityFilters.map { it.toDomain() },
  )

  private fun ReportEntityFilterDocument.toDomain() = ReportEntityFilter(
    entityId = EntityDefinitionId(entityId),
    filterExpression = filterExpression,
  )

  private fun AppVersion.toDocument() = AppVersionDocument().also { doc ->
    doc.id = id.value
    doc.appId = appId.value
    doc.versionNumber = versionNumber.value
    doc.releaseNotes = releaseNotes
    doc.entityDefinitions = entityDefinitions.map { it.toDocument() }
    doc.reports = reports.map { it.toDocument() }
    doc.status = status.name
    doc.createdAt = createdAt
  }

  private fun EntityDefinition.toDocument() = EntityDefinitionDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.properties = properties.map { it.toDocument() }
  }

  private fun Property.toDocument() = PropertyDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.type = type.name
    doc.nullable = nullable
    doc.constraints = constraints.map { it.toDocument() }
  }

  private fun PropertyConstraint.toDocument() = ConstraintDocument().also { doc ->
    doc.constraintType = this::class.simpleName ?: error("Unknown constraint type: $this")
    when (this) {
      is PropertyConstraint.UniqueKey -> Unit
      is PropertyConstraint.MinLong -> doc.longValue = min
      is PropertyConstraint.MaxLong -> doc.longValue = max
      is PropertyConstraint.MinDouble -> doc.doubleValue = min
      is PropertyConstraint.MaxDouble -> doc.doubleValue = max
      is PropertyConstraint.MinLength -> doc.intValue = min
      is PropertyConstraint.MaxLength -> doc.intValue = max
      is PropertyConstraint.Pattern -> doc.stringValue = regex
      is PropertyConstraint.MinSize -> doc.intValue = min
      is PropertyConstraint.MaxSize -> doc.intValue = max
    }
  }

  private fun Report.toDocument() = ReportDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.pages = pages.map { it.toDocument() }
  }

  private fun ReportPage.toDocument() = ReportPageDocument().also { doc ->
    doc.html = html
    doc.script = script
    doc.entityFilters = entityFilters.map { it.toDocument() }
  }

  private fun ReportEntityFilter.toDocument() = ReportEntityFilterDocument().also { doc ->
    doc.entityId = entityId.value
    doc.filterExpression = filterExpression
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val APP_ID_FIELD = "appId"
    internal const val VERSION_NUMBER_FIELD = "versionNumber"
  }
}
