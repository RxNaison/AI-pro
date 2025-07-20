package com.rx.aipro.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rx.aipro.presentation.data.AppDatabase
import com.rx.aipro.presentation.data.ChatSessionDao
import com.rx.aipro.presentation.data.ChatSessionEntity
import com.rx.aipro.presentation.data.UserSettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val availableGeminiModels = listOf(
    "gemini-2.5-flash",
    "gemini-2.0-flash",
    "gemini-2.5-pro",
    "gemini-2.0-flash-lite"
)

data class MainUiState(
    val models: List<String> = availableGeminiModels,
    val selectedModelIndex: Int = 0,
    val savedChats: List<ChatSessionEntity> = emptyList(),
    val apiKey: String = "",
    val isLoading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val chatSessionDao: ChatSessionDao = AppDatabase.getDatabase(application).chatSessionDao()
    private val userSettingsDataStore = UserSettingsDataStore(application)
    private val _selectedModelIndex = MutableStateFlow(0)

    val uiState: StateFlow<MainUiState> = combine(
        chatSessionDao.getAllSessions(),
        userSettingsDataStore.getApiKey,
        _selectedModelIndex
    ) { sessions, apiKey, selectedIndex ->
        MainUiState(
            savedChats = sessions,
            apiKey = apiKey,
            selectedModelIndex = selectedIndex,
            models = availableGeminiModels
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun onModelSelected(index: Int) {
        _selectedModelIndex.value = index
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            userSettingsDataStore.saveApiKey(key)
        }
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