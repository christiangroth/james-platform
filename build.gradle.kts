import de.chrgroth.gradle.plugins.releasenotes.ReleasenotesConfiguration

plugins {
    id("kotlin-project")
    id("net.researchgate.release") version "3.0.2"

    id("se.patrikerdes.use-latest-versions") version "0.2.18"
    id("com.github.ben-manes.versions") version "0.49.0"

    id("de.chrgroth.gradle.plugins.releasenotes")
}

// TODO #34 automate
// call task koverMergedReport for merged report manually
koverMerged {
    enable()
}

releasenotes {
    mainBranch = "main"
    enforceOnNonMainBranch = true

    configure {
        ReleasenotesConfiguration(
            name = "default",
            outputPath = "RELEASENOTES.md",
            snippetsPath = "releasenotes-snippets",
            templatesPath = "releasenotes-templates",
            bugfixesHeader = "## Bugfixes\n",
            bugfixesFooter = "",
            featuresHeader = "## New Features\n",
            featuresFooter = "",
            highlightsHeader = "## Highlights\n",
            highlightsFooter = "",
            updateNoticesHeader = "## Breaking Changes\n",
            updateNoticesFooter = "",
            dateFormat = "yyyy.MM.dd",
        )
    }
}
