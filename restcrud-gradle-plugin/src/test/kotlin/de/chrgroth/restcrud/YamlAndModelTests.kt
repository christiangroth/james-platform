package de.chrgroth.restcrud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.IllegalStateException

class YamlParsingTests {

    @Test
    fun invalidYaml() {
        assertThrows(IllegalStateException::class.java) {
            println(YamlUtils.load(File("src/test/resources/invalid-yaml.yaml")))
        }
    }

    @Test
    fun fullModel() {
        val parseResult = YamlUtils.load(File("src/test/resources/full.yaml"))
        assertEquals("de.foo.bar", parseResult.codeGeneration.packageName)
        assertEquals("de/foo/bar", parseResult.codeGeneration.packagePath())
        assertEquals(ApplicationFramework.Ktor, parseResult.codeGeneration.applicationFramework)
        assertEquals(PersistenceFramework.KMongo, parseResult.codeGeneration.persistenceFramework)
        assertEquals(3, parseResult.datamodel.size)
        assertEquals("Foo", parseResult.datamodel[0].name)
        assertEquals("/api/foos", parseResult.datamodel[0].endpoint)
        assertEquals(2, parseResult.datamodel[0].attributes.size)
        assertEquals("id", parseResult.datamodel[0].attributes[0].name)
        assertEquals("Long", parseResult.datamodel[0].attributes[0].type)
        assertEquals(true, parseResult.datamodel[0].attributes[0].key)
        assertEquals(false, parseResult.datamodel[0].attributes[0].optional)
        assertEquals("description", parseResult.datamodel[0].attributes[1].name)
        assertEquals("String", parseResult.datamodel[0].attributes[1].type)
        assertEquals(false, parseResult.datamodel[0].attributes[1].key)
        assertEquals(true, parseResult.datamodel[0].attributes[1].optional)
        assertEquals("Bar", parseResult.datamodel[1].name)
        assertEquals("/api/bars", parseResult.datamodel[1].endpoint)
        assertEquals(2, parseResult.datamodel[1].attributes.size)
        assertEquals("id", parseResult.datamodel[1].attributes[0].name)
        assertEquals("Long", parseResult.datamodel[1].attributes[0].type)
        assertEquals(true, parseResult.datamodel[1].attributes[0].key)
        assertEquals(false, parseResult.datamodel[1].attributes[0].optional)
        assertEquals("version", parseResult.datamodel[1].attributes[1].name)
        assertEquals("Long", parseResult.datamodel[1].attributes[1].type)
        assertEquals(true, parseResult.datamodel[1].attributes[1].key)
        assertEquals(false, parseResult.datamodel[1].attributes[1].optional)
        assertEquals(2, parseResult.endpoints().size)
        assertEquals("Foo", parseResult.endpoints()[0].name)
        assertEquals("Bar", parseResult.endpoints()[1].name)
    }
}

class CodeGenerationValidationTests {

    @Test
    fun noPackageName() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml(null, emptyList()))
        assertEquals(listOf("Code generation package name '' must match pattern: [a-z]+([.][a-z]+)*"), result.errors)
    }

    @Test
    fun emptyPackageName() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml("  ", emptyList()))
        assertEquals(listOf("Code generation package name '' must match pattern: [a-z]+([.][a-z]+)*"), result.errors)
    }

    @Test
    fun invalidPackageName() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml("foo.bar123", emptyList()))
        assertEquals(listOf("Code generation package name 'foo.bar123' must match pattern: [a-z]+([.][a-z]+)*"), result.errors)
    }

    @Test
    fun valid() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml(" foo.bar.baz  ", emptyList()))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("foo.bar.baz", result.result.packageName)
        assertEquals(ApplicationFramework.Ktor, result.result.applicationFramework)
        assertEquals(PersistenceFramework.KMongo, result.result.persistenceFramework)
    }
}

class DatamodelValidationTests {

    private fun convert(datamodel: DatamodelYaml?) = convert(listOf(datamodel))[0]
    private fun convert(datamodel: List<DatamodelYaml?>?) =
        YamlUtils.convertDatamodel(ConfigurationYaml("foo.bar", datamodel))

    @Test
    fun noDatamodel() {
        val result = convert(null as List<DatamodelYaml?>?)
        assertEquals(listOf("No datamodel defined"), result.flatMap { it.errors })
    }

    @Test
    fun nullDatamodel() {
        val result = convert(null as DatamodelYaml?)
        assertEquals(listOf("Found null/empty datamodel"), result.errors)
    }

