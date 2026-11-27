import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val devPreview = providers.gradleProperty("jeancalcul.devPreview").orNull == "true"
val devHubUrl = providers.gradleProperty("jeancalcul.devHubUrl").orNull.orEmpty()
require(devHubUrl.isEmpty() || devHubUrl.matches(Regex("https?://[A-Za-z0-9.:-]+"))) { "Invalid dev hub URL" }
val syntheticEmulator = providers.gradleProperty("jeancalcul.emulator").orNull == "true"
if (devPreview) layout.buildDirectory.set(layout.projectDirectory.dir("build-devpreview"))

val previewVersionCode = providers.gradleProperty("jeancalcul.versionCode").orNull?.toInt() ?: 2030000000
val devVersionCode = 2099000000
require(previewVersionCode in 2030000000..2100000000) { "Preview versionCode is out of range" }
require(devVersionCode in 2030000000..2100000000) { "Dev preview versionCode is out of range" }
val updateChannel = providers.gradleProperty("jeancalcul.updateChannel").orNull ?: "preview-pr-62"
require(updateChannel.matches(Regex("[a-z0-9-]+"))) { "Invalid update channel" }

val sourceCommit = providers.environmentVariable("GITHUB_SHA").orNull.orEmpty()
require(sourceCommit.isEmpty() || sourceCommit.matches(Regex("[0-9a-f]{40}"))) { "Invalid source commit" }

