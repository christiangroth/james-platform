package de.chrgroth.james.runtime.http4k


// TODO connect to domain
interface UserRepository {
    fun exists(id: String): Boolean
}
