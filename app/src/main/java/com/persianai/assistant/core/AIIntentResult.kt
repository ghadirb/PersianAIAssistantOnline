package com.persianai.assistant.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AIIntentResult(
    val text: String,
    val intentName: String,
    val success: Boolean = true,
    val actionType: String? = null,
    val actionData: String? = null,
    val spokenOutput: String? = null,
    val debug: Map<String, String> = emptyMap()
) : Parcelable
