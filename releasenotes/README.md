## Releasenotes Gradle Plugin

This project contains a helpful gradle plugin that helps you manage your releasenotes within your project.

## Overview
In your project (per language de/en) you have one "real" file, which contains all historical releasenotes. 
This file generally does not contain any variables - just simple straight text (typically asciidoc). 
You are not meant to edit this file manually, except to fix previously made mistakes!
 
You have (per language) a next version template and a folder, in which the upcoming releasenotes should be gathered as snippet files.
When you want to release a new version, all snippet files in the folder will be collected into the template.
Then the snippet files will be deleted.
After that all variables within the template will be resolved and replaced.
Finally the resulting text will be prepended in the "real" file.
This process was [inspired by GitLab](https://about.gitlab.com/blog/2018/07/03/solving-gitlabs-changelog-conflict-crisis/).

This plugin is meant to be used together with the [documentation plugin](https://git.e-spirit.de/projects/PLE/repos/documentation-plugin/browse). 
The default configuration is set accordingly.

## Usage

### Quickstart

Create a new empty subproject with the following `build.gradle.kts`

```Kotlin
plugins {
    id("de.espirit.documentation") version "INSERT-VERSION-HERE"
    id("de.espirit.releasenotes-git") version "INSERT-VERSION-HERE"
}

documentation {
    moduleName = "Releasenotes" // this is never used, but has to be provided for the documentation plugin
}
```

Now run 
```
./gradlew createFolderStructure
```
This task will create the default folders and files in your subproject. 

When you want to create a new releasenote snippet simply run one or multiple of the following tasks 
```
./gradlew createFeature
./gradlew createBugfix
./gradlew createHighlight
./gradlew createUpdateNotice
```

It will create a new file (per de/en) in the releasenotes snippets folder. 
Of course, you can instead do it manually, if you want.

### Types of snippets
In general the plugin allows adding four different types of snippets to the release notes document.
This aligns with the global structure of the releasenote documents.

#### Features and bugfixes
Feature and bugfix snippets will be displayed as table.
By default, the table has a two column layout containing of the ticket id and a description.

You can control the heading and table columns via the following configuration:

```Kotlin
releasenotes {
    de = LanguageSpecificConfigurationDe().copy(headings = Headings(featuresHeadline = "My Features"))
}
```
Please use your IDE to navigate into the function declarations and take a look at the Headings class for example,
to find out more about custom configurations.

#### Highlights and update notices
Highlight and update notice snippets by default contain plain asciidoc content and will be collected using a configurable heading.
The filenames must end on `-highlight.adoc` or `-updateNotice.adoc` respectively.

You can control the headings via the following configuration:

```Kotlin
releasenotes {
    de = LanguageSpecificConfigurationDe().copy(headings = Headings(highlightsHeadline = "My Highlights"))
}
```

### Configuration
You can configure the project structure, the templates and the variables in the templates however you wish.
To do so, add the following block to your ``build.gradle.kts``

```Kotlin
releasenotes {
    de = LanguageSpecificConfigurationDe().copy(
        templatePaths = DefaultTemplatePaths.de.copy(nextVersionTemplatePath = "src/docs/asciidoc/de/templates/nextVersionTemplate.adoc")
    )
}
```

The above examples just show some available customizations.
For further configurations, use the documentation plugin documentation as a guideline.
IntelliJ provides helpful code completion as well.

### Custom Templates
To change the default templates you may override the individual files and add them to version control in your project.
You can do so by calling the following gradle task to extract the default templates used by the plugin:
 
```
./gradlew createDefaultTemplates
```

Afterwards just adapt the template to your needs and add them to version control.
If you do not want to customize all templates, delete all other generated files.

The plugin will always use the file from version control, if present, or else fallback to the includes default templates.

### Bamboo
You can use this plugin in combination with tools like the CORE plan generator and the gradle release plugin and so on.
If tasks are present, the plugin will hook into them automatically and give you an info logging what happened.

Since this is not always possible (for example because you can't create the required tasks before applying the releasenotes plugin), you can wire it up like:

```Kotlin
tasks.assemble.dependsOn(buildReleasenotes)
tasks.afterReleaseBuild { dependsOn(tasks.deleteReleasenotes, tasks.copyBuiltReleaseNotesToSources, tasks.updateJiraFixVersion) }
```

The task `buildReleasenotes` will build the final releasenotes files in the build folder of the project.
This way, compiling the releasenotes can be done arbitrarily often and does no harm to your sources.
When you do a release, the final output should be brought back to your sources, which is done by the task `copyBuiltReleaseNotesToSourcesTask`.
`deleteReleasenotes` is not executed automatically with `buildReleasenotes` and should be executed only after a release was green/finished.

### Jira Connector
If you want the fixVersion automatically to be created in your Jira project and added to the issue, please configure the JiraConnector as follows:

```Kotlin
releasenotes {
    jira {
        enabled = true
        user = "your_technical_user"
        password = "your_technical_users_password"
        versionPrefix = "Optional Version Prefix "
    }
}
```

Please be sure to have a technical user to connect to Jira with appropriate permissions needed to create a new version in your Jira project and set an issues fixVersion field.

The optional version prefix should be used if you manage multiple components with independent technical versions in Git in one Jira project.
Please specify a different prefix per Git project / component so versions do not conflict in Jira.

For example in CAAS we have multiple FSMs, and the CaaS platform projects all managed in one Jira project.
So we would use the following prefixes there: "Module ", "Platform ", "Connect ".

Pay attention to the trailing space!
This is needed because the prefix, and the created version will be concatenated **without any whitespace** in between.
This enables human readable versions like Module 1.2.3 and more technical driven versions like module-1.2.3.
It's all up to what you want.
