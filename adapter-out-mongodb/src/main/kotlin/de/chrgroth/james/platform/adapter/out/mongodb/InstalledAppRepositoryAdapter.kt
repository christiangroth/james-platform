package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InstalledAppRepositoryAdapter(
  private val installedAppDocumentRepository: InstalledAppDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : InstalledAppRepositoryPort {

  override fun findById(id: InstalledAppId): InstalledApp? =
    mongoQueryMetrics.timed("installed_app.findById") {
      installedAppDocumentRepository.findById(id.value)?.toDomain()
    }

  override fun findByUserIdAndAppId(userId: String, appId: AppId): InstalledApp? =
    mongoQueryMetrics.timed("installed_app.findByUserIdAndAppId") {
      installedAppDocumentRepository.find(
        Filters.and(Filters.eq(USER_ID_FIELD, userId), Filters.eq(APP_ID_FIELD, appId.value)),
      ).firstResult()?.toDomain()
    }

  override fun findAllByUserId(userId: String): List<InstalledApp> =
    mongoQueryMetrics.timed("installed_app.findAllByUserId") {
      installedAppDocumentRepository.find(USER_ID_FIELD, userId).list().map { it.toDomain() }
    }

  override fun save(installedApp: InstalledApp) {
    mongoQueryMetrics.timed("installed_app.save") {
      val doc = installedApp.toDocument()
      installedAppDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, installedApp.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  private fun InstalledAppDocument.toDomain() = InstalledApp(
    id = InstalledAppId(id),
    userId = userId,
    appId = AppId(appId),
    installedVersionId = AppVersionId(installedVersionId),
    installedAt = installedAt,
  )

  private fun InstalledApp.toDocument() = InstalledAppDocument().also { doc ->
    doc.id = id.value
    doc.userId = userId
    doc.appId = appId.value
    doc.installedVersionId = installedVersionId.value
    doc.installedAt = installedAt
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val USER_ID_FIELD = "userId"
    internal const val APP_ID_FIELD = "appId"
  }
}
