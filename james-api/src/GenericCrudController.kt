package de.chrgroth

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.*
import kotlin.reflect.KClass

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

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid payload?
    private suspend fun respondToUpsert(call: ApplicationCall, paramItemId: Id?) {
        val existingItem = get(call, paramItemId)
        val payloadItem = call.receiveOrNull(type)
        if (payloadItem == null) {
            call.fail(HttpStatusCode.BadRequest, "No item payload given!")
        } else if (existingItem != null && getId(existingItem) != getId(payloadItem)) {
            call.fail(HttpStatusCode.BadRequest, "Path and body id value do not match!")
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
                // TODO need more error details!
                call.respond(HttpStatusCode.InternalServerError, "Unable to store item!")
            }
        }
    }

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid payload?
    private suspend fun respondToDelete(call: ApplicationCall, paramItemId: Id?) {
        val item = get(call, paramItemId)
        if (item == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val deleted = remove(call, item)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            // TODO need more error details!
            call.respond(HttpStatusCode.InternalServerError, "Unable to delete item!")
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
