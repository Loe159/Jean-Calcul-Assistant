plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
android {
    namespace = "fr.loevan.jeancalcul.v2"
    compileSdk = 37
    defaultConfig {
        applicationId = "fr.loevan.jeancalcul.v2"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures { compose = true }
    testOptions { animationsDisabled = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.itoseo:coui:0.0.1-alpha7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}
