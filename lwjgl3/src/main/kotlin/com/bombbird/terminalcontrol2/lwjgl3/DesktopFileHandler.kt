package com.bombbird.terminalcontrol2.lwjgl3

import com.bombbird.terminalcontrol2.files.ExternalFileHandler
import com.bombbird.terminalcontrol2.files.SAVE_EXTENSION
import com.esotericsoftware.minlog.Log
import ktx.assets.toAbsoluteFile
import org.lwjgl.system.MemoryUtil.memAllocPointer
import org.lwjgl.util.nfd.NativeFileDialog

/** File handler for Desktop platform */
class DesktopFileHandler: ExternalFileHandler {
    override fun selectAndReadFromFile(onComplete: (String?) -> Unit, onFailure: (String) -> Unit) {
        val outputPath = memAllocPointer(1)

        val path = when (NativeFileDialog.NFD_OpenDialog(SAVE_EXTENSION, "", outputPath)) {
            NativeFileDialog.NFD_CANCEL -> return
            NativeFileDialog.NFD_ERROR -> {
                Log.info("DesktopFileHandler", "NFD error occurred")
                return onFailure("Error opening selected file")
            }
            NativeFileDialog.NFD_OKAY -> {
                val res = outputPath.stringUTF8
                NativeFileDialog.nNFD_Free(outputPath.get(0))
                res
            }
            else -> return
        } ?: return onFailure("Invalid file path")

        val importHandle = path.toAbsoluteFile()
        onComplete(importHandle.readString())
    }

    override fun selectAndSaveToFile(data: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val outputPath = memAllocPointer(1)

        val path = when (NativeFileDialog.NFD_SaveDialog(SAVE_EXTENSION, "", outputPath)) {
            NativeFileDialog.NFD_CANCEL -> return
            NativeFileDialog.NFD_ERROR -> {
                Log.info("DesktopFileHandler", "NFD error occurred")
                return onFailure("Error opening selected file")
            }
            NativeFileDialog.NFD_OKAY -> {
                val res = outputPath.stringUTF8
                NativeFileDialog.nNFD_Free(outputPath.get(0))
                res
            }
            else -> return
        } ?: return onFailure("Invalid file path")

        val exportHandle = path.toAbsoluteFile()
        exportHandle.writeString(data, false)
        onSuccess()
    }
}