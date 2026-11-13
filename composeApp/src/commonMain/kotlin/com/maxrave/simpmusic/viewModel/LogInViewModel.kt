package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.DiscordRepository
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogInViewModel(
    private val dataStoreManager: DataStoreManager,
    private val discordRepository: DiscordRepository,
) : BaseViewModel() {
    private val _spotifyStatus: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val spotifyStatus: StateFlow<Boolean> get() = _spotifyStatus

    private val _fullSpotifyCookies: MutableStateFlow<List<Pair<String, String?>>> = MutableStateFlow(emptyList())
    val fullSpotifyCookies: StateFlow<List<Pair<String, String?>>> get() = _fullSpotifyCookies.asStateFlow()

    private val _fullYouTubeCookies: MutableStateFlow<List<Pair<String, String?>>> = MutableStateFlow(emptyList())
    val fullYouTubeCookies: StateFlow<List<Pair<String, String?>>> get() = _fullYouTubeCookies.asStateFlow()

    fun saveSpotifySpdc(cookie: String) {
        viewModelScope.launch {
            cookie
                .split("; ")
                .filter { it.isNotEmpty() }
                .associate {
                    val (key, value) = it.split("=")
                    key to value
                }.let {
                    if (it["sp_dc"].isNullOrEmpty()) Logger.w("Auth", "Spotify: sign-in finished without an sp_dc cookie")
                    dataStoreManager.setSpdc(it["sp_dc"] ?: "")
                    _spotifyStatus.value = true
                }
        }
    }

    fun setVisitorData(visitorData: String) {
        viewModelScope.launch {
            dataStoreManager.setVisitorData(visitorData)
        }
    }

    fun setDataSyncId(dataSyncId: String) {
        viewModelScope.launch {
            dataStoreManager.setDataSyncId(dataSyncId)
        }
    }

    fun setFullSpotifyCookies(cookies: List<Pair<String, String?>>) {
        viewModelScope.launch {
            _fullSpotifyCookies.value = cookies
        }
    }

    fun setFullYouTubeCookies(cookies: List<Pair<String, String?>>) {
        viewModelScope.launch {
            _fullYouTubeCookies.value = cookies
        }
    }

    fun saveDiscordToken(token: String) {
        viewModelScope.launch {
            dataStoreManager.setDiscordToken(token)
        }
    }

    private val _discordLoginState: MutableStateFlow<DiscordLoginState> = MutableStateFlow(DiscordLoginState.Idle)
    val discordLoginState: StateFlow<DiscordLoginState> get() = _discordLoginState.asStateFlow()

    // True once a Discord login is stored, whoever finished it: SharedViewModel takes the redirect,
    // this view model the pasted link, and the screen closes itself on either.
    private val _discordLoggedIn = MutableStateFlow(false)
    val discordLoggedIn: StateFlow<Boolean> get() = _discordLoggedIn.asStateFlow()

    init {
        viewModelScope.launch {
            discordRepository.loggedIn.collect { _discordLoggedIn.value = it }
        }
    }

    /** Sends the user to Discord's authorization page (Full builds). */
    fun startDiscordLogin() {
        val url = discordRepository.startLogin()
        _discordLoginState.value =
            if (url != null) {
                DiscordLoginState.AwaitingApproval(authorizeUrl = url)
            } else {
                DiscordLoginState.Failed
            }
    }

    /** Finishes the login from the link the user pasted, for when the redirect never reaches the app. */
    fun completeDiscordLoginFromCallback(input: String) {
        val link = input.trim()
        if (link.isEmpty()) return
        viewModelScope.launch {
            _discordLoginState.value = DiscordLoginState.CompletingLogin
            _discordLoginState.value =
                if (discordRepository.completeLogin(link)) DiscordLoginState.LoggedIn else DiscordLoginState.Failed
        }
    }

    fun resetDiscordLoginState() {
        _discordLoginState.value = DiscordLoginState.Idle
    }
}

sealed interface DiscordLoginState {
    data object Idle : DiscordLoginState

    /** The browser is open and the user has not come back yet. */
    data class AwaitingApproval(
        val authorizeUrl: String,
    ) : DiscordLoginState

    data object CompletingLogin : DiscordLoginState

    data object LoggedIn : DiscordLoginState

    data object Failed : DiscordLoginState
}
