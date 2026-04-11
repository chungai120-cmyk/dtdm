pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Bản KSP dành riêng cho Kotlin 1.9.22 (Khớp với tài liệu)
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cuoiky"
include(":app")