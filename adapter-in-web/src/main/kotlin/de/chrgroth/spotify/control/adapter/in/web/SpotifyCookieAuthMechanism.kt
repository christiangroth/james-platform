package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
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
class SpotifyCookieAuthMechanism(
  private val tokenEncryption: TokenEncryptionPort,
) : HttpAuthenticationMechanism {

  override fun authenticate(context: RoutingContext, identityProviderManager: IdentityProviderManager): Uni<SecurityIdentity> {
    val cookieValue = context.request().getCookie(COOKIE_NAME)?.value
      ?: return Uni.createFrom().optional(Optional.empty())
    val decrypted = tokenEncryption.decrypt(cookieValue).getOrNull()
      ?: return Uni.createFrom().optional(Optional.empty())
    val userId = UserId(decrypted)
    val identity = QuarkusSecurityIdentity.builder()
      .setPrincipal(Principal { userId.value })
      .setAnonymous(false)
      .build()
    return Uni.createFrom().item(identity)
  }

  override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
    Uni.createFrom().item(ChallengeData(REDIRECT_STATUS, "Location", "/"))

  companion object : KLogging() {
    const val COOKIE_NAME = "spotify-session"
    private const val REDIRECT_STATUS = 307
  }
}
