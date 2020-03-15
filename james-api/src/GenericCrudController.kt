package de.chrgroth

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.*
import kotlin.reflect.KClass

// TODO need consumes/produces information??
// TODO returns 404 instead of 405 if route exist but with different method
abstract class GenericCrudController<Type : Any, Id : Any>(private val type: KClass<Type>, pathPrefix: String) {
    private val itemIdPathParameterName = "itemId"
    private val pathAllItems = "$API_PREFIX/$pathPrefix"
    private val pathSingleItem = "$API_PREFIX/$pathPrefix/{$itemIdPathParameterName}"

    open fun routes(routing: Routing) = with(routing) {
        get(pathAllItems) { respondToList(call) }
        post(pathAllItems) { respondToUpsert(call, resolveItemIdParameter(call)) }

        get(pathSingleItem) { respondToGet(call, resolveItemIdParameter(call)) }
        put(pathSingleItem) { respondToUpsert(call, resolveItemIdParameter(call)) }
        delete(pathSingleItem) { respondToDelete(call, resolveItemIdParameter(call)) }
    }

    private fun resolveItemIdParameter(call: ApplicationCall) =
        convertItemIdParameter(call.parameters[itemIdPathParameterName])

    private suspend fun respondToList(call: ApplicationCall) = call.respond(list(call))

    private suspend fun respondToGet(call: ApplicationCall, paramItemId: Id?) {
        val item = get(call, paramItemId)
        if (item != null) {
            call.respond(item)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid app?
    private suspend fun respondToUpsert(call: ApplicationCall, paramItemId: Id?) {
        try {
            val existingItem = get(call, paramItemId)
            val payloadItem = call.receiveOrNull(type)
            if (payloadItem == null) {
                // TODO generic message!
                call.fail(HttpStatusCode.BadRequest, "No item given!")
            } else if (existingItem != null && getId(existingItem) != getId(payloadItem)) {
                // TODO generic message!
                call.fail(HttpStatusCode.BadRequest, "id value does not match!")
            } else {
                val updatedPayloadItem = if (getId(payloadItem) == null && paramItemId != null)
                    createCopyWithId(payloadItem, paramItemId)
                else
                    payloadItem

                val persistedItem = upsert(call, updatedPayloadItem)
                if (persistedItem != null) {
                    if (existingItem != null) {
                        call.respond(HttpStatusCode.OK, persistedItem)
                    } else {
                        call.respond(HttpStatusCode.Created, persistedItem)
                    }
                } else {
                    // TODO generic message!
                    // TODO not sure if this should be a 5xx
                    call.respond(HttpStatusCode.BadRequest, "Unable to store given item!")
                }
            }
        } catch (e: JsonProcessingException) {
            call.fail(
                HttpStatusCode.BadRequest,
                "Unable to deserialize app version: ${e.javaClass.simpleName}",
                e.message
            )
        }
    }

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid app?
    private suspend fun respondToDelete(call: ApplicationCall, paramItemId: Id?) {
        val item = get(call, paramItemId)
        if (item == null) {
            // TODO generic message!
            // TODO fail or return NoContent?
            call.respond(HttpStatusCode.NoContent)
            return
        }

        val deleted = remove(call, item)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            // TODO generic message!
            // TODO not sure if this should be a 5xx
            call.respond(HttpStatusCode.BadRequest, "Unable to remove item!")
        }
    }

    abstract fun convertItemIdParameter(paramValue: String?): Id?

    abstract suspend fun list(call: ApplicationCall): List<Type>
    abstract suspend fun get(call: ApplicationCall, id: Id?): Type?
    abstract suspend fun getId(item: Type): Id?
    abstract suspend fun createCopyWithId(item: Type, id: Id): Type
    abstract suspend fun upsert(call: ApplicationCall, item: Type): Type?
    abstract suspend fun remove(call: ApplicationCall, item: Type): Boolean
}
