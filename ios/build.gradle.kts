plugins {
    // RoboVM plugin is provided via root buildscript classpath.
    id("robovm")
}

val appName: String by rootProject.extra

// Equivalent of old ext { mainClassName = ... }
extra["mainClassName"] = "com.bombbird.terminalcontrol2.ios.IOSLauncher"

// Keep existing RoboVM task wiring.
tasks.named("launchIPhoneSimulator") { dependsOn(tasks.named("build")) }
tasks.named("launchIPadSimulator") { dependsOn(tasks.named("build")) }
tasks.named("launchIOSDevice") { dependsOn(tasks.named("build")) }
tasks.named("createIPA") { dependsOn(tasks.named("build")) }

dependencies {
    implementation("com.badlogicgames.gdx:gdx-backend-robovm:${property("gdxVersion")}")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:${property("gdxVersion")}:natives-ios")
    implementation("com.badlogicgames.gdx:gdx-platform:${property("gdxVersion")}:natives-ios")
    implementation("com.mobidevelop.robovm:robovm-cocoatouch:${property("robovmVersion")}")
    implementation("com.mobidevelop.robovm:robovm-rt:${property("robovmVersion")}")
    implementation(project(":core"))
}

