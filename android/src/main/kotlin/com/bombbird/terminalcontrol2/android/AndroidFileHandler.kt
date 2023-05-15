package com.bombbird.terminalcontrol2.android

import android.content.Intent
import com.badlogic.gdx.backends.android.AndroidApplication
import com.bombbird.terminalcontrol2.files.ExternalFileHandler
import com.bombbird.terminalcontrol2.files.SAVE_EXTENSION
import java.io.IOException

/** File handler for Android platform */
class AndroidFileHandler(private val app: AndroidApplication): ExternalFileHandler {
    private var prevOnComplete: ((String?) -> Unit)? = null
    private var prevOnFailure: ((String) -> Unit)? = null
    private var prevOnSuccess: (() -> Unit)? = null
    private var prevSaveString: String? = null

    override fun selectAndReadFromFile(onComplete: (String?) -> Unit, onFailure: (String) -> Unit) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        prevOnComplete = onComplete
        prevOnFailure = onFailure

        app.startActivityForResult(intent, AndroidLauncher.OPEN_SAVE_FILE)
    }

    override fun selectAndSaveToFile(data: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "exported_save.$SAVE_EXTENSION")
            type = "*/*"
        }

        prevOnSuccess = onSuccess
        prevOnFailure = onFailure
        prevSaveString = data

        app.startActivityForResult(intent, AndroidLauncher.CREATE_SAVE_FILE)
    }

    /**
     * Function to be called when the AndroidApplication class receives the activity result for a save
     * @param data Results of the saving intent
     */
    fun handleSaveResult(data: Intent?) {
        val uri = data?.data
        val saveString = prevSaveString
        if (uri == null || saveString == null) {
            prevOnFailure?.invoke("Error selecting file")
        } else {
            app.contentResolver.openOutputStream(uri)?.let { output ->
                try {
                    output.write(saveString.toByteArray())
                    prevOnSuccess?.invoke()
                } catch (e: IOException) {
                    prevOnFailure?.invoke("Error writing to file")
                } finally {
                    output.close()
                }
            }
        }
        prevOnFailure = null
        prevOnSuccess = null
    }

    /**
     * Function to be called when the AndroidApplication class receives the activity result for a read
     * @param data Results of the reading intent
     */
    fun handleOpenResult(data: Intent?) {
        val uri = data?.data
        if (uri == null) {
            prevOnFailure?.invoke("Error selecting file")
        } else {
            app.contentResolver.openInputStream(uri)?.let { input ->
                try {
                    val allBytes = input.readBytes()
                    prevOnComplete?.invoke(allBytes.decodeToString())
                } catch (e: IOException) {
                    prevOnFailure?.invoke("Error reading from file")
                } finally {
                    input.close()
                }
            }
        }
        prevOnFailure = null
        prevOnComplete = null
    }
}