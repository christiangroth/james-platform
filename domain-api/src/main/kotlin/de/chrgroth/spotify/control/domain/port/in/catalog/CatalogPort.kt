package de.chrgroth.spotify.control.domain.port.`in`.catalog

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

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
