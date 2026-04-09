package de.chrgroth.spotify.control.domain.user

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.RefreshToken
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.user.SpotifyTokens
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginServiceTests {

  private val spotifyAuth: SpotifyAuthPort = mockk()
  private val userRepository: UserRepositoryPort = mockk()
  private val tokenEncryption: TokenEncryptionPort = mockk()

  private val adapter = LoginService(spotifyAuth, userRepository, tokenEncryption, listOf("user-1"))

  private val tokens = SpotifyTokens(AccessToken("access"), RefreshToken("refresh"), 3600)
  private val profile = SpotifyProfile(SpotifyProfileId("user-1"), "Test User")

  @Test
  fun `allowed user succeeds and user is upserted`() {
    every { spotifyAuth.exchangeCode("code") } returns tokens.right()
    every { spotifyAuth.getUserProfile(AccessToken("access")) } returns profile.right()
    every { tokenEncryption.encrypt(any()) } returns "encrypted".right()
    every { userRepository.upsert(any()) } just runs

    val result = adapter.handleCallback("code")

    assertThat(result.isRight()).isTrue()
    assertThat((result as Either.Right).value).isEqualTo(UserId("user-1"))
    verify { userRepository.upsert(any()) }
  }

  @Test
  fun `not-allowed user returns failure and user is not upserted`() {
    every { spotifyAuth.exchangeCode("code") } returns tokens.right()
    every { spotifyAuth.getUserProfile(AccessToken("access")) } returns SpotifyProfile(SpotifyProfileId("user-2"), "Other User").right()

    val result = adapter.handleCallback("code")

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(AuthError.USER_NOT_ALLOWED)
    verify(exactly = 0) { userRepository.upsert(any()) }
  }

  @Test
  fun `token exchange failure is propagated`() {
    every { spotifyAuth.exchangeCode("code") } returns AuthError.TOKEN_EXCHANGE_FAILED.left()

    val result = adapter.handleCallback("code")

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(AuthError.TOKEN_EXCHANGE_FAILED)
    verify(exactly = 0) { userRepository.upsert(any()) }
  }

  @Test
  fun `profile fetch failure is propagated`() {
    every { spotifyAuth.exchangeCode("code") } returns tokens.right()
    every { spotifyAuth.getUserProfile(AccessToken("access")) } returns AuthError.PROFILE_FETCH_FAILED.left()

    val result = adapter.handleCallback("code")

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(AuthError.PROFILE_FETCH_FAILED)
    verify(exactly = 0) { userRepository.upsert(any()) }
  }

  @Test
  fun `unexpected exception during upsert returns UNEXPECTED error`() {
    every { spotifyAuth.exchangeCode("code") } returns tokens.right()
    every { spotifyAuth.getUserProfile(AccessToken("access")) } returns profile.right()
    every { tokenEncryption.encrypt(any()) } returns "encrypted".right()
    every { userRepository.upsert(any()) } throws RuntimeException("DB connection failed")

    val result = adapter.handleCallback("code")

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(AuthError.UNEXPECTED)
  }

  // --- isAllowed tests ---

  @Test
  fun `isAllowed returns true for user in allowed list`() {
    assertThat(adapter.isAllowed(UserId("user-1"))).isTrue()
  }

  @Test
  fun `isAllowed returns false for user not in allowed list`() {
    assertThat(adapter.isAllowed(UserId("unknown-user"))).isFalse()
  }
}
