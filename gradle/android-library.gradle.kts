import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

extensions.configure<LibraryExtension> {
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
