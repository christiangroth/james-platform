package de.chrgroth.james.platform.domain.port.`in`.catalog

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.catalog.AppArtist
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

interface CatalogPort {
  fun findAllArtists(): List<AppArtist>
  fun syncArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit>
  fun resyncCatalog(): Either<DomainError, Unit>
  fun resyncArtist(artistId: String): Either<DomainError, Unit>
  fun wipeCatalog(): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.SyncArtistDetails): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.SyncArtistAlbums): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.SyncAlbumDetails): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.ResyncCatalog): Either<DomainError, Unit>
  fun enqueueArtistAlbumsSync(partition: Int, totalPartitions: Int)
}
