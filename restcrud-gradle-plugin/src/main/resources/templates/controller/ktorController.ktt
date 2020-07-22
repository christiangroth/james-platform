object {type}Controller: GenericCrudController<{type}, Id<{type}>>({type}::class, "{endpoint}") {
    override suspend fun list(call: ApplicationCall) = MongoDB.list{type}s()
    override suspend fun get(call: ApplicationCall, id: Id<{type}>?) = if (id != null) MongoDB.get{type}(id) else null
    override suspend fun getId(item: {type}) = item._id
    override suspend fun createCopyWithId(item: {type}, id: Id<{type}>) = item.copy(_id = id)
    override suspend fun upsert(call: ApplicationCall, item: {type}) = MongoDB.upsert(item)
    override suspend fun remove(call: ApplicationCall, item: {type}) = MongoDB.delete(item)
}
