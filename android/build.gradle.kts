import java.util.Properties
import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
}

val appName: String by rootProject.extra
val buildVersion: Int by rootProject.extra

android {
    namespace = "com.bombbird.terminalcontrol2"
    buildToolsVersion = "36.0.0"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src/main/java", "src/main/kotlin")
            aidl.srcDirs("src/main/java", "src/main/kotlin")
            renderscript.srcDirs("src/main/java", "src/main/kotlin")
            res.srcDirs("res")
            assets.srcDirs("../assets")
            jniLibs.srcDirs("libs")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/robovm/ios/robovm.xml",
                "META-INF/DEPENDENCIES.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/dependencies.txt",
            )
            pickFirsts += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE",
                "META-INF/license.txt",
                "META-INF/LGPL2.1",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE",
                "META-INF/notice.txt",
            )
        }
    }

    defaultConfig {
        applicationId = "com.bombbird.terminalcontrol2"
        minSdk = 23
        targetSdk = 36
        versionCode = buildVersion
        versionName = project.version.toString()
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        renderScript = true
        aidl = true
        buildConfig = true
    }
}

val natives by configurations.creating

configurations.configureEach {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${property("androidDesugarJdkVersion")}")
    implementation("com.badlogicgames.gdx:gdx-backend-android:${property("gdxVersion")}")
    implementation("com.google.android.gms:play-services-games-v2:${property("playServicesGameVersion")}")
    implementation("com.google.android.gms:play-services-auth:${property("playServicesAuthVersion")}")
    implementation("com.google.api-client:google-api-client-android:${property("googleApiClientVersion")}")
    implementation("com.google.apis:google-api-services-drive:${property("googleApiServicesDriveVersion")}")
    implementation("androidx.datastore:datastore-preferences:${property("androidxDatastoreVersion")}")
    implementation(project(":core"))

    natives("com.badlogicgames.gdx:gdx-freetype-platform:${property("gdxVersion")}:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:${property("gdxVersion")}:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:${property("gdxVersion")}:natives-x86")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:${property("gdxVersion")}:natives-x86_64")
    natives("com.badlogicgames.gdx:gdx-platform:${property("gdxVersion")}:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:${property("gdxVersion")}:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:${property("gdxVersion")}:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:${property("gdxVersion")}:natives-x86_64")

    // Keep Log4j pinned to a safe baseline.
    constraints {
        implementation("org.apache.logging.log4j:log4j-core") {
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

// Extract native .so files into libs/* so they get packaged with the APK.
val copyAndroidNatives by tasks.registering {
    inputs.files(natives)
    outputs.dir(layout.projectDirectory.dir("libs"))

    doFirst {
        listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86").forEach { abi ->
            file("libs/$abi").mkdirs()
        }

        natives
            .copy()
            .files
            .forEach { jarFile ->
                val outputDir =
                    when {
                        jarFile.name.endsWith("natives-armeabi-v7a.jar") -> file("libs/armeabi-v7a")
                        jarFile.name.endsWith("natives-arm64-v8a.jar") -> file("libs/arm64-v8a")
                        jarFile.name.endsWith("natives-x86_64.jar") -> file("libs/x86_64")
                        jarFile.name.endsWith("natives-x86.jar") -> file("libs/x86")
                        else -> null
                    }
                if (outputDir != null) {
                    copy {
                        from(zipTree(jarFile))
                        into(outputDir)
                        include("*.so")
                    }
                }
            }
    }
}

// Stable wiring: ensure natives are copied before Android build work begins.
// (Avoids Gradle 9 brittle hooks like whenTaskAdded.)
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(copyAndroidNatives)
}

tasks.register<Exec>("run") {
    val localProperties = project.file("../local.properties")
    val sdkPath =
        if (localProperties.exists()) {
            val props = Properties()
            localProperties.inputStream().use { props.load(it) }
            props.getProperty("sdk.dir") ?: System.getenv("ANDROID_SDK_ROOT")
        } else {
            System.getenv("ANDROID_SDK_ROOT")
        }

    val adb = "$sdkPath/platform-tools/adb"
    commandLine(
        adb,
        "shell",
        "am",
        "start",
        "-n",
        "com.bombbird.terminalcontrol2/com.bombbird.terminalcontrol2.android.AndroidLauncher",
    )
}

