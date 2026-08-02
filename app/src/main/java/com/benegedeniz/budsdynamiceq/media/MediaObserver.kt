package com.benegedeniz.budsdynamiceq.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

enum class GenreFetchState {
    NONE, LOADING, SUCCESS, ERROR
}

data class SongMetadata(
    val title: String? = null,
    val artist: String? = null,
    val genre: String? = null,
    val genreFetchState: GenreFetchState = GenreFetchState.NONE
) {
    val displayString: String get() = listOfNotNull(artist, title).filter { it.isNotBlank() }.joinToString(" - ")
}

class MediaObserver(private val context: Context) {

    companion object {
        private const val TAG = "MediaObserver"
    }

    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val _currentMetadata = MutableStateFlow<SongMetadata?>(null)
    val currentMetadata: StateFlow<SongMetadata?> = _currentMetadata.asStateFlow()

    private val _recentHistory = MutableStateFlow<List<SongMetadata>>(emptyList())
    val recentHistory: StateFlow<List<SongMetadata>> = _recentHistory.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentFetchJob: Job? = null
    
    private val genreCache = context.getSharedPreferences("genre_cache", Context.MODE_PRIVATE)

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveControllers(controllers)
    }

    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    /**
     * Starts observing media sessions.
     * Note: Requires NotificationListenerService permission to be granted.
     */
    fun startObserving() {
        try {
            val componentName = ComponentName(context, MediaListenerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.service.notification.NotificationListenerService.requestRebind(componentName)
            }
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateActiveControllers(controllers)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to observe media sessions. Did user grant notification access?", e)
        }
    }

    fun stopObserving() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping observation", e)
        }
        for ((controller, callback) in controllerCallbacks) {
            controller.unregisterCallback(callback)
        }
        controllerCallbacks.clear()
        scope.cancel()
    }

    private fun updateActiveControllers(controllers: List<MediaController>?) {
        val currentControllers = controllers ?: emptyList()

        // Remove callbacks for controllers that are no longer active
        val removed = controllerCallbacks.keys - currentControllers.toSet()
        for (controller in removed) {
            val callback = controllerCallbacks.remove(controller)
            if (callback != null) {
                controller.unregisterCallback(callback)
            }
        }

        // Add callbacks for new controllers
        for (controller in currentControllers) {
            if (!controllerCallbacks.containsKey(controller)) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        super.onMetadataChanged(metadata)
                        // If this controller is currently playing, or it's the only one, update title
                        if (controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING || currentControllers.size == 1) {
                            updateTitleFromMetadata(metadata)
                        }
                    }

                    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                        super.onPlaybackStateChanged(state)
                        if (state?.state == android.media.session.PlaybackState.STATE_PLAYING) {
                            updateTitleFromMetadata(controller.metadata)
                        }
                    }
                }
                controller.registerCallback(callback)
                controllerCallbacks[controller] = callback
            }
        }

        // Initially find the playing controller, or fallback to first
        val playingController = currentControllers.firstOrNull { 
            it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING 
        } ?: currentControllers.firstOrNull()
        
        updateTitleFromMetadata(playingController?.metadata)
    }

    private fun updateTitleFromMetadata(metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val genre = metadata?.getString(MediaMetadata.METADATA_KEY_GENRE)
        
        handleNewMetadata(title, artist, genre)
    }

    fun updateTitleFromNotification(title: String?, artist: String?) {
        handleNewMetadata(title, artist, null)
    }

    private fun handleNewMetadata(title: String?, artist: String?, genre: String?) {
        // If we have a native genre, it's a success right away.
        var initialState = if (!genre.isNullOrBlank()) GenreFetchState.SUCCESS else GenreFetchState.NONE
        var initialGenre = genre

        // If no native genre, try cache
        if (initialGenre.isNullOrBlank() && !title.isNullOrBlank() && !artist.isNullOrBlank()) {
            val cacheKey = "${artist}_${title}"
            val cachedGenre = genreCache.getString(cacheKey, null)
            if (!cachedGenre.isNullOrBlank()) {
                initialGenre = cachedGenre
                initialState = GenreFetchState.SUCCESS
            } else {
                initialState = GenreFetchState.LOADING
            }
        }

        val newMetadata = SongMetadata(title, artist, initialGenre, initialState)
        
        if (_currentMetadata.value == newMetadata) return

        Log.d(TAG, "Song metadata changed: ${newMetadata.displayString} (State: $initialState)")
        _currentMetadata.value = newMetadata

        // Add to recent history if it's a new unique song (ignoring state updates for the same song)
        if (newMetadata.title != null && newMetadata.artist != null) {
            val currentHistory = _recentHistory.value
            val isSameSongAsLast = currentHistory.firstOrNull()?.let { 
                it.title == newMetadata.title && it.artist == newMetadata.artist 
            } == true
            
            if (!isSameSongAsLast) {
                _recentHistory.value = listOf(newMetadata) + currentHistory.take(4)
            } else if (newMetadata.genreFetchState == GenreFetchState.SUCCESS) {
                // Update the state of the top item if we just fetched the genre
                _recentHistory.value = listOf(newMetadata) + currentHistory.drop(1)
            }
        }

        currentFetchJob?.cancel()

        if (initialState == GenreFetchState.LOADING && !title.isNullOrBlank() && !artist.isNullOrBlank()) {
            currentFetchJob = scope.launch {
                val fetchedGenre = fetchGenreFromITunes(title, artist)
                if (_currentMetadata.value?.title == title && _currentMetadata.value?.artist == artist) {
                    if (fetchedGenre != null) {
                        val cacheKey = "${artist}_${title}"
                        genreCache.edit().putString(cacheKey, fetchedGenre).apply()
                        
                        val updatedMetadata = SongMetadata(title, artist, fetchedGenre, GenreFetchState.SUCCESS)
                        Log.d(TAG, "Fetched genre from iTunes: $fetchedGenre")
                        _currentMetadata.value = updatedMetadata
                        
                        // Update the history entry with the newly fetched genre
                        val currentHistory = _recentHistory.value
                        if (currentHistory.isNotEmpty() && currentHistory.first().title == title && currentHistory.first().artist == artist) {
                            _recentHistory.value = listOf(updatedMetadata) + currentHistory.drop(1)
                        }
                    } else {
                        val updatedMetadata = SongMetadata(title, artist, null, GenreFetchState.ERROR)
                        Log.d(TAG, "Failed to fetch genre from iTunes")
                        _currentMetadata.value = updatedMetadata
                        
                        // Update the history entry with the error state
                        val currentHistory = _recentHistory.value
                        if (currentHistory.isNotEmpty() && currentHistory.first().title == title && currentHistory.first().artist == artist) {
                            _recentHistory.value = listOf(updatedMetadata) + currentHistory.drop(1)
                        }
                    }
                }
            }
        }
    }

    private fun fetchGenreFromITunes(title: String, artist: String): String? {
        try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$query&entity=song&limit=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    val genre = firstResult.optString("primaryGenreName")
                    if (genre.isNotBlank()) {
                        return genre
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch genre from iTunes", e)
        }
        return null
    }
}
