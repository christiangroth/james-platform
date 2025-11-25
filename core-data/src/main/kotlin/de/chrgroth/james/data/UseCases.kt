package de.chrgroth.james.data

import arrow.core.ValidatedNel
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainError
import java.util.*

// TODO #6 need to check if user is active

// TODO #2 think about advanced data usecases
interface DataUseCases {
  fun prepareNewObject(appId: UUID, appVersion: Semver, datatypeName: String, datatypeVersion: Long, owner: UUID): ValidatedNel<DomainError, DataObject>
  fun store(bucketId: UUID, dataObject: DataObject): ValidatedNel<DomainError, DataObject>
  fun load(bucketId: UUID, datatypeName: String): ValidatedNel<DomainError, List<DataObject>>
  fun load(bucketId: UUID, dataObjectId: UUID): ValidatedNel<DomainError, DataObject>

  // TODO #2 or #10 simple load function for paged data without any filters or sorting
  // TODO #10 add find methods using various parametrs/filters, sorting and paging
  fun delete(bucketId: UUID, dataObjectId: UUID): ValidatedNel<DomainError, Unit>
}

internal class DataUseCasesService(
  private val queryPersistence: DataQueryPersistencePort,
  private val commandPersistence: DataCommandPersistencePort,
  private val appVersionDatatypesSchemaContentCache: AppVersionDatatypesSchemaContentCache,
) : DataUseCases {

  override fun prepareNewObject(appId: UUID, appVersion: Semver, datatypeName: String, datatypeVersion: Long, owner: UUID): ValidatedNel<DomainError, DataObject> {

    val datatypeSchemaContent = appVersionDatatypesSchemaContentCache.get(appId, appVersion, datatypeName)
    val initialData = if (datatypeSchemaContent != null) {
      // TODO #2 use schema content for all default values etc
      mapOf<String, String>()
    } else {
      emptyMap<String, String>()
    }

    return DataObject.create(
      id = UUID.randomUUID(),
      appId = appId,
      appVersion = appVersion,
      datatypeName = datatypeName,
      datatypeVersion = datatypeVersion,
      ownerId = owner,
      data = initialData,
    )
  }

  override fun store(bucketId: UUID, dataObject: DataObject): ValidatedNel<DomainError, DataObject> =
    commandPersistence.upsert(bucketId, dataObject)

  override fun load(bucketId: UUID, datatypeName: String): ValidatedNel<DomainError, List<DataObject>> =
    queryPersistence.load(bucketId, datatypeName)

  override fun load(bucketId: UUID, dataObjectId: UUID): ValidatedNel<DomainError, DataObject> =
    queryPersistence.getOrError(bucketId, dataObjectId)

  override fun delete(bucketId: UUID, dataObjectId: UUID): ValidatedNel<DomainError, Unit> =
    commandPersistence.delete(bucketId, dataObjectId)
}
