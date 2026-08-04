package com.benegedeniz.budsdynamiceq.data

import android.content.Context
import android.util.Log
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

class GestureRepository(private val context: Context) {
    private val gesturesFile = File(context.filesDir, "gestures.json")
    
    private val _gestures = MutableStateFlow<List<HeadGesture>>(emptyList())
    val gestures: StateFlow<List<HeadGesture>> = _gestures.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadGestures() {
        withContext(Dispatchers.IO) {
            try {
                if (gesturesFile.exists()) {
                    val content = gesturesFile.readText()
                    if (content.isNotBlank()) {
                        val list = json.decodeFromString<List<HeadGesture>>(content)
                        _gestures.value = list
                    }
                }
            } catch (e: Exception) {
                Log.e("GestureRepository", "Error loading gestures (schema changed?), wiping old data", e)
                gesturesFile.writeText("[]")
                _gestures.value = emptyList()
            }
        }
    }

    suspend fun saveGestures(gestures: List<HeadGesture>) {
        withContext(Dispatchers.IO) {
            try {
                val content = json.encodeToString(gestures)
                gesturesFile.writeText(content)
                _gestures.value = gestures
            } catch (e: Exception) {
                Log.e("GestureRepository", "Error saving gestures", e)
            }
        }
    }

    suspend fun addGesture(gesture: HeadGesture) {
        val current = _gestures.value.toMutableList()
        current.add(gesture)
        saveGestures(current)
    }

    suspend fun updateGesture(gesture: HeadGesture) {
        val current = _gestures.value.toMutableList()
        val index = current.indexOfFirst { it.id == gesture.id }
        if (index != -1) {
            current[index] = gesture
            saveGestures(current)
        }
    }

    suspend fun deleteGesture(id: String) {
        val current = _gestures.value.toMutableList()
        if (current.removeIf { it.id == id }) {
            saveGestures(current)
        }
    }
}
