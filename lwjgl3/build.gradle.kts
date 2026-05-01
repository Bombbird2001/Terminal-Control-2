import java.util.Locale
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    id("tc2.application-conventions")
    id("tc2.test-conventions")
}

val appName: String by rootProject.extra
val buildVersion: Int by rootProject.extra

application {
    mainClass.set("com.bombbird.terminalcontrol2.lwjgl3.Lwjgl3Launcher")
}

base {
    archivesName.set(appName)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

sourceSets {
    named("main") {
        resources {
            setSrcDirs(listOf("../assetsDesktop"))
            exclude("Libs")
        }
    }
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:${property("gdxVersion")}")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:${property("gdxVersion")}:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-platform:${property("gdxVersion")}:natives-desktop")
    implementation("org.lwjgl:lwjgl-nfd:${property("lwjgl3Version")}")
    implementation("org.lwjgl:lwjgl-nfd:${property("lwjgl3Version")}:natives-windows")
    implementation("org.lwjgl:lwjgl-nfd:${property("lwjgl3Version")}:natives-linux")
    implementation("org.lwjgl:lwjgl-nfd:${property("lwjgl3Version")}:natives-macos")
    implementation("org.lwjgl:lwjgl-nfd:${property("lwjgl3Version")}:natives-macos-arm64")
    implementation(files("../extJars/discord-game-sdk4j-0.5.5.jar"))
    implementation(files("../extJars/jAdapterForNativeTTS-0.12.0.jar"))
    implementation(project(":core"))

    testImplementation(testFixtures(project(":core")))

    // Preserve prior behavior (these are a bit unusual but relied upon by packaging).
    api(files("../core/build/classes/kotlin/main"))
    api(files("build/classes/kotlin/main"))
}

