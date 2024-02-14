package de.chrgroth.james.data

import arrow.core.ValidatedNel
import java.util.UUID
import de.chrgroth.james.DomainError

interface DataQueryPersistencePort {
    fun getOrError(bucketId: UUID, dataObjectId: UUID): ValidatedNel<DomainError, DataObject>
    fun load(bucketId: UUID, datatypeName: String): ValidatedNel<DomainError, List<DataObject>> // TODO #2 need paging
    // TODO #10 add find methods using various parametrs/filters, sorting and paging
}

interface DataCommandPersistencePort {
    fun upsert(bucketId: UUID, dataObject: DataObject): ValidatedNel<DomainError, DataObject>
    fun delete(bucketId: UUID, dataObjectId: UUID): ValidatedNel<DomainError, Unit>
}
