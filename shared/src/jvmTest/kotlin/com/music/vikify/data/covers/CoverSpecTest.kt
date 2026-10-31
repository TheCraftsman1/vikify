package com.music.vikify.data.covers

import com.music.vikify.data.model.artworkAt
import com.music.vikify.data.personalization.LocalMixKind
import com.music.vikify.data.personalization.SavedMixCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverSpecTest {
    private val spec = CoverSpec("language_telugu_melodies", "Telugu Melodies", variant = 1)

    @Test fun unicodeTitleAndVisualInputsSurviveUriRoundTrip() {
        val telugu = spec.copy(title = "తెలుగు మెలోడీస్ 🎵", locale = "te-IN", paletteId = "violet_01")
        val decoded = CoverUri.decode(CoverUri.encode(telugu, 720))!!
        assertEquals(telugu, decoded.first)
        assertEquals(720, decoded.second)
    }

    @Test fun everyVersionAndVisualChangeInvalidatesItsOwnCacheEntry() {
        val variants = listOf(spec.copy(title = "Tamil Melodies"), spec.copy(locale = "te-IN"),
            spec.copy(templateId = "ribbon_01"), spec.copy(paletteId = "violet_01"), spec.copy(variant = 2),
            spec.copy(templateVersion = 2), spec.copy(paletteVersion = 2), spec.copy(logoVersion = 2),
            spec.copy(typographyVersion = spec.typographyVersion + 1), spec.copy(rendererVersion = 2))
        variants.forEach { assertNotEquals(spec.cacheKey(480), it.cacheKey(480)) }
        assertNotEquals(spec.cacheKey(480), spec.cacheKey(720))
        assertNotEquals(spec.cacheKey(480, "android-35"), spec.cacheKey(480, "android-36"))
    }

    @Test fun changingSongsOrSourceVersionDoesNotRepaintASavedCopy() {
        val first = SavedMixCopy("copy-identity", "My mix", LocalMixKind.DAILY_MIX, 1, 100, emptyList())
        val refreshed = first.copy(sourceVersion = 2, createdAt = 200)
        assertEquals(first.coverUrl, refreshed.coverUrl)
        assertNotEquals(first.coverUrl, first.copy(title = "Renamed mix").coverUrl)
    }

    @Test fun imageConsumersUseBoundedSizesAndKeepTheSameVisualSpecification() {
        val uri = CoverUri.encode(spec)
        assertEquals(192, CoverUri.decode(uri.artworkAt(56)!!)!!.second)
        assertEquals(480, CoverUri.decode(uri.artworkAt(480)!!)!!.second)
        assertEquals(720, CoverUri.decode(uri.artworkAt(50_000)!!)!!.second)
        assertEquals(spec, CoverUri.decode(uri.artworkAt(720)!!)!!.first)
        assertEquals("file:///android_asset/moods/on_repeat.webp", "file:///android_asset/moods/on_repeat.webp".artworkAt(720))
    }

    @Test fun malformedAndDuplicateRequestsCannotCreateAmbiguousCacheIdentities() {
        assertNull(CoverUri.decode("https://example.test/image.png"))
        assertNull(CoverUri.decode(CoverUri.encode(spec) + "&title=Other"))
        assertNull(CoverUri.decode(CoverUri.encode(spec).replace("variant=1", "variant=999")))
        assertNull(CoverUri.decode("vikify-cover://v1?title=%ZZ"))
    }

    @Test fun missingStylesHaveAnExplicitSafeFallback() {
        assertEquals(CoverComposition.FALLBACK, CoverCatalog.template(spec.copy(templateId = "missing")).composition)
        assertEquals(CoverComposition.FALLBACK, CoverCatalog.template(spec.copy(templateVersion = 999)).composition)
        assertEquals("lime_01", CoverCatalog.palette(spec.copy(paletteId = "missing")).id)
    }

    @Test fun titleNormalizationPreservesScriptsAndWholeSupplementaryCharacters() {
        assertEquals("Telugu Melodies", CoverCatalog.title(" Telugu\n\tMelodies "))
        assertEquals("తెలుగు మెలోడీస్", CoverCatalog.title("తెలుగు మెలోడీస్"))
        val title = CoverCatalog.title("🎵".repeat(140))
        assertEquals(120, title.codePointCount(0, title.length))
        assertFalse(Character.isHighSurrogate(title.last()))
        assertEquals("Your mix", CoverCatalog.title("   "))
    }

    @Test fun identitySelectsAStableCompositionAndAllFourPalettesAreReachable() {
        val first = CoverCatalog.dynamic("saved:one", "Title")
        assertEquals(first, CoverCatalog.dynamic("saved:one", "Title"))
        assertEquals(first.variant, CoverCatalog.dynamic("saved:one", "Another title").variant)
        val palettes = (1..100).map { CoverCatalog.dynamic("saved:$it", "Title").paletteId }.toSet()
        assertEquals(CoverCatalog.palettes.map { it.id }.toSet(), palettes)
        assertTrue(first.variant in 0..2)
    }
}
