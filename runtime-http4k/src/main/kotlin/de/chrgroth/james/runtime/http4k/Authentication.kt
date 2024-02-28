package de.chrgroth.james.runtime.http4k

import com.lambdaworks.crypto.SCryptUtil
import org.http4k.core.Body
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.SameSite
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.invalidateCookie
import org.http4k.core.cookie.replaceCookie
import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.string
import org.http4k.lens.webForm
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.template.TemplateRenderer
import java.time.temporal.ChronoUnit


private const val COOKIE_NAME = "jwt"

sealed class AuthFilter(private val authService: AuthService) : Filter {

    // TODO carry original url to jump to after login
    override fun invoke(next: HttpHandler): HttpHandler = {
        val cookie = it.cookie(COOKIE_NAME)
        if (cookie == null) {
            onNullCookie()
        } else {
            val tokenVerified = authService.verify(cookie.value)
            if (tokenVerified) {
                next(it)
            } else {
                onUnverifiedToken()
            }
        }
    }

    abstract fun onNullCookie(): Response
    abstract fun onUnverifiedToken(): Response
}

class ApiAuthFilter(authService: AuthService) : AuthFilter(authService) {
    override fun onNullCookie(): Response = onUnverifiedToken()
    override fun onUnverifiedToken(): Response =
        Response(Status.UNAUTHORIZED)
}

class TemplateAuthFilter(authService: AuthService) : AuthFilter(authService) {
    override fun onNullCookie(): Response = onUnverifiedToken()
    override fun onUnverifiedToken(): Response =
        Response(Status.FOUND).header("Location", "/api/authentication")
}

class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
) {

    fun createRoues(templates: TemplateRenderer): RoutingHttpHandler =
        routes(
            "api" bind routes(
                "authentication" bind Method.GET to {
                    Response(Status.OK).body(templates(LoginViewModel())).header("content-type", "text/html")
                },
                "authentication" bind Method.POST to {
                    val idField = FormField.string().optional("id", "user identifier")
                    val passwordField = FormField.string().optional("password", "password")
                    val loginForm = Body.webForm(Validator.Strict, idField, passwordField).toLens().extract(it)

                    val id = idField.extract(loginForm) ?: ""
                    val password = passwordField.extract(loginForm) ?: ""

                    val token = login(id, password)
                    if (token != null) {
                        Response(Status.FOUND).header("Location", "/").replaceCookie(createJwtCookie(token))
                    } else {
                        Response(Status.UNAUTHORIZED).body(templates(LoginViewModel()))
                            .header("content-type", "text/html")
                            .invalidateCookie(COOKIE_NAME)
                    }
                },
                "authentication/logout" bind Method.GET to {
                    Response(Status.TEMPORARY_REDIRECT).header("Location", "/api/authentication")
                        .replaceCookie(createJwtCookie())
                },
            )
        )

    fun login(id: String, password: String): String? {
        if (!userRepository.exists(id)) {
            return null
        }

        // TODO faked!
        if (id != password) {
            return null
        }

        // TODO load hash from storage
        val hashedPassword = SCryptUtil.scrypt(password, 16384, 8, 1)
        if (!SCryptUtil.check(password, hashedPassword)) {
            return null
        }

        return jwtService.create(id, 2, ChronoUnit.DAYS)
    }

    fun verify(token: String): Boolean =
        userRepository.exists(jwtService.verify(token) ?: "")

    private fun createJwtCookie(value: String? = null) = Cookie(
        name = COOKIE_NAME,
        value = value ?: "DELETED",
        // TODO domain depends on dev mode
        domain = "localhost",
        path = "/",
        // TODO secure depends on dev mode
        secure = false,
        httpOnly = true,
        sameSite = SameSite.Lax,
    )
}
