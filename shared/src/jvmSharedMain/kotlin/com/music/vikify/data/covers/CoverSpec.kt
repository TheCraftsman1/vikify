package com.music.vikify.data.covers

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

enum class CoverComposition { WAVE, RIBBON, ORBIT, FALLBACK }
data class CoverTemplate(val id: String, val name: String, val composition: CoverComposition, val version: Int = 1)
data class CoverPalette(val id: String, val name: String, val background: Int, val shadow: Int,
    val primary: Int, val highlight: Int, val accent: Int, val version: Int = 1)

/** Visual inputs only. Song membership, generation time and playback state do not belong in this identity. */
data class CoverSpec(
    val collectionId: String, val title: String, val locale: String = "en-IN",
    val templateId: String = "glossy_wave_01", val paletteId: String = "lime_01", val variant: Int = 0,
    val templateVersion: Int = 1, val paletteVersion: Int = 1, val logoVersion: Int = 1,
    val typographyVersion: Int = 2, val rendererVersion: Int = 1,
) {
    init {
        require(collectionId.isNotBlank() && collectionId.length <= 200)
        require(title.isNotBlank() && title.length <= 240 && title.none { it < ' ' })
        require(locale.length <= 40 && templateId.length <= 80 && paletteId.length <= 80)
        require(variant in 0..2)
    }

    fun cacheKey(size: Int, backend: String = "android-canvas"): String = hash(listOf(
        collectionId, title, locale, templateId, paletteId, variant.toString(), templateVersion.toString(),
        paletteVersion.toString(), logoVersion.toString(), typographyVersion.toString(), rendererVersion.toString(),
        CoverCatalog.sizeBucket(size).toString(), backend,
    ).joinToString("") { "${it.length}:$it" })
}

object CoverCatalog {
    val templates = listOf(
        CoverTemplate("glossy_wave_01", "Glossy wave", CoverComposition.WAVE),
        CoverTemplate("ribbon_01", "Ribbon", CoverComposition.RIBBON),
        CoverTemplate("orbit_01", "Orbit", CoverComposition.ORBIT),
    )
    val fallback = CoverTemplate("fallback_01", "Simple", CoverComposition.FALLBACK)
    val palettes = listOf(
        CoverPalette("lime_01", "Lime", 0xff0d1507.toInt(), 0xff172a05.toInt(), 0xffa7ec00.toInt(), 0xffefffb3.toInt(), 0xffb6ff00.toInt()),
        CoverPalette("violet_01", "Violet", 0xff100a20.toInt(), 0xff321344.toInt(), 0xffad60ed.toInt(), 0xffffd9ff.toInt(), 0xffc990ff.toInt()),
        CoverPalette("teal_01", "Teal", 0xff061819.toInt(), 0xff123d42.toInt(), 0xff37d7bb.toInt(), 0xffd8fff0.toInt(), 0xff72f9da.toInt()),
        CoverPalette("ember_01", "Ember", 0xff1a0e07.toInt(), 0xff4b240d.toInt(), 0xffee8c30.toInt(), 0xffffe4b9.toInt(), 0xffffb55b.toInt()),
    )
    fun template(spec: CoverSpec) = templates.firstOrNull { it.id == spec.templateId && it.version == spec.templateVersion } ?: fallback
    fun palette(spec: CoverSpec) = palettes.firstOrNull { it.id == spec.paletteId && it.version == spec.paletteVersion } ?: palettes.first()
    fun sizeBucket(requested: Int) = when { requested <= 192 -> 192; requested <= 480 -> 480; else -> 720 }
    fun variant(id: String) = ((hash(id).take(8).toLong(16) and 0x7fffffff) % 3).toInt()
    fun dynamic(id: String, title: String, locale: String = "en-IN") = CoverSpec(id, title(title), locale,
        templateId = templates[variant(id)].id, paletteId = palettes[(hash(id).take(8).toLong(16) % palettes.size).toInt()].id, variant = variant(id))

    fun title(input: String): String {
        val clean = input.replace(Regex("[\\p{Cc}\\s]+"), " ").trim()
        if (clean.isEmpty()) return "Your mix"
        // Do not bisect a supplementary character when limiting user supplied titles.
        val end = clean.offsetByCodePoints(0, minOf(120, clean.codePointCount(0, clean.length)))
        return clean.substring(0, end)
    }
}

/** Private image scheme, shared by Coil consumers and artwork size helpers. */
object CoverUri {
    const val PREFIX = "vikify-cover://v1?"
    fun encode(spec: CoverSpec, size: Int = 480): String {
        val fields = linkedMapOf("id" to spec.collectionId, "title" to spec.title, "locale" to spec.locale,
            "template" to spec.templateId, "palette" to spec.paletteId, "variant" to spec.variant.toString(),
            "tv" to spec.templateVersion.toString(), "pv" to spec.paletteVersion.toString(), "lv" to spec.logoVersion.toString(),
            "fv" to spec.typographyVersion.toString(), "rv" to spec.rendererVersion.toString(), "size" to CoverCatalog.sizeBucket(size).toString())
        return PREFIX + fields.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
    }

    fun decode(value: String): Pair<CoverSpec, Int>? = try {
        if (!value.startsWith(PREFIX) || value.length > 3_000) null else {
            val uri = URI(value)
            val entries = uri.rawQuery.split('&').map { it.split('=', limit = 2) }
            require(entries.all { it.size == 2 } && entries.map { it[0] }.distinct().size == entries.size)
            val fields = entries.associate { it[0] to URLDecoder.decode(it[1], "UTF-8") }
            fun number(key: String, default: Int = 1) = fields[key]?.toIntOrNull()?.takeIf { it in 1..1_000 } ?: default
            val locale = fields["locale"] ?: "en-IN"
            require(Locale.forLanguageTag(locale).language.isNotEmpty())
            CoverSpec(fields.getValue("id"), CoverCatalog.title(fields.getValue("title")), locale,
                fields.getValue("template"), fields.getValue("palette"), fields.getValue("variant").toInt(),
                number("tv"), number("pv"), number("lv"), number("fv", 2), number("rv")) to
                CoverCatalog.sizeBucket(fields["size"]?.toIntOrNull() ?: 480)
        }
    } catch (failure: Exception) { null }
}

internal fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
