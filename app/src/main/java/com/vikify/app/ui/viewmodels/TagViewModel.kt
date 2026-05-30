package com.vikify.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.metadata.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TagViewModel @Inject constructor(
    private val tagRepository: TagRepository
) : ViewModel() {

    // Editor State
    data class TagEditorState(
        val isLoading: Boolean = false,
        val filePath: String = "",
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val year: String = ""
    )

    private val _editorState = MutableStateFlow(TagEditorState())
    val editorState = _editorState.asStateFlow()

    fun loadTags(path: String) {
        viewModelScope.launch {
            _editorState.value = _editorState.value.copy(isLoading = true, filePath = path)
            val tags = tagRepository.readTags(path)
            _editorState.value = _editorState.value.copy(
                isLoading = false,
                title = tags["title"] ?: "",
                artist = tags["artist"] ?: "",
                album = tags["album"] ?: "",
                year = tags["year"] ?: ""
            )
        }
    }

    fun updateField(field: String, value: String) {
        val current = _editorState.value
        _editorState.value = when (field) {
            "title" -> current.copy(title = value)
            "artist" -> current.copy(artist = value)
            "album" -> current.copy(album = value)
            "year" -> current.copy(year = value)
            else -> current
        }
    }

    fun saveTags(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val s = _editorState.value
            _editorState.value = s.copy(isLoading = true)
            val result = tagRepository.writeTags(s.filePath, s.title, s.artist, s.album, s.year)
            _editorState.value = s.copy(isLoading = false)
            onComplete(result.isSuccess)
        }
    }
}
