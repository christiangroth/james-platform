package de.chrgroth.james.platform.domain.port.`in`.user

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.user.ApiToken
import de.chrgroth.james.platform.domain.model.user.ApiTokenId
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.model.user.AuthenticatedApiToken
import de.chrgroth.james.platform.domain.model.user.CreatedApiToken

/** Personal access tokens of the User identified by `username` (the session principal). A User can only ever see and manage their own tokens. */
interface ApiTokenPort {
  fun listTokens(username: String): Either<DomainError, List<ApiToken>>

  /** Scopes requiring a role the User does not have are rejected; [appIds] restricts the User scopes to these Apps (empty = all Apps). */
  fun createToken(username: String, name: String, scopes: Set<ApiTokenScope>, appIds: Set<String>): Either<DomainError, CreatedApiToken>

  fun setActive(username: String, id: ApiTokenId, active: Boolean): Either<DomainError, Unit>

  fun deleteToken(username: String, id: ApiTokenId): Either<DomainError, Unit>

  /**
   * Returns the token, its owner and the scopes currently in effect if [plainToken] is known, active and its owner exists and is active,
   * recording its usage; null otherwise (also if no scope is in effect anymore).
   */
  fun authenticate(plainToken: String): AuthenticatedApiToken?
}
