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
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.Principal
import java.util.Optional

@ApplicationScoped
@Suppress("Unused")
class CookieAuthMechanism(
  private val tokenEncryption: TokenEncryptionPort,
  private val userRepository: UserRepositoryPort,
) : HttpAuthenticationMechanism {

  override fun authenticate(context: RoutingContext, identityProviderManager: IdentityProviderManager): Uni<SecurityIdentity> {
    val username = context.request().getCookie(COOKIE_NAME)?.value
      ?.let { tokenEncryption.decrypt(it).getOrNull() }
      ?: return Uni.createFrom().optional(Optional.empty())
    val user = userRepository.findByUsername(Username(username))
      ?: return Uni.createFrom().optional(Optional.empty())
    val identityBuilder = QuarkusSecurityIdentity.builder()
      .setPrincipal(Principal { username })
      .setAnonymous(false)
    user.roles.forEach { role -> identityBuilder.addRole(role.name) }
    return Uni.createFrom().item(identityBuilder.build())
  }

  override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
    Uni.createFrom().item(ChallengeData(REDIRECT_STATUS, "Location", "/"))

  companion object : KLogging() {
    const val COOKIE_NAME = "james-session"
    private const val REDIRECT_STATUS = 307
  }
}
