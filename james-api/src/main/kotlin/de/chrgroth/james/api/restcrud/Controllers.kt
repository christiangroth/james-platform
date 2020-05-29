package de.chrgroth.james.api.restcrud

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.*
import kotlin.reflect.KClass

import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.id.toId

object AppController: GenericCrudController<App, Id<App>>(App::class, "apps/") {
    override suspend fun list(call: ApplicationCall) = MongoDB.listApps()
    override suspend fun get(call: ApplicationCall, id: Id<App>?) = if (id != null) MongoDB.getApp(id) else null
    override suspend fun getId(item: App) = item._id
    override suspend fun createCopyWithId(item: App, id: Id<App>) = item.copy(_id = id)
    override suspend fun upsert(call: ApplicationCall, item: App) = MongoDB.upsert(item)
    override suspend fun remove(call: ApplicationCall, item: App) = MongoDB.delete(item)
}

object AppVersionController: GenericCrudController<AppVersion, Id<AppVersion>>(AppVersion::class, "apps/{appId}/versions/") {
    override suspend fun list(call: ApplicationCall) = MongoDB.listAppVersions()
    override suspend fun get(call: ApplicationCall, id: Id<AppVersion>?) = if (id != null) MongoDB.getAppVersion(id) else null
    override suspend fun getId(item: AppVersion) = item._id
    override suspend fun createCopyWithId(item: AppVersion, id: Id<AppVersion>) = item.copy(_id = id)
    override suspend fun upsert(call: ApplicationCall, item: AppVersion) = MongoDB.upsert(item)
    override suspend fun remove(call: ApplicationCall, item: AppVersion) = MongoDB.delete(item)
}

const val API_PREFIX = "/api"

data class ResponseError(val code: Int, val message: String, val details: String? = null)
suspend fun ApplicationCall.fail(code: HttpStatusCode, message: String, details: String? = null) =
    respond(code, ResponseError(code.value, message, details))

// TODO i18n -> load correct value from entity for localized values
// TODO returns 404 instead of 405 if route exist but with different method
abstract class GenericCrudController<Type : Any, Id : Any>(private val type: KClass<Type>, pathPrefix: String) {
    private val itemIdPathParameterName = "itemId"
    private val pathAllItems = "$API_PREFIX/$pathPrefix"
    private val pathSingleItem = "$API_PREFIX/$pathPrefix/{$itemIdPathParameterName}"

    open fun routes(routing: Routing) = with(routing) {
        get(pathAllItems) { respondToList(call) }
        post(pathAllItems) { respondToUpsert(call, resolveItemIdParameter(call)) }

        get(pathSingleItem) { executeWithExistingItemId(call) { respondToGet(call, it) } }
        put(pathSingleItem) { executeWithExistingItemId(call) { respondToUpsert(call, it) } }
        delete(pathSingleItem) { executeWithExistingItemId(call) { respondToDelete(call, it) } }
    }

    private suspend fun executeWithExistingItemId(call: ApplicationCall, action: suspend (Id) -> Unit) {
        val itemIdParameter = getItemIdParameter(call)
        if (itemIdParameter == null || itemIdParameter.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Path id parameter must be given!")
        }

        val paramItemId = resolveItemIdParameter(call)
        println("${call.parameters[itemIdPathParameterName]} -> $paramItemId")
        if (paramItemId != null) {
            action(paramItemId)
        } else {
            call.respond(HttpStatusCode.BadRequest, "Path id parameter cannot be converted!")
        }
    }

    private fun resolveItemIdParameter(call: ApplicationCall) = convertItemIdParameter(getItemIdParameter(call))
    private fun convertItemIdParameter(paramValue: String?): Id? {
        return try {
            ObjectId(paramValue).toId<Type>() as Id
        } catch (e: IllegalArgumentException) {
            // TODO logging
            println("Failed to convert $paramValue to Id: ${e.message}")
            null
        }
    }

    private fun getItemIdParameter(call: ApplicationCall) = call.parameters[itemIdPathParameterName]

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
        val payloadItem = call.receiveOrNull(type)
        println("payloadItem: $payloadItem")
        if (payloadItem == null) {
            call.fail(HttpStatusCode.BadRequest, "No item payload given!")
        } else if (paramItemId != null && getId(payloadItem) != paramItemId) {
            println("getId(payloadItem): ${getId(payloadItem)}")
            println("paramItemId: $paramItemId")
            call.fail(HttpStatusCode.BadRequest, "Path parameter id must match payload id!!")
        } else {
            val existingItem = get(call, paramItemId)
            println("existingItem: $existingItem")
            if (existingItem != null && getId(existingItem) != getId(payloadItem)) {
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

    abstract suspend fun list(call: ApplicationCall): List<Type>
    abstract suspend fun get(call: ApplicationCall, id: Id?): Type?
    abstract suspend fun getId(item: Type): Id?
    abstract suspend fun createCopyWithId(item: Type, id: Id): Type
    abstract suspend fun upsert(call: ApplicationCall, item: Type): Type?
    abstract suspend fun remove(call: ApplicationCall, item: Type): Boolean
}
