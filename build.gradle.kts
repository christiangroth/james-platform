import de.chrgroth.gradle.plugins.releasenotes.ReleasenotesConfiguration
import java.time.Duration

plugins {
  id("kotlin-project")
  alias(libs.plugins.release)
  id("de.chrgroth.gradle.plugins.releasenotes")
  alias(libs.plugins.buildTimeTracker)
  alias(libs.plugins.versionCatalogUpdate)
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

release {
  git {
    requireBranch = "main"
  }
}
