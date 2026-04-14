import de.chrgroth.gradle.plugins.releasenotes.ReleasenotesConfiguration
import java.time.Duration

plugins {
  id("kotlin-project")
  alias(libs.plugins.buildTimeTracker)
  alias(libs.plugins.versionCatalogUpdate)

  alias(libs.plugins.release)
  id("de.chrgroth.gradle.release-notes") version "1.0.1"

  id("dev.iurysouza.modulegraph") version "0.13.0"
}

buildTimeTracker {
  maxWidth = 120
  minTaskDuration = Duration.ofMillis(50)
}

moduleGraphConfig {
  includeIsolatedModules = true
  readmePath = layout.buildDirectory.file("reports/modulegraph/modules.md").get().asFile.path
}

kover {
  merge {
    allProjects()
  }
}

private val releasenotesBasePath = "docs/releasenotes/"

releasenotes {
  mainBranch = "main"
  skipReleaseNotesOnBranchPrefixes = listOf("main", "dependabot/")

  configure {
    ReleasenotesConfiguration(
      name = "repo-markdown",
      outputPath = "$releasenotesBasePath/RELEASENOTES.md",
      snippetsPath = "$releasenotesBasePath/snippets",
      templatesPath = "$releasenotesBasePath/templates",
      bugfixesHeader = "## Bugfixes / Chore",
      bugfixesFooter = "",
      featuresHeader = "## New Features",
      featuresFooter = "",
      highlightsHeader = "",
      highlightsFooter = "",
      updateNoticesHeader = "## Breaking Changes",
      updateNoticesFooter = "",
      preserveWhitespace = true,
      dateFormat = "yyyy.MM.dd",
    )
  }
}

tasks.afterReleaseBuild {
  dependsOn(":application-quarkus:imageBuild", ":application-quarkus:imagePush")
}

tasks.register("releasenotesEnsureVersion") {
  group = "releasenotes"
  description = "Bumps the project version to minor level when feature snippets are present, ensuring correct semver on release."

  doLast {
    val snippetsDir = file("$releasenotesBasePath/snippets")
    val hasFeatures = snippetsDir.walkTopDown().filter { it.isFile }.any { it.name.endsWith("-feature.md") }

    if (!hasFeatures) {
      logger.lifecycle("No feature snippets found – patch-level version bump applies.")
      return@doLast
    }

    val gradlePropertiesFile = rootProject.file("gradle.properties")
    val gradlePropertiesContent = gradlePropertiesFile.readText()
    val currentVersionMatch = Regex("version=([0-9]+)\\.([0-9]+)\\.([0-9]+)").find(gradlePropertiesContent)
    if (currentVersionMatch == null) {
      logger.warn("Could not parse project version from gradle.properties. Skipping version bump.")
      return@doLast
    }
    val (currentMajor, currentMinor, _) = currentVersionMatch.destructured

    val mainVersionOutput = providers.exec {
      commandLine("git", "show", "main:gradle.properties")
    }.standardOutput.asText.get()
    val mainVersionMatch = Regex("version=([0-9]+)\\.([0-9]+)\\.([0-9]+)").find(mainVersionOutput)
    if (mainVersionMatch == null) {
      logger.warn("Could not parse project version from main branch gradle.properties. Skipping version bump.")
      return@doLast
    }
    val (mainMajor, mainMinor, _) = mainVersionMatch.destructured

    if (currentMajor.toInt() != mainMajor.toInt() || currentMinor.toInt() != mainMinor.toInt()) {
      logger.lifecycle("Minor version already differs from main ($mainMajor.$mainMinor.x vs $currentMajor.$currentMinor.x) – skipping bump.")
      return@doLast
    }

    val newVersion = "$currentMajor.${currentMinor.toInt() + 1}.0"
    gradlePropertiesFile.writeText(gradlePropertiesContent.replace(Regex("version=.*"), "version=$newVersion"))
    setVersion(newVersion)
    logger.lifecycle("Bumped project version to: $newVersion")
  }
}

tasks.beforeReleaseBuild {
  dependsOn("releasenotesEnsureVersion")
}

release {
  failOnSnapshotDependencies = false
  git {
    requireBranch = "main"
  }
}

tasks.named("checkSnapshotDependencies") {
  enabled = false
}
