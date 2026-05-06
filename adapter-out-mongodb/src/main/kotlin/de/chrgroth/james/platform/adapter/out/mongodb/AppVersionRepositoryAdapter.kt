package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.MongoNamespace
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.ReportId
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class AppVersionRepositoryAdapter(
  private val appVersionDocumentRepository: AppVersionDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
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

  override fun findAll(): List<AppVersion> =
    mongoQueryMetrics.timed("app_version.findAll") {
      appVersionDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun findAllPublishedWithoutReleaseNotes(): List<AppVersion> =
    mongoQueryMetrics.timed("app_version.findAllPublishedWithoutReleaseNotes") {
      appVersionDocumentRepository
        .find("$STATUS_FIELD = ?1 and releaseNotes is null", AppVersionStatus.PUBLISHED.name)
        .list()
        .map { it.toDomain() }
    }

  override fun renameToNewCollection() {
    mongoQueryMetrics.timed("app_version.renameToNewCollection") {
      val db = mongoClient.getDatabase(databaseName)
      if (db.listCollectionNames().contains(OLD_COLLECTION_NAME)) {
        db.getCollection(OLD_COLLECTION_NAME).renameCollection(MongoNamespace(databaseName, NEW_COLLECTION_NAME))
      }
    }
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

  override fun delete(versionId: AppVersionId) {
    mongoQueryMetrics.timed("app_version.delete") {
      appVersionDocumentRepository.mongoCollection().deleteOne(Filters.eq(ID_FIELD, versionId.value))
    }
  }

  override fun deleteAll() {
    mongoQueryMetrics.timed("app_version.deleteAll") {
      appVersionDocumentRepository.mongoCollection().deleteMany(Filters.exists(ID_FIELD))
    }
  }

  private fun AppVersionDocument.toDomain() = AppVersion(
    id = AppVersionId(id),
    appId = AppId(appId),
    versionNumber = versionNumber?.let { VersionNumber(it) },
    releaseNotes = releaseNotes,
    entityDefinitions = entityDefinitions.map { it.toDomain() },
    reports = reports.map { it.toDomain() },
    status = AppVersionStatus.valueOf(status),
    createdAt = createdAt,
  )

  private fun EntityDefinitionDocument.toDomain(): EntityDefinition {
    val safeDisplayText: String? = displayText // handles potential JVM null from BSON codec on legacy docs
    return EntityDefinition(
      id = EntityDefinitionId(id),
      name = name,
      displayText = safeDisplayText?.takeIf { it.isNotBlank() },
      properties = properties.map { it.toDomain() },
      sortBy = sortBy.mapNotNull { it.toDomain() },
      computedProperties = computedProperties.mapNotNull { it.toDomain() },
    )
  }

  private fun SortCriteriaDocument.toDomain(): SortCriteria? {
    val dir = runCatching { SortDirection.valueOf(direction) }.getOrNull() ?: return null
    return SortCriteria(propertyId = propertyId, direction = dir)
  }

  private fun PropertyDocument.toDomain() = Property(
    id = PropertyId(id),
    name = name,
    type = PropertyType.valueOf(type),
    nullable = nullable,
    constraints = constraints.mapNotNull { it.toDomain() }.toSet(),
    default = default,
    smartDefault = smartDefault,
    valueProposals = valueProposals,
  )

  private fun ComputedPropertyDocument.toDomain(): ComputedProperty? {
    val propertyType = runCatching { PropertyType.valueOf(type) }.getOrNull() ?: return null
    return ComputedProperty(
      id = ComputedPropertyId(id),
      name = name,
      type = propertyType,
      script = script,
    )
  }

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
    html = html,
    script = script,
  )

  private fun AppVersion.toDocument() = AppVersionDocument().also { doc ->
    doc.id = id.value
    doc.appId = appId.value
    doc.versionNumber = versionNumber?.value
    doc.releaseNotes = releaseNotes
    doc.entityDefinitions = entityDefinitions.map { it.toDocument() }
    doc.reports = reports.map { it.toDocument() }
    doc.status = status.name
    doc.createdAt = createdAt
  }

  private fun EntityDefinition.toDocument() = EntityDefinitionDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.displayText = displayText ?: "Display Text"
    doc.properties = properties.map { it.toDocument() }
    doc.sortBy = sortBy.map { it.toDocument() }
    doc.computedProperties = computedProperties.map { it.toDocument() }
  }

  private fun SortCriteria.toDocument() = SortCriteriaDocument().also { doc ->
    doc.propertyId = propertyId
    doc.direction = direction.name
  }

  private fun Property.toDocument() = PropertyDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.type = type.name
    doc.nullable = nullable
    doc.constraints = constraints.map { it.toDocument() }
    doc.default = default
    doc.smartDefault = smartDefault
    doc.valueProposals = valueProposals
  }

  private fun ComputedProperty.toDocument() = ComputedPropertyDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.type = type.name
    doc.script = script
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
    doc.html = html
    doc.script = script
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val APP_ID_FIELD = "appId"
    internal const val VERSION_NUMBER_FIELD = "versionNumber"
    internal const val STATUS_FIELD = "status"
    private const val OLD_COLLECTION_NAME = "app_version"
    private const val NEW_COLLECTION_NAME = "app_app_version"
  }
}
