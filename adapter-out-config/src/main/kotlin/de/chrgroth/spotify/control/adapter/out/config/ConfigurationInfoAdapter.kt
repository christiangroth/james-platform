package de.chrgroth.spotify.control.adapter.out.config

import de.chrgroth.spotify.control.domain.model.infra.ConfigEntry
import de.chrgroth.spotify.control.domain.model.infra.ConfigurationStats
import de.chrgroth.spotify.control.domain.port.out.infra.ConfigurationInfoPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty

private const val MASKED_VALUE = "***"
private const val ERROR_VALUE = "ERROR"
private const val MASKED_CONFIG_KEYS_PROPERTY = "app.health.masked-config-keys"
private const val MASKED_ENV_KEYS_PROPERTY = "app.health.masked-env-keys"

@ApplicationScoped
@Suppress("Unused")
class ConfigurationInfoAdapter(
  @param:ConfigProperty(name = MASKED_CONFIG_KEYS_PROPERTY)
  private val configKeyMasks: List<String>,
  @param:ConfigProperty(name = MASKED_ENV_KEYS_PROPERTY)
  private val envKeyMasks: List<String>,
) : ConfigurationInfoPort {
  private val cachedStats: ConfigurationStats by lazy { buildConfigurationStats() }

  override fun getConfigurationStats(): ConfigurationStats = cachedStats

  private fun buildConfigurationStats(): ConfigurationStats {
    val config = ConfigProvider.getConfig()
    val configEntries = config.propertyNames
      .filter { key -> !key.startsWith("%dev.") && !key.startsWith("%test.") }
      .filter { key -> !ENV_KEY_PATTERN.matches(key) }
      .filter { key -> key != MASKED_CONFIG_KEYS_PROPERTY && key != MASKED_ENV_KEYS_PROPERTY }
      .sorted()
      .map { key ->
        val value = if (shouldMaskConfigKey(key)) {
          MASKED_VALUE
        } else {
          runCatching { config.getOptionalValue(key, String::class.java).orElse("") }
            .getOrElse { e ->
              logger.warn(e) { "Failed to read config value for key '$key'" }
              ERROR_VALUE
            }
        }
        ConfigEntry(key, value)
      }
    val envEntries = System.getenv()
      .entries
      .sortedBy { it.key }
      .map { (key, value) ->
        ConfigEntry(key, if (envKeyMasks.contains(key)) MASKED_VALUE else value)
      }
    return ConfigurationStats(configEntries, envEntries)
  }

  private fun shouldMaskConfigKey(key: String): Boolean {
    if (configKeyMasks.contains(key)) return true
    val keyWithoutProfile = if (key.startsWith("%")) key.substringAfter(".") else key
    return configKeyMasks.contains(keyWithoutProfile)
  }

  companion object : KLogging() {
    private val ENV_KEY_PATTERN = Regex("^[A-Z][A-Z0-9_]*$")
  }
}
