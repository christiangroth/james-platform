package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.bson.types.ObjectId

@ApplicationScoped
class AppPlaybackDocumentRepository : PanacheMongoRepositoryBase<AppPlaybackDocument, ObjectId>
