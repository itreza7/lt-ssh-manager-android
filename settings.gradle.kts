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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required in later phases: Termux terminal-emulator / terminal-view are published here.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SSH Manager"
include(":app")
// Vendored Termux terminal modules (driven from the SSH channel; see their build files).
include(":terminal-emulator")
include(":terminal-view")
