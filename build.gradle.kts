import de.chrgroth.gradle.plugins.releasenotes.ReleasenotesConfiguration
import java.time.Duration

plugins {
  id("kotlin-project")
  id("net.researchgate.release") version "3.1.0"

  id("se.patrikerdes.use-latest-versions") version "0.2.18"
  id("com.github.ben-manes.versions") version "0.49.0"

  id("de.chrgroth.gradle.plugins.releasenotes")

  // TODO install dot executable
  // see: https://github.com/savvasdalkitsis/module-dependency-graph
  id("com.savvasdalkitsis.module-dependency-graph") version "0.10"

  id("com.asarkar.gradle.build-time-tracker") version "5.0.1"
}

buildTimeTracker {
  maxWidth = 120
  minTaskDuration = Duration.ofMillis(250)
}

// TODO #34 automate
// call task koverMergedReport for merged report manually
koverMerged {
  enable()
}

releasenotes {
  mainBranch = "main"
  skipReleaseNotesOnBranchPrefixes = listOf("main", "dependabot/")

  configure {
    ReleasenotesConfiguration(
      name = "repo-markdown",
      outputPath = "RELEASENOTES.md",
      snippetsPath = "releasenotes-snippets",
      templatesPath = "releasenotes-templates",
      bugfixesHeader = "## Bugfixes",
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

release {
  git {
    requireBranch = "main"
  }
}
