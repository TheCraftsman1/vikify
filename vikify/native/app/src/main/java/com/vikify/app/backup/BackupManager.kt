package com.vikify.app.backup

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.vikify.app.db.MusicDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.client.http.ByteArrayContent

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    private val TAG = "BackupManager"
    
    // Status Flow
    sealed class BackupStatus {
        object Idle : BackupStatus()
        object Loading : BackupStatus()
        data class Success(val message: String) : BackupStatus()
        data class Error(val message: String) : BackupStatus()
    }
    
    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status = _status.asStateFlow()

    // Drive Service Helper
    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            _status.value = BackupStatus.Error("Not signed in")
            return null
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Vikify").build()
    }

    /**
     * Backup Favorites and Playlist Metadata to Drive App Data Folder
     */
    suspend fun backupData() = withContext(Dispatchers.IO) {
        _status.value = BackupStatus.Loading
        try {
            val drive = getDriveService() ?: return@withContext

            // 1. Gather Data (JSON)
            // Ideally use Moshi or Gson to serialize entities
            val favorites = database.likedSongsByCreateDateAsc().first()
            val dataContent = favorites.joinToString("\n") { song -> 
                "${song.id}|${song.title}|${song.artists.joinToString(", ") { it.name }}|${song.id}" 
            }
            
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = "vikify_backup.txt"
            fileMetadata.parents = listOf("appDataFolder")

            val mediaContent = ByteArrayContent("text/plain", dataContent.toByteArray())

            // 2. Check for existing file to overwrite? For now, just create new.
            // (Production: search for fileId first)
            
            val file = drive.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            _status.value = BackupStatus.Success("Backup created: ${file.id}")
            Log.d(TAG, "Backup created: ${file.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            _status.value = BackupStatus.Error(e.message ?: "Backup failed")
        }
    }
    
    // Restore logic would go here
}
