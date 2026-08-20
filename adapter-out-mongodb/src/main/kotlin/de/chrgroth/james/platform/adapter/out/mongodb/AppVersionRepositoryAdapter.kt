package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.MongoNamespace
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Granularity
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.ReportId
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.DurationPropertyLocation
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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

  override fun migrateDurationProperties(defaultGranularity: TimeGranularity): List<DurationPropertyLocation> =
    mongoQueryMetrics.timed("app_version.migrateDurationProperties") {
      val locations = mutableListOf<DurationPropertyLocation>()
      // listAll/replaceOne operate on the raw storage documents (type/listItemType/constraintType are plain Strings there), never on the
      // domain model, since a stored "DURATION" value can no longer be parsed by PropertyType.valueOf once the enum constant is removed.
      appVersionDocumentRepository.listAll().forEach { versionDoc ->
        var changed = false
        versionDoc.entityDefinitions.forEach { entityDoc ->
          val entityId = EntityDefinitionId(entityDoc.id)
          entityDoc.properties.forEach { propertyDoc ->
            if (migratePropertyDocument(propertyDoc, entityId, emptyList(), defaultGranularity, locations)) changed = true
          }
          entityDoc.computedProperties.forEach { computedPropertyDoc ->
            if (computedPropertyDoc.type == "DURATION") {
              computedPropertyDoc.type = "LONG"
              changed = true
            }
          }
        }
        if (changed) {
          appVersionDocumentRepository.mongoCollection().replaceOne(Filters.eq(ID_FIELD, versionDoc.id), versionDoc, ReplaceOptions().upsert(true))
        }
      }
      locations
    }

  /** Migrates a single (possibly nested) property document, returning whether it or any of its nested properties changed. */
  private fun migratePropertyDocument(
    doc: PropertyDocument,
    entityId: EntityDefinitionId,
    parentPath: List<PropertyId>,
    defaultGranularity: TimeGranularity,
    locations: MutableList<DurationPropertyLocation>,
  ): Boolean {
    var changed = false
    val path = parentPath + PropertyId(doc.id)

    if (doc.type == "DURATION") {
      doc.type = "LONG"
      doc.unit = PropertyUnitDocument().also {
        it.family = UnitFamily.TIME.name
        it.storageGranularity = TimeGranularity.SECONDS.name
        it.defaultGranularity = defaultGranularity.name
      }
      migrateDurationConstraints(doc.constraints)
      locations += DurationPropertyLocation(entityId, path, isListItem = false)
      changed = true
    } else if (doc.type == "LIST" && doc.listItemType == "DURATION") {
      doc.listItemType = "LONG"
      migrateDurationConstraints(doc.itemConstraints)
      locations += DurationPropertyLocation(entityId, path, isListItem = true)
      changed = true
    }

    doc.nestedProperties.forEach { nested ->
      if (migratePropertyDocument(nested, entityId, path, defaultGranularity, locations)) changed = true
    }

    return changed
  }

  /** Converts MinDuration/MaxDuration (stored as an ISO-8601 [stringValue]) to MinLong/MaxLong (value in seconds), in place. */
  private fun migrateDurationConstraints(constraints: List<ConstraintDocument>) {
    constraints.forEach { constraint ->
      when (constraint.constraintType) {
        "MinDuration" -> {
          constraint.constraintType = "MinLong"
          constraint.longValue = constraint.stringValue?.let { Duration.parse(it).seconds }
          constraint.stringValue = null
        }
        "MaxDuration" -> {
          constraint.constraintType = "MaxLong"
          constraint.longValue = constraint.stringValue?.let { Duration.parse(it).seconds }
          constraint.stringValue = null
        }
      }
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

  private fun EntityDefinitionDocument.toDomain() = EntityDefinition(
    id = EntityDefinitionId(id),
    name = name,
    displayText = displayText?.takeIf { it.isNotBlank() },
    properties = properties.map { it.toDomain() },
    sortBy = sortBy.mapNotNull { it.toDomain() },
    computedProperties = computedProperties.mapNotNull { it.toDomain() },
    aggregations = aggregations.mapNotNull { it.toDomain() },
    migrationScript = migrationScript,
  )

  private fun SortCriteriaDocument.toDomain(): SortCriteria? {
    val dir = runCatching { SortDirection.valueOf(direction) }.getOrNull() ?: return null
    return SortCriteria(propertyId = propertyId, direction = dir)
  }

  private fun PropertyDocument.toDomain(): Property = Property(
    id = PropertyId(id),
    name = name,
    type = PropertyType.valueOf(type),
    nullable = nullable,
    constraints = constraints.mapNotNull { it.toDomain() }.toSet(),
    default = default,
    smartDefault = smartDefault,
    valueProposals = valueProposals,
    targetEntityId = targetEntityId?.let { EntityDefinitionId(it) },
    listItemType = listItemType?.let { PropertyType.valueOf(it) },
    itemConstraints = itemConstraints.mapNotNull { it.toDomain() }.toSet(),
    nestedProperties = nestedProperties.map { it.toDomain() },
    unit = unit?.toDomain(),
  )

  private fun PropertyUnitDocument.toDomain(): PropertyUnit? {
    val parsedFamily = runCatching { UnitFamily.valueOf(family) }.getOrNull() ?: return null
    val storage = granularityOrNull(parsedFamily, storageGranularity) ?: return null
    val default = granularityOrNull(parsedFamily, defaultGranularity) ?: return null
    return runCatching { PropertyUnit(family = parsedFamily, storageGranularity = storage, defaultGranularity = default) }.getOrNull()
  }

  private fun granularityOrNull(family: UnitFamily, name: String): Granularity? = when (family) {
    UnitFamily.TIME -> runCatching { TimeGranularity.valueOf(name) }.getOrNull()
    UnitFamily.DISTANCE -> runCatching { DistanceGranularity.valueOf(name) }.getOrNull()
  }

  private fun ComputedPropertyDocument.toDomain(): ComputedProperty? {
    val propertyType = runCatching { PropertyType.valueOf(type) }.getOrNull() ?: return null
    return ComputedProperty(
      id = ComputedPropertyId(id),
      name = name,
      type = propertyType,
      script = script,
    )
  }

  private fun AggregationDefinitionDocument.toDomain(): AggregationDefinition? {
    val parsedFunction = runCatching { AggregationFunction.valueOf(function) }.getOrNull() ?: return null
    return AggregationDefinition(
      id = AggregationDefinitionId(id),
      name = name,
      function = parsedFunction,
      sourceProperty = PropertyId(sourceProperty),
      refPath = refPath?.let { PropertyId(it) },
      timeBucket = timeBucket?.let { runCatching { TimeBucket.valueOf(it) }.getOrNull() },
      timeProperty = timeProperty?.let { PropertyId(it) },
      groupBy = groupBy?.let { PropertyId(it) },
    )
  }

  private fun ConstraintDocument.toDomain(): PropertyConstraint? = when (constraintType) {
    "UniqueKey" -> PropertyConstraint.UniqueKey
    "MinLong" -> longValue?.let { PropertyConstraint.MinLong(it) }
    "MaxLong" -> longValue?.let { PropertyConstraint.MaxLong(it) }
    "StepLong" -> longValue?.let { PropertyConstraint.StepLong(it) }
    "MinDouble" -> doubleValue?.let { PropertyConstraint.MinDouble(it) }
    "MaxDouble" -> doubleValue?.let { PropertyConstraint.MaxDouble(it) }
    "StepDouble" -> doubleValue?.let { PropertyConstraint.StepDouble(it) }
    "MinLength" -> intValue?.let { PropertyConstraint.MinLength(it) }
    "MaxLength" -> intValue?.let { PropertyConstraint.MaxLength(it) }
    "Pattern" -> stringValue?.let { PropertyConstraint.Pattern(it) }
    "MinSize" -> intValue?.let { PropertyConstraint.MinSize(it) }
    "MaxSize" -> intValue?.let { PropertyConstraint.MaxSize(it) }
    "MinDate" -> stringValue?.let { runCatching { PropertyConstraint.MinDate(LocalDate.parse(it)) }.getOrNull() }
    "MaxDate" -> stringValue?.let { runCatching { PropertyConstraint.MaxDate(LocalDate.parse(it)) }.getOrNull() }
    "MinTime" -> stringValue?.let { runCatching { PropertyConstraint.MinTime(LocalTime.parse(it)) }.getOrNull() }
    "MaxTime" -> stringValue?.let { runCatching { PropertyConstraint.MaxTime(LocalTime.parse(it)) }.getOrNull() }
    "MinDatetime" -> stringValue?.let { runCatching { PropertyConstraint.MinDatetime(LocalDateTime.parse(it)) }.getOrNull() }
    "MaxDatetime" -> stringValue?.let { runCatching { PropertyConstraint.MaxDatetime(LocalDateTime.parse(it)) }.getOrNull() }
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
    doc.displayText = displayText
    doc.properties = properties.map { it.toDocument() }
    doc.sortBy = sortBy.map { it.toDocument() }
    doc.computedProperties = computedProperties.map { it.toDocument() }
    doc.aggregations = aggregations.map { it.toDocument() }
    doc.migrationScript = migrationScript
  }

  private fun SortCriteria.toDocument() = SortCriteriaDocument().also { doc ->
    doc.propertyId = propertyId
    doc.direction = direction.name
  }

  private fun Property.toDocument(): PropertyDocument = PropertyDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.type = type.name
    doc.nullable = nullable
    doc.constraints = constraints.map { it.toDocument() }
    doc.default = default
    doc.smartDefault = smartDefault
    doc.valueProposals = valueProposals
    doc.targetEntityId = targetEntityId?.value
    doc.listItemType = listItemType?.name
    doc.itemConstraints = itemConstraints.map { it.toDocument() }
    doc.nestedProperties = nestedProperties.map { it.toDocument() }
    doc.unit = unit?.toDocument()
  }

  private fun PropertyUnit.toDocument() = PropertyUnitDocument().also { doc ->
    doc.family = family.name
    doc.storageGranularity = storageGranularity.enumName()
    doc.defaultGranularity = defaultGranularity.enumName()
  }

  private fun Granularity.enumName(): String = (this as Enum<*>).name

  private fun ComputedProperty.toDocument() = ComputedPropertyDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.type = type.name
    doc.script = script
  }

  private fun AggregationDefinition.toDocument() = AggregationDefinitionDocument().also { doc ->
    doc.id = id.value
    doc.name = name
    doc.function = function.name
    doc.sourceProperty = sourceProperty.value
    doc.refPath = refPath?.value
    doc.timeBucket = timeBucket?.name
    doc.timeProperty = timeProperty?.value
    doc.groupBy = groupBy?.value
  }

  private fun PropertyConstraint.toDocument() = ConstraintDocument().also { doc ->
    doc.constraintType = this::class.simpleName ?: error("Unknown constraint type: $this")
    when (this) {
      is PropertyConstraint.UniqueKey -> Unit
      is PropertyConstraint.MinLong -> doc.longValue = min
      is PropertyConstraint.MaxLong -> doc.longValue = max
      is PropertyConstraint.StepLong -> doc.longValue = step
      is PropertyConstraint.MinDouble -> doc.doubleValue = min
      is PropertyConstraint.MaxDouble -> doc.doubleValue = max
      is PropertyConstraint.StepDouble -> doc.doubleValue = step
      is PropertyConstraint.MinLength -> doc.intValue = min
      is PropertyConstraint.MaxLength -> doc.intValue = max
      is PropertyConstraint.Pattern -> doc.stringValue = regex
      is PropertyConstraint.MinSize -> doc.intValue = min
      is PropertyConstraint.MaxSize -> doc.intValue = max
      is PropertyConstraint.MinDate -> doc.stringValue = min.toString()
      is PropertyConstraint.MaxDate -> doc.stringValue = max.toString()
      is PropertyConstraint.MinTime -> doc.stringValue = min.toString()
      is PropertyConstraint.MaxTime -> doc.stringValue = max.toString()
      is PropertyConstraint.MinDatetime -> doc.stringValue = min.toString()
      is PropertyConstraint.MaxDatetime -> doc.stringValue = max.toString()
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
