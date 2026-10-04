package com.noryan.romrunner.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.noryan.romrunner.data.launch.SystemOrder
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.data.scanner.RomScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val platforms: List<Platform> = emptyList(),
    val games: List<Game> = emptyList(),
    val isScanning: Boolean = false,
    val message: String? = null,
    val romsRootUri: String? = null
)

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    private val isScanning = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val romsRootUri = MutableStateFlow(repository.getRootFolderUri())

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observePlatforms(),
        repository.observeGames(),
        isScanning,
        message,
        romsRootUri
    ) { platforms, games, scanning, msg, rootUri ->
        LibraryUiState(platforms, SystemOrder.sort(games, platforms), scanning, msg, rootUri)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    /** Called once the user picks (or changes) the single ROMs/Games root folder. */
    fun setRootFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repository.setRootFolderUri(uri.toString())
        romsRootUri.value = uri.toString()
        rescanAll(context)
    }

    fun rescanAll(context: Context) {
        val rootUriString = romsRootUri.value ?: return
        viewModelScope.launch {
            isScanning.value = true
            val platforms = uiState.value.platforms
            val existing = repository.allExistingFileUris()
            val found = RomScanner.scanRoot(context, Uri.parse(rootUriString), platforms, existing)
            if (found.isNotEmpty()) repository.addGames(found)
            message.value = if (found.isNotEmpty()) "Added ${found.size} game(s)" else "No new games found"
            isScanning.value = false
        }
    }

    fun toggleFavorite(game: Game) {
        viewModelScope.launch { repository.updateGame(game.copy(isFavorite = !game.isFavorite)) }
    }

    fun renameGame(game: Game, newTitle: String) {
        viewModelScope.launch { repository.updateGame(game.copy(title = newTitle)) }
    }

    fun removeGame(game: Game) {
        viewModelScope.launch { repository.deleteGame(game) }
    }

    fun markPlayed(game: Game) {
        viewModelScope.launch { repository.markPlayed(game) }
    }

    fun consumeMessage() {
        message.value = null
    }

    class Factory(private val repository: LibraryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(repository) as T
    }
}
