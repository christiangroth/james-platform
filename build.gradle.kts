import de.chrgroth.gradle.plugins.releasenotes.ReleasenotesConfiguration

plugins {
    id("kotlin-project")
    id("net.researchgate.release") version "3.0.2"

    id("se.patrikerdes.use-latest-versions") version "0.2.18"
    id("com.github.ben-manes.versions") version "0.52.0"

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
            name = "repo-markdown",
            outputPath = "RELEASENOTES.md",
            snippetsPath = "releasenotes-snippets",
            templatesPath = "releasenotes-templates",
            bugfixesHeader = "## Bugfixes",
            bugfixesFooter = "\n",
            featuresHeader = "## New Features",
            featuresFooter = "\n",
            highlightsHeader = "## Highlights",
            highlightsFooter = "\n",
            updateNoticesHeader = "## Breaking Changes",
            updateNoticesFooter = "\n",
            preserveWhitespace = false,
            dateFormat = "yyyy.MM.dd",
        )
    }

    /*
    val runtimeReleasenotes = { runtimeName: String ->
        ReleasenotesConfiguration(
            name = runtimeName,
            outputPath = "runtime-$runtimeName/src/main/resources/releasenotes.yaml",
            snippetsPath = "runtime-$runtimeName/src/main/resources/releasenotes-snippets",
            templatesPath = "runtime-$runtimeName/src/main/resources/releasenotes-templates",
            bugfixesHeader = "  bugfixes:",
            bugfixesFooter = "",
            featuresHeader = "  features:",
            featuresFooter = "",
            highlightsHeader = "  highlights:",
            highlightsFooter = "",
            updateNoticesHeader = "  breaking:",
            updateNoticesFooter = "",
            preserveWhitespace = true,
            dateFormat = "yyyy.MM.dd",
        )
    }

    configure {
        runtimeReleasenotes("quarkus")
    }
    */
}

release {
    git {
        requireBranch = "main"
    }
}
