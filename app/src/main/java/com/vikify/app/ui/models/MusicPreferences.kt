package com.vikify.app.ui.models

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Music Language Preferences
 * 
 * Stores user's preferred music languages for personalized feed.
 * Used during onboarding and to filter YouTube content.
 */

private val Context.musicPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "music_preferences"
)

/**
 * Available music languages/regions
 */
enum class MusicLanguage(
    val displayName: String,
    val emoji: String,
    val youtubeLocale: String,  // YouTube Music locale code
    val languageCode: String     // For content filtering
) {
    ENGLISH("English", "🇺🇸", "US", "en"),
    HINDI("Hindi", "🇮🇳", "IN", "hi"),
    TELUGU("Telugu", "🎵", "IN", "te"),
    TAMIL("Tamil", "🎵", "IN", "ta"),
    PUNJABI("Punjabi", "🎵", "IN", "pa"),
    MALAYALAM("Malayalam", "🎵", "IN", "ml"),
    KANNADA("Kannada", "🎵", "IN", "kn"),
    KOREAN("K-Pop", "🇰🇷", "KR", "ko"),
    SPANISH("Spanish", "🇪🇸", "ES", "es"),
    LATIN("Latin", "🌎", "MX", "es"),
    ARABIC("Arabic", "🇸🇦", "SA", "ar"),
    JAPANESE("Japanese", "🇯🇵", "JP", "ja")
}

object MusicPreferences {
    
    private val SELECTED_LANGUAGES = stringSetPreferencesKey("selected_languages")
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("music_onboarding_completed")
    private val FILTER_LOW_QUALITY = booleanPreferencesKey("filter_low_quality")
    
    /**
     * Get selected music languages as Flow
     */
    fun getSelectedLanguages(context: Context): Flow<Set<MusicLanguage>> {
        return context.musicPreferencesDataStore.data.map { preferences ->
            val languageNames = preferences[SELECTED_LANGUAGES] ?: setOf(MusicLanguage.ENGLISH.name)
            languageNames.mapNotNull { name ->
                try {
                    MusicLanguage.valueOf(name)
                } catch (e: Exception) {
                    null
                }
            }.toSet()
        }
    }
    
    /**
     * Save selected music languages
     */
    suspend fun setSelectedLanguages(context: Context, languages: Set<MusicLanguage>) {
        context.musicPreferencesDataStore.edit { preferences ->
            preferences[SELECTED_LANGUAGES] = languages.map { it.name }.toSet()
        }
    }
    
    /**
     * Check if music preference onboarding is completed
     */
    fun hasCompletedOnboarding(context: Context): Flow<Boolean> {
        return context.musicPreferencesDataStore.data.map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }
    }
    
    /**
     * Mark onboarding as completed
     */
    suspend fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        context.musicPreferencesDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }
    
    /**
     * Get filter low quality content preference
     */
    fun shouldFilterLowQuality(context: Context): Flow<Boolean> {
        return context.musicPreferencesDataStore.data.map { preferences ->
            preferences[FILTER_LOW_QUALITY] ?: true  // Default: filter enabled
        }
    }
    
    /**
     * Set filter low quality content preference
     */
    suspend fun setFilterLowQuality(context: Context, filter: Boolean) {
        context.musicPreferencesDataStore.edit { preferences ->
            preferences[FILTER_LOW_QUALITY] = filter
        }
    }
    
    /**
     * Get YouTube locales for selected languages
     * Returns list of locale codes for YouTube API calls
     */
    fun getYouTubeLocales(languages: Set<MusicLanguage>): List<String> {
        return languages.map { it.youtubeLocale }.distinct()
    }
    
    /**
     * Get primary YouTube locale (first selected language)
     */
    fun getPrimaryLocale(languages: Set<MusicLanguage>): String {
        return languages.firstOrNull()?.youtubeLocale ?: "US"
    }
}
