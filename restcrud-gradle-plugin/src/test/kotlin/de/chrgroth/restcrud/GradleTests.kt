package de.chrgroth.restcrud

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.internal.HasConvention
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GradleTests {

    @TempDir
    lateinit var testProjectDir: Path
    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder()
                .withName("restcrud-test")
                .withProjectDir(testProjectDir.toFile())
                .build().also {
                    it.plugins.apply(RestcrudPlugin::class.java)

                    // we need this to trigger the afterEvaluate hook and update source directories
                    (it as ProjectInternal).evaluate()
                }
    }

    @Test
    fun `plugin registers extension`() {
        val extensionByName = project.extensions.getByName(EXTENSION_NAME)
        assertTrue(extensionByName is RestcrudExtension)
        val extensionyByType = project.extensions.getByType(RestcrudExtension::class.java)
        assertEquals("restcrud.yaml", extensionyByType.definitionsFilename)
        assertEquals("gen-src/restcrud", extensionyByType.genSrcDir)
        assertEquals("1.3.0", extensionyByType.versionKtor)
        assertEquals("3.12.+", extensionyByType.versionKMongo)
        assertEquals("1.2.1", extensionyByType.versionLogback)
        assertEquals("2.10.+", extensionyByType.versionJacksonKotlin)
    }

    @Test
    fun `plugin registers generate task with correct dependencies`() {
        val task = project.tasks.getByName(TASK_NAME_GENERATE).apply {
            assertEquals("restcrud", group)
        }
        assertTrue(task.dependsOn.isEmpty())

        val compileKotlinTask = project.tasks.getByName("compileKotlin")
        assertTrue(compileKotlinTask.dependsOn.contains(task))
    }

    @Test
    fun `plugin enhances kotlin main source set`() {
        val sourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
        val genericMainSourceSet = sourceSetContainer.getByName("main")
        val kotlinSourceSet = (genericMainSourceSet as HasConvention).convention.getPlugin(KotlinSourceSet::class.java)
        assertTrue(kotlinSourceSet.kotlin.srcDirs.contains(project.buildDir.resolve("gen-src/restcrud")))
    }

    @Test
    fun `restcrudGenerate creates sources for sample project build`() {
        testProjectDir.createBasicBuildFile()
        "./src/test/resources/full.yaml".asFile()
                .copyTo(testProjectDir.resolve("restcrud.yaml").toFile())

        val result = testProjectDir.executeTask("build")
        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    private fun Path.createBasicBuildFile(before: String = "", after: String = "") {
        buildFile(
                """
            $before
            project.version = "1.0.0"
            plugins {
                id("de.chrgroth.gradle.restcrud")
            }
            
            $after
            """.trimIndent()
        )
    }

    private fun Path.buildFile(buildFileSource: String): File = resolve("build.gradle.kts").toFile().apply {
        createNewFile()
        writeText(buildFileSource)
    }

    private fun String.asFile() = File(this)

    private fun Path.executeTask(taskName: String, expectFail: Boolean = false) =
            GradleRunner.create()
                    .withProjectDir(toFile())
                    .withArguments(taskName, "--info", "--stacktrace")
                    .withPluginClasspath().run {
                        if (expectFail) {
                            buildAndFail()
                        } else {
                            build()
                        }
                    }
}
