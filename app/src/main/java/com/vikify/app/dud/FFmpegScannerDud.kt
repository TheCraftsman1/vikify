package com.vikify.app.features.scanners

import com.vikify.app.data.repository.models.SongTempData
import com.vikify.app.features.scanners.MetadataScanner
import java.io.File

class FFmpegScanner() : MetadataScanner {
    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        throw NotImplementedError()
    }

    companion object {
        const val VERSION_STRING = "N/A"
    }
}
