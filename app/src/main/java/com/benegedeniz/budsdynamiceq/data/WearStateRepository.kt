package com.benegedeniz.budsdynamiceq.data

import android.content.Context
import android.util.Log
import com.benegedeniz.budsdynamiceq.data.model.WearStateAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class WearStateRepository(private val context: Context) {
    private val actionsFile = File(context.filesDir, "wear_state_actions.json")
    
    private val _actions = MutableStateFlow<List<WearStateAction>>(emptyList())
    val actions: StateFlow<List<WearStateAction>> = _actions.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadActions() {
        withContext(Dispatchers.IO) {
            try {
                if (actionsFile.exists()) {
                    val content = actionsFile.readText()
                    if (content.isNotBlank()) {
                        val list = json.decodeFromString<List<WearStateAction>>(content)
                        ensureDefaultActions(list)
                        return@withContext
                    }
                }
                ensureDefaultActions(emptyList())
            } catch (e: Exception) {
                Log.e("WearStateRepository", "Error loading wear state actions (schema changed?), wiping old data", e)
                ensureDefaultActions(emptyList())
            }
        }
    }

    private suspend fun ensureDefaultActions(list: List<WearStateAction>) {
        val newList = list.toMutableList()
        var changed = false
        if (newList.none { it.trigger == com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.EARBUD_REMOVED }) {
            newList.add(WearStateAction(id = "default_removed", name = context.getString(com.benegedeniz.budsdynamiceq.R.string.trigger_earbud_removed), trigger = com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.EARBUD_REMOVED, actions = emptyList(), enabled = false))
            changed = true
        }
        if (newList.none { it.trigger == com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.BOTH_WEARING }) {
            newList.add(WearStateAction(id = "default_wearing", name = context.getString(com.benegedeniz.budsdynamiceq.R.string.trigger_both_worn), trigger = com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.BOTH_WEARING, actions = emptyList(), enabled = false))
            changed = true
        }
        
        _actions.value = newList
        if (changed) saveActions(newList)
    }

    suspend fun saveActions(actions: List<WearStateAction>) {
        withContext(Dispatchers.IO) {
            try {
                val content = json.encodeToString(actions)
                actionsFile.writeText(content)
                _actions.value = actions
            } catch (e: Exception) {
                Log.e("WearStateRepository", "Error saving wear state actions", e)
            }
        }
    }

    suspend fun addAction(action: WearStateAction) {
        val current = _actions.value.toMutableList()
        current.add(action)
        saveActions(current)
    }

    suspend fun updateAction(action: WearStateAction) {
        val current = _actions.value.toMutableList()
        val index = current.indexOfFirst { it.id == action.id }
        if (index != -1) {
            current[index] = action
            saveActions(current)
        }
    }

    suspend fun deleteAction(id: String) {
        val current = _actions.value.toMutableList()
        if (current.removeIf { it.id == id }) {
            saveActions(current)
        }
    }
}
