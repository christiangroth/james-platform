import de.chrgroth.gradle.plugins.releasenotes.ReleasenotesConfiguration

plugins {
    id("de.chrgroth.gradle.plugins.releasenotes")
}

releasenotes {
    this.mainBranch = "main"
    this.enforceOnNonMainBranch = true
    this.configurations.add(
        ReleasenotesConfiguration(
            name = "default",
            outputPath = "RELEASENOTES.md",
            snippetsPath = "snippets",
            templatesPath = "templates",
            bugfixesHeader = "## Bugfixes",
            bugfixesFooter = "",
            featuresHeader = "## New Features",
            featuresFooter = "",
            highlightsHeader = "## Highlights",
            highlightsFooter = "",
            updateNoticesHeader = "## Update Notices",
            updateNoticesFooter = "",
            dateFormat = "yyyy.MM.dd",
        )
    )
    this.configurations.add(
        ReleasenotesConfiguration(
            name = "german",
            outputPath = "RELEASENOTES_DE.md",
            snippetsPath = "snippets-de",
            templatesPath = "templates-de",
            bugfixesHeader = "## Bugfixes",
            bugfixesFooter = "",
            featuresHeader = "## New Features",
            featuresFooter = "",
            highlightsHeader = "## Highlights",
            highlightsFooter = "",
            updateNoticesHeader = "## Update Notices",
            updateNoticesFooter = "",
            dateFormat = "dd.MM.yyyy",
        )
    )
}
