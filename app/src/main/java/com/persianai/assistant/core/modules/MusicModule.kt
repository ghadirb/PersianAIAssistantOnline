package com.persianai.assistant.core.modules

import android.content.Context
import android.content.Intent
import android.util.Log
import com.persianai.assistant.activities.ImprovedMusicActivity
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent
import com.persianai.assistant.core.intent.MusicPlayIntent

class MusicModule(context: Context) : BaseModule(context) {
    override val moduleName: String = "Music"

    override suspend fun canHandle(intent: AIIntent): Boolean {
        return intent is MusicPlayIntent
    }

    override suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult {
        return when (intent) {
            is MusicPlayIntent -> handleMusicPlay(request, intent)
            else -> createResult("نوع Intent نشناخته‌شده", intent.name, false)
        }
    }

    private suspend fun handleMusicPlay(request: AIIntentRequest, intent: MusicPlayIntent): AIIntentResult {
        val query = intent.query ?: intent.rawText
        
        logAction("PLAY", "query=$query")
        
        return try {
            val musicIntent = Intent(context, ImprovedMusicActivity::class.java).apply {
                if (intent.query != null) {
                    putExtra("search_query", intent.query)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(musicIntent)
            
            createResult(
                text = "🎵 بازی موسیقی شروع شد\n📀 جستجو: $query",
                intentName = intent.name,
                actionType = "play_music",
                actionData = query
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error playing music", e)
            createResult(
                text = "❌ خطا در بازی موسیقی",
                intentName = intent.name,
                success = false
            )
        }
    }
}