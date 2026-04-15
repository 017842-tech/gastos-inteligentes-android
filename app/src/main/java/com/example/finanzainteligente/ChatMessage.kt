package com.wccslic.finanzainteligente

data class ChatMessage(
    val message: String = "",
    val sender: String = "IA", // "User" or "IA"
    val timestamp: Long = System.currentTimeMillis()
)
