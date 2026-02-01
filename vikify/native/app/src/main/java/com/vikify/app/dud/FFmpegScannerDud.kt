package com.vikify.app.utils.scanners

import com.vikify.app.models.SongTempData
import java.io.File

class FFmpegScanner() : MetadataScanner {
    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        throw NotImplementedError()
    }

    companion object {
        const val VERSION_STRING = "N/A"
    }
}