    @Test
    fun noName() {
        val result = convert(DatamodelYaml(null, "/foos", listOf("id Long")))
        assertEquals(listOf("Datamodel name '' must match pattern: [A-Z][a-zA-Z]*"), result.errors)
    }

    @Test
    fun emptyName() {
        val result = convert(DatamodelYaml("  ", "/foos", listOf("id Long")))
        assertEquals(listOf("Datamodel name '' must match pattern: [A-Z][a-zA-Z]*"), result.errors)
    }

    @Test
    fun invalidName() {
        listOf("Foo123", "Foo Bar", "foo").forEach { invalidName ->
            val result = convert(DatamodelYaml(invalidName, "/foos", listOf("id Long")))
            assertEquals(
                listOf("Datamodel name '$invalidName' must match pattern: [A-Z][a-zA-Z]*"),
                result.errors
            )
        }
    }

    @Test
    fun noEndpoint() {
        val result = convert(DatamodelYaml("Foo", null, listOf("id Long")))
        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun emptyEndpoint() {
        val result = convert(DatamodelYaml("Foo", "", listOf("id Long")))
        assertEquals(listOf("Datamodel endpoint '' (processed to '') must match pattern: [/][a-zA-Z]+([/][a-zA-Z]+)*"), result.errors)
    }

    @Test
    fun missingLeadingSlashIsAdded() {
        val result = convert(DatamodelYaml("Foo", "api/foos", listOf("id Long")))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun leadingDoubleSlashesAreCorrected() {
        val result = convert(DatamodelYaml("Foo", "//api/foos/", listOf("id Long")))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun doubleSlashesInBetweenAreCorrected() {
        val result = convert(DatamodelYaml("Foo", "/api//foos/", listOf("id Long")))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun trailingDoubleSlashesAreCorrected() {
        val result = convert(DatamodelYaml("Foo", "/api/foos//", listOf("id Long")))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun trailingSlashIsRemoved() {
        val result = convert(DatamodelYaml("Foo", "/api/foos/", listOf("id Long")))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun valid() {
        val result = convert(DatamodelYaml("Foo", "/api/foos", listOf("id Long")))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("Foo", result.result.name)
        assertEquals("/api/foos", result.result.endpoint)
    }
}

class DatamodelAttributeValidationTests {

    private fun convert(attribute: String?) = convert(listOf(attribute))[0]
    private fun convert(attributes: List<String?>?) =
        YamlUtils.convertAttributes(DatamodelYaml("Type", "/types", attributes))

    @Test
    fun noAttributes() {
        val result = convert(null as List<String?>?)
        assertEquals(listOf("Type: No attributes defined"), result.flatMap { it.errors })
    }

    @Test
    fun nullAttribute() {
        val result = convert(null as String?)
        assertEquals(listOf("Type: Found null/empty attribute"), result.errors)
    }

    @Test
    fun emptyAttribute() {
        val result = convert("  ")
        assertEquals(listOf("Type: Attribute '' does not match pattern: [key] name type[?]"), result.errors)
    }

    @Test
    fun onePartAttribute() {
        val result = convert("id")
        assertEquals(listOf("Type: Attribute 'id' does not match pattern: [key] name type[?]"), result.errors)
    }

    @Test
    fun fourPartAttribute() {
        val result = convert("key id optional Long")
        assertEquals(listOf("Type: Attribute 'key id optional Long' does not match pattern: [key] name type[?]"), result.errors)
    }

    @Test
    fun threePartAttributeButNotKey() {
        val result = convert("optional id Long")
        assertEquals(listOf("Type: When attribute consists of three parts, first part must be 'key': optional id Long"), result.errors)
    }

    @Test
    fun optionalKey() {
        val result = convert("key id Long?")
        assertEquals(listOf("Type: Key attribute 'id' must not be optional"), result.errors)
    }

    @Test
    fun valid() {
        val result = convert("id Long")
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("id", result.result.name)
        assertEquals("Long", result.result.type)
        assertEquals(false, result.result.key)
        assertEquals(false, result.result.optional)
    }

    @Test
    fun validKey() {
        val result = convert("key id Long")
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("id", result.result.name)
        assertEquals("Long", result.result.type)
        assertEquals(true, result.result.key)
        assertEquals(false, result.result.optional)
    }

    @Test
    fun validOptional() {
        val result = convert("id Long?")
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("id", result.result.name)
        assertEquals("Long", result.result.type)
        assertEquals(false, result.result.key)
        assertEquals(true, result.result.optional)
    }
}
