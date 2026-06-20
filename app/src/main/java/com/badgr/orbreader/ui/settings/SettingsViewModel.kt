package com.badgr.orbreader.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badgr.orbreader.data.preferences.UserPreferences
import com.badgr.orbreader.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = UserPreferencesRepository(application)

    val prefs = repo.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences()
    )

    fun setWpm(wpm: Int)                         = viewModelScope.launch { repo.setDefaultWpm(wpm) }
    fun setFontSize(size: Int)                   = viewModelScope.launch { repo.setFontSize(size) }
    fun setShowOrpColor(show: Boolean)           = viewModelScope.launch { repo.setShowOrpColor(show) }
    fun setOrpColorIndex(idx: Int)               = viewModelScope.launch { repo.setOrpColorIndex(idx) }
    fun setThemeMode(mode: Int)                  = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setFontIndex(idx: Int)                   = viewModelScope.launch { repo.setFontIndex(idx) }
    fun setChunkSize(size: Int)                  = viewModelScope.launch { repo.setChunkSize(size) }
    fun setSentencePauseMultiplier(mult: Float)  = viewModelScope.launch { repo.setSentencePauseMultiplier(mult) }
    fun setClausePauseMultiplier(mult: Float)    = viewModelScope.launch { repo.setClausePauseMultiplier(mult) }
    fun setColorBlindnessMode(mode: Int)         = viewModelScope.launch { repo.setColorBlindnessMode(mode) }
    fun resetHelpSeen()                          = viewModelScope.launch { repo.setHasSeenHelp(false) }
}
