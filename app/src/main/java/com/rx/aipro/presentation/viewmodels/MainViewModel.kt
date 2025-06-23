package com.rx.aipro.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rx.aipro.presentation.data.AppDatabase
import com.rx.aipro.presentation.data.ChatSessionDao
import com.rx.aipro.presentation.data.ChatSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val availableGeminiModels = listOf(
    "gemini-2.5-pro-preview-05-06",
    "gemini-2.5-pro",
    "gemini-2.5-flash-preview-04-17",
    "gemini-2.0-flash"
)

data class MainUiState(
    val models: List<String> = availableGeminiModels,
    val selectedModelIndex: Int = 0,
    val savedChats: List<ChatSessionEntity> = emptyList(),
    val isLoading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val chatSessionDao: ChatSessionDao = AppDatabase.getDatabase(application).chatSessionDao()

    val uiState: StateFlow<MainUiState> =
        chatSessionDao.getAllSessions()
            .map { sessions ->
                val currentSelectedModelIndex = _selectedModelIndex.value
                MainUiState(
                    models = availableGeminiModels,
                    selectedModelIndex = currentSelectedModelIndex,
                    savedChats = sessions
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MainUiState()
            )

    private val _selectedModelIndex = MutableStateFlow(0)


    fun onModelSelected(index: Int) {
        _selectedModelIndex.value = index
    }

    fun getSelectedModelName(): String {
        return availableGeminiModels.getOrElse(_selectedModelIndex.value) { availableGeminiModels.first() }
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            chatSessionDao.deleteSessionById(sessionId)
        }
    }
}