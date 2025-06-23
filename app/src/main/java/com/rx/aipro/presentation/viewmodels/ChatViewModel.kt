package com.rx.aipro.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rx.aipro.presentation.data.*
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.asTextOrNull
import com.rx.aipro.BuildConfig
import com.rx.aipro.presentation.components.Author
import com.rx.aipro.presentation.components.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.rx.aipro.presentation.screens.NEW_CHAT_ID_PLACEHOLDER

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userInput: String = ""
)

class ChatViewModel(
    application: Application,
    passedChatId: String?,
    val modelName: String
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val chatSessionDao = AppDatabase.getDatabase(application).chatSessionDao()
    private val chatMessageDao = AppDatabase.getDatabase(application).chatMessageDao()

    private lateinit var generativeModel: GenerativeModel
    private var chat: Chat? = null
    private var isNewSession = passedChatId == null
    private val isTrulyNewSessionRequest: Boolean = (passedChatId == NEW_CHAT_ID_PLACEHOLDER || passedChatId == null)
    private var currentSessionId: String =
        if (isTrulyNewSessionRequest) UUID.randomUUID().toString() else passedChatId!!

    private var currentMessageOrder: Int = 0

    init {
        initializeModelAndLoadHistory()
    }

    private fun initializeModelAndLoadHistory() {
        viewModelScope.launch {
            if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "YOUR_API_KEY_HERE") {
                _uiState.update { it.copy(errorMessage = "API Key not set.") }
                return@launch
            }

            try {
                generativeModel = GenerativeModel(
                    modelName = this@ChatViewModel.modelName,
                    apiKey = BuildConfig.GEMINI_API_KEY
                )

                val dbMessages = chatMessageDao.getMessagesForSession(currentSessionId)
                val historyForGemini = dbMessages.map { it.toContent() }
                currentMessageOrder = dbMessages.size

                chat = generativeModel.startChat(history = historyForGemini)

                _uiState.update {
                    it.copy(
                        messages = dbMessages.map { entity -> entity.toChatMessage() },
                        isLoading = false
                    )
                }

                if (isTrulyNewSessionRequest && dbMessages.isEmpty()) {
                    _uiState.update { state ->
                        state.copy(messages = state.messages +
                                ChatMessage(text = "New chat with $modelName.", author = Author.MODEL)
                        )
                    }
                }


            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Failed to init model or load history: ${e.message}") }
            }
        }
    }

    fun onUserInputChange(text: String) {
        _uiState.update { it.copy(userInput = text) }
    }

    fun sendMessage() {
        val userMessageText = _uiState.value.userInput.trim()
        if (userMessageText.isEmpty()) return

        if (!::generativeModel.isInitialized || chat == null) {
            handleError("Chat session or model not ready for $modelName.")
            return
        }

        val userChatMessage = ChatMessage(text = userMessageText, author = Author.USER)
        _uiState.update {
            it.copy(
                messages = it.messages + userChatMessage,
                userInput = "",
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {

                updateOrCreateSession(userMessageText, userChatMessage.timestamp)

                val userEntity = userChatMessage.toEntity(currentSessionId, ++currentMessageOrder)
                chatMessageDao.insert(userEntity)
                updateOrCreateSession(userMessageText, userChatMessage.timestamp)

                val response: GenerateContentResponse = chat!!.sendMessage(userMessageText)
                val modelResponseText = response.text ?: response.candidates.firstNotNullOfOrNull {
                    it.content.parts.firstNotNullOfOrNull { part -> part.asTextOrNull() }
                }

                if (modelResponseText != null) {
                    val modelChatMessage = ChatMessage(text = modelResponseText, author = Author.MODEL)
                    val modelEntity = modelChatMessage.toEntity(currentSessionId, ++currentMessageOrder)
                    chatMessageDao.insert(modelEntity)
                    updateOrCreateSession(modelResponseText, modelChatMessage.timestamp, isModelResponse = true)

                    _uiState.update {
                        it.copy(
                            messages = it.messages + modelChatMessage,
                            isLoading = false
                        )
                    }
                } else {
                    handleError("Gemini ($modelName) didn't return text.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handleError("Error sending to $modelName: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private suspend fun updateOrCreateSession(messageText: String, timestamp: Long, isModelResponse: Boolean = false) {
        withContext(Dispatchers.IO) {
            val existingSession = chatSessionDao.getSessionById(currentSessionId)
            if (existingSession == null) {
                isNewSession = false
                val newSession = ChatSessionEntity(
                    id = currentSessionId,
                    modelName = this@ChatViewModel.modelName,
                    firstMessageSnippet = messageText.take(50) + if (messageText.length > 50) "..." else "",
                    lastMessageTimestamp = timestamp
                )
                chatSessionDao.insert(newSession)
            } else if (!isModelResponse) {
                existingSession.lastMessageTimestamp = timestamp
                if (currentMessageOrder <= 2) {
                    existingSession.firstMessageSnippet = (_uiState.value.messages.firstOrNull { it.author == Author.USER }?.text ?: messageText).take(50) + "..."
                }
                chatSessionDao.update(existingSession)
            } else {
                existingSession.lastMessageTimestamp = timestamp
                chatSessionDao.update(existingSession)
            }
        }
    }


    private fun handleError(message: String) {
        val errorMessageEntry = ChatMessage(text = message, author = Author.ERROR)
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message
            )
        }
    }

    fun startNewChatSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(messages = emptyList(), errorMessage = null, isLoading = false, userInput = "") }
            currentMessageOrder = 0
            if (::generativeModel.isInitialized) {
                chat = generativeModel.startChat(history = emptyList())
                _uiState.update {state ->
                    state.copy(messages = state.messages + ChatMessage(text = "Chat with $modelName cleared.", author = Author.MODEL))
                }
            } else {
                initializeModelAndLoadHistory()
            }
        }
    }
}