package com.bombbird.terminalcontrol2.lwjgl3

import com.bombbird.terminalcontrol2.files.ExternalFileHandler
import com.esotericsoftware.minlog.Log
import ktx.assets.toAbsoluteFile
import org.lwjgl.system.MemoryUtil.memAllocPointer
import org.lwjgl.util.nfd.NativeFileDialog

/** File handler for Desktop platform */
class DesktopFileHandler: ExternalFileHandler {
    override fun selectAndReadFromFile(): String? {
        val outputPath = memAllocPointer(1)

        return when (NativeFileDialog.NFD_OpenDialog("tc2sav", "", outputPath)) {
            NativeFileDialog.NFD_CANCEL -> null
            NativeFileDialog.NFD_ERROR -> {
                Log.info("DesktopFileHandler", "NFD error occurred")
                null
            }
            NativeFileDialog.NFD_OKAY -> {
                val res = outputPath.stringUTF8
                NativeFileDialog.nNFD_Free(outputPath.get(0))
                res
            }
            else -> null
        }
    }

    override fun selectAndSaveToFile(data: String): Boolean {
        val outputPath = memAllocPointer(1)

        val path = when (NativeFileDialog.NFD_SaveDialog("tc2sav", "", outputPath)) {
            NativeFileDialog.NFD_CANCEL -> null
            NativeFileDialog.NFD_ERROR -> {
                Log.info("DesktopFileHandler", "NFD error occurred")
                null
            }
            NativeFileDialog.NFD_OKAY -> {
                val res = outputPath.stringUTF8
                NativeFileDialog.nNFD_Free(outputPath.get(0))
                res
            }
            else -> null
        } ?: return false

        val exportHandle = path.toAbsoluteFile()
        exportHandle.writeString(data, false)
        return true
    }
}