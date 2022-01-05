package de.chrgroth.james.user

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class UserDomainTests {

    private val existingUser = User.create("existing@james.de", "Existing").expectSuccess()
    private val existingUserId = existingUser.id
    private val unknownUserId = UUID.randomUUID()

    private lateinit var queryPersistence: UserQueryPersistencePort
    private lateinit var commandPersistence: UserCommandPersistencePort
    private lateinit var port: UserCommandPort

    @BeforeEach
    internal fun initialize() {
        queryPersistence = mockk<UserQueryPersistencePort>().also {
            every { it.getOrError(existingUserId) }.returns(Result(existingUser))
            every { it.getOrError(unknownUserId) }.returns(Error(UserErrorCodes.NOT_FOUND, unknownUserId.toString()))

            every { it.getByEmail(any()) }.returns(Result(null))

            val duplicateTestUser = User.create("duplicate@james.de", "Duplicate").expectSuccess()
            every { it.getByEmail(duplicateTestUser.email) }.returns(Result(duplicateTestUser))
        }

        commandPersistence = mockk<UserCommandPersistencePort>().also {
            every { it.upsert(any()) }.answers { Result(this.args[0] as User) }
        }

        port = UserCommandAdapter(queryPersistence, commandPersistence)
    }

    @Test
    internal fun `valid suer cleans up values`() {
        val user = port.registerUser("  email@james.de  ", " Some Name   \t").expectSuccess()
        assertThat(user.email).isEqualTo("email@james.de")
        assertThat(user.name).isEqualTo("Some Name")
    }

    @Test
    internal fun `valid user`() {
        port.registerUser("someone@james.de", "Joe").expectSuccess()
        verifySequence {
            queryPersistence.getByEmail("someone@james.de")
            commandPersistence.upsert(withArg {
                val user = this.actual as User
                assertThat(user.id).isNotIn(existingUserId, unknownUserId)
                assertThat(user.email).isEqualTo("someone@james.de")
                assertThat(user.name).isEqualTo("Joe")
            })
        }
    }

    @Test
    internal fun `duplicate email`() {
        port.registerUser("duplicate@james.de", "Joe").expectError(
            code = UserErrorCodes.EMAIL_EXISTS,
            details = "duplicate@james.de",
        )
    }

    @Test
    internal fun `blank email`() {
        port.registerUser("", "Joe").expectError(
            code = UserErrorCodes.EMAIL_BLANK,
            details = null,
        )
    }

    @Test
    internal fun `invalid email`() {
        port.registerUser("someone_james.de", "Joe").expectError(
            code = UserErrorCodes.EMAIL_INVALID,
            details = "'someone_james.de' does not match .+@.+\\..+",
        )
    }

    @Test
    internal fun `blank name`() {
        port.registerUser("someone@james.de", " ").expectError(
            code = UserErrorCodes.NAME_BLANK,
            details = null,
        )
    }

    @Test
    internal fun `change email`() {
        port.changeEmail(existingUserId, "someone_new@james.de").expectSuccess()
        verifySequence {
            queryPersistence.getByEmail("someone_new@james.de")
            queryPersistence.getOrError(existingUserId)
            commandPersistence.upsert(withArg {
                val user = this.actual as User
                assertThat(user.id).isEqualTo(existingUserId)
                assertThat(user.email).isEqualTo("someone_new@james.de")
                assertThat(user.name).isEqualTo("Existing")
            })
        }
    }

    @Test
    internal fun `change email unknown user`() {
        port.changeEmail(unknownUserId, "someone_other@james.de").expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = unknownUserId.toString(),
        )
    }

    @Test
    internal fun `change email duplicate mail`() {
        port.changeEmail(existingUserId, "duplicate@james.de").expectError(
            code = UserErrorCodes.EMAIL_EXISTS,
            details = "duplicate@james.de",
        )
    }

    @Test
    internal fun `change name`() {
        port.changeName(existingUserId, "James").expectSuccess()
        verifySequence {
            queryPersistence.getOrError(existingUserId)
            commandPersistence.upsert(withArg {
                val user = this.actual as User
                assertThat(user.id).isEqualTo(existingUserId)
                assertThat(user.email).isEqualTo("existing@james.de")
                assertThat(user.name).isEqualTo("James")
            })
        }
    }

    @Test
    internal fun `change name invalid`() {
        port.changeName(existingUserId, "").expectError(
            code = UserErrorCodes.NAME_BLANK,
            details = null
        )
    }

    @Test
    internal fun `existing user`() {
        port.deleteUser(existingUserId).expectError(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }

    @Test
    internal fun `unknown user`() {
        port.deleteUser(unknownUserId).expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = unknownUserId.toString(),
        )
    }
}
