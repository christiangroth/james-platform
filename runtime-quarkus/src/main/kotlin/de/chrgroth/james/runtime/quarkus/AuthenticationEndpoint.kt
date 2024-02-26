package de.chrgroth.james.runtime.quarkus

import com.charleskorn.kaml.YamlNode
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Request
import mu.KLogging

@Path("/api/authentication")
@Suppress("unused")
class AuthenticationEndpoint {

    private lateinit var releasenotes: YamlNode

    data class LoginData(
        @FormParam("email")
        val email: String,

        @FormParam("password")
        val password: String,
    )

    @POST
    @Path("/login")
    fun login(
        request: Request
    ) {
    }

    companion object : KLogging()
}