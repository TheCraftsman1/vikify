package com.vikify.app.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.pages.BrowseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // Optional browseId from navigation args (if using NavController)
    private val navBrowseId: String? = savedStateHandle.get<String>("browseId")
    private val navParams: String? = savedStateHandle.get<String>("params")

    val result = MutableStateFlow<BrowseResult?>(null)
    val isLoading = MutableStateFlow(false)

    init {
        // If provided in nav args, load automatically
        if (navBrowseId != null) {
            load(navBrowseId, navParams)
        }
    }
    
    fun load(browseId: String, params: String? = null) {
        viewModelScope.launch {
            isLoading.value = true
            YouTube.browse(browseId, params).onSuccess {
                result.value = it
            }.onFailure {
                reportException(it)
            }
            isLoading.value = false
        }
    }
}
