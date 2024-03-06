package de.chrgroth.james.runtime.http4k

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import mu.KLogging
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.template.TemplateRenderer

data class ReleasenotesEntry(
    val version: String?,
    val date: String?,
    val highlights: List<String>,
    val breaking: List<String>,
    val features: List<String>,
    val bugfixes: List<String>,
)

private const val PATH = "/releasenotes.yaml"

class ReleasenotesService {

    private val releasenotes: List<ReleasenotesEntry>

    init {
        logger.info { "Parsing releasenotes at $PATH..." }

        val stream = javaClass.getResourceAsStream(PATH)
            ?: error("Unable to parse releasenotes, stream is null!")

        releasenotes = Yaml.default.parseToYamlNode(stream)
            .yamlList.items.map { yamlNode ->
                yamlNode.yamlMap.run {
                    ReleasenotesEntry(
                        version = getStringScalar("version"),
                        date = getStringScalar("date"),
                        highlights = getListOfStrings("highlights"),
                        breaking = getListOfStrings("breaking"),
                        features = getListOfStrings("features"),
                        bugfixes = getListOfStrings("bugfixes"),
                    )
                }
            }
    }

    private fun YamlMap.getStringScalar(propertyName: String): String? =
        get<YamlScalar>(propertyName)?.content

    private fun YamlMap.getListOfStrings(propertyName: String): List<String> =
        get<YamlList>(propertyName)?.items?.map { it.yamlScalar.content } ?: emptyList()

    data class ReleasenotesViewModel(val releasenotes: List<ReleasenotesEntry>) : NamedViewModel

    fun createRoutes(templates: TemplateRenderer): RoutingHttpHandler =
        routes(
            "/releasenotes" bind Method.GET to {
                Response(Status.OK).body(
                    templates(
                        ReleasenotesViewModel(releasenotes)
                    )
                )
            },
        )

    companion object : KLogging()
}
