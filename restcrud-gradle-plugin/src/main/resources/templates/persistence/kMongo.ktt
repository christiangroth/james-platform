// TODO paging and stuff
// TODO logging & error handling
object MongoDB {

    // TODO get values from confog!!
    private val mongoClient = KMongo.createClient(
        ServerAddressHelper.createServerAddress("localhost", 27017),
        listOf(MongoCredential.createCredential("james", "admin", "semaj".toCharArray())),
        MongoClientOptions.builder()
            .applicationName("james-api")
            // TODO timeout does not work
            .connectTimeout(10000)
            .build()
        )

{methodsSource}

    private fun<T: Any> genericUpsert(collection: MongoCollection<T>, item: T): T? {
        try {
            collection.save(item)
            return item
        } catch (e: MongoWriteException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoWriteConcernException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoCommandException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoException) {
            println(e.message)
            e.printStackTrace()
        }
        return null
    }

    private fun<T: Any> genericDelete(collection: MongoCollection<T>, filter: Bson): Boolean {
        try {
            var result = collection.deleteOne(filter)
            return result.deletedCount > 0
        } catch (e: MongoWriteException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoWriteConcernException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoCommandException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoException) {
            println(e.message)
            e.printStackTrace()
        }
        return false
    }
}
