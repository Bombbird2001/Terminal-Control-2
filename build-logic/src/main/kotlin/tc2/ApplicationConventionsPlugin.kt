package tc2

import org.gradle.api.Plugin
import org.gradle.api.Project

class ApplicationConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("application")
        pluginManager.apply("tc2.jvm-library-conventions")
    }
}

