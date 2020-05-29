package de.chrgroth.james.api.restcrud

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
            .connectTimeout(10000)
            .build()
    )

    private val apps = mongoClient.getDatabase("james-api").getCollection<App>("apps")
    fun listApps() = apps.find().toList()
    fun getApp(id: Id<App>) = apps.findOneById(id)
    fun upsert(item: App) = genericUpsert(apps, item)
    fun delete(item: App) = genericDelete(apps, App::_id eq item._id)
    
    private val appVersions = mongoClient.getDatabase("james-api").getCollection<AppVersion>("appVersions")
    fun listAppVersions() = appVersions.find().toList()
    fun getAppVersion(id: Id<AppVersion>) = appVersions.findOneById(id)
    fun upsert(item: AppVersion) = genericUpsert(appVersions, item)
    fun delete(item: AppVersion) = genericDelete(appVersions, AppVersion::_id eq item._id)
    
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
