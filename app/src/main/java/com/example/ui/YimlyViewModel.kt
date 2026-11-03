package com.example.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YimlyViewModel : ViewModel() {
    companion object {
        const val WEBSITE_URL = "https://yimly.robinhort.link"
    }

    private val _uiState = MutableStateFlow(WEBSITE_URL)
    val uiState: StateFlow<String> = _uiState.asStateFlow()
}
