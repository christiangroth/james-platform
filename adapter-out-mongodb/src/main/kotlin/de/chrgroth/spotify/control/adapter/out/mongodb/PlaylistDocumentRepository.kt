package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PlaylistDocumentRepository : PanacheMongoRepositoryBase<PlaylistDocument, String>
