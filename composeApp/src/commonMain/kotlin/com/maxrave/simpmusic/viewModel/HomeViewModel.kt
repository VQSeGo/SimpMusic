package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.home.HomeItem
import com.maxrave.domain.data.model.home.chart.Chart
import com.maxrave.domain.data.model.mood.Mood
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.manager.DataStoreManager.Values.TRUE
import com.maxrave.domain.repository.HomeRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.Startup
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.music_video
import simpmusic.composeapp.generated.resources.new_release
import simpmusic.composeapp.generated.resources.song
import simpmusic.composeapp.generated.resources.view_count

class HomeViewModel(
    private val dataStoreManager: DataStoreManager,
    private val homeRepository: HomeRepository,
) : BaseViewModel() {
    private val _homeItemList: MutableStateFlow<List<HomeItem>> =
        MutableStateFlow(arrayListOf())
    val homeItemList: StateFlow<List<HomeItem>> = _homeItemList

    private var _homeListState = MutableStateFlow<ListState>(ListState.IDLE)
    val homeListState: StateFlow<ListState> = _homeListState

    private var _continuation = MutableStateFlow<String?>(null)
    val continuation: StateFlow<String?> = _continuation

    private val _exploreMoodItem: MutableStateFlow<Mood?> = MutableStateFlow(null)
    val exploreMoodItem: StateFlow<Mood?> = _exploreMoodItem
    private val _accountInfo: MutableStateFlow<Pair<String?, String?>?> = MutableStateFlow(null)
    val accountInfo: StateFlow<Pair<String?, String?>?> = _accountInfo

    private var homeJob: Job? = null

    // The mood chip the feed on screen was loaded for; meaningful only while the list is not empty.
    private var shownHomeParams: String? = null

    val showSnackBarErrorState = MutableSharedFlow<String>()

    private val _chart: MutableStateFlow<Chart?> = MutableStateFlow(null)
    val chart: StateFlow<Chart?> = _chart
    private val _newRelease: MutableStateFlow<List<HomeItem>> = MutableStateFlow(arrayListOf())
    val newRelease: StateFlow<List<HomeItem>> = _newRelease
    var regionCodeChart: MutableStateFlow<String?> = MutableStateFlow(null)

    val loading = MutableStateFlow<Boolean>(true)
    val loadingChart = MutableStateFlow<Boolean>(true)

    private val _songEntity: MutableStateFlow<SongEntity?> = MutableStateFlow(null)
    val songEntity: StateFlow<SongEntity?> = _songEntity

    private var _params: MutableStateFlow<String?> = MutableStateFlow(null)
    val params: StateFlow<String?> = _params

    // For showing alert that should log in to YouTube
    private val _showLogInAlert: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogInAlert: StateFlow<Boolean> = _showLogInAlert

    val dataSyncId =
        dataStoreManager
            .dataSyncId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _mainHomeThumbnail: MutableStateFlow<String?> = MutableStateFlow(null)
    val mainHomeThumbnail: StateFlow<String?> = _mainHomeThumbnail

    // The page background image YouTube sends with Home, null when it sends none.
    private val _homeBackground: MutableStateFlow<String?> = MutableStateFlow(null)
    val homeBackground: StateFlow<String?> = _homeBackground

    init {
        viewModelScope.launch {
            if (dataStoreManager.cookie.first().isEmpty() &&
                dataStoreManager.shouldShowLogInRequiredAlert.first() == TRUE
            ) {
                _showLogInAlert.update { true }
            }
        }
        viewModelScope.launch {
            regionCodeChart.value = dataStoreManager.chartKey.first()
            launch {
                homeItemList.collectLatest { list ->
                    _mainHomeThumbnail.value =
                        list
                            .firstOrNull()
                            ?.contents
                            ?.firstOrNull()
                            ?.thumbnails
                            ?.lastOrNull()
                            ?.url
                }
            }
            // One subscription loads Home: at start, and again when the region, the language, the
            // session or the mood chip changes. The settings store re-emits on any write, hence the dedupe.
            // The cookie seen by the last load that got past the wait below; null until the first one.
            var loadedCookie: String? = null
            combine(
                dataStoreManager.location,
                dataStoreManager.language,
                dataStoreManager.cookie,
                params,
            ) { location, language, cookie, params ->
                HomeInputs(location, language, cookie, params)
            }.distinctUntilChanged()
                .collectLatest { inputs ->
                    if (loadedCookie != inputs.cookie) {
                        _accountInfo.value =
                            Pair(
                                dataStoreManager.getString("AccountName").first(),
                                dataStoreManager.getString("AccountThumbUrl").first(),
                            )
                    }
                    if (loadedCookie != null && loadedCookie != inputs.cookie && inputs.cookie.isNotEmpty()) {
                        // Signed in while running: CommonRepositoryImpl's ytCookieJob puts the cookie on the
                        // client from the same store, give it a moment. At start there is nothing to wait for,
                        // CommonRepositoryImpl.init sets the stored cookie synchronously.
                        Logger.w(tag, "Cookie changed, refreshing home")
                        loading.value = true
                        // Another account's feed must not survive a failed load of this one.
                        _homeItemList.value = listOf()
                        delay(1000)
                    }
                    loadedCookie = inputs.cookie
                    getHomeItemList(inputs.params)
                }
        }
    }

    private data class HomeInputs(
        val location: String,
        val language: String,
        val cookie: String,
        val params: String?,
    )

    fun doneShowLogInAlert(neverShowAgain: Boolean = false) {
        viewModelScope.launch {
            _showLogInAlert.update { false }
            if (neverShowAgain) {
                dataStoreManager.setShouldShowLogInRequiredAlert(false)
            }
        }
    }

    /**
     * Loads Home. The feed, moods, chart and new releases load independently, so the feed (and its
     * copy from the last launch, served first) is not held back by the slowest of the four.
     *
     * [refresh] is a deliberate reload (refresh button, pull, retry): shimmer and no cached copy. Otherwise a
     * feed already on screen for the same chip stays there until the fresh one replaces it.
     */
    fun getHomeItemList(
        params: String? = null,
        refresh: Boolean = false,
    ) {
        Startup.mark("home-requested")
        if (refresh || _homeItemList.value.isEmpty() || shownHomeParams != params) {
            loading.value = true
        }
        _homeListState.value = ListState.LOADING
        homeJob?.cancel()
        homeJob =
            viewModelScope.launch {
                val chartKey = dataStoreManager.chartKey.first()
                launch {
                    homeRepository
                        .getHomeData(
                            params,
                            getString(Res.string.view_count),
                            getString(Res.string.song),
                            useCache = !refresh,
                        ).collect { home -> onHomeFeed(home, params) }
                    // A Success without a continuation may be the cached copy (its token is dropped) and paging
                    // waits for the network. The flow has ended now, so nothing fresher is coming.
                    if (_homeListState.value == ListState.LOADING) {
                        _homeListState.value = ListState.PAGINATION_EXHAUST
                    }
                    loading.value = false
                }
                launch {
                    homeRepository.getMoodAndMomentsData().collect { mood ->
                        _exploreMoodItem.value = (mood as? Resource.Success)?.data
                        if (mood is Resource.Error) Logger.w("Error", "getHomeItemList mood: ${mood.message}")
                    }
                }
                launch {
                    homeRepository.getChartData(chartKey).collect { chart ->
                        _chart.value = (chart as? Resource.Success)?.data
                        if (chart is Resource.Error) Logger.w("Error", "getHomeItemList chart: ${chart.message}")
                        regionCodeChart.value = chartKey
                        loadingChart.value = false
                    }
                }
                launch {
                    homeRepository
                        .getNewRelease(
                            getString(Res.string.new_release),
                            getString(Res.string.music_video),
                        ).collect { newRelease ->
                            _newRelease.value = (newRelease as? Resource.Success)?.data ?: arrayListOf()
                        }
                }
            }
    }

    private suspend fun onHomeFeed(
        home: Resource<Triple<String?, List<HomeItem>, String?>>,
        params: String?,
    ) {
        Logger.d("home size", "${home.data?.second?.size}")
        when (home) {
            is Resource.Success -> {
                val continuation = home.data?.first
                _continuation.value = continuation
                _homeItemList.value = home.data?.second ?: listOf()
                _homeBackground.value = home.data?.third
                shownHomeParams = params
                // A token can only come from the network. Without one this may be the cached copy, and the
                // fresh feed is still on its way: hold paging (LOADING) until the flow ends.
                _homeListState.value = if (continuation.isNullOrEmpty()) ListState.LOADING else ListState.IDLE
            }

            is Resource.Error -> {
                // The network failed. A feed for this chip already on screen (from the cache or an earlier load)
                // stays; otherwise the screen falls back to the offline state as before.
                val keep = _homeItemList.value.isNotEmpty() && shownHomeParams == params
                if (!keep) {
                    _continuation.value = null
                    _homeItemList.value = home.data?.second ?: listOf()
                    if (_homeItemList.value.isNotEmpty()) shownHomeParams = params
                }
                _homeListState.value = ListState.PAGINATION_EXHAUST
                Logger.w("Error", "getHomeItemList: ${home.message}")
                home.message?.let { showSnackBarErrorState.emit(it) }
            }
        }
        if (dataStoreManager.cookie.first() != "") {
            _accountInfo.value =
                Pair(
                    dataStoreManager.getString("AccountName").first(),
                    dataStoreManager.getString("AccountThumbUrl").first(),
                )
        }
        loading.value = false
    }

    /** A shelf's "Play all": YouTube's own finite queue of that shelf's songs. */
    fun playAll(item: HomeItem) {
        item.playAllEndpoint?.let { playFromEndpoint(it, item.title) }
    }

    fun getContinueHomeItem(continuation: String?) {
        viewModelScope.launch {
            if (continuation.isNullOrEmpty()) {
                _homeListState.value = ListState.PAGINATION_EXHAUST
                return@launch
            } else {
                log("Get more home item with continuation: $continuation")
                _homeListState.value = ListState.PAGINATING
                homeRepository
                    .getHomeDataContinue(
                        continuation,
                        getString(Res.string.view_count),
                        getString(Res.string.song),
                    ).collect { home ->
                        when (home) {
                            is Resource.Success -> {
                                _continuation.value = home.data?.first
                                val newItems = home.data?.second ?: listOf()
                                _homeItemList.update { it + newItems }
                                if (home.data?.first.isNullOrEmpty()) {
                                    _homeListState.value = ListState.PAGINATION_EXHAUST
                                } else {
                                    _homeListState.value = ListState.IDLE
                                }
                            }

                            is Resource.Error -> {
                                _continuation.value = null
                                Logger.w(tag, "getContinueHomeItem: ${home.message}")
                                showSnackBarErrorState.emit(home.message ?: "Unknown error")
                                _homeListState.value = ListState.PAGINATION_EXHAUST
                            }
                        }
                    }
            }
        }
    }

    fun exploreChart(region: String) {
        viewModelScope.launch {
            loadingChart.value = true
            homeRepository
                .getChartData(
                    region,
                ).collect { values ->
                    regionCodeChart.value = region
                    dataStoreManager.setChartKey(region)
                    when (values) {
                        is Resource.Success -> {
                            _chart.value = values.data
                        }

                        else -> {
                            _chart.value = null
                        }
                    }
                    loadingChart.value = false
                }
        }
    }

    fun setParams(params: String?) {
        _params.value = params
    }

    override fun onCleared() {
        super.onCleared()
        homeJob?.cancel()
    }

    companion object {
        // Home params
        const val HOME_PARAMS_RELAX = "ggM8SgQIBxADSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_SLEEP = "ggM8SgQIBxABSgQIBRADSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_ENERGIZE = "ggM8SgQIBxABSgQIBRABSgQICRADSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_SAD = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChADSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_ROMANCE = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRADSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_FEEL_GOOD = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBADSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_WORKOUT = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBADSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_PARTY = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhADSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_COMMUTE = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxADSgQIBhAB"
        const val HOME_PARAMS_FOCUS = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAD"
    }
}