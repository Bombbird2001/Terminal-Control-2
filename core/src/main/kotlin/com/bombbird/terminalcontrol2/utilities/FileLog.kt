package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.files.getExtDir
import com.bombbird.terminalcontrol2.global.BUILD_VERSION
import com.esotericsoftware.minlog.Log
import com.esotericsoftware.minlog.Log.*
import java.io.PrintStream

/** Custom class for redirecting console outputs to a log file */
object FileLog {
    private var printStream: PrintStream? = null

    /**
     * Initializes the log system to use a file
     */
    fun initializeFile() {
        try {
            val fos = getExtDir("Logs/BUILD $BUILD_VERSION.log")?.write(true) ?: return
            printStream = PrintStream(fos)
            System.setOut(printStream)
        } catch (e: Exception) {
            Log.warn("FileLog", "Failed to initialize file log system due to\n$e")
        }

        // Delete logs from older builds
        val logsDir = getExtDir("Logs") ?: return
        val logs = logsDir.list()
        for (log in logs) {
            val version = log.name().substringAfter("BUILD ").substringBefore(".log").toIntOrNull()
            if (version == null || version < BUILD_VERSION) {
                log.delete()
                info("FileLog", "Deleted old log file ${log.name()}")
            }
        }
    }

    /**
     * Logs a message with DEBUG level
     * @param tag The tag of the message
     * @param message The message
     */
    fun debug(tag: String, message: String) {
        if (!DEBUG) return

        Log.debug(tag, message)
    }

    /**
     * Logs a message with INFO level
     * @param tag The tag of the message
     * @param message The message
     */
    fun info(tag: String, message: String) {
        if (!INFO) return

        Log.info(tag, message)
    }

    /**
     * Logs a message with WARN level
     * @param tag The tag of the message
     * @param message The message
     */
    fun warn(tag: String, message: String) {
        if (!WARN) return

        Log.warn(tag, message)
    }

    /**
     * Logs a message with ERROR level
     * @param tag The tag of the message
     * @param message The message
     */
    fun error(tag: String, message: String) {
        if (!ERROR) return

        Log.error(tag, message)
    }

    /** Ends the FileLog system, closing the underlying streams */
    fun close() {
        printStream?.close()
    }
}