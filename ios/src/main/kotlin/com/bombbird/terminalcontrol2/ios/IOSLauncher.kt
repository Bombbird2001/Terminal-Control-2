@file:JvmName("IOSLauncher")

package com.bombbird.terminalcontrol2.ios

import org.robovm.apple.foundation.NSAutoreleasePool
import org.robovm.apple.uikit.UIApplication

import com.badlogic.gdx.backends.iosrobovm.IOSApplication
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration
import com.bombbird.terminalcontrol2.TerminalControl2

/** Launches the iOS (RoboVM) application. */
class IOSLauncher : IOSApplication.Delegate() {
	override fun createApplication(): IOSApplication {
		return IOSApplication(TerminalControl2(IOSFileHandler()), IOSApplicationConfiguration().apply {
            // Configure your application here.
        })
	}

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val pool = NSAutoreleasePool()
            val principalClass: Class<UIApplication>? = null
            val delegateClass = IOSLauncher::class.java
            UIApplication.main(args, principalClass, delegateClass)
            pool.close()
        }
    }
}