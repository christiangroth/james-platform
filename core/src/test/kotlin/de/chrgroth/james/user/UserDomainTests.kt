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
    private val existingId = existingUser.id
    private val unknownId = UUID.randomUUID()

    private lateinit var queryPersistence: UserQueryPersistencePort
    private lateinit var commandPersistence: UserCommandPersistencePort
    private lateinit var port: UserCommandPort

    @BeforeEach
    internal fun initialize() {
        queryPersistence = mockk<UserQueryPersistencePort>().also {
            every { it.getOrError(existingId) }.returns(Result(existingUser))
            every { it.getOrError(unknownId) }.returns(Error(UserErrorCodes.NOT_FOUND, unknownId.toString()))

            every { it.getByEmail(any()) }.returns(Result(null))

            val duplicateUser = User.create("duplicate@james.de", "Duplicate").expectSuccess()
            every { it.getByEmail(duplicateUser.email) }.returns(Result(duplicateUser))
        }

        commandPersistence = mockk<UserCommandPersistencePort>().also {
            every { it.upsert(any()) }.answers { Result(this.args[0] as User) }
        }

        port = UserCommandAdapter(queryPersistence, commandPersistence)
    }

    @Test
    internal fun `valid user`() {
        port.registerUser("  someone@james.de   ", " Some Name   \t").expectSuccess()
        verifySequence {
            queryPersistence.getByEmail("someone@james.de")
            commandPersistence.upsert(withArg {
                val user = this.actual as User
                assertThat(user.id).isNotIn(existingId, unknownId)
                assertThat(user.email).isEqualTo("someone@james.de")
                assertThat(user.name).isEqualTo("Some Name")
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
        port.changeEmail(existingId, "someone_new@james.de").expectSuccess()
        verifySequence {
            queryPersistence.getOrError(existingId)
            queryPersistence.getByEmail("someone_new@james.de")
            commandPersistence.upsert(withArg {
                val user = this.actual as User
                assertThat(user.id).isEqualTo(existingId)
                assertThat(user.email).isEqualTo("someone_new@james.de")
                assertThat(user.name).isEqualTo("Existing")
            })
        }
    }

    @Test
    internal fun `change email unknown user`() {
        port.changeEmail(unknownId, "someone_other@james.de").expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )
    }

    @Test
    internal fun `change email duplicate mail`() {
        port.changeEmail(existingId, "duplicate@james.de").expectError(
            code = UserErrorCodes.EMAIL_EXISTS,
            details = "duplicate@james.de",
        )
    }

    @Test
    internal fun `change name`() {
        port.changeName(existingId, "James").expectSuccess()
        verifySequence {
            queryPersistence.getOrError(existingId)
            commandPersistence.upsert(withArg {
                val user = this.actual as User
                assertThat(user.id).isEqualTo(existingId)
                assertThat(user.email).isEqualTo("existing@james.de")
                assertThat(user.name).isEqualTo("James")
            })
        }
    }

    @Test
    internal fun `change name invalid`() {
        port.changeName(existingId, "").expectError(
            code = UserErrorCodes.NAME_BLANK,
            details = null
        )
    }

    @Test
    internal fun `existing user`() {
        port.deleteUser(existingId).expectError(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }

    @Test
    internal fun `unknown user`() {
        port.deleteUser(unknownId).expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )
    }
}
