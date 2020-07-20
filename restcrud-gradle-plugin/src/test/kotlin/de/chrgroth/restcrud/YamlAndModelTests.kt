package de.chrgroth.restcrud

import de.chrgroth.restcrud.ValidationResult.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.IllegalStateException

fun <T> ValidationResult<T>.expectSuccess(): Success<T> {
    assertTrue(this is Success<T>)
    return this as Success<T>
}

fun <T> ValidationResult<T>.expectFailure(): Failure<T> {
    assertTrue(this is Failure<T>)
    return this as Failure<T>
}

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
        assertEquals("de/foo/bar", parseResult.codeGeneration.packagePath)
        assertEquals(3, parseResult.model.size)
        assertEquals("Foo", parseResult.model[0].name)
        assertEquals("/api/foos", parseResult.model[0].endpoint)
        assertEquals(2, parseResult.model[0].attributes.size)
        assertEquals("id", parseResult.model[0].attributes[0].name)
        assertEquals("Long", parseResult.model[0].attributes[0].type)
        assertEquals(false, parseResult.model[0].attributes[0].optional)
        assertEquals("description", parseResult.model[0].attributes[1].name)
        assertEquals("String", parseResult.model[0].attributes[1].type)
        assertEquals(true, parseResult.model[0].attributes[1].optional)
        assertEquals("Bar", parseResult.model[1].name)
        assertEquals("/api/bars", parseResult.model[1].endpoint)
        assertEquals(2, parseResult.model[1].attributes.size)
        assertEquals("id", parseResult.model[1].attributes[0].name)
        assertEquals("Long", parseResult.model[1].attributes[0].type)
        assertEquals(false, parseResult.model[1].attributes[0].optional)
        assertEquals("version", parseResult.model[1].attributes[1].name)
        assertEquals("Long", parseResult.model[1].attributes[1].type)
        assertEquals(false, parseResult.model[1].attributes[1].optional)
        assertEquals(2, parseResult.endpoints().size)
        assertEquals("Foo", parseResult.endpoints()[0].name)
        assertEquals("Bar", parseResult.endpoints()[1].name)
    }
}

class CodeGenerationValidationTests {

    @Test
    fun noPackageName() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml(null, emptyList())).expectFailure()
        assertEquals(listOf("Code generation package name '' must match pattern: [a-z]+([.][a-z]+)*"), result.errors)
    }

    @Test
    fun emptyPackageName() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml("  ", emptyList())).expectFailure()
        assertEquals(listOf("Code generation package name '' must match pattern: [a-z]+([.][a-z]+)*"), result.errors)
    }

    @Test
    fun invalidPackageName() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml("foo.bar123", emptyList())).expectFailure()
        assertEquals(listOf("Code generation package name 'foo.bar123' must match pattern: [a-z]+([.][a-z]+)*"), result.errors)
    }

    @Test
    fun valid() {
        val result = YamlUtils.convertCodeGeneration(ConfigurationYaml(" foo.bar.baz  ", emptyList())).expectSuccess()
        assertEquals("foo.bar.baz", result.result.packageName)
    }
}

class ModelValidationTests {

    private fun convert(model: ModelYaml?) = convert(listOf(model))[0]
    private fun convert(model: List<ModelYaml?>) = YamlUtils.convertModel(model)

    @Test
    fun nullModelList() {
        val result = YamlUtils.convert(ConfigurationYaml("foo.bar", null)).expectFailure()
        assertEquals(listOf("No model defined"), result.errors)
    }

    @Test
    fun emptyModelList() {
        val result = YamlUtils.convert(ConfigurationYaml("foo.bar", emptyList())).expectFailure()
        assertEquals(listOf("No model defined"), result.errors)
    }

    @Test
    fun nullModel() {
        val result = convert(null as ModelYaml?).expectFailure()
        assertEquals(listOf("Found null/empty model"), result.errors)
    }

    @Test
    fun noName() {
        val result = convert(ModelYaml(null, "/foos", listOf("id Long"))).expectFailure()
        assertEquals(listOf("Model name '' must match pattern: [A-Z][a-zA-Z]*"), result.errors)
    }

