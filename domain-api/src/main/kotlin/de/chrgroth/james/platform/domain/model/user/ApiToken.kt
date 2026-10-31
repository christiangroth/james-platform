package de.chrgroth.james.platform.domain.model.user

import java.time.Instant

@JvmInline
value class ApiTokenId(val value: String)

/**
 * What a personal access token may do on the `/api/v1` JSON API. Each endpoint there declares the scope it requires; endpoints without such a
 * declaration (including the whole `/ui` area) are never reachable with a token. A scope only takes effect while the token's owner still has the
 * [requiredRole] - see [ApiToken.effectiveScopes]. [isUserScope] marks the scopes on installations, which [ApiToken.appIds] can restrict.
 */
enum class ApiTokenScope(val requiredRole: UserRole, val isUserScope: Boolean) {
  /** Read the owner's installed Apps (incl. their structure) and their data - all Apps, or only those in [ApiToken.appIds]. */
  USER_READ(UserRole.USER, isUserScope = true),

  /** Create, update and delete data of the owner's installed Apps - all Apps, or only those in [ApiToken.appIds]. Implies [USER_READ]. */
  USER_WRITE(UserRole.USER, isUserScope = true),

  /** Install Apps from the App store and upgrade installations - all Apps, or only those in [ApiToken.appIds]. Implies [USER_READ]. */
  USER_INSTALL(UserRole.USER, isUserScope = true),

  /**
   * Manage import connections and imports into the owner's installations - fetch, data path, filter, mapping and dry-run - all Apps, or only
   * those in [ApiToken.appIds]. Requires the `DATA_IMPORT` role like the import UI. Implies [USER_READ]. Accepting an import (which writes
   * data) additionally requires [USER_WRITE].
   */
  USER_IMPORT(UserRole.DATA_IMPORT, isUserScope = true),

  /** Read all of the owner's Apps as Developer, including all Versions and drafts. */
  DEVELOPER_READ(UserRole.DEVELOPER, isUserScope = false),

  /** Create Apps, edit their name and description, and create, edit and delete drafts of the owner's Apps. Implies [DEVELOPER_READ]. */
  DEVELOPER_WRITE(UserRole.DEVELOPER, isUserScope = false),

  /** Release (publish) drafts of the owner's Apps. Implies [DEVELOPER_READ]. */
  DEVELOPER_RELEASE(UserRole.DEVELOPER, isUserScope = false),
  ;

  /** This scope plus the scopes it implies. */
  fun withImplied(): Set<ApiTokenScope> = when (this) {
    USER_WRITE -> setOf(USER_WRITE, USER_READ)
    USER_INSTALL -> setOf(USER_INSTALL, USER_READ)
    USER_IMPORT -> setOf(USER_IMPORT, USER_READ)
    DEVELOPER_WRITE -> setOf(DEVELOPER_WRITE, DEVELOPER_READ)
    DEVELOPER_RELEASE -> setOf(DEVELOPER_RELEASE, DEVELOPER_READ)
    else -> setOf(this)
  }
}

/**
 * A personal access token of a single User for CLI / agent access to the `/api/v1` JSON API. Only the SHA-256 hash of the secret is stored;
 * [tokenPrefix] holds the first characters of the secret so the token can be recognised in the UI. Inactive tokens are kept but rejected, so
 * access can be granted for a limited time without rotating the secret.
 *
 * [appIds] restricts the User scopes ([ApiTokenScope.USER_READ], [ApiTokenScope.USER_WRITE], [ApiTokenScope.USER_INSTALL],
 * [ApiTokenScope.USER_IMPORT]) to the installations of the given Apps; empty means
 * all Apps. Developer scopes are never restricted by it.
 */
data class ApiToken(
  val id: ApiTokenId,
  val userId: UserId,
  val name: String,
  val tokenHash: String,
  val tokenPrefix: String,
  val scopes: Set<ApiTokenScope>,
  val appIds: Set<String>,
  val active: Boolean,
  val createdAt: Instant,
  val lastUsedAt: Instant?,
) {

  /** The scopes this token currently grants to [owner]: its own scopes plus implied ones, limited to those whose role [owner] still has. */
  fun effectiveScopes(owner: User): Set<ApiTokenScope> =
    scopes.flatMap { it.withImplied() }.filter { it.requiredRole in owner.roles }.toSet()

  /** Whether the User scopes of this token cover data of the App [appId]. */
  fun coversApp(appId: String): Boolean = appIds.isEmpty() || appId in appIds
}

/** Result of creating a token: the plain secret is only available right after creation. */
data class CreatedApiToken(
  val token: ApiToken,
  val plainToken: String,
)

/** A successfully authenticated token request: the (active) token, its (active) owner and the scopes currently in effect. */
data class AuthenticatedApiToken(
  val token: ApiToken,
  val owner: User,
  val effectiveScopes: Set<ApiTokenScope>,
)
