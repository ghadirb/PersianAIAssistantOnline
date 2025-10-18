package com.persianai.assistant.models

/**
 * مدل آهنگ موسیقی
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val url: String,
    val mood: String,
    val duration: Long,
    val albumArt: String
)
