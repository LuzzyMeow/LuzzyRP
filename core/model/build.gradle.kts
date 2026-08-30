// :core:model —— 纯领域模型层（纯 Kotlin JVM，无 Android 依赖）
// 内容：UIMessage / MessageChunk / Conversation / CharacterCard / Worldbook /
//       RegexScript / MemoryItem / SummaryItem / ToolApprovalState / ProviderSetting
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

java {
    // 与 Kotlin jvmTarget=17 对齐，保证 Java/Kotlin 编译目标一致（JVM 17 字节码规范）
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
