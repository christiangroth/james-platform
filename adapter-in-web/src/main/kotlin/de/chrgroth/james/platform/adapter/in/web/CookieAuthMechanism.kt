package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.port.out.user.TokenEncryptionPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import de.chrgroth.james.platform.domain.model.user.Username
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.core.http.Cookie
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.Principal
import java.time.Instant
import java.util.Optional

@ApplicationScoped
@Suppress("Unused")
class CookieAuthMechanism(
  private val tokenEncryption: TokenEncryptionPort,
  private val userRepository: UserRepositoryPort,
) : HttpAuthenticationMechanism {

  override fun authenticate(context: RoutingContext, identityProviderManager: IdentityProviderManager): Uni<SecurityIdentity> {
    val cookieValue = context.request().getCookie(COOKIE_NAME)?.value
      ?: return Uni.createFrom().optional(Optional.empty())
    val decrypted = tokenEncryption.decrypt(cookieValue).getOrNull()
      ?: return Uni.createFrom().optional(Optional.empty())
    val (username, issuedAt) = parsePayload(decrypted)
    val user = userRepository.findByUsername(Username(username))
      ?: return Uni.createFrom().optional(Optional.empty())
    if (issuedAt != null) {
      val secondsRemaining = COOKIE_MAX_AGE_SECONDS - (Instant.now().epochSecond - issuedAt)
      if (secondsRemaining < COOKIE_REFRESH_THRESHOLD_SECONDS) {
        refreshCookie(context, username)
      }
    }
    val identityBuilder = QuarkusSecurityIdentity.builder()
      .setPrincipal(Principal { username })
      .setAnonymous(false)
    user.roles.forEach { role -> identityBuilder.addRole(role.name) }
    return Uni.createFrom().item(identityBuilder.build())
  }

  private fun parsePayload(decrypted: String): Pair<String, Long?> {
    val idx = decrypted.indexOf(PAYLOAD_SEPARATOR)
    if (idx == -1) return Pair(decrypted, null)
    val username = decrypted.substring(0, idx)
    val issuedAt = decrypted.substring(idx + 1).toLongOrNull()
    return Pair(username, issuedAt)
  }

  private fun refreshCookie(context: RoutingContext, username: String) {
    val encrypted = tokenEncryption.encrypt(buildPayload(username)).getOrNull() ?: return
    context.response().addCookie(
      Cookie.cookie(COOKIE_NAME, encrypted)
        .setMaxAge(COOKIE_MAX_AGE_SECONDS.toLong())
        .setPath("/")
        .setHttpOnly(true),
    )
  }

  override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
    Uni.createFrom().item(ChallengeData(REDIRECT_STATUS, "Location", "/"))

  companion object : KLogging() {
    const val COOKIE_NAME = "james-session"
    const val COOKIE_MAX_AGE_SECONDS = 14 * 24 * 60 * 60 // 14 days
    private const val COOKIE_REFRESH_THRESHOLD_SECONDS = 5 * 24 * 60 * 60 // refresh if less than 5 days remaining
    private const val PAYLOAD_SEPARATOR = "|"
    private const val REDIRECT_STATUS = 307

    fun buildPayload(username: String): String = "$username$PAYLOAD_SEPARATOR${Instant.now().epochSecond}"
  }
}
