package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Indexes
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

@ApplicationScoped
@Suppress("UnusedParameter")
class MongoIndexInitializer(
  private val appDocumentRepository: AppDocumentRepository,
  private val appDataDocumentRepository: AppDataDocumentRepository,
  private val appVersionDocumentRepository: AppVersionDocumentRepository,
  private val installedAppDocumentRepository: InstalledAppDocumentRepository,
  private val userDocumentRepository: UserDocumentRepository,
) {

  fun onStartup(@Observes event: StartupEvent) {
    // app: speed up findAllByDeveloperId
    appDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(AppRepositoryAdapter.DEVELOPER_ID_FIELD),
    )
    // app_data: speed up findAllByInstalledAppId
    appDataDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(AppDataRepositoryAdapter.INSTALLED_APP_ID_FIELD),
    )
    // app_data: speed up findAllByInstalledAppIdAndEntityType
    appDataDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(AppDataRepositoryAdapter.INSTALLED_APP_ID_FIELD, AppDataRepositoryAdapter.ENTITY_TYPE_FIELD),
    )
    // app_app_version: speed up findAllByAppId
    appVersionDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(AppVersionRepositoryAdapter.APP_ID_FIELD),
    )
    // app_app_version: speed up findByAppIdAndVersionNumber
    appVersionDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(AppVersionRepositoryAdapter.APP_ID_FIELD, AppVersionRepositoryAdapter.VERSION_NUMBER_FIELD),
    )
    // installed_app: speed up findAllByUserId
    installedAppDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(InstalledAppRepositoryAdapter.USER_ID_FIELD),
    )
    // installed_app: speed up findByUserIdAndAppId
    installedAppDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(InstalledAppRepositoryAdapter.USER_ID_FIELD, InstalledAppRepositoryAdapter.APP_ID_FIELD),
    )
    // app_user: speed up findByUsername
    userDocumentRepository.mongoCollection().createIndex(
      Indexes.ascending(UserRepositoryAdapter.USERNAME_FIELD),
    )
    logger.info { "MongoDB indexes ready." }
  }

  companion object : KLogging()
}
