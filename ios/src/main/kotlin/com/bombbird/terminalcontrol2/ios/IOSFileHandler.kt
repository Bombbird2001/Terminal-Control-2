package com.bombbird.terminalcontrol2.ios

import com.bombbird.terminalcontrol2.files.ExternalFileHandler

class IOSFileHandler: ExternalFileHandler {
    override fun selectAndReadFromFile(onComplete: (String?) -> Unit, onFailure: (String) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun selectAndSaveToFile(data: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        TODO("Not yet implemented")
    }
}