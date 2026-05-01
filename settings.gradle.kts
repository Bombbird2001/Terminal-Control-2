pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://s01.oss.sonatype.org")
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }

    val kspVersion = providers.gradleProperty("kspVersion").orNull
    val kotlinVersion = providers.gradleProperty("kotlinVersion").orNull

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"

        // Plugin versions used by subprojects.
        id("com.google.devtools.ksp") version (kspVersion ?: error("Missing kspVersion in gradle.properties"))
        id("org.jetbrains.kotlin.jvm") version (kotlinVersion ?: error("Missing kotlinVersion in gradle.properties"))
        id("org.jetbrains.kotlin.android") version kotlinVersion
        id("kotlin-android") version kotlinVersion
        id("com.android.application") version "9.0.0-alpha06"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven(url = "https://s01.oss.sonatype.org")
        mavenLocal()
        google()
        gradlePluginPortal()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://jitpack.io")
    }
}

include("lwjgl3", "ios", "android", "core", "relay")
