package de.chrgroth.james.runtime.http4k

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import de.chrgroth.james.app.App

data class ReleasenotesEntry(
    val version: String,
    val date: String,
    val highlights: List<String>,
    val breaking: List<String>,
    val features: List<String>,
    val bugfixes: List<String>,
)

private const val PATH = "/releasenotes.yaml"

class ReleasenotesService {

    val releasenotes: List<ReleasenotesEntry>

    init {
        val stream = App.javaClass.getResourceAsStream(PATH)
        releasenotes = if (stream == null) {
            error("Unable to parse releasenotes, stream is null!")
        } else {
            Yaml.default.parseToYamlNode(stream).yamlList.items.map {
                ReleasenotesEntry(
                    version = it.yamlMap.get<YamlScalar>("version")?.content ?: "",
                    date = it.yamlMap.get<YamlScalar>("date")?.content ?: "",
                    highlights = it.yamlMap.get<YamlList>("highlights")?.items?.map { it.yamlScalar.content }
                        ?: emptyList(),
                    breaking = it.yamlMap.get<YamlList>("breaking")?.items?.map { it.yamlScalar.content }
                        ?: emptyList(),
                    features = it.yamlMap.get<YamlList>("features")?.items?.map { it.yamlScalar.content }
                        ?: emptyList(),
                    bugfixes = it.yamlMap.get<YamlList>("bugfixes")?.items?.map { it.yamlScalar.content }
                        ?: emptyList(),
                )
            }
        }
    }
}
