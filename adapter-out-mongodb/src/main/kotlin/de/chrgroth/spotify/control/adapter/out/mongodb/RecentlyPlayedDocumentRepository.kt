package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.bson.types.ObjectId

@ApplicationScoped
class RecentlyPlayedDocumentRepository : PanacheMongoRepositoryBase<RecentlyPlayedDocument, ObjectId>