    @Test
    fun emptyName() {
        val result = convert(ModelYaml("  ", "/foos", listOf("id Long"))).expectFailure()
        assertEquals(listOf("Model name '' must match pattern: [A-Z][a-zA-Z]*"), result.errors)
    }

    @Test
    fun invalidName() {
        listOf("Foo123", "Foo Bar", "foo").forEach { invalidName ->
            val result = convert(ModelYaml(invalidName, "/foos", listOf("id Long"))).expectFailure()
            assertEquals(
                listOf("Model name '$invalidName' must match pattern: [A-Z][a-zA-Z]*"),
                result.errors
            )
        }
    }

    @Test
    fun noEndpoint() {
        convert(ModelYaml("Foo", null, listOf("id Long"))).expectSuccess()
    }

    @Test
    fun emptyEndpoint() {
        val result = convert(ModelYaml("Foo", "", listOf("id Long"))).expectFailure()
        assertEquals(listOf("Model endpoint '' (processed to '') must match pattern: [/][a-zA-Z]+([/][a-zA-Z]+)*"), result.errors)
    }

    @Test
    fun missingLeadingSlashIsAdded() {
        val result = convert(ModelYaml("Foo", "api/foos", listOf("id Long"))).expectSuccess()
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun leadingDoubleSlashesAreCorrected() {
        val result = convert(ModelYaml("Foo", "//api/foos/", listOf("id Long"))).expectSuccess()
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun doubleSlashesInBetweenAreCorrected() {
        val result = convert(ModelYaml("Foo", "/api//foos/", listOf("id Long"))).expectSuccess()
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun trailingDoubleSlashesAreCorrected() {
        val result = convert(ModelYaml("Foo", "/api/foos//", listOf("id Long"))).expectSuccess()
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun trailingSlashIsRemoved() {
        val result = convert(ModelYaml("Foo", "/api/foos/", listOf("id Long"))).expectSuccess()
        assertEquals("/api/foos", result.result.endpoint)
    }

    @Test
    fun valid() {
        val result = convert(ModelYaml("Foo", "/api/foos", listOf("id Long"))).expectSuccess()
        assertEquals("Foo", result.result.name)
        assertEquals("/api/foos", result.result.endpoint)
    }
}

class ModelAttributeValidationTests {

    private fun convert(attribute: String?) = convert(listOf(attribute))[0]
    private fun convert(attributes: List<String?>) = YamlUtils.convertAttributes("Type", attributes)

    @Test
    fun nullAttributes() {
        val result = YamlUtils.convertModel(ModelYaml("Foo", "/api/foos", null)).expectFailure()
        assertEquals(listOf("Foo: No attributes defined"), result.errors)
    }

    @Test
    fun emptylAttributes() {
        val result = YamlUtils.convertModel(ModelYaml("Foo", "/api/foos", listOf())).expectFailure()
        assertEquals(listOf("Foo: No attributes defined"), result.errors)
    }

    @Test
    fun nullAttribute() {
        val result = convert(null as String?).expectFailure()
        assertEquals(listOf("Type: Found null/empty attribute"), result.errors)
    }

    @Test
    fun emptyAttribute() {
        val result = convert("  ").expectFailure()
        assertEquals(listOf("Type: Attribute '' does not match pattern: name type[?]"), result.errors)
    }

    @Test
    fun onePartAttribute() {
        val result = convert("id").expectFailure()
        assertEquals(listOf("Type: Attribute 'id' does not match pattern: name type[?]"), result.errors)
    }

    @Test
    fun fourPartAttribute() {
        val result = convert("key id optional Long").expectFailure()
        assertEquals(listOf("Type: Attribute 'key id optional Long' does not match pattern: name type[?]"), result.errors)
    }

    @Test
    fun threePartAttribute() {
        val result = convert("key id Long?").expectFailure()
        assertEquals(listOf("Type: Attribute 'key id Long?' does not match pattern: name type[?]"), result.errors)
    }

    @Test
    fun valid() {
        val result = convert("id Long").expectSuccess()
        assertEquals("id", result.result.name)
        assertEquals("Long", result.result.type)
        assertEquals(false, result.result.optional)
    }

    @Test
    fun validOptional() {
        val result = convert("id Long?").expectSuccess()
        assertEquals("id", result.result.name)
        assertEquals("Long", result.result.type)
        assertEquals(true, result.result.optional)
    }
}
