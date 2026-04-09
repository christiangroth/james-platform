package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.RefreshToken
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyAuthAdapterTests {

  @Inject
  lateinit var spotifyAuth: SpotifyAuthPort

  @Test
  fun `exchangeCode returns tokens from mock`() {
    val result = spotifyAuth.exchangeCode("mock-code")

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val tokens = (result as Either.Right).value
    assertThat(tokens.accessToken.value).isEqualTo("mock-access-token")
    assertThat(tokens.refreshToken.value).isEqualTo("mock-refresh-token")
    assertThat(tokens.expiresInSeconds).isEqualTo(3600)
  }

  @Test
  fun `getUserProfile returns profile from mock`() {
    val result = spotifyAuth.getUserProfile(AccessToken("mock-access-token"))

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val profile = (result as Either.Right).value
    assertThat(profile.id.value).isEqualTo("test-user-a")
    assertThat(profile.displayName).isEqualTo("Mock User")
  }

  @Test
  fun `refreshToken returns new access token from mock`() {
    val result = spotifyAuth.refreshToken(RefreshToken("mock-refresh-token"))

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val tokens = (result as Either.Right).value
    assertThat(tokens.accessToken.value).isEqualTo("mock-refreshed-access-token")
    assertThat(tokens.expiresInSeconds).isEqualTo(3600)
  }
}
