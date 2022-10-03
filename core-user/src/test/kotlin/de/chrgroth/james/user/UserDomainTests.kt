package de.chrgroth.james.user

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import io.mockk.MockKVerificationScope
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class UserDomainTests {

    private val existingUser = User.create(email = "existing@james.de", name = "Existing").expectSuccess()
    private val existingId = existingUser.id
    private val unknownId = UUID.randomUUID()

    private lateinit var queryPersistence: UserQueryPersistencePort
    private lateinit var commandPersistence: UserCommandPersistencePort
    private lateinit var userAdminUseCases: UserAdminUseCases
    private lateinit var userSelfServiceUseCases: UserSelfServiceUseCases

    @BeforeEach
    internal fun initialize() {
        queryPersistence = mockk<UserQueryPersistencePort>().also {
            every { it.getOrError(existingId) } returns (Result(existingUser))
            every { it.getOrError(unknownId) } returns (Error(UserErrorCodes.NOT_FOUND, unknownId.toString()))

            every { it.getByEmail(any()) } returns (Result(null))

            val duplicateUser = User.create(email = "duplicate@james.de", name = "Duplicate").expectSuccess()
            every { it.getByEmail(duplicateUser.email) } returns (Result(duplicateUser))
        }

        commandPersistence = mockk<UserCommandPersistencePort>().also {
            every { it.upsert(any()) } answers { Result(this.args[0] as User) }
        }

        userAdminUseCases = UserAdminUseCasesService(queryPersistence, commandPersistence)
        userSelfServiceUseCases = UserSelfServiceUseCasesService(queryPersistence, commandPersistence)
    }

    @Test
    internal fun `valid user`() {
        fun User.assertions() {
            assertThat(id).isNotIn(existingId, unknownId)
            assertThat(email).isEqualTo("someone@james.de")
            assertThat(name).isEqualTo("Some Name")
        }

        userAdminUseCases.registerUser("  someone@james.de   ", " Some Name   \t").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getByEmail("someone@james.de")
            commandPersistence.upsert(withArg {
                (this.actual as User).assertions()
            })
        }
    }

    @Test
    internal fun `duplicate email`() {
        userAdminUseCases.registerUser("duplicate@james.de", "Joe").expectError(
            code = UserErrorCodes.EMAIL_EXISTS,
            details = "duplicate@james.de",
        )
        verifyMocks {
            queryPersistence.getByEmail("duplicate@james.de")
        }
    }

    @Test
    internal fun `blank email`() {
        userAdminUseCases.registerUser("", "Joe").expectError(
            code = UserErrorCodes.EMAIL_BLANK,
            details = null,
        )
        verifyMocks()
    }

    @Test
    internal fun `invalid email`() {
        userAdminUseCases.registerUser("someone_james.de", "Joe").expectError(
            code = UserErrorCodes.EMAIL_INVALID,
            details = "'someone_james.de' does not match .+@.+\\..+",
        )
        verifyMocks()
    }

    @Test
    internal fun `blank name`() {
        userAdminUseCases.registerUser("someone@james.de", " ").expectError(
            code = UserErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks()
    }

    @Test
    internal fun `change email`() {
        fun User.assertions() {
            assertThat(id).isEqualTo(existingId)
            assertThat(email).isEqualTo("someone_new@james.de")
            assertThat(name).isEqualTo("Existing")
        }

        userSelfServiceUseCases.changeEmail(existingId, "someone_new@james.de").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getOrError(existingId)
            queryPersistence.getByEmail("someone_new@james.de")
            commandPersistence.upsert(withArg {
                (this.actual as User).assertions()
            })
        }
    }

    @Test
    internal fun `change email unknown user`() {
        userSelfServiceUseCases.changeEmail(unknownId, "someone_other@james.de").expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    @Test
    internal fun `change email duplicate mail`() {
        userSelfServiceUseCases.changeEmail(existingId, "duplicate@james.de").expectError(
            code = UserErrorCodes.EMAIL_EXISTS,
            details = "duplicate@james.de",
        )

        verifyMocks {
            queryPersistence.getOrError(existingId)
            queryPersistence.getByEmail("duplicate@james.de")
        }
    }

    @Test
    internal fun `change name`() {
        fun User.assertions() {
            assertThat(id).isEqualTo(existingId)
            assertThat(email).isEqualTo("existing@james.de")
            assertThat(name).isEqualTo("James")
        }

        userSelfServiceUseCases.changeName(existingId, "James").expectSuccess().assertions()
        verifyMocks {
            queryPersistence.getOrError(existingId)
            commandPersistence.upsert(withArg {
                (this.actual as User).assertions()
            })
        }
    }

    @Test
    internal fun `change name invalid`() {
        userSelfServiceUseCases.changeName(existingId, "").expectError(
            code = UserErrorCodes.NAME_BLANK,
            details = null
        )

        verifyMocks {
            queryPersistence.getOrError(existingId)
        }
    }

    @Test
    internal fun `existing user`() {
        userAdminUseCases.deleteUser(existingId).expectError(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )

        verifyMocks {
            queryPersistence.getOrError(existingId)
        }
    }

    @Test
    internal fun `unknown user`() {
        userAdminUseCases.deleteUser(unknownId).expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = unknownId.toString(),
        )

        verifyMocks {
            queryPersistence.getOrError(unknownId)
        }
    }

    private fun verifyMocks(verifyBlock: (MockKVerificationScope.() -> Unit)? = null) {
        if (verifyBlock != null) {
            verifySequence(inverse = false, verifyBlock = verifyBlock)
        }
        confirmVerified(queryPersistence)
        confirmVerified(commandPersistence)
    }
}
