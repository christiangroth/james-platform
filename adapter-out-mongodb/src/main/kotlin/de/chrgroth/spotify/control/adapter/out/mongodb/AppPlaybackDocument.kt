package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "app_playback")
class AppPlaybackDocument {

  /**
   * Single key: "${spotifyUserId}:${playedAt.toEpochMilli()}"
   * A user can only play one track at any given moment, so the combination of
   * user + playedAt is a natural unique identifier for a playback event.
   */
  @BsonId
  lateinit var id: String
  lateinit var spotifyUserId: String
  lateinit var playedAt: Instant
  lateinit var trackId: String
  var secondsPlayed: Long = 0L
}
