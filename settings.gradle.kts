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
    ":core-network",
    ":core-observability",
    ":core-security",
    ":core-ui",
    ":feature-conversation",
    ":feature-settings",
    ":feature-tasks",
    ":feature-voice",
    ":tool-bridge",
)

project(":app").projectDir = file("android/app")
project(":assistant-service").projectDir = file("android/assistant-service")
project(":assistant-session").projectDir = file("android/assistant-session")
project(":core-domain").projectDir = file("android/core-domain")
project(":core-data").projectDir = file("android/core-data")
project(":core-network").projectDir = file("android/core-network")
project(":core-observability").projectDir = file("android/core-observability")
project(":core-security").projectDir = file("android/core-security")
project(":core-ui").projectDir = file("android/core-ui")
project(":feature-conversation").projectDir = file("android/feature-conversation")
project(":feature-settings").projectDir = file("android/feature-settings")
project(":feature-tasks").projectDir = file("android/feature-tasks")
project(":feature-voice").projectDir = file("android/feature-voice")
project(":tool-bridge").projectDir = file("android/tool-bridge")
