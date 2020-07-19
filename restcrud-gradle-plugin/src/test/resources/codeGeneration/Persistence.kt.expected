package de.foo.bar

import com.mongodb.*
import com.mongodb.client.MongoCollection
import com.mongodb.internal.connection.ServerAddressHelper
import org.bson.conversions.Bson
import org.litote.kmongo.*
import kotlin.collections.toList

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

    private val Foos = mongoClient.getDatabase("james-api").getCollection<Foo>("Foos")
    fun listFoos() = Foos.find().toList()
    fun getFoo(id: Id<Foo>) = Foos.findOneById(id)
    fun upsert(item: Foo) = genericUpsert(Foos, item)
    fun delete(item: Foo) = genericDelete(Foos, Foo::_id eq item._id)

    private val Bars = mongoClient.getDatabase("james-api").getCollection<Bar>("Bars")
    fun listBars() = Bars.find().toList()
    fun getBar(id: Id<Bar>) = Bars.findOneById(id)
    fun upsert(item: Bar) = genericUpsert(Bars, item)
    fun delete(item: Bar) = genericDelete(Bars, Bar::_id eq item._id)


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
