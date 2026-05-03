plugins {
    id("java-test-fixtures")
    id("com.google.devtools.ksp")
    jacoco
    id("tc2.test-conventions")
}

val appName: String by rootProject.extra

base {
    archivesName.set("$appName-core")
}

dependencies {
    api("com.badlogicgames.gdx:gdx-freetype:${property("gdxVersion")}")
    api("com.badlogicgames.gdx:gdx:${property("gdxVersion")}")
    api("com.badlogicgames.ashley:ashley:${property("ashleyVersion")}")
    api("com.esotericsoftware:kryo:${property("kryoVersion")}")
    api("com.github.crykn:kryonet:${property("kryoNetVersion")}")
    api("io.github.libktx:ktx-actors:${property("ktxVersion")}")
    api("io.github.libktx:ktx-app:${property("ktxVersion")}")
    api("io.github.libktx:ktx-ashley:${property("ktxVersion")}")
    api("io.github.libktx:ktx-assets-async:${property("ktxVersion")}")
    api("io.github.libktx:ktx-assets:${property("ktxVersion")}")
    api("io.github.libktx:ktx-async:${property("ktxVersion")}")
    api("io.github.libktx:ktx-collections:${property("ktxVersion")}")
    api("io.github.libktx:ktx-freetype-async:${property("ktxVersion")}")
    api("io.github.libktx:ktx-freetype:${property("ktxVersion")}")
    api("io.github.libktx:ktx-graphics:${property("ktxVersion")}")
    api("io.github.libktx:ktx-json:${property("ktxVersion")}")
    api("io.github.libktx:ktx-log:${property("ktxVersion")}")
    api("io.github.libktx:ktx-math:${property("ktxVersion")}")
    api("io.github.libktx:ktx-scene2d:${property("ktxVersion")}")
    api("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlinVersion")}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")
    api("com.squareup.okhttp3:okhttp:${property("okhttpVersion")}")
    api("com.squareup.moshi:moshi-kotlin:${property("moshiKotlinVersion")}")
    api("com.squareup.moshi:moshi-adapters:${property("moshiKotlinVersion")}")
    api("commons-codec:commons-codec:${property("apacheCommonsVersion")}")

    ksp("com.squareup.moshi:moshi-kotlin-codegen:${property("moshiKotlinVersion")}")

    testFixturesApi("io.kotest:kotest-runner-junit5:${property("kotestVersion")}")
    testFixturesApi("io.kotest:kotest-framework-datatest:${property("kotestVersion")}")
}

jacoco {
    toolVersion = property("jacocoVersion").toString()
}

tasks.named<Test>("test") {
    // Preserve prior behavior: always re-run tests.
    dependsOn("cleanTest")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}

