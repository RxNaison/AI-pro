package com.rx.aipro.presentation

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.rx.aipro.presentation.screens.ChatScreen
import com.rx.aipro.presentation.screens.MainScreen
import com.rx.aipro.presentation.screens.Screen
import com.rx.aipro.presentation.viewmodels.ChatViewModel
import com.rx.aipro.presentation.viewmodels.ChatViewModelFactory
import com.rx.aipro.presentation.viewmodels.availableGeminiModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearAppNavigation()
        }
    }
}

@Composable
fun WearAppNavigation() {
    val navController = rememberSwipeDismissableNavController()
    val application = LocalContext.current.applicationContext as Application

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            val mainViewModel: com.rx.aipro.presentation.viewmodels.MainViewModel = viewModel(
                factory = com.rx.aipro.presentation.screens.MainViewModelFactory(application)
            )
            MainScreen(navController = navController, mainViewModel = mainViewModel)
        }
        composable(Screen.Chat.route) { backStackEntry ->
            val rawChatId = backStackEntry.arguments?.getString("chatId")
            val modelName = backStackEntry.arguments?.getString("modelName") ?: availableGeminiModels.first()

            val chatViewModel: ChatViewModel = viewModel(
                key = "$rawChatId-$modelName",
                factory = ChatViewModelFactory(application, rawChatId, modelName)
            )
            ChatScreen(chatViewModel = chatViewModel)
        }
    }
}