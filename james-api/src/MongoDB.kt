package de.chrgroth

import com.mongodb.*
import com.mongodb.internal.connection.ServerAddressHelper
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
            .connectTimeout(10000)
            .build()
    )

    private val apps = mongoClient.getDatabase("james-api").getCollection<App>("apps")

    fun listApps() = apps.find().toList()

    fun get(id: Id<App>): App? {
        return apps.findOneById(id)
    }

    fun upsert(app: App): App? {
        try {
            apps.save(app)
            return app
        } catch (e: MongoWriteException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoWriteConcernException) {
            println(e.message)
            e.printStackTrace()
        } catch (e: MongoException) {
            println(e.message)
            e.printStackTrace()
        }
        return null
    }

    fun delete(app: App): Boolean {
        try {
            var result = apps.deleteOne(App::_id eq app._id)
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