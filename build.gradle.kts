buildscript {
    repositories {
        mavenCentral()
        maven(url = "https://s01.oss.sonatype.org")
        mavenLocal()
        google()
        gradlePluginPortal()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
    dependencies {
        // Needed for the iOS RoboVM subproject (plugin is applied by id there).
        classpath("com.mobidevelop.robovm:robovm-gradle-plugin:${project.properties["robovmVersion"]}")

        // Defensive constraint (Log4j CVEs) for buildscript classpath only.
        constraints {
            classpath("org.apache.logging.log4j:log4j-core") {
                version {
                    strictly("[2.17, 3[")
                    prefer("2.17.0")
                }
                because(
                    "CVE-2021-44228, CVE-2021-45046, CVE-2021-45105: Log4j vulnerable to remote code execution and other critical security vulnerabilities",
                )
            }
        }
    }
}

plugins {
    // Keep KSP available to subprojects.
    id("com.google.devtools.ksp") apply false

    // Ensure AGP/Kotlin Android plugin jars are loaded early (classloader alignment).
    id("com.android.application") apply false

    // Convention plugins (from included build `build-logic`).
    id("tc2.jvm-library-conventions") apply false
    id("tc2.application-conventions") apply false
    id("tc2.test-conventions") apply false

    // Keep IntelliJ IDEA integration (requested).
    idea
}

allprojects {
    version = "2.3.0-beta"

    // Shared properties consumed by subprojects.
    extra["appName"] = "Terminal Control 2"
    extra["buildVersion"] = 42
}

// Apply shared JVM conventions to non-Android subprojects.
configure(subprojects.filter { it.path != ":android" }) {
    apply(plugin = "tc2.jvm-library-conventions")

    dependencies {
        constraints {
            add("implementation", "org.apache.logging.log4j:log4j-core") {
                version {
                    strictly("[2.17, 3[")
                    prefer("2.17.0")
                }
                because(
                    "CVE-2021-44228, CVE-2021-45046, CVE-2021-45105: Log4j vulnerable to remote code execution and other critical security vulnerabilities",
                )
            }
        }
    }
}

