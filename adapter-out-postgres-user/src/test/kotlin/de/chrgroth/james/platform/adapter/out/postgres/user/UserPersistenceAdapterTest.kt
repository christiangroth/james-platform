package de.chrgroth.james.platform.adapter.out.postgres.user

import de.chrgroth.james.platform.domain.user.PasswordStatus
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.UserRole
import de.chrgroth.james.platform.domain.user.UserStatus
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.mockito.InjectMock
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.util.*
import javax.inject.Inject
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@QuarkusTest
@TestProfile(UserPersistenceTestProfile::class)
class UserPersistenceAdapterTest {

    @Inject
    private lateinit var adapter: UserPersistenceAdapter

    @InjectMock
    private lateinit var repository: UserRepository

    private val testUser = User(
        id = UserId("test-id"),
        username = "testuser",
        passwordHash = "hashed-password",
        passwordStatus = PasswordStatus.PERMANENT,
        roles = setOf(UserRole.USER),
        status = UserStatus.ACTIVE,
        statusReason = null,
        deactivationCounter = 0u
    )

    @Test
    fun `findById should return user when exists`() {
        // Given
        val userId = UserId("test-id")
        whenever(repository.byId(userId)).thenReturn(testUser.valid())

        // When
        val result = adapter.byId(userId)

        // Then
        assert(result.isValid)
        assertEquals(testUser, result.orNull())
    }

    @Test
    fun `findById should return null when user does not exist`() {
        // Given
        val userId = UserId("non-existent-id")
        whenever(repository.byId(userId)).thenReturn(null.valid())

        // When
        val result = adapter.byId(userId)

        // Then
        assert(result.isValid)
        assertNull(result.orNull())
    }

    @Test
    fun `save should delegate to repository`() {
        // Given
        whenever(repository.create(testUser)).thenReturn(Unit.valid())

        // When
        val result = adapter.create(testUser)

        // Then
        assert(result.isValid)
        verify(repository).create(testUser)
    }

    @Test
    fun `update should delegate to repository`() {
        // Given
        whenever(repository.update(testUser)).thenReturn(Unit.valid())

        // When
        val result = adapter.update(testUser)

        // Then
        assert(result.isValid)
        verify(repository).update(testUser)
    }

    @Test
    fun `delete should delegate to repository`() {
        // Given
        val userId = UserId("test-id")
        whenever(repository.delete(userId)).thenReturn(Unit.valid())

        // When
        val result = adapter.delete(userId)

        // Then
        assert(result.isValid)
        verify(repository).delete(userId)
    }

    @Test
    fun `findByUsername should return user when exists`() {
        // Given
        val username = "testuser"
        whenever(repository.byUsername(username)).thenReturn(testUser.valid())

        // When
        val result = adapter.byUsername(username)

        // Then
        assert(result.isValid)
        assertEquals(testUser, result.orNull())
    }

    @Test
    fun `findAll should return all users`() {
        // Given
        val users = setOf(testUser)
        whenever(repository.all()).thenReturn(users.valid())

        // When
        val result = adapter.all()

        // Then
        assert(result.isValid)
        assertEquals(users, result.orNull())
    }

    private fun <T> T.valid() = this.validNel()
}
