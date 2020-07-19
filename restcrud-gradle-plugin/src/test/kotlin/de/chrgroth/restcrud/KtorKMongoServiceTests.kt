package de.chrgroth.restcrud

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KtorKMongoServiceTests {

    @TempDir
    lateinit var tempDir: File
    lateinit var configuration: Configuration
    lateinit var codeGenerator: CodeGenerator
    lateinit var service: Service

    @BeforeEach
    fun setup() {
        configuration = YamlUtils.load(File("src/test/resources/full.yaml"))
        codeGenerator = CodeGenerator(tempDir, configuration.codeGeneration)
        service = createService(ApplicationFramework.Ktor, PersistenceFramework.KMongo, configuration, codeGenerator)
    }

    @Test
    fun `service is resolved correctly`() {
        assertThat(service).isNotNull
        assertThat(service).isInstanceOf(KtorKMongoService::class.java)
    }

    @Test
    fun `generateModels creates expected source files`() {
        service.generateModels()
        val modelsFile = expectFile("Models.kt")
        assertThat(modelsFile.readText()).isEqualTo(File("src/test/resources/codeGeneration/Models.kt").readText())
    }

    @Test
    fun `generateControllers creates expected source files`() {
        service.generateControllers()
        val modelsFile = expectFile("Controllers.kt")
        assertThat(modelsFile.readText()).isEqualTo(File("src/test/resources/codeGeneration/Controllers.kt").readText())
    }

    @Test
    fun `generatePersistence creates expected source files`() {
        service.generatePersistence()
        val modelsFile = expectFile("Persistence.kt")
        assertThat(modelsFile.readText()).isEqualTo(File("src/test/resources/codeGeneration/Persistence.kt").readText())
    }

    @Test
    fun `generateApplication creates expected source files`() {
        service.generateApplication()
        val modelsFile = expectFile("Application.kt")
        assertThat(modelsFile.readText()).isEqualTo(File("src/test/resources/codeGeneration/Application.kt").readText())
    }

    private fun expectFile(name: String): File {
        val allFiles = tempDir.resolve(configuration.codeGeneration.packagePath).listFiles()
        assertThat(allFiles).hasSize(1)
        val expectedFile = allFiles[0]
        assertThat(expectedFile.name).isEqualTo(name)
        return expectedFile
    }
}