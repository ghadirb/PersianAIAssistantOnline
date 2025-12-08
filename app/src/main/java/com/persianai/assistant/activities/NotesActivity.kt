package com.persianai.assistant.activities

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.databinding.ActivityNotesBinding
import com.persianai.assistant.utils.NoteStorage
import com.persianai.assistant.models.NoteItem
import com.persianai.assistant.ui.NoteAdapter

class NotesActivity : AppCompatActivity(), NoteAdapter.NoteClickListener {

    private lateinit var binding: ActivityNotesBinding
    private lateinit var storage: NoteStorage
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "📝 یادداشت‌ها"

        storage = NoteStorage(this)
        adapter = NoteAdapter(mutableListOf(), this)
        binding.notesRecycler.layoutManager = LinearLayoutManager(this)
        binding.notesRecycler.adapter = adapter

        binding.btnAdd.setOnClickListener { addNote() }
        binding.noteInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addNote()
                true
            } else false
        }

        loadNotes()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun addNote() {
        val text = binding.noteInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "متن را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val list = storage.addNote(text)
        binding.noteInput.setText("")
        updateList(list)
    }

    private fun loadNotes() {
        updateList(storage.loadNotes())
    }

    private fun updateList(list: List<NoteItem>) {
        adapter.update(list)
        binding.emptyState.text = if (list.isEmpty()) "هنوز یادداشتی ندارید" else ""
    }

    override fun onToggleDone(id: String) {
        updateList(storage.toggleDone(id))
    }

    override fun onDelete(id: String) {
        updateList(storage.delete(id))
    }
}
