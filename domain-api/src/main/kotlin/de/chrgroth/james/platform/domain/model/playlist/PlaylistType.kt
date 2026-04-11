package de.chrgroth.james.platform.domain.model.playlist

enum class PlaylistType {
  ALL,
  YEAR,
  SINGULARITY,
  UNKNOWN,
  ;

  companion object {
    const val SINGULARITY_PLAYLIST_NAME = "End of the Road"
  }
}
