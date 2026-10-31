package de.chrgroth.james.platform.domain.port.out.user

import de.chrgroth.james.platform.domain.model.user.ApiToken
import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.UserId
import java.time.Instant

interface ApiTokenRepositoryPort {
  fun findAllByUserId(userId: UserId): List<ApiToken>
  fun findByHash(tokenHash: String): ApiToken?
  fun insert(token: ApiToken)

  /** Returns false if no token with [id] belongs to [userId]. */
  fun setActive(id: ApiTokenId, userId: UserId, active: Boolean): Boolean
  fun updateLastUsedAt(id: ApiTokenId, lastUsedAt: Instant)

  /** Returns false if no token with [id] belongs to [userId]. */
  fun delete(id: ApiTokenId, userId: UserId): Boolean
  fun deleteAllByUserId(userId: UserId)
}