val releaseStoreFile = System.getenv("ETA_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("ETA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("ETA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ETA_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// CI 对非发版构建传入提交号，按 SemVer 构建元数据追加为 +<sha>，不影响版本先后；本地与发版构建不传
val buildMetadata = providers.gradleProperty("eta.buildMetadata").orNull?.takeIf { it.isNotBlank() }
require(buildMetadata == null || buildMetadata.matches(Regex("[0-9A-Za-z.-]+"))) {
    "eta.buildMetadata 只能包含字母、数字、点和连字符"
}
val buildMetadataSuffix = buildMetadata?.let { "+$it" }.orEmpty()

android {
    namespace = "io.github.mangi.eta"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "fr.loevan.jeancalcul.v2"
        minSdk = 33
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // versionCode 规则：yyyyMMdd + 两位当日序号（01 起），发版时随 versionName 一起手动递增。
        versionCode = previewVersionCode
        versionName = "2.0.0-preview.$previewVersionCode"
        buildConfigField("String", "UPDATE_CHANNEL", "\"$updateChannel\"")
        buildConfigField("String", "SOURCE_COMMIT", "\"$sourceCommit\"")
        buildConfigField("boolean", "DEV_PREVIEW", "false")
        buildConfigField("String", "DEV_HUB_URL", "\"\"")
        manifestPlaceholders["devPreviewEnabled"] = "false"
        // Synthetic AVD runs must not download a public preview or display its installer dialog.
        buildConfigField("boolean", "SYNTHETIC_EMULATOR", syntheticEmulator.toString())

        ndk {
            // 同时过滤本地与依赖 AAR 的原生库，确保 APK 仅包含 ARM64。
            abiFilters += if (syntheticEmulator) "x86_64" else "arm64-v8a"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".github/preview-signing.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 版本名带构建类型标记，设置页与 APK 文件名据此区分 Debug 包
            versionNameSuffix = "-debug" + buildMetadataSuffix
            isMinifyEnabled = false
            isPseudoLocalesEnabled = true
            if (devPreview) {
                applicationIdSuffix = ".preview"
                versionNameSuffix = "-dev"
                buildConfigField("String", "UPDATE_CHANNEL", "\"\"")
                buildConfigField("String", "SOURCE_COMMIT", "\"\"")
                buildConfigField("boolean", "DEV_PREVIEW", "true")
                buildConfigField("String", "DEV_HUB_URL", "\"$devHubUrl\"")
                manifestPlaceholders["devPreviewEnabled"] = "true"
            }
        }
        release {
            buildConfigField("String", "UPDATE_CHANNEL", "\"\"")
            versionNameSuffix = buildMetadataSuffix
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    if (devPreview) {
        sourceSets.getByName("debug") {
            res.directories.add("src/devPreview/res")
            manifest.srcFile("src/devPreview/AndroidManifest.xml")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        localeFilters += listOf("en")
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libproot_exec.so", "**/libproot_loader.so", "**/libeta_pty.so")
        }
        resources {
            // 合并 Xposed 模块声明，避免 release 裁剪后模块入口失效
            merges += "META-INF/xposed/*"
            // 仅排除会引发打包冲突的签名/版本元数据，避免误伤 Compose 资源
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // versionCode 采用日期编码，已发布值只能递增；上限 2100000000 足够覆盖到 2099 年。
        disable += "HighAppVersionCode"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric 在高版本 JDK 下需要访问内部 API，参数只作用于测试 JVM。
        unitTests.all { testTask ->
            // Robolectric downloads its Android runtime in the test JVM, outside Gradle's HTTP client.
            listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
                "http.nonProxyHosts", "javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword").forEach { key ->
                System.getProperty(key)?.let { value -> testTask.systemProperty(key, value) }
            }
            testTask.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }
}

@CacheableTask
abstract class GenerateDevPreviewCatalog : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val scanner: RegularFileProperty

    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val output = assetsDirectory.file("dev-previews.json").get().asFile
        output.parentFile.mkdirs()
        execOperations.exec {
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            args("-cp", compilerClasspath.asPath, scanner.get().asFile.absolutePath,
                kotlinDirectory.get().asFile.absolutePath, output.absolutePath)
        }
    }
}

val catalogCompiler = if (devPreview) {
    configurations.create("devPreviewCatalogCompiler") {
        isCanBeConsumed = false
    }.also {
        dependencies.add(it.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
        dependencies.add(it.name, "com.google.code.gson:gson:2.13.2")
    }
} else null

androidComponents {
    onVariants { variant ->
        val emulatorApk = syntheticEmulator
        val previewDebug = devPreview && variant.buildType == "debug"
        if (previewDebug) {
            val assets = layout.buildDirectory.dir("dev-build-assets").get().asFile
            check(assets.isDirectory || assets.mkdirs()) { "Could not create $assets" }
            val catalog = tasks.register<GenerateDevPreviewCatalog>("generateDevPreviewCatalog") {
                kotlinDirectory.set(layout.projectDirectory.dir("src/main/kotlin"))
                scanner.set(rootProject.layout.projectDirectory.file("gradle/dev-preview/PreviewCatalog.java"))
                compilerClasspath.from(catalogCompiler)
                javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
                assetsDirectory.set(layout.buildDirectory.dir("generated/dev-preview-assets"))
            }
            variant.sources.assets?.addStaticSourceDirectory(assets.absolutePath)
            variant.sources.assets?.addGeneratedSourceDirectory(catalog, GenerateDevPreviewCatalog::assetsDirectory)
        }
        // 文件名只取 versionName：构建类型标记与 CI 构建元数据都由 versionNameSuffix 带出
        variant.outputs.forEach { output ->
            if (previewDebug) {
                output.versionCode.set(devVersionCode)
                output.versionName.set("2.0.0-preview.$devVersionCode-dev")
                output.outputFileName.set("JeanCalculPreview.apk")
            } else {
                output.outputFileName.set(output.versionName.map { if (emulatorApk) "app-debug.apk" else "JeanCalcul-v$it.apk" })
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(libs.jsoup)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    compileOnly(libs.libxposed.api)
    // UI 侧 RemotePreferences 写入桥：通过 XposedService 将配置提交到 LSPosed 数据库；
    // Hook 侧用 XposedInterface.getRemotePreferences 读取当前进程持有的配置缓存。
    implementation(libs.libxposed.service)
    implementation(libs.dexkit)
    implementation(libs.coui.blur)
    implementation(libs.coui.ui)
    implementation(libs.coui.preference)
    implementation(libs.coui.nav)
    implementation(libs.coui.icons)
    // @Preview functions, rendered headlessly by `android preview render` (docs/agents/PREVIEWS.md).
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.car.app.core)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.activity.compose)
    // 只使用 GFM 解析器；聊天渲染层由 ui/markdown 自建，按块冻结并接入逐字显现。
    implementation(libs.intellij.markdown)
    implementation(libs.hidden.api.bypass)

    // DataStore：Provider / Model 结构化 JSON 与当前选中 ID 等键值
    implementation(libs.datastore.preferences)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // OkHttp：替代 HttpURLConnection，支持 SSE
    implementation(libs.okhttp)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.okhttp.sse)

    // Kotlinx Serialization：Provider 设置与运行时配置 JSON
    implementation(libs.kotlinx.serialization.json)

    // Coroutines：显式引入，避免依赖传递版本不确定
    implementation(libs.kotlinx.coroutines.android)
    // POC only: inference fully local; no model weight bundled in APK.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
}
