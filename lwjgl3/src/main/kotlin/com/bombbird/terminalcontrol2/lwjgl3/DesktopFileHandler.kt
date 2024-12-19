package com.bombbird.terminalcontrol2.lwjgl3

import com.bombbird.terminalcontrol2.files.ExternalFileHandler
import com.bombbird.terminalcontrol2.files.SAVE_EXTENSION
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.assets.toAbsoluteFile
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil.memAllocPointer
import org.lwjgl.util.nfd.NFDFilterItem
import org.lwjgl.util.nfd.NativeFileDialog

/** File handler for Desktop platform */
class DesktopFileHandler: ExternalFileHandler {
    override fun selectAndReadFromFile(onComplete: (String?) -> Unit, onFailure: (String) -> Unit) {
        val outputPath = memAllocPointer(1)
        val noFilter = NFDFilterItem.create(0)

        val path = when (NativeFileDialog.NFD_OpenDialog(outputPath, noFilter, SAVE_EXTENSION)) {
            NativeFileDialog.NFD_CANCEL -> {
                freeResources(outputPath, noFilter)
                return
            }
            NativeFileDialog.NFD_ERROR -> {
                freeResources(outputPath, noFilter)
                FileLog.info("DesktopFileHandler", "NFD error occurred")
                return onFailure("Error opening selected file")
            }
            NativeFileDialog.NFD_OKAY -> {
                val res = outputPath.stringUTF8
                freeResources(outputPath, noFilter)
                res
            }
            else -> return
        }

        val importHandle = path.toAbsoluteFile()
        onComplete(importHandle.readString())
    }

    override fun selectAndSaveToFile(data: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val outputPath = memAllocPointer(1)
        val noFilter = NFDFilterItem.create(0)

        val path = when (NativeFileDialog.NFD_SaveDialog(outputPath, noFilter, SAVE_EXTENSION, "")) {
            NativeFileDialog.NFD_CANCEL -> {
                freeResources(outputPath, noFilter)
                return
            }
            NativeFileDialog.NFD_ERROR -> {
                freeResources(outputPath, noFilter)
                FileLog.info("DesktopFileHandler", "NFD error occurred")
                return onFailure("Error opening selected file")
            }
            NativeFileDialog.NFD_OKAY -> {
                val res = outputPath.stringUTF8
                freeResources(outputPath, noFilter)
                res
            }
            else -> return
        }

        val exportHandle = path.toAbsoluteFile()
        exportHandle.writeString(data, false)
        onSuccess()
    }

    private fun freeResources(outputPath: PointerBuffer, filter: NFDFilterItem.Buffer) {
        NativeFileDialog.nNFD_FreePath(outputPath.get(0))
        filter.free()
    }
}