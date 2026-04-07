pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven{ url = uri("https://jitpack.io")}
    }
}

rootProject.name = "MyTvXml"

// :tracker  — shared analytics library (services, session management)
// :app      — mobile client
// :apptv    — Android TV client
include(":tracker")
include(":app")
include(":apptv")

include(":core")
include(":companion")
