package tc2

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class TestConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

