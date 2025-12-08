package com.persianai.assistant.models

data class NoteItem(
    val id: String,
    val text: String,
    val done: Boolean,
    val createdAt: Long
)
