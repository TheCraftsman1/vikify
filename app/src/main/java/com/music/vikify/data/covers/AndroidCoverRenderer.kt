package com.music.vikify.data.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.LocaleList
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.music.vikify.R
import java.io.ByteArrayOutputStream
import kotlin.random.Random

data class RenderedCover(val bytes: ByteArray, val lines: Int, val fontSize: Float, val titleFits: Boolean, val fallback: Boolean)

/** Native, static artwork: reusable paths and lighting, then an independent logo and shaped title. */
class AndroidCoverRenderer(context: Context) {
    private val context = context.applicationContext
    private val titleTypeface by lazy {
        try { ResourcesCompat.getFont(this.context, R.font.sf_pro_display_bold) ?: Typeface.DEFAULT_BOLD }
        catch (failure: Exception) { Typeface.DEFAULT_BOLD }
    }

    fun render(spec: CoverSpec, requestedSize: Int): RenderedCover {
        val size = CoverCatalog.sizeBucket(requestedSize)
        val template = CoverCatalog.template(spec)
        val palette = CoverCatalog.palette(spec)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(size / 1024f, size / 1024f)
            canvas.drawColor(0xff101010.toInt())
            val frame = Path().apply { addRoundRect(RectF(25f, 21f, 999f, 995f), 58f, 58f, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(frame)
            canvas.drawPaint(Paint().apply {
                shader = LinearGradient(50f, 30f, 970f, 1000f, intArrayOf(palette.shadow, palette.background, 0xff080b08.toInt()),
                    floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
            })
            glow(canvas, 790f, -80f, 710f, palette.primary, 0.52f)
            glow(canvas, 930f, 660f, 480f, palette.primary, 0.25f)
            val shift = (spec.variant - 1) * 15f
            canvas.save(); canvas.translate(shift, 0f)
            when (template.composition) {
                CoverComposition.WAVE -> wave(canvas, palette)
                CoverComposition.RIBBON -> ribbon(canvas, palette)
                CoverComposition.ORBIT -> orbit(canvas, palette)
                CoverComposition.FALLBACK -> glow(canvas, 850f, 240f, 580f, palette.primary, 0.4f)
            }
            canvas.restore()
            grain(canvas, spec.variant)
            canvas.drawRect(25f, 620f, 999f, 995f, Paint().apply {
                shader = LinearGradient(0f, 610f, 0f, 995f,
                    intArrayOf(Color.TRANSPARENT, alpha(palette.background, 0.35f), alpha(palette.background, 0.78f)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
            logo(canvas)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = Color.WHITE; typeface = titleTypeface
                textLocales = LocaleList.forLanguageTags(spec.locale)
                setShadowLayer(9f, 0f, 3f, 0x99000000.toInt())
            }
            val layout = fit(spec.title, paint)
            val ink = inkBounds(spec.title, layout, paint)
            val titleBottom = 896f
            val accentBottom = 916f
            val accentHeight = (ink.height() + 36f).coerceIn(104f, 225f)
            canvas.drawRoundRect(RectF(25f, accentBottom - accentHeight, 58f, accentBottom), 6f, 6f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent })
            canvas.save(); canvas.translate(92f, titleBottom - ink.bottom); layout.draw(canvas); canvas.restore()
            canvas.restore()
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
            }
            val fits = layout.height <= 225 && (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 }
            return RenderedCover(bytes, layout.lineCount, paint.textSize, fits,
                template.composition == CoverComposition.FALLBACK || palette.id != spec.paletteId)
        } finally { bitmap.recycle() }
    }

    private fun fit(title: String, paint: TextPaint): StaticLayout {
        fun layout(): StaticLayout {
            val builder = StaticLayout.Builder.obtain(title, 0, title.length, paint, 830)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false).setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setLineSpacing(0f, 0.98f)
            if (Build.VERSION.SDK_INT >= 28) builder.setUseLineSpacingFromFallbacks(true)
            return builder.build()
        }
        for (size in listOf(88f, 82f, 76f, 70f, 64f, 58f, 52f, 46f, 40f, 34f)) {
            paint.textSize = size
            val result = layout()
            if (result.height <= 225 && (0 until result.lineCount).all { result.getEllipsisCount(it) == 0 }) return result
        }
        return layout()
    }

    /** Optical alignment uses actual glyphs, since fallback scripts have different font padding. */
    private fun inkBounds(title: String, layout: StaticLayout, paint: TextPaint): RectF {
        val result = RectF(0f, Float.MAX_VALUE, 830f, -Float.MAX_VALUE)
        val bounds = Rect()
        for (line in 0 until layout.lineCount) {
            paint.getTextBounds(title, layout.getLineStart(line), layout.getLineEnd(line), bounds)
            val baseline = layout.getLineBaseline(line)
            result.top = minOf(result.top, baseline + bounds.top.toFloat())
            result.bottom = maxOf(result.bottom, baseline + bounds.bottom.toFloat())
        }
        return result
    }

    private fun wave(canvas: Canvas, palette: CoverPalette) {
        glossy(canvas, Path().apply {
            moveTo(430f, -100f); lineTo(598f, -100f); lineTo(598f, 246f)
            cubicTo(598f, 330f, 690f, 330f, 690f, 246f); lineTo(690f, 170f)
            cubicTo(690f, 70f, 824f, 70f, 824f, 170f); lineTo(824f, 352f)
            cubicTo(824f, 455f, 767f, 475f, 707f, 475f)
            cubicTo(525f, 475f, 430f, 400f, 430f, 266f); close()
        }, palette)
        glossy(canvas, Path().apply {
            moveTo(928f, -130f); lineTo(1080f, -130f); lineTo(1080f, 448f)
            cubicTo(1080f, 500f, 1045f, 505f, 1015f, 464f); lineTo(949f, 380f)
            cubicTo(872f, 288f, 928f, 205f, 928f, 125f); close()
        }, palette)
        glossy(canvas, Path().apply {
            moveTo(726f, 780f); lineTo(726f, 508f); cubicTo(726f, 405f, 861f, 405f, 861f, 508f)
            lineTo(861f, 572f); cubicTo(861f, 653f, 932f, 653f, 932f, 572f); lineTo(932f, 510f)
            cubicTo(932f, 405f, 1065f, 405f, 1065f, 510f); lineTo(1065f, 1004f); lineTo(1008f, 953f)
            cubicTo(932f, 905f, 932f, 862f, 932f, 774f); lineTo(932f, 703f)
            cubicTo(932f, 626f, 861f, 626f, 861f, 703f); lineTo(861f, 780f)
            cubicTo(861f, 882f, 726f, 882f, 726f, 780f); close()
        }, palette)
        sphere(canvas, 891f, 382f, 65f, palette)
    }

    private fun ribbon(canvas: Canvas, palette: CoverPalette) {
        val center = Path().apply {
            moveTo(620f, -140f); cubicTo(1020f, 20f, 1020f, 250f, 722f, 275f)
            cubicTo(370f, 300f, 490f, 610f, 820f, 560f)
            cubicTo(1080f, 518f, 1170f, 750f, 1000f, 890f)
        }
        glossy(canvas, stroke(center, 150f), palette)
        sphere(canvas, 908f, 115f, 46f, palette)
    }

    private fun orbit(canvas: Canvas, palette: CoverPalette) {
        canvas.save(); canvas.rotate(-28f, 784f, 280f)
        val ring = Path().apply { addOval(RectF(513f, -105f, 1055f, 590f), Path.Direction.CW) }
        glossy(canvas, stroke(ring, 120f), palette)
        canvas.restore()
        glossy(canvas, stroke(Path().apply { moveTo(1040f, 540f); cubicTo(710f, 440f, 658f, 800f, 1060f, 894f) }, 142f), palette)
        sphere(canvas, 650f, 432f, 76f, palette)
    }

    private fun stroke(path: Path, width: Float) = Path().also { result ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = width
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }.getFillPath(path, result)
    }

    private fun glossy(canvas: Canvas, path: Path, palette: CoverPalette) {
        val bounds = RectF().also { path.computeBounds(it, true) }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = alpha(palette.primary, 0.4f); style = Paint.Style.STROKE; strokeWidth = 24f
            maskFilter = BlurMaskFilter(31f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(palette.primary, palette.highlight, palette.primary, palette.shadow, palette.primary),
                floatArrayOf(0f, 0.2f, 0.39f, 0.7f, 1f), Shader.TileMode.CLAMP)
        })
        canvas.save(); canvas.clipPath(path)
        canvas.drawRect(bounds, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(bounds.right - bounds.width() * 0.18f, bounds.centerY(), bounds.width() * 0.84f,
                intArrayOf(alpha(palette.background, 0.96f), alpha(palette.shadow, 0.8f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        })
        canvas.restore()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7f; maskFilter = BlurMaskFilter(3.5f, BlurMaskFilter.Blur.NORMAL)
            shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(alpha(palette.highlight, 0.9f), alpha(palette.primary, 0.12f), alpha(palette.highlight, 0.7f)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.4f
            shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                alpha(palette.highlight, 0.6f), alpha(palette.primary, 0.1f), Shader.TileMode.CLAMP)
        })
    }

    private fun sphere(canvas: Canvas, x: Float, y: Float, radius: Float, palette: CoverPalette) {
        glow(canvas, x, y, radius * 1.8f, palette.primary, 0.3f)
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x - radius * 0.4f, y - radius * 0.5f, radius * 1.65f,
                intArrayOf(palette.highlight, palette.primary, palette.shadow), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, opacity: Float) {
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, radius, alpha(color, opacity), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
    }

    private fun grain(canvas: Canvas, variant: Int) {
        val random = Random(37 + variant)
        val paint = Paint().apply { strokeWidth = 1.2f }
        repeat(6_000) {
            paint.color = if (it % 2 == 0) 0x0cffffff else 0x11000000
            canvas.drawPoint(random.nextFloat() * 1024, random.nextFloat() * 1024, paint)
        }
    }

    private fun logo(canvas: Canvas) {
        canvas.save(); canvas.translate(88f, 68f); canvas.scale(104f / 514f, 104f / 514f); canvas.translate(-255f, -202f)
        val drawable = context.getDrawable(R.drawable.ic_logo)
        if (drawable != null) { drawable.setBounds(0, 0, 1024, 1024); drawable.draw(canvas) }
        canvas.restore()
    }

    private fun alpha(color: Int, opacity: Float) = (color and 0x00ffffff) or ((opacity.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
