package com.rx.aipro.presentation.screens

import androidx.compose.material.icons.filled.Create
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.wear.compose.material.dialog.Dialog
import com.rx.aipro.presentation.viewmodels.MainViewModel
import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.rx.aipro.R
import com.rx.aipro.presentation.data.ChatSessionEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val pickerState = rememberPickerState(initialNumberOfOptions = uiState.models.size.coerceAtLeast(0))
    LaunchedEffect(uiState.models) {
        if (pickerState.numberOfOptions != uiState.models.size) {
            pickerState.numberOfOptions = uiState.models.size
        }
        if (uiState.models.isNotEmpty() && pickerState.selectedOption >= uiState.models.size) {
            pickerState.scrollToOption(0)
        }
    }
    LaunchedEffect(pickerState.selectedOption) {
        if (uiState.models.isNotEmpty() && pickerState.selectedOption < uiState.models.size) {
            mainViewModel.onModelSelected(pickerState.selectedOption)
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<ChatSessionEntity?>(null) }

    Scaffold(
        timeText = { TimeText(modifier = Modifier.padding(top = 6.dp)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    "Select Model",
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                if (uiState.models.isNotEmpty()) {
                    Picker(
                        state = pickerState,
                        contentDescription = "model picker",
                        modifier = Modifier.fillMaxWidth(0.8f).height(100.dp),
                        separation = 4.dp,
                        readOnly = false
                    ) { optionIndex ->
                        Chip(
                            onClick = {
                                coroutineScope.launch {
                                    pickerState.scrollToOption(optionIndex)
                                }
                            },
                            label = { Text(uiState.models.getOrElse(optionIndex){"?"}, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (pickerState.selectedOption == optionIndex) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                            )
                        )
                    }
                } else { Text("No models available.") }
            }

            item {
                Chip(
                    onClick = {
                        val selectedModel = mainViewModel.getSelectedModelName()
                        navController.navigate(Screen.Chat.createRoute(modelName = selectedModel, chatId = null))
                    },
                    label = { Text("Start New Chat") },
                    icon = { Icon(Icons.Filled.Create, contentDescription = "New Chat") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f).padding(top = 16.dp)
                )
            }

            if (uiState.savedChats.isNotEmpty()) {
                item {
                    ListHeader { Text("Saved Chats", style = MaterialTheme.typography.caption1, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
                }
                items(uiState.savedChats, key = { it.id }) { chatSession ->
                    SavedChatItemCard(
                        chatSession = chatSession,
                        onClick = {
                            navController.navigate(Screen.Chat.createRoute(chatId = chatSession.id, modelName = chatSession.modelName))
                        },
                        onDeleteClick = {
                            chatToDelete = chatSession
                            showDeleteDialog = true
                        }
                    )
                }
            } else {
                item { Text("No saved chats.", style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 20.dp)) }
            }
        }

        if (showDeleteDialog && chatToDelete != null) {
            Dialog(
                showDialog = showDeleteDialog,
                onDismissRequest = {
                    showDeleteDialog = false
                    chatToDelete = null
                },
            ) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    item {
                        Text(
                            "Delete Chat?",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.title3,
                        )
                    }
                    item {
                        Text(
                            "\"${chatToDelete?.firstMessageSnippet?.take(30)}...\"",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body2,
                        )
                    }
                    item {
                        Text(
                            "This action cannot be undone.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.caption1,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                chatToDelete?.let { mainViewModel.deleteChatSession(it.id) }
                                showDeleteDialog = false
                                chatToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Delete")
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                chatToDelete = null
                            },
                            colors = ButtonDefaults.secondaryButtonColors(),
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedChatItemCard(
    chatSession: ChatSessionEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_message_24),
                contentDescription = "Chat history",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chatSession.firstMessageSnippet,
                    style = MaterialTheme.typography.caption1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Model: ${chatSession.modelName.substringAfterLast('-')}",
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dateFormat.format(Date(chatSession.lastMessageTimestamp)),
                    style = MaterialTheme.typography.caption3,
                )
            }
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete chat",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .clickable { onDeleteClick() }
            )
        }
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}