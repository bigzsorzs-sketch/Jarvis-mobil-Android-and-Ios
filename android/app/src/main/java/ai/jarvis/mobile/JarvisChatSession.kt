package ai.jarvis.mobile

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** A new Activity launch gets a blank chat. Configuration changes keep its ViewModel. */
internal class JarvisConversation(val id: String = UUID.randomUUID().toString()) {
    val turns = mutableStateListOf<Pair<String, String>>()
    val aiTurns = mutableListOf<Pair<String, String>>()
    var oldestId by mutableLongStateOf(0L)
    var hasOlder by mutableStateOf(false)
    var loadingOlder by mutableStateOf(false)
    var loading by mutableStateOf(false)
}

internal class JarvisChatSession(application: Application) : AndroidViewModel(application) {
    private val store = JarvisChatStore(application)
    private val loaded = mutableMapOf<String, JarvisConversation>()
    var current by mutableStateOf(JarvisConversation())
        private set
    var summaries by mutableStateOf<List<JarvisChatSummary>>(emptyList())
        private set
    var warning by mutableStateOf("")
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            try {
                summaries = withContext(Dispatchers.IO) { store.conversations() }
            } catch (_: Exception) {
                warning = "Nem tudtam betölteni a helyi beszélgetésnaplót; az adatokat nem töröltem."
            }
        }
    }

    fun newChat() {
        if (current.turns.isNotEmpty()) loaded[current.id] = current
        current = JarvisConversation()
    }

    fun open(id: String) {
        if (id == current.id) return
        if (current.turns.isNotEmpty()) loaded[current.id] = current
        loaded[id]?.let { current = it; return }
        val target = JarvisConversation(id).apply { loading = true }
        loaded[id] = target
        current = target
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { store.recent(50, id) }
                target.turns.addAll(entries.map { it.request to it.answer })
                target.aiTurns.addAll(entries.filter { it.aiContext }.map { it.request to it.answer })
                target.oldestId = entries.firstOrNull()?.id ?: 0L
                target.hasOlder = entries.size == 50
            } catch (_: Exception) {
                loaded.remove(id)
                warning = "Nem tudtam betölteni a helyi beszélgetésnaplót; az adatokat nem töröltem."
            } finally { target.loading = false }
        }
    }

    fun save(target: JarvisConversation, turn: Pair<String, String>, aiContext: Boolean = false) {
        if (aiContext) {
            val bounded = JarvisConversationContext.bounded(target.aiTurns + turn)
            target.aiTurns.clear()
            target.aiTurns.addAll(bounded)
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.append(turn.first, turn.second, target.id, aiContext) }
                refresh()
            } catch (_: Exception) {
                warning = "Hiba: egy beszélgetési bejegyzést nem sikerült titkosítva menteni."
            }
        }
    }

    suspend fun loadEarlier(target: JarvisConversation) {
        if (!target.hasOlder || target.loadingOlder || target.oldestId <= 0L) return
        target.loadingOlder = true
        try {
            val entries = withContext(Dispatchers.IO) { store.before(target.oldestId, 50, target.id) }
            target.turns.addAll(0, entries.map { it.request to it.answer })
            target.oldestId = entries.firstOrNull()?.id ?: target.oldestId
            target.hasOlder = entries.size == 50
        } catch (_: Exception) {
            warning = "Nem tudtam betölteni a régebbi helyi beszélgetéseket."
        } finally { target.loadingOlder = false }
    }
}
