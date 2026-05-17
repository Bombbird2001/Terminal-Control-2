plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}

dependencies {
    // Kotlin DSL scripts will apply these plugins in consuming builds.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
}

gradlePlugin {
    plugins {
        register("tc2JvmLibraryConventions") {
            id = "tc2.jvm-library-conventions"
            implementationClass = "tc2.JvmLibraryConventionsPlugin"
        }
        register("tc2ApplicationConventions") {
            id = "tc2.application-conventions"
            implementationClass = "tc2.ApplicationConventionsPlugin"
        }
        register("tc2TestConventions") {
            id = "tc2.test-conventions"
            implementationClass = "tc2.TestConventionsPlugin"
        }
    }
}

