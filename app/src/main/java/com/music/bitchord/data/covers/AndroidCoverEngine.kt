package com.music.bitchord.data.covers

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import com.music.bitchord.data.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidCoverEngine(context: Context) {
    private val renderer = AndroidCoverRenderer(context)
    private val disk = CoverDiskCache(File(context.cacheDir, "composed_covers"), valid = { file, size ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outMimeType != "image/png" || bounds.outWidth != size || bounds.outHeight != size) false else {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val valid = bitmap != null && bitmap.width == size && bitmap.height == size
            bitmap?.recycle()
            valid
        }
    })

    suspend fun cover(spec: CoverSpec, size: Int): CachedCover = disk.get(spec, size, "android-${Build.VERSION.SDK_INT}") { requested, side ->
        try { renderer.render(requested, side).bytes }
        catch (failure: Exception) {
            DebugLog.w("CoverRenderer", "Using simple cover fallback", failure)
            renderer.render(requested.copy(templateId = CoverCatalog.fallback.id), side).bytes
        }
    }.also { if (it.keptPrevious) DebugLog.w("CoverRenderer", "Keeping previous cover after a render failure") }

    suspend fun clear() = disk.clear()

    companion object {
        @Volatile private var shared: AndroidCoverEngine? = null
        fun get(context: Context): AndroidCoverEngine = shared ?: synchronized(this) {
            shared ?: AndroidCoverEngine(context.applicationContext).also { shared = it }
        }
    }
}
