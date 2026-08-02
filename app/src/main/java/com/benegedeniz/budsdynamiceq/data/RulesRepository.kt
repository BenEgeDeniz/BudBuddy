package com.benegedeniz.budsdynamiceq.data

import android.content.Context
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class RulesRepository(private val context: Context) {

    private val rulesFile = File(context.filesDir, "rules.json")
    
    private val _rules = MutableStateFlow<List<EqRule>>(emptyList())
    val rules: StateFlow<List<EqRule>> = _rules.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadRules() = withContext(Dispatchers.IO) {
        if (rulesFile.exists()) {
            try {
                val content = rulesFile.readText()
                val loadedRules = json.decodeFromString<List<EqRule>>(content)
                _rules.value = loadedRules.sortedBy { it.priority }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun saveRules(newRules: List<EqRule>) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(newRules)
            rulesFile.writeText(content)
            _rules.value = newRules.sortedBy { it.priority }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addRule(rule: EqRule) {
        val current = _rules.value.toMutableList()
        current.add(rule)
        saveRules(current)
    }

    suspend fun updateRule(rule: EqRule) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            current[index] = rule
            saveRules(current)
        }
    }

    suspend fun deleteRule(id: String) {
        val current = _rules.value.toMutableList()
        current.removeAll { it.id == id }
        saveRules(current)
    }
}
