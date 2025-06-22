package com.rx.aipro.presentation.components

enum class Author {
    USER, MODEL, ERROR
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val author: Author,
    val timestamp: Long = System.currentTimeMillis()
)