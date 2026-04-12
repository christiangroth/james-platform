package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UserDocumentRepository : PanacheMongoRepositoryBase<UserDocument, String>
