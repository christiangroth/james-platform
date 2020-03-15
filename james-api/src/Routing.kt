package de.chrgroth

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond

const val API_PREFIX = "/api"

data class ResponseError(val code: Int, val message: String, val details: String? = null)
suspend fun ApplicationCall.fail(code: HttpStatusCode, message: String, details: String? = null) =
    respond(code, ResponseError(code.value, message, details))
