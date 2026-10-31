package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.james.platform.domain.model.user.ApiToken
import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.port.out.user.ApiTokenRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.Date

@ApplicationScoped
class ApiTokenRepositoryAdapter(
  private val apiTokenDocumentRepository: ApiTokenDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : ApiTokenRepositoryPort {

  override fun findAllByUserId(userId: UserId): List<ApiToken> =
    mongoQueryMetrics.timed("api_token.findAllByUserId") {
      apiTokenDocumentRepository.list(USER_ID_FIELD, userId.value).map { it.toDomain() }
    }

  override fun findByHash(tokenHash: String): ApiToken? =
    mongoQueryMetrics.timed("api_token.findByHash") {
      apiTokenDocumentRepository.find(TOKEN_HASH_FIELD, tokenHash).firstResult()?.toDomain()
    }

  override fun insert(token: ApiToken) {
    mongoQueryMetrics.timed("api_token.insert") {
      apiTokenDocumentRepository.persist(token.toDocument())
    }
  }

  override fun setActive(id: ApiTokenId, userId: UserId, active: Boolean): Boolean =
    mongoQueryMetrics.timed("api_token.setActive") {
      apiTokenDocumentRepository.mongoCollection()
        .updateOne(ownedBy(id, userId), Updates.set(ACTIVE_FIELD, active))
        .matchedCount > 0
    }

  override fun updateLastUsedAt(id: ApiTokenId, lastUsedAt: Instant) {
    mongoQueryMetrics.timed("api_token.updateLastUsedAt") {
      apiTokenDocumentRepository.mongoCollection()
        .updateOne(Filters.eq(ID_FIELD, id.value), Updates.set(LAST_USED_AT_FIELD, Date.from(lastUsedAt)))
    }
  }

  override fun delete(id: ApiTokenId, userId: UserId): Boolean =
    mongoQueryMetrics.timed("api_token.delete") {
      apiTokenDocumentRepository.mongoCollection().deleteOne(ownedBy(id, userId)).deletedCount > 0
    }

  override fun deleteAllByUserId(userId: UserId) {
    mongoQueryMetrics.timed("api_token.deleteAllByUserId") {
      apiTokenDocumentRepository.mongoCollection().deleteMany(Filters.eq(USER_ID_FIELD, userId.value))
    }
  }

  private fun ownedBy(id: ApiTokenId, userId: UserId) = Filters.and(Filters.eq(ID_FIELD, id.value), Filters.eq(USER_ID_FIELD, userId.value))

  private fun ApiTokenDocument.toDomain() = ApiToken(
    id = ApiTokenId(id),
    userId = UserId(userId),
    name = name,
    tokenHash = tokenHash,
    tokenPrefix = tokenPrefix,
    // unknown scope names (e.g. after a scope was removed) are dropped rather than failing the whole lookup
    scopes = scopes.mapNotNull { runCatching { ApiTokenScope.valueOf(it) }.getOrNull() }.toSet(),
    appIds = appIds,
    active = active,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
  )

  private fun ApiToken.toDocument() = ApiTokenDocument().also { doc ->
    doc.id = id.value
    doc.userId = userId.value
    doc.name = name
    doc.tokenHash = tokenHash
    doc.tokenPrefix = tokenPrefix
    doc.scopes = scopes.map { it.name }.toSet()
    doc.appIds = appIds
    doc.active = active
    doc.createdAt = createdAt
    doc.lastUsedAt = lastUsedAt
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val USER_ID_FIELD = "userId"
    internal const val TOKEN_HASH_FIELD = "tokenHash"
    private const val ACTIVE_FIELD = "active"
    private const val LAST_USED_AT_FIELD = "lastUsedAt"
  }
}
