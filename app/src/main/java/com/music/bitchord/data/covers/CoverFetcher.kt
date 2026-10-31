package com.music.bitchord.data.covers

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source

class CoverFetcher(private val data: String, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val (spec, side) = CoverUri.decode(data) ?: (CoverSpec("invalid-cover", "Your mix", templateId = CoverCatalog.fallback.id) to 480)
        return try {
            val result = AndroidCoverEngine.get(options.context).cover(spec, side)
            SourceFetchResult(ImageSource(result.file.source().buffer(), FileSystem.SYSTEM), "image/png", DataSource.DISK)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            val bytes = withContext(Dispatchers.Default) {
                AndroidCoverRenderer(options.context).render(spec.copy(templateId = CoverCatalog.fallback.id), side).bytes
            }
            SourceFetchResult(ImageSource(bytes.inputStream().source().buffer(), FileSystem.SYSTEM), "image/png", DataSource.MEMORY)
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val value = data.toString()
            return if (value.startsWith(CoverUri.PREFIX)) CoverFetcher(value, options) else null
        }
    }
}
