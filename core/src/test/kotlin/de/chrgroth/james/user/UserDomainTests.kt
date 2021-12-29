package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 assert values cleaned up on instance creation
// TODO #25 test if persistence returns Error(s)
// TODO #25 assert calls to persistence are correct!
// TODO #25 assert Errors global representation

private val data = mutableSetOf<User>()

private val query = object : UserQueryPersistencePort {
    override fun get(id: UUID): Maybe<User?> = Result(data.firstOrNull { it.id == id })

    override fun getOrError(id: UUID) = when (val user = data.firstOrNull { it.id == id }) {
        null -> Error(code = UserErrorCodes.NOT_FOUND, details = id.toString())
        else -> Result(user)
    }

    override fun getByEmail(email: String) = Result(data.firstOrNull { it.email == email })

    override fun find() = Result(data.toSet())
}

private val command = object : UserCommandPersistencePort {
    override fun upsert(item: User): Maybe<User> {
        data.removeIf { it.id == item.id }
        return if (data.add(item)) {
            Result(item)
        } else {
            Error(code = UserErrorCodes.PERSISTENCE_ERROR, details = "unable to store user")
        }
    }

    override fun delete(id: UUID): Maybe<Unit> = if (data.removeIf { it.id == id }) {
        Result(Unit)
    } else {
        Error(code = UserErrorCodes.NOT_FOUND, details = id.toString())
    }
}

private val adapter = UserCommandAdapter(query, command)

class UserRegistrationTests {

    @BeforeEach
    internal fun prepare() {
        data.clear()
    }

    @Test
    internal fun `valid user`() {
        adapter.registerUser("someone@james.de", "Joe").expectSuccess()
    }

    @Test
    internal fun `duplicate user email`() {
        adapter.registerUser("someone@james.de", "Joe").expectSuccess()
        adapter.registerUser("someone@james.de", "Joe").expectError(
            code = UserErrorCodes.EMAIL_EXISTS,
            details = "someone@james.de",
        )
    }

    @Test
    internal fun `duplicate invalid email`() {
        adapter.registerUser("someone_james.de", "Joe").expectError(
            code = UserErrorCodes.EMAIL_INVALID,
            details = "'someone_james.de' does not match .+@.+\\..+",
        )
    }

    @Test
    internal fun `duplicate invalid name`() {
        adapter.registerUser("someone@james.de", " ").expectError(
            code = UserErrorCodes.NAME_BLANK,
            details = null,
        )
    }
}

class UserLifecycleTests {

    private lateinit var user: User

    @BeforeEach
    internal fun prepare() {
        data.clear()
        user = adapter.registerUser("someone@james.de", "Joe").expectSuccess()
    }

    @Test
    internal fun `change email`() {
        adapter.changeEmail(user.id, "someone_new@james.de").expectSuccess()
    }

    @Test
    internal fun `change email unknown user`() {
        UUID.randomUUID().also {
            adapter.changeEmail(it, "someone_new@james.de").expectError(
                code = UserErrorCodes.NOT_FOUND,
                details = it.toString(),
            )
        }
    }

    @Test
    internal fun `change email dulicate mail`() {
        adapter.registerUser("someone_new@james.de", "Joe").expectSuccess()
        adapter.changeEmail(user.id, "someone_new@james.de").expectError(
            code = UserErrorCodes.EMAIL_EXISTS,
            details = "someone_new@james.de",
        )
    }

    @Test
    internal fun `change name`() {
        adapter.changeName(user.id, "James").expectSuccess()
    }

    @Test
    internal fun `change name invalid`() {
        adapter.changeName(user.id, "").expectError(
            code = UserErrorCodes.NAME_BLANK,
            details = null
        )
    }
}

class UserDeletionTests {

    @BeforeEach
    internal fun prepare() {
        data.clear()
    }

    @Test
    internal fun `valid user`() {
        val user = adapter.registerUser("someone@james.de", "Joe").expectSuccess()
        adapter.deleteUser(user.id).expectError(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }
}
