import org.gradle.jvm.tasks.Jar

plugins {
    id("com.google.devtools.ksp")
    id("tc2.application-conventions")
    id("tc2.test-conventions")
    jacoco
}

application {
    mainClass.set("com.bombbird.terminalcontrol2.relay.RelayServer")
}

base {
    archivesName.set("Relay_Server")
}

dependencies {
    implementation(project(":core"))

    ksp("com.squareup.moshi:moshi-kotlin-codegen:${property("moshiKotlinVersion")}")

    testImplementation(testFixtures(project(":core")))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })

    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/maven/**")

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    doLast {
        archiveFile.get().asFile.setExecutable(true, false)
    }
}

jacoco {
    toolVersion = property("jacocoVersion").toString()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}

tasks.test {
    dependsOn("cleanTest")
    finalizedBy(tasks.jacocoTestReport)
}

