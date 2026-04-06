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
    }
}

rootProject.name = "MyTvXml"

// ─────────────────────────────────────────────────────────────────────
// Module structure:
//   :tracker  — shared Android library (MediaTrackerService + NLS + MediaBrowserExplorer)
//   :app      — Mobile phone app  (simple 2-button UI, full permission redirect)
//   :apptv    — Android TV app    (movie browser UI, TV-optimised, D-pad ready)
//
// To run either app in Android Studio:
//   Run → Edit Configurations → Select "app" or "apptv"
// ─────────────────────────────────────────────────────────────────────
include(":tracker")
include(":app")
include(":apptv")
