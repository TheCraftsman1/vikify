package com.vikify.app.metadata

import android.content.Context
import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import android.util.Log
import com.kyant.taglib.TagLib
import com.kyant.taglib.PropertyMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun readTags(filePath: String): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        val file = File(filePath)
        if (!file.exists()) return@withContext result

        try {
             ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val metadata = TagLib.getMetadata(pfd.fd)
                if (metadata != null) {
                    // PropertyMap is TypeAlias for Map<String, Array<String>>
                     metadata.propertyMap.forEach { (key, values) ->
                         if (values.isNotEmpty()) {
                             // Map standard keys to lowercase simple keys expected by UI
                             when(key.uppercase()) {
                                 "TITLE" -> result["title"] = values.first()
                                 "ARTIST" -> result["artist"] = values.first()
                                 "ALBUM" -> result["album"] = values.first()
                                 "DATE", "YEAR" -> result["year"] = values.first()
                                 else -> { /* ignore or add others */ }
                             }
                         }
                     }
                }
            }
        } catch (e: Exception) {
            Log.e("TagRepository", "Failed to read tags", e)
        }
        result
    }

    suspend fun writeTags(
        filePath: String, 
        title: String?, 
        artist: String?, 
        album: String?,
        year: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext Result.failure(Exception("File not found"))

        try {
            // Need read/write access
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
                // First get existing to parse current map, or just create new updates
                val existingMetadata = TagLib.getMetadata(pfd.fd)
                
                // Construct PropertyMap (HashMap<String, Array<String>>)
                // We need to be careful not to wipe other tags if we can help it, 
                // but for now we might only update the ones requested.
                // TagLib.savePropertyMap generally MERGES or OVERWRITES specific keys?
                // The signature is savePropertyMap(fd, PropertyMap). 
                // Let's assume we need to pass the FULL map or it updates specific ones?
                // Usually TagLib's save overwrites the tags.
                
                val newProps = HashMap<String, Array<String>>()
                
                // Copy existing if possible or safe
                existingMetadata?.propertyMap?.let { newProps.putAll(it) }

                if (title != null) newProps["TITLE"] = arrayOf(title)
                if (artist != null) newProps["ARTIST"] = arrayOf(artist)
                if (album != null) newProps["ALBUM"] = arrayOf(album)
                if (year != null) newProps["DATE"] = arrayOf(year) // DATE often used for Year in Vorbis/ID3v2.4
                
                val success = TagLib.savePropertyMap(pfd.fd, newProps)
                if (!success) return@withContext Result.failure(Exception("TagLib save failed"))
            }

            // Force Android MediaStore to rescan file
            scanFile(filePath)
            
            Result.success(true)
        } catch (e: Exception) {
            Log.e("TagRepository", "Failed to write tags", e)
            Result.failure(e)
        }
    }

    private fun scanFile(path: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            null
        ) { _, uri ->
            Log.d("TagRepository", "Scanned $path -> $uri")
        }
    }
}
