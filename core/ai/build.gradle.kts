// :core:ai —— AI SDK 层（纯 Kotlin JVM，无 Android 依赖）
// 内容：Provider 接口、ProviderManager、OpenAI/Anthropic/Google 三协议实现、
//       SSE 真流式（callbackFlow + EventSources + trySend 零节流）、标签兜底解析器
//
// [INVARIANT-STREAMING] 本模块的 streamText 实现链是真流式不变性的落点：
//   - callbackFlow + okhttp3.sse.EventSources.createFactory().newEventSource()
//   - onEvent → trySend 逐 event 零节流（禁止缓冲/合并/定时 flush）
//   - awaitClose { eventSource.cancel() }
//   修改前必须阅读仓库根 HARD_REQUIREMENTS.md 规定 1。
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
    api(project(":core:model"))
    api(project(":core:common"))

    api(libs.okhttp)
    api(libs.okhttp.sse)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
