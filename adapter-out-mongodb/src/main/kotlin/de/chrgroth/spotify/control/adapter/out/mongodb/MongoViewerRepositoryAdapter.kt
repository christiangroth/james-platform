package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilter
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerFilterOperator
import de.chrgroth.spotify.control.domain.port.out.infra.MongoViewerRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.BsonDocument
import org.bson.conversions.Bson
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.regex.Pattern

@ApplicationScoped
@Suppress("Unused")
class MongoViewerRepositoryAdapter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : MongoViewerRepositoryPort {

  private val jsonWriterSettings = JsonWriterSettings.builder()
    .indent(true)
    .outputMode(JsonMode.RELAXED)
    .build()

  override fun listCollections(): List<String> =
    mongoClient.getDatabase(databaseName).listCollectionNames().toList().sorted()

  override fun sampleFieldNames(collection: String): List<String> {
    val coll = mongoClient.getDatabase(databaseName).getCollection(collection, BsonDocument::class.java)
    val fieldNames = mutableSetOf<String>()
    try {
      coll.find().limit(MAX_SAMPLE_DOCS).forEach { doc ->
        fieldNames.addAll(doc.keys)
      }
    } catch (e: MongoException) {
      logger.warn(e) { "Failed to sample fields from collection '$collection'" }
    }
    return fieldNames.sorted()
  }

  override fun countDocuments(collection: String, filters: List<MongoViewerFilter>): Long {
    val coll = mongoClient.getDatabase(databaseName).getCollection(collection)
    return try {
      coll.countDocuments(buildFilter(filters))
    } catch (e: MongoException) {
      logger.warn(e) { "Failed to count documents in collection '$collection'" }
      0L
    }
  }

  override fun queryDocuments(
    collection: String,
    filters: List<MongoViewerFilter>,
    sortField: String?,
    sortDesc: Boolean,
    skip: Long,
    limit: Int,
  ): List<String> {
    val coll = mongoClient.getDatabase(databaseName).getCollection(collection, BsonDocument::class.java)
    return try {
      var findIterable = coll.find(buildFilter(filters))
      if (!sortField.isNullOrBlank()) {
        findIterable = findIterable.sort(
          if (sortDesc) Sorts.descending(sortField) else Sorts.ascending(sortField),
        )
      }
      findIterable.skip(skip.toInt()).limit(limit).map { doc ->
        doc.toJson(jsonWriterSettings)
      }.toList()
    } catch (e: MongoException) {
      logger.warn(e) { "Failed to query documents from collection '$collection'" }
      emptyList()
    }
  }

  private fun buildFilter(filters: List<MongoViewerFilter>): Bson {
    val filterList = filters.mapNotNull { f ->
      when (f.operator) {
        MongoViewerFilterOperator.CONTAINS -> {
          if (f.value.isBlank()) null
          else Filters.regex(f.field, Pattern.compile(Pattern.quote(f.value), Pattern.CASE_INSENSITIVE))
        }
        MongoViewerFilterOperator.EQUALS -> {
          if (f.value.isBlank()) null
          else Filters.eq(f.field, f.value)
        }
        MongoViewerFilterOperator.IN -> {
          val values = f.value.split(",").map { it.trim() }.filter { it.isNotBlank() }
          if (values.isEmpty()) null else Filters.`in`(f.field, values)
        }
        MongoViewerFilterOperator.NOT_IN -> {
          val values = f.value.split(",").map { it.trim() }.filter { it.isNotBlank() }
          if (values.isEmpty()) null else Filters.nin(f.field, values)
        }
      }
    }
    return when (filterList.size) {
      0 -> Filters.empty()
      1 -> filterList.first()
      else -> Filters.and(filterList)
    }
  }

  companion object : KLogging() {
    private const val MAX_SAMPLE_DOCS = 10
  }
}
