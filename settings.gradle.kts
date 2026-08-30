// LuzzyRP 模块拓扑：
//   :app          主应用（UI/VM/Room/DataStore/DI/Service）
//   :core:model   纯领域模型（无 Android 依赖）
//   :core:ai      AI SDK（Provider/SSE 流式/三协议/标签兜底）
//   :core:common  通用工具（HTTP 桥接/JSON/PNG chunk）
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
include(":core:model")
include(":core:ai")
include(":core:common")
