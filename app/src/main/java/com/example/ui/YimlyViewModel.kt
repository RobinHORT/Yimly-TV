package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.YimlySessionService
import com.example.data.YimlySessionServiceImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface YimlyUiState {
    data object Loading : YimlyUiState
    data class Ready(val sessionId: String, val hostRoomUrl: String) : YimlyUiState
    data class Error(val retryCountdown: Int, val technicalDetail: String) : YimlyUiState
}

class YimlyViewModel(
    private val sessionService: YimlySessionService = YimlySessionServiceImpl()
) : ViewModel() {

    companion object {
        private const val TAG = "YimlyViewModel"
        private const val RETRY_DELAY_SECONDS = 4
    }

    private val _uiState = MutableStateFlow<YimlyUiState>(YimlyUiState.Loading)
    val uiState: StateFlow<YimlyUiState> = _uiState.asStateFlow()

    private var retryJob: Job? = null
    private var sessionCreationJob: Job? = null

    init {
        // Every launch creates a fresh session automatically
        createFreshSession()
    }

    fun createFreshSession() {
        retryJob?.cancel()
        sessionCreationJob?.cancel()

        sessionCreationJob = viewModelScope.launch {
            _uiState.value = YimlyUiState.Loading
            Log.d(TAG, "Requesting fresh karaoke session...")

            val result = sessionService.createNewSession()
            result.onSuccess { sessionInfo ->
                Log.i(TAG, "Session ready: ${sessionInfo.sessionId}, room URL: ${sessionInfo.hostRoomUrl}")
                _uiState.value = YimlyUiState.Ready(
                    sessionId = sessionInfo.sessionId,
                    hostRoomUrl = sessionInfo.hostRoomUrl
                )
            }.onFailure { exception ->
                val errorMsg = exception.localizedMessage ?: "Unknown connection error"
                Log.e(TAG, "Session creation failed: $errorMsg", exception)
                startAutoRetryCountdown(errorMsg)
            }
        }
    }

    private fun startAutoRetryCountdown(technicalDetail: String) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            for (secondsLeft in RETRY_DELAY_SECONDS downTo 1) {
                _uiState.value = YimlyUiState.Error(
                    retryCountdown = secondsLeft,
                    technicalDetail = technicalDetail
                )
                delay(1000L)
            }
            // Auto retry
            createFreshSession()
        }
    }

    fun retryImmediately() {
        Log.d(TAG, "Manual retry requested from TV UI")
        createFreshSession()
    }

    override fun onCleared() {
        super.onCleared()
        retryJob?.cancel()
        sessionCreationJob?.cancel()
    }

    class Factory(
        private val sessionService: YimlySessionService = YimlySessionServiceImpl()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return YimlyViewModel(sessionService) as T
        }
    }
}
