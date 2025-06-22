package com.rx.aipro.presentation.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource // If you add custom drawable icons
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.rx.aipro.presentation.components.Author
import com.rx.aipro.presentation.components.ChatMessage
import com.rx.aipro.presentation.viewmodels.ChatViewModel
import kotlinx.coroutines.launch
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import com.rx.aipro.R

@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel()) {
    val uiState by chatViewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!spokenText.isNullOrEmpty()) {
                chatViewModel.onUserInputChange(spokenText[0])
            }
        }
    }

    fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            chatViewModel.onUserInputChange("${uiState.userInput} (Voice input unavailable)")
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    MaterialTheme {
        Scaffold(
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                autoCentering = AutoCenteringParams(itemIndex = 0)
            ) {
                item {
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 8.dp),
                        onClick = { chatViewModel.startNewChatSession() },
                        label = { Text("New Chat", textAlign = TextAlign.Center) },
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = "New Chat") },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }

                if (uiState.errorMessage != null && uiState.messages.none { it.author == Author.ERROR && it.text == uiState.errorMessage }) {
                    item {
                        Text(
                            text = "Info: ${uiState.errorMessage}",
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(vertical = 4.dp)
                                .background(
                                    MaterialTheme.colors.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        )
                    }
                }

                items(uiState.messages, key = { it.id }) { message ->
                    WearChatMessageItem(message = message)
                }

                item { Spacer(modifier = Modifier.height(60.dp)) }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WearMessageInput(
                    userInput = uiState.userInput,
                    onUserInputChange = { chatViewModel.onUserInputChange(it) },
                    onSendMessage = {
                        chatViewModel.sendMessage()
                        focusManager.clearFocus()
                    },
                    onVoiceInput = { launchVoiceInput() },
                    isLoading = uiState.isLoading
                )
            }
        }
    }
}

@Composable
fun WearChatMessageItem(message: ChatMessage) {
    val isUser = message.author == Author.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = when (message.author) {
        Author.USER -> MaterialTheme.colors.primary
        Author.MODEL -> MaterialTheme.colors.surface
        Author.ERROR -> MaterialTheme.colors.error.copy(alpha = 0.7f)
    }
    val textColor = if (message.author == Author.ERROR) MaterialTheme.colors.onError else MaterialTheme.colors.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                style = MaterialTheme.typography.body2
            )
        }
    }
}

@Composable
fun WearMessageInput(
    userInput: String,
    onUserInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onVoiceInput: () -> Unit,
    isLoading: Boolean
) {
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 23.dp, vertical = 15.dp)
            .background(MaterialTheme.colors.background.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
//        Button(
//            onClick = onVoiceInput,
//            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
//            enabled = !isLoading,
//            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
//        ) {
//            Icon(
//                painter = painterResource(id = R.drawable.baseline_mic_24),
//                contentDescription = "Voice Input",
//                modifier = Modifier.size(20.dp)
//            )
//        }

        BasicTextField(
            value = userInput,
            onValueChange = onUserInputChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 5.dp)
                .height(36.dp)
                .background(MaterialTheme.colors.surface.copy(alpha = 0.5f), CircleShape)
                .focusRequester(focusRequester)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colors.onSurface,
                fontSize = 13.sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            enabled = !isLoading
        )
        // Alternative: Use a Chip that opens a full-screen text input
        /*
        Chip(
            onClick = { // Open text input screen / dialog },
            label = { Text(if (userInput.isEmpty()) "Type..." else userInput, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.weight(1f).padding(horizontal=4.dp),
            colors = ChipDefaults.secondaryChipColors()
        )
        */


        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                indicatorColor = MaterialTheme.colors.primary
            )
        } else {
            Button(
                onClick = onSendMessage,
                enabled = userInput.isNotBlank(),
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (userInput.isNotBlank()) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}