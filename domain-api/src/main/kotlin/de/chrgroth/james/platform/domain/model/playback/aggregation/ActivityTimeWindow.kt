package de.chrgroth.james.platform.domain.model.playback.aggregation

private const val HOUR_00 = 0
private const val HOUR_06 = 6
private const val HOUR_12 = 12
private const val HOUR_18 = 18

enum class ActivityTimeWindow(val fromHour: Int) {
  H00_06(HOUR_00),
  H06_12(HOUR_06),
  H12_18(HOUR_12),
  H18_24(HOUR_18),
  ;

  companion object {
    fun fromHour(hour: Int): ActivityTimeWindow = when {
      hour < HOUR_06 -> H00_06
      hour < HOUR_12 -> H06_12
      hour < HOUR_18 -> H12_18
      else -> H18_24
    }
  }
}
