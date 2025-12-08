package com.persianai.assistant.utils

import android.content.Context
import com.persianai.assistant.models.NoteItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NoteStorage(context: Context) {
    private val prefs = context.getSharedPreferences("notes_storage", Context.MODE_PRIVATE)

    fun loadNotes(): List<NoteItem> {
        val raw = prefs.getString("notes", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<NoteItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                NoteItem(
                    id = o.getString("id"),
                    text = o.getString("text"),
                    done = o.optBoolean("done", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return list.sortedWith(compareBy<NoteItem> { it.done }.thenByDescending { it.createdAt })
    }

    fun addNote(text: String): List<NoteItem> {
        val newList = loadNotes().toMutableList()
        newList.add(
            NoteItem(
                id = UUID.randomUUID().toString(),
                text = text,
                done = false,
                createdAt = System.currentTimeMillis()
            )
        )
        save(newList)
        return loadNotes()
    }

    fun toggleDone(id: String): List<NoteItem> {
        val newList = loadNotes().map {
            if (it.id == id) it.copy(done = !it.done) else it
        }
        save(newList)
        return loadNotes()
    }

    fun delete(id: String): List<NoteItem> {
        val newList = loadNotes().filter { it.id != id }
        save(newList)
        return loadNotes()
    }

    private fun save(list: List<NoteItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("text", it.text)
                    put("done", it.done)
                    put("createdAt", it.createdAt)
                }
            )
        }
        prefs.edit().putString("notes", arr.toString()).apply()
    }
}
