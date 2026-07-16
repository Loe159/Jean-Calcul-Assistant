pluginManagement {
    repositories {
        google()
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

rootProject.name = "Jean-Calcul-Assistant"

include(
    ":app",
    ":assistant-service",
    ":assistant-session",
    ":core-domain",
    ":core-data",
    ":core-ui",
    ":tool-bridge",
)

project(":app").projectDir = file("android/app")
project(":assistant-service").projectDir = file("android/assistant-service")
project(":assistant-session").projectDir = file("android/assistant-session")
project(":core-domain").projectDir = file("android/core-domain")
project(":core-data").projectDir = file("android/core-data")
project(":core-ui").projectDir = file("android/core-ui")
project(":tool-bridge").projectDir = file("android/tool-bridge")
