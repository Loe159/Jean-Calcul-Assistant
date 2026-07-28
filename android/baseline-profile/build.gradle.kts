plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baseline.profile)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "fr.loevan.jeancalcul.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"

    flavorDimensions += "distribution"
    productFlavors {
        create("core") {
            dimension = "distribution"
            buildConfigField("String", "TARGET_PACKAGE", "\"fr.loevan.jeancalcul\"")
        }
        create("powerUser") {
            dimension = "distribution"
            buildConfigField("String", "TARGET_PACKAGE", "\"fr.loevan.jeancalcul.poweruser\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

baselineProfile {
    useConnectedDevices = true
}

// Keep device work explicit. Pass -Pphase1DeviceValidation for profile generation or benchmarks.
tasks.configureEach {
    if (
        (name.startsWith("connected") && name.endsWith("AndroidTest")) ||
        (name.startsWith("collect") && name.endsWith("BaselineProfile"))
    ) {
        onlyIf { providers.gradleProperty("phase1DeviceValidation").isPresent }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
