package com.rx.aipro.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.Chat
import com.rx.aipro.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.asTextOrNull
import com.rx.aipro.presentation.components.Author
import com.rx.aipro.presentation.components.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val GEMINI_MODEL = "gemini-2.0-flash"

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userInput: String = ""
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val generativeModel: GenerativeModel
    private var chat: Chat? = null

    init {
        if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "YOUR_API_KEY_HERE") {
            _uiState.update {
                it.copy(errorMessage = "API Key not set. Please set it in local.properties.")
            }
            generativeModel = GenerativeModel(
                modelName = GEMINI_MODEL,
                apiKey = "DUMMY_API_KEY_FOR_INITIALIZATION"
            )
        } else {
            generativeModel = GenerativeModel(
                modelName = GEMINI_MODEL,
                apiKey = BuildConfig.GEMINI_API_KEY
            )
            chat = generativeModel.startChat(history = emptyList())
        }
    }

    fun onUserInputChange(text: String) {
        _uiState.update { it.copy(userInput = text) }
    }

    fun sendMessage() {
        val userMessageText = _uiState.value.userInput.trim()

        if (userMessageText.isEmpty()) return

        if (chat == null) {
            if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "YOUR_API_KEY_HERE") {
                try {
                    chat = generativeModel.startChat(history = emptyList())
                } catch (e: Exception) {
                    handleError("Failed to initialize chat session: ${e.message}")
                    return
                }
            } else {
                handleError("Chat session not available. Check API Key or network.")
                return
            }
        }


        val userMessage = ChatMessage(text = userMessageText, author = Author.USER)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                userInput = "",
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val response = chat!!.sendMessage(userMessageText)

                val modelResponseText = response.text ?: response.candidates.firstNotNullOfOrNull {
                    it.content.parts.firstNotNullOfOrNull { part -> part.asTextOrNull() }
                }

                if (modelResponseText != null) {
                    val modelMessage = ChatMessage(text = modelResponseText, author = Author.MODEL)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + modelMessage,
                            isLoading = false
                        )
                    }
                } else {
                    val errorText = "Gemini didn't return a text response. Candidates: ${response.candidates.joinToString { it.toString() }}"
                    handleError(errorText)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                handleError("Error sending message: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun handleError(message: String) {
        val errorMessageEntry = ChatMessage(text = message, author = Author.ERROR)
        _uiState.update {
            it.copy(
                messages = it.messages + errorMessageEntry,
                isLoading = false,
                errorMessage = message
            )
        }
    }

    fun startNewChatSession() {
        _uiState.update { it.copy(messages = emptyList(), errorMessage = null, isLoading = false, userInput = "") }
        if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "YOUR_API_KEY_HERE") {
            chat = generativeModel.startChat(history = emptyList())
            _uiState.update {
                it.copy(messages = it.messages + ChatMessage(text = "New chat started.", author = Author.MODEL))
            }
        } else {
            _uiState.update {
                it.copy(errorMessage = "Cannot start new chat. API Key might be missing.")
            }
        }
    }
}
