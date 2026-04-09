package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.OAuthError
import de.chrgroth.spotify.control.domain.port.`in`.user.LoginServicePort
import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Path("")
@ApplicationScoped
@Suppress("Unused")
class OAuthResource(
  private val loginService: LoginServicePort,
  private val tokenEncryption: TokenEncryptionPort,
  @param:ConfigProperty(name = "spotify.client-id")
  private val clientId: String,
  @param:ConfigProperty(name = "app.oauth.redirect-uri")
  private val redirectUri: String,
  @param:ConfigProperty(name = "spotify.accounts.base-url")
  private val accountsBaseUrl: String,
) {

  private val stateStore = ConcurrentHashMap<String, Long>()

  private val scopes = listOf(
    "user-read-currently-playing",
    "user-read-recently-played",
    "playlist-read-private",
    "playlist-read-collaborative",
    "playlist-modify-public",
    "playlist-modify-private",
    "user-read-private",
  ).joinToString(" ")

  @GET
  @PermitAll
  @Path("/oauth/authorize")
  fun authorize(): Response {
    val state = UUID.randomUUID().toString()
    stateStore[state] = System.currentTimeMillis()
    cleanExpiredStates()
    logger.info { "[$state] Starting OAuth authorization" }
    val authUrl = "$accountsBaseUrl/authorize" +
      "?client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
      "&response_type=code" +
      "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
      "&scope=${URLEncoder.encode(scopes, "UTF-8")}" +
      "&state=${URLEncoder.encode(state, "UTF-8")}"
    return Response.temporaryRedirect(URI.create(authUrl)).build()
  }

  @GET
  @PermitAll
  @Path("/oauth/callback")
  fun callback(
    @QueryParam("code") code: String?,
    @QueryParam("state") state: String?,
    @QueryParam("error") error: String?,
  ): Response {
    val validationError = validateCallbackParams(code, state, error)
    if (validationError != null) {
      logger.warn { "[$state] OAuth callback validation failed: ${validationError.code}" }
      return Response.temporaryRedirect(URI.create("/?error=${validationError.code}")).build()
    }
    stateStore.remove(state!!)

    return loginService.handleCallback(code!!).fold(
      ifLeft = { domainError ->
        logger.warn { "[$state] OAuth login failed: ${domainError.code}" }
        Response.temporaryRedirect(URI.create("/?error=${domainError.code}")).build()
      },
      ifRight = { userId ->
        logger.info { "[$state] OAuth login successful for user: ${userId.value}" }
        tokenEncryption.encrypt(userId.value).fold(
          ifLeft = { encryptError ->
            logger.error { "[$state] Failed to encrypt session cookie: ${encryptError.code}" }
            Response.temporaryRedirect(URI.create("/?error=${encryptError.code}")).build()
          },
          ifRight = { cookieValue ->
            Response.temporaryRedirect(URI.create("/dashboard"))
              .cookie(
                NewCookie.Builder(SpotifyCookieAuthMechanism.COOKIE_NAME)
                  .value(cookieValue)
                  .path("/")
                  .httpOnly(true)
                  .sameSite(NewCookie.SameSite.LAX)
                  .maxAge(SESSION_COOKIE_MAX_AGE_SECONDS)
                  .build()
              )
              .build()
          }
        )
      }
    )
  }

  private fun validateCallbackParams(code: String?, state: String?, error: String?): OAuthError? = when {
    error != null -> OAuthError.SPOTIFY_DENIED
    code == null || state == null -> OAuthError.INVALID_REQUEST
    !stateStore.containsKey(state) -> OAuthError.STATE_MISMATCH
    else -> null
  }

  @GET
  @PermitAll
  @Path("/logout")
  fun logout(): Response =
    Response.temporaryRedirect(URI.create("/"))
      .cookie(
        NewCookie.Builder(SpotifyCookieAuthMechanism.COOKIE_NAME)
          .value("")
          .path("/")
          .maxAge(0)
          .build()
      )
      .build()

  private fun cleanExpiredStates() {
    val expiry = System.currentTimeMillis() - STATE_TTL_MS
    stateStore.entries.removeIf { it.value < expiry }
  }

  companion object : KLogging() {
    private const val STATE_TTL_MS = 600_000L
    private const val SESSION_COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60 // 30 days
  }
}
