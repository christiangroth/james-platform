package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppName
import de.chrgroth.james.platform.domain.model.app.AppStatus
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AppRepositoryAdapter(
  private val appDocumentRepository: AppDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppRepositoryPort {

  override fun findById(appId: AppId): App? =
    mongoQueryMetrics.timed("app.findById") {
      appDocumentRepository.findById(appId.value)?.toDomain()
    }

  override fun findByName(name: AppName): App? =
    mongoQueryMetrics.timed("app.findByName") {
      appDocumentRepository.find(NAME_FIELD, name.value).firstResult()?.toDomain()
    }

  override fun findAll(): List<App> =
    mongoQueryMetrics.timed("app.findAll") {
      appDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun save(app: App) {
    mongoQueryMetrics.timed("app.save") {
      val doc = app.toDocument()
      appDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, app.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  private fun AppDocument.toDomain() = App(
    id = AppId(id),
    name = AppName(name),
    description = description,
    status = AppStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

  private fun App.toDocument() = AppDocument().also { doc ->
    doc.id = id.value
    doc.name = name.value
    doc.description = description
    doc.status = status.name
    doc.createdAt = createdAt
    doc.updatedAt = updatedAt
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val NAME_FIELD = "name"
  }
}
