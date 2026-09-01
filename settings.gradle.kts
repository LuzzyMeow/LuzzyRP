// LuzzyRP v1.0.0 重建：单模块 :app（原生 WebView 壳）
// 上游：RP-Hub 1.8.9（assets/rphub/ 目录按 AGENTS.md §4 SOP 同步）
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
    }
}

rootProject.name = "LuzzyRP"

include(":app")
