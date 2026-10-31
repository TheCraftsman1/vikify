package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.covers.AndroidCoverEngine
import com.music.bitchord.data.covers.AndroidCoverRenderer
import com.music.bitchord.data.covers.CoverCatalog
import com.music.bitchord.data.covers.CoverSpec
import com.music.bitchord.data.covers.CoverUri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoverRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val spec = CoverSpec("language_telugu_melodies", "Telugu Melodies", variant = 1)
    private fun output() = File(context.getExternalFilesDir(null), "cover-review").also { check(it.isDirectory || it.mkdirs()) }

    @Test fun privateCoverUriLoadsThroughTheRealAppImageLoader() = runBlocking {
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
            .data(CoverUri.encode(spec.copy(collectionId = "loader-check:${UUID.randomUUID()}"), 480)).size(480).build())
        assertTrue((result as? ErrorResult)?.throwable?.stackTraceToString() ?: "Expected a decoded cover", result is SuccessResult)
    }

    @Test fun exportCompositionsAtRealAndroidSizes() {
        val renderer = AndroidCoverRenderer(context)
        val output = output()
        for (template in CoverCatalog.templates) for (palette in CoverCatalog.palettes) {
            val rendered = renderer.render(spec.copy(templateId = template.id, paletteId = palette.id), 720)
            assertTrue("Title should fit on ${template.id}/${palette.id}", rendered.titleFits)
            val bitmap = BitmapFactory.decodeByteArray(rendered.bytes, 0, rendered.bytes.size)
            assertEquals(720, bitmap.width); assertEquals(720, bitmap.height)
            assertTrue("Visible title glyphs", whitePixels(bitmap, 65, 510, 650, 650) > 100)
            bitmap.recycle()
            File(output, "${template.id}-${palette.id}.png").writeBytes(rendered.bytes)
        }
        listOf(192, 480).forEach { side ->
            val rendered = renderer.render(spec, side)
            File(output, "telugu-melodies-$side.png").writeBytes(rendered.bytes)
        }
    }

    @Test fun teluguAndLongTitlesUseNativeShapingAndBoundedLayout() {
        val renderer = AndroidCoverRenderer(context)
        val telugu = renderer.render(spec.copy(title = "తెలుగు మెలోడీస్", locale = "te-IN", paletteId = "violet_01"), 720)
        assertTrue(telugu.titleFits)
        val paint = TextPaint().apply { typeface = ResourcesCompat.getFont(context, R.font.sf_pro_display_bold) }
        assertTrue("Telugu fallback glyph must exist", paint.hasGlyph("త"))
        val long = renderer.render(spec.copy(title = "Telugu Melodies for Quiet Evenings and Long Journeys Back Home", paletteId = "teal_01"), 720)
        assertTrue(long.titleFits)
        assertTrue(long.lines in 2..3)
        File(output(), "telugu-script.png").writeBytes(telugu.bytes)
        File(output(), "long-title.png").writeBytes(long.bytes)
    }

    @Test fun missingStyleRendersASafeBrandedCover() {
        val result = AndroidCoverRenderer(context).render(spec.copy(templateId = "missing", paletteId = "missing"), 720)
        assertTrue(result.fallback)
        assertTrue(result.titleFits)
        File(output(), "fallback.png").writeBytes(result.bytes)
    }

    @Test fun repeatedRenderingAndDiskRequestsRemainStable() = runBlocking {
        val renderer = AndroidCoverRenderer(context)
        assertArrayEquals(renderer.render(spec, 192).bytes, renderer.render(spec, 192).bytes)
        val unique = spec.copy(collectionId = "cache-test:${UUID.randomUUID()}")
        val engine = AndroidCoverEngine.get(context)
        val first = engine.cover(unique, 480)
        val second = engine.cover(unique, 480)
        assertFalse(first.cacheHit)
        assertTrue(second.cacheHit)
        assertEquals(first.file, second.file)
        first.file.writeBytes(first.file.readBytes().take(40).toByteArray())
        val repaired = engine.cover(unique, 480)
        assertFalse("A PNG header without image data is not a valid cache hit", repaired.cacheHit)
        assertNotNull(BitmapFactory.decodeFile(repaired.file.absolutePath)?.also { it.recycle() })
        assertNotEquals(first.file, engine.cover(unique.copy(title = "Renamed mix"), 480).file)
    }

    @Test fun exportReferenceComparison() {
        val result = AndroidCoverRenderer(context).render(spec, 720)
        val generated = BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        val reference = context.assets.open("moods/on_repeat.webp").use(BitmapFactory::decodeStream)
        val sheet = Bitmap.createBitmap(1040, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xff101010.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f }
        canvas.drawText("Existing On Repeat", 20f, 42f, paint)
        canvas.drawText("Composed Telugu Melodies", 540f, 42f, paint)
        canvas.drawBitmap(reference, null, Rect(20, 68, 520, 568), null)
        canvas.drawBitmap(generated, null, Rect(540, 68, 1040, 568), null)
        File(output(), "reference-comparison.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        reference.recycle(); generated.recycle(); sheet.recycle()
        // This suite runs on a disposable test profile; allow subsequent manual preview inspection.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            com.music.bitchord.data.settings.AppSettings.setOnboardingComplete()
            assertTrue(context.getSharedPreferences("bitchord_settings", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("onboarding_complete", true).commit())
        }
    }

    private fun whitePixels(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in left until right) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.red(pixel) > 230 && Color.green(pixel) > 230 && Color.blue(pixel) > 230) count++
        }
        return count
    }
}
