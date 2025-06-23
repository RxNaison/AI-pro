package com.rx.aipro.presentation.screens

const val NEW_CHAT_ID_PLACEHOLDER = "initiate_new_chat_session"

sealed class Screen(val route: String) {
    data object Main : Screen("main_screen")
    data object Chat : Screen("chat_screen/{chatId}/{modelName}") {
        fun createRoute(chatId: String?, modelName: String): String {
            val effectiveChatId = chatId ?: NEW_CHAT_ID_PLACEHOLDER
            return "chat_screen/$effectiveChatId/$modelName"
        }
    }
}