val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    isIgnoreExitValue = true
    if (osName.contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

val checkVersion by tasks.registering {
    doLast {
        val gradleVersion = project.version.toString()
        val gradleVersionCode = buildVersion.toString()

        val text = project.file("../assetsDesktop/BUILD.build").readText()
        val values = text.split(' ')
        if (values.size < 2) {
            throw GradleException("Version: Type string length is ${values.size}, needs 2")
        }
        if (values[0] != gradleVersion || values[1] != gradleVersionCode) {
            throw GradleException(
                "Version: ${values[0]} code ${values[1]} not equal to Gradle's $gradleVersion code $gradleVersionCode",
            )
        }
        println("Version check successful")
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(appName)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(configurations.runtimeClasspath, checkVersion)

    // Must be lazy: some "files(.../build/classes/...)" entries don't exist until after compilation,
    // and eagerly evaluating `isDirectory` would incorrectly fall back to `zipTree(...)` and fail.
    from({
        configurations.runtimeClasspath
            .get()
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })

    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    // Remove duplicate maven metadata
    exclude("META-INF/maven/**")

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    doLast {
        archiveFile.get().asFile.setExecutable(true, false)
    }
}

tasks.register("dist") {
    dependsOn(tasks.named("jar"))
}

val testDataFile by tasks.registering(Test::class) {
    dependsOn("cleanTestDataFile")
    ignoreFailures = true
    workingDir = project.file("../assetsDesktop")
}

tasks.named<Test>("test") {
    dependsOn(project(":core").tasks.named("test"), testDataFile)
}

tasks.register("buildJar") {
    dependsOn("build")
    doLast {
        println("JAR successfully built")
    }
}

// Packaging pipeline (kept as-is, but made lazy/typed).
val baseDir = "C:\\My Apps\\Terminal Control 2"
val macConfigFile = "mac.json"
val win64ConfigFile = "windows64.json"
val linux64ConfigFile = "linux64.json"
val latestVersionFile = "latest.txt"
val packrFile = "packr.jar"
val sevenZipLocation = "C:\\Programs\\7-Zip\\7z.exe"

val copyJar by tasks.registering(Copy::class) {
    dependsOn("buildJar")
    from(tasks.named("jar"))
    into("$baseDir\\Desktop")
    doLast { println("JAR file successfully copied") }
}

data class PackrConfig(
    val platform: String,
    val jdk: String,
    val executable: String,
    val classpath: List<String>,
    val mainclass: String,
    val vmargs: List<String>,
    val minimizejre: String,
    val output: String,
    val icon: String? = null,
)

fun PackrConfig.toJson(pretty: Boolean = true): String {
    fun String.jsonEscape(): String =
        buildString {
            for (c in this@jsonEscape) {
                when (c) {
                    '\\' -> append("\\\\")
                    '\"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }

    fun Any?.toJsonValue(indent: String, level: Int): String {
        val nextIndent = if (pretty) indent.repeat(level + 1) else ""
        val currIndent = if (pretty) indent.repeat(level) else ""
        val newline = if (pretty) "\\n" else ""
        val sep = if (pretty) " " else ""
        return when (this) {
            null -> "null"
            is String -> "\"${this.jsonEscape()}\""
            is Number, is Boolean -> this.toString()
            is List<*> -> {
                if (isEmpty()) "[]"
                else buildString {
                    append("[$newline")
                    this@toJsonValue.forEachIndexed { idx, v ->
                        append(nextIndent)
                        append(v.toJsonValue(indent, level + 1))
                        if (idx != size - 1) append(",")
                        append(newline)
                    }
                    append(currIndent)
                    append("]")
                }
            }
            else -> error("Unsupported JSON value type: ${this::class.java.name}")
        }
    }

    val entries: List<Pair<String, Any?>> =
        listOf(
            "platform" to platform,
            "jdk" to jdk,
            "executable" to executable,
            "classpath" to classpath,
            "mainclass" to mainclass,
            "vmargs" to vmargs,
            "minimizejre" to minimizejre,
            "output" to output,
            "icon" to icon,
        ).filterNot { it.second == null }

    val indent = "  "
    val newline = if (pretty) "\n" else ""
    val sep = if (pretty) " " else ""

    return buildString {
        append("{$newline")
        entries.forEachIndexed { idx, (k, v) ->
            append(if (pretty) indent else "")
            append("\"$k\":$sep")
            append(v.toJsonValue(indent, 1))
            if (idx != entries.size - 1) append(",")
            append(newline)
        }
        append("}")
    }
}

val updateFileVersion by tasks.registering {
    dependsOn(copyJar)
    doLast {
        copy {
            from(baseDir) {
                include(macConfigFile, win64ConfigFile, linux64ConfigFile)
            }
            into("$baseDir\\old")
        }

        val versionStr = version.toString()

        val macConfig =
            PackrConfig(
                platform = "mac",
                jdk = "C:\\Programs\\OpenJDK\\Temurin\\jdk8u462-b08_mac",
                executable = "Terminal Control 2",
                classpath = listOf("$baseDir\\Desktop\\Terminal Control 2-$versionStr.jar"),
                mainclass = application.mainClass.get(),
                vmargs = listOf("-XstartOnFirstThread", "-Xms256M"),
                minimizejre = "soft",
                output = "$baseDir\\Desktop\\$versionStr\\Terminal-Control-2-$versionStr-mac",
                icon = "$baseDir\\Icon.icns",
            )

        val win64Config =
            PackrConfig(
                platform = "windows64",
                jdk = "C:\\Programs\\OpenJDK\\Temurin\\jdk8u462-b08_x86_64",
                executable = "Terminal Control 2",
                classpath = listOf("$baseDir\\Desktop\\Terminal Control 2-$versionStr.jar"),
                mainclass = application.mainClass.get(),
                vmargs = listOf("-Xms256M"),
                minimizejre = "soft",
                output = "$baseDir\\Desktop\\$versionStr\\Terminal-Control-2-$versionStr-windows-64",
            )

        val linux64Config =
            PackrConfig(
                platform = "linux64",
                jdk = "C:\\Programs\\OpenJDK\\Temurin\\jdk8u462-b08_linux64",
                executable = "Terminal Control 2",
                classpath = listOf("$baseDir\\Desktop\\Terminal Control 2-$versionStr.jar"),
                mainclass = application.mainClass.get(),
                vmargs = listOf("-Xms256M"),
                minimizejre = "soft",
                output = "$baseDir\\Desktop\\$versionStr\\Terminal-Control-2-$versionStr-linux-64",
            )

        file("$baseDir\\$macConfigFile").writeText(macConfig.toJson(pretty = true))
        file("$baseDir\\$win64ConfigFile").writeText(win64Config.toJson(pretty = true))
        file("$baseDir\\$linux64ConfigFile").writeText(linux64Config.toJson(pretty = true))
        println("JSON files successfully updated")

        file("$baseDir\\$latestVersionFile").writeText(versionStr)
        println("$latestVersionFile successfully updated")
    }
}

val packrJar by tasks.registering {
    dependsOn(updateFileVersion)
    doLast {
        println(
            "packrJar: skipped external exec (packr) in Gradle 9 Kotlin DSL migration. " +
                "Run packr manually using $packrFile with configs $macConfigFile/$win64ConfigFile/$linux64ConfigFile if needed.",
        )
    }
}

val copyDiscordLibs by tasks.registering {
    dependsOn(packrJar)
    doLast {
        copy {
            println("Copying windows-64 Discord libraries...")
            from("..\\assetsDesktop\\libs\\windows_amd64")
            into("$baseDir\\Desktop\\$version\\Terminal-Control-2-$version-windows-64\\libs\\windows_amd64")
        }
        copy {
            println("Copying linux-64 Discord libraries...")
            from("..\\assetsDesktop\\libs\\linux")
            into("$baseDir\\Desktop\\$version\\Terminal-Control-2-$version-linux-64\\libs\\linux")
        }
        copy {
            println("Copying mac Discord libraries...")
            from("..\\assetsDesktop\\libs\\macos")
            into("$baseDir\\Desktop\\$version\\Terminal-Control-2-$version-mac\\Contents\\Resources\\libs\\macos")
        }
        println("Discord libraries successfully copied")
    }
}

val compressFolder by tasks.registering {
    dependsOn(copyDiscordLibs)
    doLast {
        file("$baseDir\\Desktop\\$version\\Terminal-Control-2-$version-mac")
            .renameTo(file("$baseDir\\Desktop\\$version\\Terminal Control 2.app"))
        println("Mac folder successfully renamed")

        println(
            "compressFolder: skipped external exec (7z) in Gradle 9 Kotlin DSL migration. " +
                "Compress manually using $sevenZipLocation if needed.",
        )
    }
}

tasks.register("packageDesktop") {
    dependsOn(compressFolder)
    doLast {
        file("$baseDir\\Desktop\\$version\\BUILD.txt").writeText(
            "Autogenerated build\nVersion $version, build $buildVersion",
        )
        println("Packaging successful")
    }
}

