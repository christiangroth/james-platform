package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AppDataRepositoryAdapter(
  private val appDataDocumentRepository: AppDataDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppDataRepositoryPort {

  override fun findAllByInstalledAppId(installedAppId: InstalledAppId): List<AppData> =
    mongoQueryMetrics.timed("app_data.findAllByInstalledAppId") {
      appDataDocumentRepository.find(INSTALLED_APP_ID_FIELD, installedAppId.value).list().map { it.toDomain() }
    }

  override fun findAllByInstalledAppIdAndEntityType(installedAppId: InstalledAppId, entityType: EntityDefinitionId): List<AppData> =
    mongoQueryMetrics.timed("app_data.findAllByInstalledAppIdAndEntityType") {
      appDataDocumentRepository.find(
        Filters.and(
          Filters.eq(INSTALLED_APP_ID_FIELD, installedAppId.value),
          Filters.eq(ENTITY_TYPE_FIELD, entityType.value),
        ),
      ).list().map { it.toDomain() }
    }

  override fun findById(id: AppDataId): AppData? =
    mongoQueryMetrics.timed("app_data.findById") {
      appDataDocumentRepository.findById(id.value)?.toDomain()
    }

  override fun save(appData: AppData) {
    mongoQueryMetrics.timed("app_data.save") {
      val doc = appData.toDocument()
      appDataDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, appData.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  override fun delete(id: AppDataId) {
    mongoQueryMetrics.timed("app_data.delete") {
      appDataDocumentRepository.deleteById(id.value)
    }
  }

  override fun deleteAllByInstalledAppId(installedAppId: InstalledAppId) {
    mongoQueryMetrics.timed("app_data.deleteAllByInstalledAppId") {
      appDataDocumentRepository.mongoCollection().deleteMany(Filters.eq(INSTALLED_APP_ID_FIELD, installedAppId.value))
    }
  }

  private fun AppDataDocument.toDomain() = AppData(
    id = AppDataId(id),
    userId = userId,
    installedAppId = InstalledAppId(installedAppId),
    appVersion = VersionNumber(appVersion),
    entityType = EntityDefinitionId(entityType),
    objectVersion = objectVersion,
    createdAt = createdAt,
    lastChangedAt = lastChangedAt,
    data = data,
  )

  private fun AppData.toDocument() = AppDataDocument().also { doc ->
    doc.id = id.value
    doc.userId = userId
    doc.installedAppId = installedAppId.value
    doc.appVersion = appVersion.value
    doc.entityType = entityType.value
    doc.objectVersion = objectVersion
    doc.createdAt = createdAt
    doc.lastChangedAt = lastChangedAt
    doc.data = data
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val INSTALLED_APP_ID_FIELD = "installedAppId"
    internal const val ENTITY_TYPE_FIELD = "entityType"
  }
}
