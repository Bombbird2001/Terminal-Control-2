package com.bombbird.terminalcontrol2.android

import android.app.Activity
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/** In-app update checker for Android platform */
class InAppUpdateManager(private val activity: Activity) {
    companion object {
        private const val FLEXIBLE_UPDATE_STALENESS_DAYS = 3
    }

    /**
     * Checks for update availability and starts update flow if needed
     */
    fun checkUpdateAvailable() {
        FileLog.info("InAppUpdateManager", "Checking for update availability")
        val appUpdateManager = AppUpdateManagerFactory.create(activity)

        // Returns an intent object that you use to check for an update.
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            FileLog.info("InAppUpdateManager", "Update availability: ${appUpdateInfo.updateAvailability()}")
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                && (appUpdateInfo.clientVersionStalenessDays() ?: Int.MAX_VALUE) >= FLEXIBLE_UPDATE_STALENESS_DAYS) {
                FileLog.info("InAppUpdateManager", "Starting update flow")
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    AndroidLauncher.ACT_IN_APP_UPDATE
                )
            }
        }

        // If checking fails
        appUpdateInfoTask.addOnFailureListener {
            FileLog.info("InAppUpdateManager", "Error checking for update availability:\n$it")
        }
    }
}