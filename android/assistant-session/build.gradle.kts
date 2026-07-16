plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/android-library.gradle.kts"))

android {
    namespace = "fr.loevan.jeancalcul.assistant.session"

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))
    implementation(libs.kotlinx.coroutines.core)
}
