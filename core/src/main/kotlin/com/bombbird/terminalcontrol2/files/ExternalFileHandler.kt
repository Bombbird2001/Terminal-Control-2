package com.bombbird.terminalcontrol2.files

const val SAVE_EXTENSION = "tc2sav"

/** Interface for external file operations specific to the platform */
interface ExternalFileHandler {
    /**
     * Gets the user to select the file and read data from it
     * @param onComplete the function to be called with the read data
     * @param onFailure the function to be called when read fails
     */
    fun selectAndReadFromFile(onComplete: (String?) -> Unit, onFailure: (String) -> Unit)

    /**
     * Gets the user to select the file to save to, and saves the data to it
     * @param data the data string to write to the file
     * @param onSuccess the function to be called when file is saved successfully
     * @param onFailure the function to be called when saving fails
     */
    fun selectAndSaveToFile(data: String, onSuccess: () -> Unit, onFailure: (String) -> Unit)
}