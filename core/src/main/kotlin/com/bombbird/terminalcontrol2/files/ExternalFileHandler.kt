package com.bombbird.terminalcontrol2.files

/** Interface for external file operations specific to the platform */
interface ExternalFileHandler {
    /**
     * Gets the user to select the file and read data from it
     * @return the read string from the file
     */
    fun selectAndReadFromFile(): String?

    /**
     * Gets the user to select the file to save to, and saves the data to it
     * @param data the data string to save to the file
     */
    fun selectAndSaveToFile(data: String): Boolean
}