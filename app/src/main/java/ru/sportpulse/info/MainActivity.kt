package ru.sportpulse.info

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import android.text.InputFilter
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var state: UserStateStore
    private lateinit var appShell: LinearLayout
    private lateinit var mainScroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var heroStatus: TextView
    private lateinit var heroTimeline: TextView
    private lateinit var sourceBadge: TextView
    private lateinit var tabScroller: HorizontalScrollView
    private val tabViews = mutableListOf<TextView>()
    private val passportExecutor = Executors.newSingleThreadExecutor()
    private val eventPackageExecutor = Executors.newSingleThreadExecutor()
    private val apiFootballExecutor = Executors.newSingleThreadExecutor()

    private lateinit var apiFootballCache: ApiFootballCache
    private var apiFootballFeed: ApiFootballFeed? = null
    private var previousApiFootballFeed: ApiFootballFeed? = null
    private var apiFootballDelta: ApiFootballDelta? = null
    private var apiFootballError: String? = null
    private var apiFootballRefreshInProgress = false
    private var importedEventPackage: SportEventPackage? = null
    private var previousEventPackage: SportEventPackage? = null
    private var eventPackageDelta: EventPackageDelta? = null
    private var catalogEvents: List<SportEvent> = DemoCatalog.events
    private var activeCatalogOrigin = SportEventOrigin.DEMO
    private var activeCatalogPackageId: String? = null

    private var activeTab = 0
    private var activeSportFilter = "Все"
    private var activeFeedWorkspaceMode =
        FeedWorkspaceMode.FOCUS
    private var activeMarketLensKind =
        MarketKind.ONE_X_TWO
    private var activePulseWorkspaceMode =
        PulseWorkspaceMode.STORY
    private var activePulseLabSection =
        PulseLabSection.ROUTE
    private var activeDecisionDeskSection =
        DecisionDeskSection.DECISION
    private var decisionDeskWorkspaceExpanded = false
    private var pendingDecisionDeskField: DecisionDeskField? = null
    private var pendingPulseFactor: SignalFactor? = null
    private var pendingPulseStoryAction: EventStoryAction? = null
    private var savedOnly = false
    private var eventSearchQuery = ""
    private var activeFeedTimeFilter = FeedTimelineFilter.ALL
    private var focusEventLimit = FOCUS_EVENT_PAGE_SIZE
    private var apiUpdatePulseExpanded = false
    private var sourceReadinessDetailsExpanded = false
    private var updateRadarExpanded = false
    private var decisionLedgerExpanded = false
    private var pulseStoryControlsExpanded = false
    private var plainAnalyticsProtocolExpanded = false
    private var plainAnalyticsProtocolEventId: String? = null
    private var pulseWorkspaceControlsAnchor: View? = null
    private var pulseLabNavigatorAnchor: View? = null
    private var analysisEventAnchor: View? = null
    private var decisionDeskOverviewAnchor: View? = null
    private var decisionDeskWorkspaceAnchor: View? = null
    private var decisionDeskSectionAnchor: View? = null
    private var passportExportInProgress = false
    private var eventPackageImportInProgress = false
    private var decisionDistanceDraft =
        DecisionDistanceAssessment.unanswered()
    private var returnToPulseAfterDistance = false
    private var pendingDecisionReceiptEventId: String? = null
    private var pendingDecisionReceiptChoice: SavedDecision? = null
    private var activityResumed = false
    private var attentionTrackingStartedElapsed: Long? = null
    private var attentionTrackingStartedDay: Long? = null

    private val tabs = listOf(
        "Матчи",
        "Штаб",
        "Чек-листы",
        "Гид",
        "18+"
    )

    private data class ProductTourStep(
        val stage: String,
        val title: String,
        val action: String,
        val result: String,
        val guardrail: String,
        val nextAction: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = UserStateStore(this)
        apiFootballCache = ApiFootballCache(this)
        val apiHistory = apiFootballCache.readHistory()
        apiFootballFeed = apiHistory.current
        previousApiFootballFeed = apiHistory.previous
        apiFootballDelta = apiFootballDelta(
            previous = apiHistory.previous,
            current = apiHistory.current
        )
        reloadEventCatalog()
        activeTab = savedInstanceState?.getInt(STATE_ACTIVE_TAB, 0) ?: 0
        activeSportFilter = savedInstanceState?.getString(STATE_FILTER) ?: "Все"
        activeFeedWorkspaceMode = savedInstanceState
            ?.getString(STATE_FEED_WORKSPACE_MODE)
            ?.let(FeedWorkspaceMode::fromStored)
            ?: state.selectedFeedWorkspaceMode
        activeMarketLensKind = savedInstanceState
            ?.getString(STATE_MARKET_LENS_KIND)
            ?.let {
                runCatching {
                    MarketKind.valueOf(it)
                }.getOrNull()
            }
            ?: state.selectedMarketKind
        activePulseWorkspaceMode = savedInstanceState
            ?.getString(STATE_PULSE_WORKSPACE_MODE)
            ?.let(PulseWorkspaceMode::fromStored)
            ?: state.selectedPulseWorkspaceMode
        activePulseLabSection = PulseLabSection.fromStored(
            savedInstanceState?.getString(
                STATE_PULSE_LAB_SECTION
            )
        )
        activeDecisionDeskSection =
            DecisionDeskSection.fromStored(
                savedInstanceState?.getString(
                    STATE_DECISION_DESK_SECTION
                )
            )
        decisionDeskWorkspaceExpanded = savedInstanceState
            ?.getBoolean(
                STATE_DECISION_DESK_WORKSPACE_EXPANDED,
                false
            ) ?: false
        savedOnly = savedInstanceState?.getBoolean(STATE_SAVED_ONLY, false) ?: false
        eventSearchQuery = savedInstanceState
            ?.getString(STATE_EVENT_SEARCH_QUERY)
            .orEmpty()
            .take(EventSearchPolicy.MAX_QUERY_LENGTH)
        activeFeedTimeFilter = FeedTimelineFilter.fromStored(
            savedInstanceState?.getString(
                STATE_FEED_TIME_FILTER
            )
        )
        focusEventLimit = savedInstanceState?.getInt(
            STATE_FOCUS_EVENT_LIMIT,
            FOCUS_EVENT_PAGE_SIZE
        )?.coerceAtLeast(FOCUS_EVENT_PAGE_SIZE)
            ?: FOCUS_EVENT_PAGE_SIZE
        apiUpdatePulseExpanded = savedInstanceState?.getBoolean(
            STATE_API_UPDATE_PULSE_EXPANDED,
            false
        ) ?: false
        sourceReadinessDetailsExpanded = savedInstanceState?.getBoolean(
            STATE_SOURCE_READINESS_DETAILS_EXPANDED,
            false
        ) ?: false
        updateRadarExpanded = savedInstanceState?.getBoolean(
            STATE_UPDATE_RADAR_EXPANDED,
            false
        ) ?: false
        decisionLedgerExpanded = savedInstanceState?.getBoolean(
            STATE_DECISION_LEDGER_EXPANDED,
            false
        ) ?: false
        pulseStoryControlsExpanded = savedInstanceState?.getBoolean(
            STATE_PULSE_STORY_CONTROLS_EXPANDED,
            false
        ) ?: false
        plainAnalyticsProtocolExpanded = savedInstanceState?.getBoolean(
            STATE_PLAIN_ANALYTICS_PROTOCOL_EXPANDED,
            false
        ) ?: false
        plainAnalyticsProtocolEventId = savedInstanceState?.getString(
            STATE_PLAIN_ANALYTICS_PROTOCOL_EVENT_ID
        ) ?: state.selectedEventId

        configureSystemBars()

        appShell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.background)
        }
        applySystemBarInsets(appShell)

        mainScroll = ScrollView(this).apply {
            setBackgroundColor(AppColors.background)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = true
        }
        val holder = FrameLayout(this)
        mainScroll.addView(
            holder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(34))
        }
        val availableWidth = resources.displayMetrics.widthPixels
        holder.addView(
            root,
            FrameLayout.LayoutParams(
                min(availableWidth, dp(780)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        root.addView(appHeader(), matchWrap())
        root.addView(hero(), matchFixed(heroHeightDp(), top = 8))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content, matchWrap(top = 6))

        appShell.addView(
            mainScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        appShell.addView(
            navigationDock(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(appShell)
        renderContent()
        if (!state.hasConfirmedAge) {
            appShell.visibility = View.INVISIBLE
        }
        mainScroll.post {
            if (!state.hasConfirmedAge) {
                showAgeGate()
            } else {
                refreshApiFootball(force = false)
                if (!state.hasSeenProductTour) {
                    showProductTour()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_ACTIVE_TAB, activeTab)
        outState.putString(STATE_FILTER, activeSportFilter)
        outState.putString(
            STATE_FEED_WORKSPACE_MODE,
            activeFeedWorkspaceMode.name
        )
        outState.putString(
            STATE_MARKET_LENS_KIND,
            activeMarketLensKind.name
        )
        outState.putString(
            STATE_PULSE_WORKSPACE_MODE,
            activePulseWorkspaceMode.name
        )
        outState.putString(
            STATE_PULSE_LAB_SECTION,
            activePulseLabSection.name
        )
        outState.putString(
            STATE_DECISION_DESK_SECTION,
            activeDecisionDeskSection.name
        )
        outState.putBoolean(
            STATE_DECISION_DESK_WORKSPACE_EXPANDED,
            decisionDeskWorkspaceExpanded
        )
        outState.putBoolean(STATE_SAVED_ONLY, savedOnly)
        outState.putString(
            STATE_EVENT_SEARCH_QUERY,
            eventSearchQuery
        )
        outState.putString(
            STATE_FEED_TIME_FILTER,
            activeFeedTimeFilter.name
        )
        outState.putInt(
            STATE_FOCUS_EVENT_LIMIT,
            focusEventLimit
        )
        outState.putBoolean(
            STATE_API_UPDATE_PULSE_EXPANDED,
            apiUpdatePulseExpanded
        )
        outState.putBoolean(
            STATE_SOURCE_READINESS_DETAILS_EXPANDED,
            sourceReadinessDetailsExpanded
        )
        outState.putBoolean(
            STATE_UPDATE_RADAR_EXPANDED,
            updateRadarExpanded
        )
        outState.putBoolean(
            STATE_DECISION_LEDGER_EXPANDED,
            decisionLedgerExpanded
        )
        outState.putBoolean(
            STATE_PULSE_STORY_CONTROLS_EXPANDED,
            pulseStoryControlsExpanded
        )
        outState.putBoolean(
            STATE_PLAIN_ANALYTICS_PROTOCOL_EXPANDED,
            plainAnalyticsProtocolExpanded
        )
        outState.putString(
            STATE_PLAIN_ANALYTICS_PROTOCOL_EVENT_ID,
            plainAnalyticsProtocolEventId
        )
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (::content.isInitialized) {
            val catalogChanged = ensureEventCatalogCurrent()
            if (catalogChanged || activeTab == 4) {
                renderContent()
            }
            startAttentionTrackingIfNeeded()
        }
    }

    override fun onPause() {
        flushAttentionTracking()
        activityResumed = false
        super.onPause()
    }

    @Deprecated("Deprecated in Android, retained because the app has no AndroidX dependency")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (
            requestCode == REQUEST_EVENT_PACKAGE &&
            resultCode == RESULT_OK
        ) {
            data?.data?.let(::importEventPackage)
        }
    }

    override fun onDestroy() {
        passportExecutor.shutdownNow()
        eventPackageExecutor.shutdownNow()
        apiFootballExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun reloadEventCatalog(
        now: Long = System.currentTimeMillis()
    ) {
        val stored = state.importedEventPackageJson()
        val decoded = decodeStoredEventPackage(stored, now)
        if (stored != null && decoded == null) {
            state.clearImportedEventPackage()
        }
        val previousStored = if (decoded == null) {
            null
        } else {
            state.previousImportedEventPackageJson()
        }
        val previousDecoded = decodeStoredEventPackage(
            previousStored,
            now
        )
        if (previousStored != null && previousDecoded == null) {
            state.clearPreviousImportedEventPackage()
        }
        importedEventPackage = decoded
        previousEventPackage = previousDecoded
        eventPackageDelta = if (
            decoded != null &&
            previousDecoded != null
        ) {
            EventPackageDeltaEngine.compare(
                previous = previousDecoded,
                current = decoded
            )
        } else {
            null
        }
        val activePackage = decoded?.takeUnless { it.isExpired(now) }
        activeCatalogPackageId = activePackage?.packageId
        val packagedEvents = activePackage?.events?.map { event ->
            event.toSportEvent(
                imageRes = imageForSport(event.sport),
                formattedTime = formatImportedEventTime(event.startAt),
                runtimeId = EventPackageIdentity.runtimeEventId(
                    activePackage.sourceLabel,
                    event.id
                )
            )
        }
        val apiEvents = apiFootballFeed?.fixtures?.map { fixture ->
            fixture.toSportEvent(
                imageRes = R.drawable.event_football,
                fetchedAt = checkNotNull(apiFootballFeed).fetchedAt
            )
        }.orEmpty()
        catalogEvents = when {
            packagedEvents != null -> packagedEvents.also {
                activeCatalogOrigin = SportEventOrigin.EVENT_PACKAGE
            }
            apiEvents.isNotEmpty() -> apiEvents.also {
                activeCatalogOrigin = SportEventOrigin.API_SPORTS
            }
            else -> DemoCatalog.events.also {
                activeCatalogOrigin = SportEventOrigin.DEMO
            }
        }
        if (state.selectedEventId !in catalogEvents.map { it.id }) {
            state.selectedEventId = catalogEvents.firstOrNull()?.id
        }
    }

    private fun decodeStoredEventPackage(
        json: String?,
        now: Long
    ): SportEventPackage? {
        return json?.let {
            runCatching {
                EventPackageDocumentCodec.decode(
                    json = it,
                    now = now,
                    requireFresh = false
                )
            }.getOrNull()
        }
    }

    private fun ensureEventCatalogCurrent(
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val expectedPackageId = importedEventPackage
            ?.takeUnless { it.isExpired(now) }
            ?.packageId
        if (expectedPackageId != activeCatalogPackageId) {
            reloadEventCatalog(now)
            refreshHeroStatus()
            refreshSourceBadge()
            return true
        }
        return false
    }

    private fun catalogEvent(id: String?): SportEvent {
        return catalogEvents.firstOrNull { it.id == id }
            ?: catalogEvents.first()
    }

    private fun imageForSport(sport: String): Int {
        val normalized = sport.lowercase(Locale.forLanguageTag("ru-RU"))
        return when {
            "футбол" in normalized ->
                R.drawable.event_football
            "хоккей" in normalized ->
                R.drawable.event_hockey
            "кибер" in normalized ->
                R.drawable.pulse_workspace
            else ->
                R.drawable.hero_sport_pulse
        }
    }

    private fun formatImportedEventTime(timestamp: Long): String {
        return SimpleDateFormat(
            "EEE, d MMM • HH:mm 'МСК'",
            Locale.forLanguageTag("ru-RU")
        ).apply {
            timeZone = TimeZone.getTimeZone("Europe/Moscow")
        }.format(Date(timestamp))
    }

    private fun matchdayBriefing(
        now: Long = System.currentTimeMillis()
    ): MatchdayBriefing {
        return MatchdayBriefingEngine.evaluate(
            events = catalogEvents,
            bookmarkedIds = state.bookmarkedIds(),
            now = now,
            zoneId = state.selectedRegionalZone.zoneId
        )
    }

    private fun refreshHeroStatus() {
        val briefing = matchdayBriefing()
        if (::heroStatus.isInitialized) {
            heroStatus.text = briefing.catalogText()
        }
        if (::heroTimeline.isInitialized) {
            heroTimeline.text = briefing.timelineText()
        }
    }

    private fun refreshSourceBadge() {
        if (!::sourceBadge.isInitialized) return
        val title: String
        val background: Int
        val foreground: Int
        when {
            state.isPauseActive() -> {
                title = "ПАУЗА • 18+"
                background = AppColors.dangerSoft
                foreground = AppColors.danger
            }
            activeCatalogPackageId != null -> {
                when (
                    importedEventPackage
                        ?.authenticity
                        ?.keyEnvironment
                ) {
                    EventPackageKeyEnvironment.PRODUCTION -> {
                        title = "ПОДПИСАН • 18+"
                        background = AppColors.accentSoft
                        foreground = AppColors.accentDark
                    }
                    EventPackageKeyEnvironment.DEVELOPMENT -> {
                        title = "ДЕМО-КЛЮЧ • 18+"
                        background = AppColors.signalSoft
                        foreground = AppColors.signal
                    }
                    null -> {
                        title = "БЕЗ ПОДПИСИ • 18+"
                        background = AppColors.warningSoft
                        foreground = AppColors.warning
                    }
                }
            }
            activeCatalogOrigin == SportEventOrigin.API_SPORTS -> {
                title = "ОНЛАЙН • 18+"
                background = AppColors.accentSoft
                foreground = AppColors.accentDark
            }
            else -> {
                title = "ДЕМО • 18+"
                background = AppColors.signalSoft
                foreground = AppColors.signal
            }
        }
        sourceBadge.text = title
        sourceBadge.setTextColor(foreground)
        sourceBadge.background = rounded(
            background,
            14,
            background,
            1
        )
    }

    private fun apiFootballConfigured(): Boolean {
        return SportsScheduleProxyPolicy.isConfigured(
            BuildConfig.SPORTS_SCHEDULE_PROXY_URL
        )
    }

    private fun refreshApiFootball(force: Boolean) {
        if (
            !apiFootballConfigured() ||
            apiFootballRefreshInProgress ||
            activeCatalogPackageId != null
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (
            force &&
            apiFootballFeed?.fetchedAt?.let {
                it <= now &&
                    now - it <
                    ApiFootballPolicy.MANUAL_REFRESH_MIN_AGE_MILLIS
            } == true
        ) {
            Toast.makeText(
                this,
                "Расписание уже свежее. Ручное обновление доступно раз в час.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!force && apiFootballFeed?.isFresh(now) == true) return

        apiFootballRefreshInProgress = true
        apiFootballError = null
        if (::content.isInitialized) {
            rerenderContentPreservingScroll()
        }
        apiFootballExecutor.execute {
            val result = runCatching {
                ApiFootballClient(
                    endpoint = BuildConfig.SPORTS_SCHEDULE_PROXY_URL
                ).fetch(now)
            }
            result.getOrNull()?.let { feed ->
                runCatching { apiFootballCache.write(feed) }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                apiFootballRefreshInProgress = false
                result.onSuccess { feed ->
                    previousApiFootballFeed = apiFootballFeed
                    apiFootballFeed = feed
                    apiFootballDelta = apiFootballDelta(
                        previous = previousApiFootballFeed,
                        current = feed
                    )
                    apiUpdatePulseExpanded = false
                    apiFootballError = null
                    reloadEventCatalog()
                    refreshHeroStatus()
                    refreshSourceBadge()
                }.onFailure { error ->
                    apiFootballError = apiFootballErrorText(error)
                }
                rerenderContentPreservingScroll()
            }
        }
    }

    private fun apiFootballErrorText(error: Throwable): String {
        return when ((error as? ApiFootballException)?.statusCode) {
            401, 403 ->
                "Источник расписания временно отклонил запрос. Оставлены последние сохранённые данные."
            429 ->
                "Источник временно ограничил обновления. Оставлены последние сохранённые данные."
            else ->
                "Не удалось обновить события. Проверьте сеть и повторите."
        }
    }

    private fun apiFootballDelta(
        previous: ApiFootballFeed?,
        current: ApiFootballFeed?
    ): ApiFootballDelta? {
        if (previous == null || current == null) return null
        return runCatching {
            ApiFootballDeltaEngine.compare(previous, current)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun launchEventPackagePicker() {
        if (eventPackageImportInProgress) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(
                intent,
                REQUEST_EVENT_PACKAGE
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "На устройстве нет приложения для выбора JSON-файла.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun importEventPackage(uri: Uri) {
        if (eventPackageImportInProgress) return
        eventPackageImportInProgress = true
        val currentPackage = importedEventPackage
        if (activeTab == 0) renderContent()
        eventPackageExecutor.execute {
            val result = runCatching {
                val json = readEventPackage(uri)
                val eventPackage = EventPackageDocumentCodec.decode(
                    json = json,
                    now = System.currentTimeMillis(),
                    requireFresh = true
                )
                val updateStatus =
                    EventPackageUpdatePolicy.requireImportable(
                        current = currentPackage,
                        candidate = eventPackage
                    )
                Triple(json, eventPackage, updateStatus)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                eventPackageImportInProgress = false
                result.onSuccess {
                        (json, eventPackage, updateStatus) ->
                    if (
                        updateStatus ==
                        EventPackageUpdateStatus.IDENTICAL
                    ) {
                        if (activeTab == 0) renderContent()
                        Toast.makeText(
                            this,
                            "Эта версия уже активна • метка ${eventPackage.shortFingerprint}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        state.replaceImportedEventPackage(json)
                        reloadEventCatalog()
                        activeSportFilter = "Все"
                        savedOnly = false
                        updateRadarExpanded = false
                        refreshHeroStatus()
                        renderContent()
                        val message = when (updateStatus) {
                            EventPackageUpdateStatus.FIRST_VERSION ->
                                "Базовая версия зафиксирована • ${eventPackage.events.size} событий"
                            else -> {
                                val changes = eventPackageDelta
                                    ?.changes
                                    ?.size
                                    ?: 0
                                "Обновление принято • изменений: $changes"
                            }
                        }
                        Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    if (activeTab == 0) renderContent()
                    Toast.makeText(
                        this,
                        error.message
                            ?: "Не удалось импортировать пакет.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun readEventPackage(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (
                    total >
                    EventPackageDocumentCodec.MAX_DOCUMENT_BYTES
                ) {
                    throw EventPackageValidationException(
                        "Документ Event Pack превышает лимит 384 КБ."
                    )
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw EventPackageValidationException(
            "Не удалось открыть выбранный файл."
        )
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            throw EventPackageValidationException(
                "Файл должен быть корректным UTF-8 JSON."
            )
        }
    }

    private fun resetEventPackage() {
        state.clearImportedEventPackage()
        reloadEventCatalog()
        activeSportFilter = "Все"
        savedOnly = false
        updateRadarExpanded = false
        refreshHeroStatus()
        renderContent()
        Toast.makeText(
            this,
            if (activeCatalogOrigin == SportEventOrigin.API_SPORTS) {
                "Включено онлайн-расписание."
            } else {
                "Включен демо-каталог."
            },
            Toast.LENGTH_SHORT
        ).show()
        refreshApiFootball(force = false)
    }

    private fun formatPackageDate(timestamp: Long): String {
        return SimpleDateFormat(
            "d MMM, HH:mm 'МСК'",
            Locale.forLanguageTag("ru-RU")
        ).apply {
            timeZone = TimeZone.getTimeZone("Europe/Moscow")
        }.format(Date(timestamp))
    }

    private fun appHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                text("СПОРТ ПУЛЬС", 17f, AppColors.ink, Typeface.BOLD),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            sourceBadge = label(
                "",
                AppColors.signalSoft,
                AppColors.signal
            )
            refreshSourceBadge()
            addView(sourceBadge)
        }
    }

    private fun hero(): FrameLayout {
        val compactViewport =
            resources.configuration.fontScale < 1.3f &&
                resources.configuration.screenHeightDp < 840
        val frame = imageFrame()
        frame.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.matchday_briefing_v390)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        frame.addView(
            View(this).apply { background = gradientScrim() },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val briefing = matchdayBriefing()
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                label(
                    "ИНФОРМАЦИЯ • НЕ БК",
                    Color.argb(205, 20, 24, 27),
                    Color.WHITE,
                    Color.argb(80, 255, 255, 255)
                ),
                wrapWrap()
            )
            addView(
                text(
                    "Матч-день",
                    20.5f,
                    Color.WHITE,
                    Typeface.BOLD
                ),
                matchWrap(top = 6)
            )
            heroTimeline = text(
                briefing.timelineText(),
                12.5f,
                Color.rgb(228, 235, 237),
                Typeface.BOLD
            ).apply {
                visibility = if (compactViewport) View.GONE else View.VISIBLE
            }
            addView(heroTimeline, matchWrap(top = 2))
            heroStatus = text(
                briefing.catalogText(),
                11f,
                Color.rgb(194, 211, 215),
                Typeface.BOLD
            ).apply {
                visibility = if (compactViewport) View.GONE else View.VISIBLE
            }
            addView(heroStatus, matchWrap(top = 2))
        }
        frame.addView(
            copy,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(18)
                rightMargin = dp(18)
                topMargin = dp(if (compactViewport) 8 else 12)
                bottomMargin = dp(if (compactViewport) 8 else 12)
            }
        )
        return frame
    }

    private fun navigationDock(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(AppColors.field)
            elevation = dp(10).toFloat()
            addView(
                View(this@MainActivity).apply {
                    setBackgroundColor(AppColors.fieldLine)
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(1),
                    Gravity.TOP
                )
            )
            addView(
                tabBar(),
                FrameLayout.LayoutParams(
                    min(
                        resources.displayMetrics.widthPixels,
                        dp(780)
                    ),
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    private fun tabBar(): HorizontalScrollView {
        val compactWidth =
            resources.configuration.screenWidthDp < 360
        val fontScale = resources.configuration.fontScale
            .coerceAtLeast(1f)
        val maximumTextScale = if (compactWidth) {
            1.08f
        } else {
            1.25f
        }
        val tabTextSize = 14f *
            min(fontScale, maximumTextScale) / fontScale
        val tabHorizontalPadding = if (compactWidth) 5 else 8
        val tabMinimumWidth = if (compactWidth) 48 else 52
        val tabSpacing = if (compactWidth) 0 else 2
        tabScroller = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(14))
            setBackgroundColor(AppColors.field)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumWidth = min(
                resources.displayMetrics.widthPixels,
                dp(780)
            ) - dp(12)
        }
        tabs.forEachIndexed { index, title ->
            val tab = text(
                title,
                tabTextSize,
                AppColors.muted,
                Typeface.BOLD
            ).apply {
                gravity = Gravity.CENTER
                minWidth = dp(tabMinimumWidth)
                minHeight = dp(50)
                setPadding(
                    dp(tabHorizontalPadding),
                    0,
                    dp(tabHorizontalPadding),
                    0
                )
                applyAccessibleAction(dp(48))
                contentDescription = "Раздел $title"
                setOnClickListener { selectTab(index) }
            }
            tabViews.add(tab)
            row.addView(tab, wrapWrap(right = tabSpacing))
        }
        tabScroller.addView(row)
        updateTabs()
        return tabScroller
    }

    private fun selectTab(index: Int, scrollToContent: Boolean = true) {
        flushAttentionTracking()
        activeTab = index.coerceIn(tabs.indices)
        updateTabs()
        renderContent()
        startAttentionTrackingIfNeeded()
        if (scrollToContent) {
            mainScroll.post {
                mainScroll.smoothScrollTo(0, content.top.coerceAtLeast(0))
            }
        }
    }

    private fun startAttentionTrackingIfNeeded(
        now: Long = System.currentTimeMillis(),
        elapsed: Long = SystemClock.elapsedRealtime()
    ) {
        if (
            attentionTrackingStartedElapsed != null ||
            !activityResumed ||
            activeTab !in 1..2 ||
            (
                activeTab == 1 &&
                    !activePulseWorkspaceMode.tracksAttention
                ) ||
            state.isPauseActive(now)
        ) {
            return
        }
        attentionTrackingStartedElapsed = elapsed
        attentionTrackingStartedDay =
            AttentionBudgetDay.epochDay(now)
    }

    private fun flushAttentionTracking(
        now: Long = System.currentTimeMillis(),
        elapsed: Long = SystemClock.elapsedRealtime()
    ): AttentionBudgetResult {
        val pending = pendingAttentionMillis(now, elapsed)
        attentionTrackingStartedElapsed = null
        attentionTrackingStartedDay = null
        return if (pending > 0L) {
            state.addAttentionUsage(
                now = now,
                durationMillis = pending
            )
        } else {
            state.attentionBudget(now)
        }
    }

    private fun currentAttentionBudget(
        now: Long = System.currentTimeMillis(),
        elapsed: Long = SystemClock.elapsedRealtime()
    ): AttentionBudgetResult {
        val stored = state.attentionBudget(now)
        val used = (
            stored.usedMillis +
                pendingAttentionMillis(now, elapsed)
            ).coerceAtMost(AttentionBudgetPolicy.DAY_MILLIS)
        return AttentionBudgetEngine.evaluate(
            dayEpoch = stored.dayEpoch,
            usedMillis = used,
            limitMinutes = stored.limitMinutes
        )
    }

    private fun pendingAttentionMillis(
        now: Long,
        elapsed: Long
    ): Long {
        val started = attentionTrackingStartedElapsed
            ?: return 0L
        val raw = (elapsed - started).coerceAtLeast(0L)
        val currentDay = AttentionBudgetDay.epochDay(now)
        return if (attentionTrackingStartedDay == currentDay) {
            raw
        } else {
            min(
                raw,
                AttentionBudgetDay.millisSinceStart(now)
            )
        }.coerceAtMost(AttentionBudgetPolicy.DAY_MILLIS)
    }

    private fun updateTabs() {
        tabViews.forEachIndexed { index, tab ->
            val selected = index == activeTab
            tab.setTextColor(
                if (selected) {
                    Color.WHITE
                } else {
                    AppColors.fieldMuted
                }
            )
            tab.background = rippleRounded(
                if (selected) AppColors.fieldRaised else AppColors.field,
                4
            )
            val indicator = if (selected) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(AppColors.fieldSignal)
                    cornerRadius = dp(2).toFloat()
                    setBounds(0, 0, dp(28), dp(3))
                }
            } else {
                null
            }
            tab.setCompoundDrawables(null, null, null, indicator)
            tab.compoundDrawablePadding = dp(3)
            tab.isSelected = selected
        }
        if (::tabScroller.isInitialized) {
            val selected = tabViews.getOrNull(activeTab)
            tabScroller.post {
                selected?.let {
                    tabScroller.smoothScrollTo(
                        (it.left - dp(8)).coerceAtLeast(0),
                        0
                    )
                }
            }
        }
    }

    private fun renderContent() {
        ensureEventCatalogCurrent()
        refreshSourceBadge()
        analysisEventAnchor = null
        content.removeAllViews()
        when (activeTab) {
            0 -> renderFeed()
            1 -> renderPulse()
            2 -> renderMarkets()
            3 -> renderGuide()
            else -> renderResponsible()
        }
        content.addView(legalFooter(), matchWrap(top = 24))
    }

    private fun renderFeed() {
        content.addView(
            sectionTitle(
                "Матч-центр",
                "Россия и СНГ • следующий шаг проверки."
            )
        )
        content.addView(
            feedWorkspaceSwitcher(),
            matchWrap(top = 4)
        )
        content.addView(
            eventSearchPanel(),
            matchWrap(top = 4)
        )
        content.addView(filterBar(), matchWrap(top = 4))
        content.addView(feedTimelineBar(), matchWrap(top = 4))
        if (activeFeedWorkspaceMode == FeedWorkspaceMode.FOCUS) {
            renderFeedFocusMode()
            return
        }
        content.addView(feedGettingStartedPanel(), matchWrap(top = 12))
        content.addView(eventPackagePanel(), matchWrap(top = 12))
        if (activeCatalogOrigin == SportEventOrigin.API_SPORTS) {
            apiFootballDelta?.let { delta ->
                content.addView(
                    apiUpdatePulsePanel(delta),
                    matchWrap(top = 12)
                )
            }
        }
        if (importedEventPackage != null) {
            content.addView(
                eventPackageUpdatePanel(),
                matchWrap(top = 12)
            )
        }
        content.addView(timeBridgePanel(), matchWrap(top = 12))
        val threadMapNow = System.currentTimeMillis()
        val threadMap = storyThreadMapResult(threadMapNow)
        if (threadMap.entries.isNotEmpty()) {
            content.addView(
                storyThreadMapPanel(
                    result = threadMap,
                    interactionLocked = state.isPauseActive(
                        threadMapNow
                    ),
                    onOpen = { entry ->
                        openPulseStory(entry.eventId)
                    },
                    onClearDetached = { entry ->
                        if (state.isPauseActive()) {
                            Toast.makeText(
                                this,
                                "Во время паузы удаление недоступно",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            state.clearStoryThread(entry.eventId)
                            Toast.makeText(
                                this,
                                "Локальная нить удалена",
                                Toast.LENGTH_SHORT
                            ).show()
                            rerenderContentPreservingScroll()
                        }
                    }
                ),
                matchWrap(top = 12)
            )
        }
        val returnCapsuleRead = state.storyReturnCapsule()
        if (
            returnCapsuleRead.integrity !=
            StoryReturnCapsuleIntegrity.EMPTY
        ) {
            val returnCapsuleResult =
                returnCapsuleRead.capsule?.let { capsule ->
                    StoryReturnCapsuleEngine.evaluate(
                        capsule = capsule,
                        currentMap = threadMap,
                        now = threadMapNow
                    )
                }
            content.addView(
                storyReturnCapsulePanel(
                    read = returnCapsuleRead,
                    result = returnCapsuleResult,
                    interactionLocked = state.isPauseActive(
                        threadMapNow
                    ),
                    onOpen = { eventId ->
                        openPulseStory(eventId)
                    },
                    onShare = ::shareStoryReturnFrame,
                    onClose = ::confirmCloseStoryReturnCapsule
                ),
                matchWrap(top = 12)
            )
        } else {
            val quietWindow = StoryQuietWindowEngine.evaluate(
                threadMap = threadMap,
                now = threadMapNow
            )
            if (
                quietWindow.state ==
                StoryQuietWindowState.AVAILABLE ||
                quietWindow.state ==
                StoryQuietWindowState.UNSCHEDULED
            ) {
                content.addView(
                    storyQuietWindowPanel(
                        result = quietWindow,
                        now = threadMapNow,
                        interactionLocked = state.isPauseActive(
                            threadMapNow
                        ),
                        onActivate = ::confirmStoryQuietWindow
                    ),
                    matchWrap(top = 12)
                )
            }
        }
        val revisionRadarNow = System.currentTimeMillis()
        revisionRadarResult(revisionRadarNow)?.let { radar ->
            content.addView(
                revisionRadarPanel(
                    result = radar,
                    onEntrySelected = { entry ->
                        openPulseStory(entry.eventId)
                    },
                    onWithdraw = ::confirmPreflightWithdrawal,
                    onForget = ::confirmForgetRevisionReceipt
                ),
                matchWrap(top = 12)
            )
        }
        val bookmarks = state.bookmarkedIds()
        val factExpressNow = System.currentTimeMillis()
        val factExpress = factExpressResult(
            bookmarks = bookmarks,
            now = factExpressNow
        )
        content.addView(
            factExpressPanel(
                result = factExpress,
                interactionLocked = state.isPauseActive(
                    factExpressNow
                ),
                onEntrySelected = { entry ->
                    openPulseStory(entry.eventId)
                },
                onManage = { savedOnlyTarget ->
                    savedOnly = savedOnlyTarget
                    if (!savedOnlyTarget) {
                        activeSportFilter = "Все"
                    }
                    rerenderContentPreservingScroll()
                },
                onBlindRound = ::showBlindRound,
                onShare = ::shareFactExpress
            ),
            matchWrap(top = 14)
        )
        val collectionXray = collectionXrayResult(
            bookmarks = bookmarks,
            now = factExpressNow
        )
        if (
            collectionXray.state != CollectionXrayState.EMPTY &&
            collectionXray.state != CollectionXrayState.NEED_MORE
        ) {
            content.addView(
                collectionXrayPanel(
                    result = collectionXray,
                    interactionLocked = state.isPauseActive(
                        factExpressNow
                    ),
                    onOpen = { cell ->
                        state.selectedEventId = cell.eventId
                        openPulseFactor(cell.factor)
                    },
                    onTimelapse = ::showCollectionXrayTimelapse,
                    onManage = {
                        savedOnly = true
                        rerenderContentPreservingScroll()
                    }
                ),
                matchWrap(top = 14)
            )
        }
        val visibleEvents = visibleFeedEvents(bookmarks)
        if (visibleEvents.isNotEmpty()) {
            val dispatchNow = System.currentTimeMillis()
            val dispatch = eventDispatchResult(
                events = visibleEvents,
                bookmarks = bookmarks,
                now = dispatchNow
            )
            content.addView(
                eventDispatchPanel(
                    result = dispatch,
                    now = dispatchNow
                ) { entry ->
                    openPulseStory(entry.eventId)
                },
                matchWrap(top = 14)
            )
        }
        content.addView(
            text(
                when {
                    savedOnly -> "СОХРАНЕННЫЕ • ${visibleEvents.size}"
                    activeSportFilter == "Все" -> "ВСЕ СОБЫТИЯ • ${visibleEvents.size}"
                    else -> "${activeSportFilter.uppercase(Locale.getDefault())} • ${visibleEvents.size}"
                },
                12f,
                AppColors.muted,
                Typeface.BOLD
            ),
            matchWrap(top = 18)
        )

        if (visibleEvents.isEmpty()) {
            content.addView(emptyFeedState(), matchWrap(top = 10))
        } else {
            visibleEvents.forEach { event ->
                content.addView(eventCard(event, event.id in bookmarks), matchWrap(top = 12))
            }
        }
    }

    private fun factExpressResult(
        bookmarks: Set<String>,
        now: Long
    ): FactExpressResult {
        val candidates = catalogEvents.mapIndexedNotNull {
                index,
                event ->
            if (event.id !in bookmarks) {
                null
            } else {
                val story = eventStoryResult(
                    event = event,
                    now = now
                )
                FactExpressCandidate(
                    eventId = event.id,
                    match = event.match,
                    sport = event.sport,
                    region = event.region,
                    catalogOrder = index,
                    story = story,
                    beacon = storyBeaconResult(
                        event = event,
                        story = story,
                        now = now
                    )
                )
            }
        }
        return FactExpressEngine.evaluate(
            candidates = candidates,
            selectedZone = state.selectedRegionalZone,
            now = now
        )
    }

    private fun collectionXrayResult(
        bookmarks: Set<String>,
        now: Long
    ): CollectionXrayResult {
        return CollectionXrayEngine.evaluate(
            candidates = collectionXrayCandidates(
                bookmarks = bookmarks,
                now = now
            ),
            now = now
        )
    }

    private fun collectionXrayCandidates(
        bookmarks: Set<String>,
        now: Long
    ): List<CollectionXrayCandidate> {
        return catalogEvents.mapIndexedNotNull {
                index,
                event ->
            if (event.id !in bookmarks) {
                null
            } else {
                CollectionXrayCandidate(
                    eventId = event.id,
                    match = event.match,
                    sport = event.sport,
                    region = event.region,
                    catalogOrder = index,
                    assessment = state.assessment(event),
                    claimedEvidence =
                        state.claimedEvidence(event),
                    sourceAudit = state.sourceAudit(event.id),
                    timeline = state.evidenceTimelinePreview(
                        eventId = event.id,
                        now = now
                    )
                )
            }
        }
    }

    private fun storyThreadMapResult(
        now: Long
    ): StoryThreadMapResult {
        val eventsById = catalogEvents.associateBy(SportEvent::id)
        val catalogOrder = catalogEvents.mapIndexed { index, event ->
            event.id to index
        }.toMap()
        val candidates = state.storyThreads().map {
                (eventId, read) ->
            val event = eventsById[eventId]
            val order = catalogOrder[eventId]
            if (
                event == null ||
                read.integrity != StoryThreadIntegrity.VALID
            ) {
                StoryThreadMapCandidate(
                    eventId = eventId,
                    match = event?.match,
                    sport = event?.sport,
                    region = event?.region,
                    catalogOrder = order,
                    read = read,
                    result = null,
                    nextMoment = null
                )
            } else {
                val story = eventStoryResult(
                    event = event,
                    now = now
                )
                val result = StoryThreadEngine.evaluate(
                    thread = checkNotNull(read.thread),
                    story = story
                )
                val beacon = storyBeaconResult(
                    event = event,
                    story = story,
                    now = now
                )
                val nextMoment = if (
                    result.status == StoryThreadStatus.OPEN ||
                    result.status == StoryThreadStatus.MOVED
                ) {
                    StoryThreadPolicy.relevantMoment(
                        chapter = result.thread.chapter,
                        beacon = beacon
                    )
                } else {
                    null
                }
                StoryThreadMapCandidate(
                    eventId = eventId,
                    match = event.match,
                    sport = event.sport,
                    region = event.region,
                    catalogOrder = checkNotNull(order),
                    read = read,
                    result = result,
                    nextMoment = nextMoment
                )
            }
        }
        return StoryThreadMapEngine.evaluate(candidates)
    }

    private fun storyThreadMapPanel(
        result: StoryThreadMapResult,
        interactionLocked: Boolean,
        onOpen: (StoryThreadMapEntry) -> Unit,
        onClearDetached: (StoryThreadMapEntry) -> Unit
    ): LinearLayout {
        require(result.entries.isNotEmpty())
        val tone = storyThreadMapTone(result.leadingState)
        return card().apply {
            addView(storyThreadMapHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    storyThreadMapStateTitle(result.leadingState),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    "Нитей ${result.entries.size} • " +
                        "сдвиг ${result.movedCount} • " +
                        "открыто ${result.openCount} • " +
                        "завершено ${result.settledCount}",
                    18f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    storyThreadMapSummary(result),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            addView(
                StoryThreadMapView(this@MainActivity).apply {
                    setResult(result)
                },
                matchFixed(112, top = 4)
            )
            result.visibleEntries.forEachIndexed { index, entry ->
                if (index > 0) {
                    addView(
                        divider(),
                        matchFixed(1, top = 6)
                    )
                }
                addView(
                    storyThreadMapRow(
                        entry = entry,
                        interactionLocked = interactionLocked,
                        onOpen = { onOpen(entry) },
                        onClearDetached = {
                            onClearDetached(entry)
                        }
                    ),
                    matchWrap(top = 7)
                )
            }
            val hiddenCount = result.entries.size -
                result.visibleEntries.size
            if (hiddenCount > 0) {
                addView(
                    text(
                        "ЕЩЕ НИТЕЙ • $hiddenCount",
                        11.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 9)
                )
            }
            addView(
                text(
                    "READ-ONLY • ВНЕ КАТАЛОГА " +
                        "${result.outsideCatalogCount} • SHA-256 " +
                        result.shortFingerprint,
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun storyThreadMapHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.story_thread_map)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Пять личных нитей сходятся в пять отдельных шлюзов состояния"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "КАРТА НИТЕЙ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Что изменилось в ваших вопросах",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun storyThreadMapRow(
        entry: StoryThreadMapEntry,
        interactionLocked: Boolean,
        onOpen: () -> Unit,
        onClearDetached: () -> Unit
    ): LinearLayout {
        val tone = storyThreadMapTone(entry.state)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                label(
                    storyThreadMapStateTitle(entry.state),
                    tone.background,
                    tone.foreground
                )
            )
            addView(
                text(
                    entry.match ?: "Событие вне текущего каталога",
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ).apply {
                    maxLines = 3
                },
                matchWrap(top = 6)
            )
            addView(
                text(
                    storyThreadMapEntryMeta(entry),
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
            addView(
                text(
                    storyThreadMapEntryBody(entry),
                    13f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            entry.nextMoment?.let { moment ->
                addView(
                    text(
                        "Далее: ${storyBeaconMomentTitle(moment)} • " +
                            storyBeaconMomentTime(moment),
                        12.5f,
                        tone.foreground,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 5)
                )
            }
            if (entry.presentInCatalog) {
                addView(
                    outlineButton(
                        "Открыть нить",
                        tone.foreground,
                        onOpen
                    ).apply {
                        contentDescription =
                            "Открыть нить события ${entry.match}"
                    },
                    matchWrap(top = 7)
                )
            } else {
                addView(
                    outlineButton(
                        if (interactionLocked) {
                            "Пауза • удаление недоступно"
                        } else {
                            "Удалить локальную нить"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            tone.foreground
                        },
                        onClearDetached
                    ).apply {
                        isEnabled = !interactionLocked
                        alpha = if (isEnabled) 1f else 0.55f
                        contentDescription =
                            "Удалить нить события вне текущего каталога"
                    },
                    matchWrap(top = 7)
                )
            }
        }
    }

    private fun storyThreadMapEntryMeta(
        entry: StoryThreadMapEntry
    ): String {
        val chapter = entry.thread?.chapter?.let {
            eventStoryChapterTitle(it)
        }
        return if (entry.presentInCatalog) {
            buildList {
                add(checkNotNull(entry.sport))
                add(checkNotNull(entry.region))
                chapter?.let(::add)
            }.joinToString(" • ")
        } else {
            buildList {
                add("ID ${entry.eventId.take(48)}")
                chapter?.let(::add)
            }.joinToString(" • ")
        }
    }

    private fun storyThreadMapEntryBody(
        entry: StoryThreadMapEntry
    ): String {
        return when (entry.state) {
            StoryThreadMapState.EMPTY -> "Нитей пока нет."
            StoryThreadMapState.TAMPERED ->
                "Локальная запись не прошла SHA-256-проверку и не участвует в выводах."
            StoryThreadMapState.DETACHED -> {
                val thread = checkNotNull(entry.thread)
                "${storyThreadQuestion(thread.chapter)} " +
                    "Последнее состояние: " +
                    eventStoryChapterStateTitle(thread.initialState) +
                    ". Текущий статус без события не вычисляется."
            }
            StoryThreadMapState.MOVED,
            StoryThreadMapState.MISSED,
            StoryThreadMapState.OPEN,
            StoryThreadMapState.RESOLVED -> {
                val result = checkNotNull(entry.result)
                "${storyThreadQuestion(result.thread.chapter)} " +
                    "Тогда: ${eventStoryChapterStateTitle(
                        result.thread.initialState
                    )} → сейчас: ${eventStoryChapterStateTitle(
                        result.currentState
                    )}."
            }
        }
    }

    private fun storyThreadMapStateTitle(
        mapState: StoryThreadMapState
    ): String {
        return when (mapState) {
            StoryThreadMapState.EMPTY -> "НИТЕЙ НЕТ"
            StoryThreadMapState.TAMPERED -> "НИТЬ ПОВРЕЖДЕНА"
            StoryThreadMapState.DETACHED -> "СОБЫТИЕ ВНЕ КАТАЛОГА"
            StoryThreadMapState.MOVED -> "НИТЬ СДВИНУЛАСЬ"
            StoryThreadMapState.MISSED -> "МОМЕНТ УПУЩЕН"
            StoryThreadMapState.OPEN -> "ВОПРОС ОТКРЫТ"
            StoryThreadMapState.RESOLVED -> "ВОПРОС ЗАКРЫТ"
        }
    }

    private fun storyThreadMapSummary(
        result: StoryThreadMapResult
    ): String {
        return when (result.leadingState) {
            StoryThreadMapState.EMPTY ->
                "Закрепите вопрос внутри события, и он появится здесь."
            StoryThreadMapState.TAMPERED ->
                "Сначала показана запись, которую нельзя проверить. Откройте событие или удалите потерянную связь."
            StoryThreadMapState.DETACHED ->
                "Личный вопрос сохранен, но его события больше нет в текущем Event Pack. Статус не придумывается."
            StoryThreadMapState.MOVED ->
                "Хотя бы один выбранный вопрос изменил состояние с момента закрепления."
            StoryThreadMapState.MISSED ->
                "Предстартовый момент одной из нитей прошел без завершения выбранной главы."
            StoryThreadMapState.OPEN ->
                "Все видимые вопросы сохраняют исходное состояние. Ближайшая опорная точка поднята выше."
            StoryThreadMapState.RESOLVED ->
                "Все сохраненные вопросы текущей области закрыты проверяемым состоянием сюжета."
        }
    }

    private fun storyThreadMapTone(
        mapState: StoryThreadMapState
    ): Tone {
        return when (mapState) {
            StoryThreadMapState.TAMPERED,
            StoryThreadMapState.MISSED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            StoryThreadMapState.DETACHED,
            StoryThreadMapState.MOVED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            StoryThreadMapState.OPEN,
            StoryThreadMapState.EMPTY ->
                Tone(AppColors.signal, AppColors.signalSoft)
            StoryThreadMapState.RESOLVED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun storyQuietWindowPanel(
        result: StoryQuietWindowResult,
        now: Long,
        interactionLocked: Boolean,
        onActivate: () -> Unit
    ): LinearLayout {
        require(
            result.state == StoryQuietWindowState.AVAILABLE ||
                result.state == StoryQuietWindowState.UNSCHEDULED
        )
        val available = result.state ==
            StoryQuietWindowState.AVAILABLE
        val tone = if (available) {
            Tone(AppColors.accentDark, AppColors.accentSoft)
        } else {
            Tone(AppColors.warning, AppColors.warningSoft)
        }
        val returnAt = result.returnAt
        val reachesReturn = returnAt?.let {
            StoryQuietWindowPolicy.reachesReturnPoint(
                now = now,
                returnAt = it
            )
        } == true
        return card().apply {
            addView(storyQuietWindowHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    when {
                        interactionLocked -> "ТИШИНА УЖЕ АКТИВНА"
                        available -> "ТОЧКА ВОЗВРАТА НАЙДЕНА"
                        else -> "ВРЕМЯ НЕ ДОКАЗАНО"
                    },
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    if (available) {
                        "Вернуться не раньше ${quietWindowTime(
                            checkNotNull(returnAt)
                        )}"
                    } else {
                        "Точная точка пока не доказана"
                    },
                    19f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    storyQuietWindowSummary(result),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            addView(
                StoryQuietWindowView(this@MainActivity).apply {
                    setResult(result)
                },
                matchFixed(108, top = 4)
            )
            if (available) {
                val entry = checkNotNull(result.entry)
                val moment = checkNotNull(result.moment)
                addView(
                    divider(),
                    matchFixed(1, top = 2, bottom = 9)
                )
                addView(
                    text(
                        checkNotNull(entry.match),
                        15f,
                        AppColors.ink,
                        Typeface.BOLD
                    ),
                    matchWrap()
                )
                addView(
                    text(
                        storyThreadQuestion(
                            checkNotNull(entry.thread).chapter
                        ),
                        13f,
                        AppColors.muted
                    ),
                    matchWrap(top = 3)
                )
                addView(
                    text(
                        storyBeaconMomentTitle(moment),
                        12.5f,
                        tone.foreground,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 5)
                )
                if (!reachesReturn) {
                    addView(
                        text(
                            "Точка дальше 24 часов. Безотзывная пауза ограничена одними сутками.",
                            12.5f,
                            AppColors.warning,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 7)
                    )
                }
                addView(
                    commandButton(
                        when {
                            interactionLocked ->
                                "Тишина уже активна"
                            reachesReturn ->
                                "Включить тишину до точки"
                            else ->
                                "Включить тишину на 24 часа"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            tone.foreground
                        },
                        onActivate
                    ).apply {
                        isEnabled = !interactionLocked
                        alpha = if (isEnabled) 1f else 0.55f
                        contentDescription = if (reachesReturn) {
                            "Включить режим тишины до ближайшей точки возврата"
                        } else {
                            "Включить режим тишины на 24 часа"
                        }
                    },
                    matchWrap(top = 11)
                )
            } else {
                addView(
                    text(
                        "Без подтвержденного абсолютного времени пауза не предлагается.",
                        12.5f,
                        AppColors.warning,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 4)
                )
            }
            addView(
                text(
                    "БЕЗ УВЕДОМЛЕНИЙ • АКТИВНЫХ НИТЕЙ " +
                        "${result.activeCount} • SHA-256 " +
                        result.shortFingerprint,
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun storyQuietWindowHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.story_quiet_window)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Одна нить проходит через закрытый шлюз тишины к единственной будущей точке"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ТИХОЕ ОКНО",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "До точки можно не возвращаться",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun storyQuietWindowSummary(
        result: StoryQuietWindowResult
    ): String {
        return when (result.state) {
            StoryQuietWindowState.AVAILABLE ->
                "По текущим локальным данным у выбранных вопросов нет более ранней проверяемой точки. Внешнее обновление может изменить этот вывод."
            StoryQuietWindowState.UNSCHEDULED ->
                "Открытых вопросов ${result.activeCount}, но их текущая афиша не дает будущего абсолютного момента. Неизвестное время не заменяется догадкой."
            StoryQuietWindowState.EMPTY ->
                "Личных вопросов пока нет."
            StoryQuietWindowState.NO_ACTIVE ->
                "Открытых проверяемых вопросов нет."
        }
    }

    private fun storyReturnCapsulePanel(
        read: StoryReturnCapsuleReadResult,
        result: StoryReturnCapsuleResult?,
        interactionLocked: Boolean,
        onOpen: (String) -> Unit,
        onShare: () -> Unit,
        onClose: () -> Unit
    ): LinearLayout {
        require(
            read.integrity != StoryReturnCapsuleIntegrity.EMPTY
        )
        require(
            (read.integrity == StoryReturnCapsuleIntegrity.VALID) ==
                (result != null)
        )
        val tone = storyReturnCapsuleTone(result?.state)
        return card().apply {
            addView(storyReturnCapsuleHeader(), matchFixed(imageHeaderHeight()))
            if (read.integrity == StoryReturnCapsuleIntegrity.TAMPERED) {
                addView(
                    label(
                        "КАПСУЛА ПОВРЕЖДЕНА",
                        AppColors.dangerSoft,
                        AppColors.danger
                    ),
                    matchWrap(top = 12)
                )
                addView(
                    text(
                        "Пломба не прошла проверку",
                        19f,
                        AppColors.danger,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 10)
                )
                addView(
                    text(
                        "Исходная точка и вопрос не используются. Приложение не восстанавливает их догадкой.",
                        13.5f,
                        AppColors.ink
                    ),
                    matchWrap(top = 5)
                )
                addView(
                    outlineButton(
                        if (interactionLocked) {
                            "Пауза • удаление недоступно"
                        } else {
                            "Удалить поврежденную капсулу"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            AppColors.danger
                        },
                        onClose
                    ).apply {
                        isEnabled = !interactionLocked
                        alpha = if (isEnabled) 1f else 0.55f
                    },
                    matchWrap(top = 11)
                )
                addView(
                    text(
                        "FAIL-CLOSED • ЛОКАЛЬНО • БЕЗ ФОНА",
                        11.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 11)
                )
                return@apply
            }

            val valid = checkNotNull(result)
            val capsule = valid.capsule
            addView(
                label(
                    storyReturnCapsuleStateTitle(valid.state),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    storyReturnCapsuleMetric(valid),
                    19f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    storyReturnCapsuleSummary(valid),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            addView(
                StoryReturnCapsuleView(this@MainActivity).apply {
                    setResult(valid)
                },
                matchFixed(108, top = 4)
            )
            addView(
                divider(),
                matchFixed(1, top = 2, bottom = 9)
            )
            addView(
                text(
                    capsule.eventLabel,
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ).apply {
                    maxLines = 3
                },
                matchWrap()
            )
            addView(
                text(
                    storyThreadQuestion(capsule.chapter),
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
            addView(
                text(
                    storyReturnCapsuleTimes(capsule),
                    12.5f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 6)
            )
            storyReturnCapsuleComparison(valid)?.let { comparison ->
                addView(
                    text(
                        comparison,
                        12.5f,
                        AppColors.ink,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 6)
                )
            }
            valid.currentEntry?.nextMoment?.takeIf {
                valid.state == StoryReturnCapsuleState.POINT_MOVED
            }?.let { moment ->
                addView(
                    text(
                        "Новая точка: ${storyBeaconMomentTitle(moment)} • " +
                            storyBeaconMomentTime(moment),
                        12.5f,
                        AppColors.warning,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 6)
                )
            }
            if (valid.isOpenable) {
                addView(
                    outlineButton(
                        if (interactionLocked) {
                            "Пауза • открытие недоступно"
                        } else {
                            "Открыть нить"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            tone.foreground
                        }
                    ) {
                        onOpen(capsule.eventId)
                    }.apply {
                        isEnabled = !interactionLocked
                        alpha = if (isEnabled) 1f else 0.55f
                        contentDescription =
                            "Открыть нить события ${capsule.eventLabel}"
                    },
                    matchWrap(top = 11)
                )
            }
            if (valid.state != StoryReturnCapsuleState.SEALED) {
                addView(
                    commandButton(
                        if (interactionLocked) {
                            "Пауза • кадр недоступен"
                        } else {
                            "Создать кадр возвращения"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            tone.foreground
                        },
                        onShare
                    ).apply {
                        isEnabled = !interactionLocked
                        alpha = if (isEnabled) 1f else 0.55f
                        contentDescription =
                            "Создать и отправить кадр возвращения"
                    },
                    matchWrap(top = 8)
                )
                addView(
                    outlineButton(
                        if (interactionLocked) {
                            "Пауза • закрытие недоступно"
                        } else {
                            "Закрыть капсулу"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            tone.foreground
                        },
                        onClose
                    ).apply {
                        isEnabled = !interactionLocked
                        alpha = if (isEnabled) 1f else 0.55f
                    },
                    matchWrap(top = 8)
                )
            }
            addView(
                text(
                    "ОДНА КАПСУЛА • БЕЗ ФОНА • SHA-256 " +
                        valid.shortFingerprint,
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun storyReturnCapsuleHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.story_return_capsule)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Одна запечатанная капсула соединена с двумя окнами сравнения тогда и сейчас"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "КАПСУЛА ВОЗВРАТА",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Что изменилось за время тишины",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun storyReturnCapsuleStateTitle(
        state: StoryReturnCapsuleState
    ): String {
        return when (state) {
            StoryReturnCapsuleState.SEALED -> "КАПСУЛА ЗАПЕЧАТАНА"
            StoryReturnCapsuleState.LIMIT_REACHED ->
                "ГРАНИЦА ПАУЗЫ ДОСТИГНУТА"
            StoryReturnCapsuleState.UNCHANGED ->
                "ТОЧКА ДОСТИГНУТА • БЕЗ ИЗМЕНЕНИЙ"
            StoryReturnCapsuleState.POINT_MOVED ->
                "ТОЧКА ПЕРЕНЕСЕНА"
            StoryReturnCapsuleState.CHANGED -> "ВЕРСИЯ ИЗМЕНИЛАСЬ"
            StoryReturnCapsuleState.RESOLVED -> "ВОПРОС ЗАКРЫТ"
            StoryReturnCapsuleState.MISSED -> "МОМЕНТ УПУЩЕН"
            StoryReturnCapsuleState.DETACHED ->
                "СОБЫТИЕ ВНЕ КАТАЛОГА"
            StoryReturnCapsuleState.MISSING -> "НИТЬ УДАЛЕНА"
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                "СВЯЗЬ ПОВРЕЖДЕНА"
        }
    }

    private fun storyReturnCapsuleMetric(
        result: StoryReturnCapsuleResult
    ): String {
        return when (result.state) {
            StoryReturnCapsuleState.SEALED ->
                "Откроется не раньше ${quietWindowTime(
                    result.capsule.pauseUntil
                )}"
            StoryReturnCapsuleState.LIMIT_REACHED ->
                "24 часа прошли, исходная точка еще впереди"
            StoryReturnCapsuleState.UNCHANGED ->
                "Точка достигнута, локальная версия прежняя"
            StoryReturnCapsuleState.POINT_MOVED ->
                "Связанная точка сдвинулась вперед"
            StoryReturnCapsuleState.CHANGED ->
                "Состояние выбранного вопроса изменилось"
            StoryReturnCapsuleState.RESOLVED ->
                "Выбранный вопрос получил завершенное состояние"
            StoryReturnCapsuleState.MISSED ->
                "Предстартовый момент прошел без завершения"
            StoryReturnCapsuleState.DETACHED ->
                "Исходного события больше нет в каталоге"
            StoryReturnCapsuleState.MISSING ->
                "Исходная локальная нить больше не найдена"
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                "Текущую нить нельзя использовать для сравнения"
        }
    }

    private fun storyReturnCapsuleSummary(
        result: StoryReturnCapsuleResult
    ): String {
        return when (result.state) {
            StoryReturnCapsuleState.SEALED ->
                "До срока приложение не раскрывает промежуточный результат и не проверяет вопрос в фоне."
            StoryReturnCapsuleState.LIMIT_REACHED ->
                "Безотзывная пауза ограничена сутками. Достижение более дальней точки не заявляется."
            StoryReturnCapsuleState.UNCHANGED ->
                "Время точки наступило, но локальные данные выбранного вопроса не дали смыслового перехода."
            StoryReturnCapsuleState.POINT_MOVED ->
                "Тот же вид опорной точки теперь имеет более позднее подтвержденное время."
            StoryReturnCapsuleState.CHANGED ->
                "Пломба исходного состояния и текущая нить дают проверяемый смысловой переход."
            StoryReturnCapsuleState.RESOLVED ->
                "Текущий локальный сюжет закрыл выбранную главу. Это не подтверждает внешний результат без обновления источника."
            StoryReturnCapsuleState.MISSED ->
                "Капсула фиксирует упущенный момент без награды, серии посещений или скрытого балла."
            StoryReturnCapsuleState.DETACHED ->
                "Вопрос сохранен в капсуле, но текущий статус без события не придумывается."
            StoryReturnCapsuleState.MISSING ->
                "Сравнение невозможно: связанная нить отсутствует. Исходная пломба остается читаемой."
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                "Текущая запись не прошла SHA-256-проверку и отклонена fail-closed."
        }
    }

    private fun storyReturnCapsuleTimes(
        capsule: StoryReturnCapsule
    ): String {
        return if (capsule.reachesReturnPoint) {
            "ПЛОМБА ${quietWindowTime(capsule.activatedAt)} • " +
                "ТОЧКА ${quietWindowTime(capsule.returnAt)}"
        } else {
            "ПАУЗА ДО ${quietWindowTime(capsule.pauseUntil)} • " +
                "ТОЧКА ${quietWindowTime(capsule.returnAt)}"
        }
    }

    private fun storyReturnCapsuleComparison(
        result: StoryReturnCapsuleResult
    ): String? {
        val currentState = result.currentEntry?.state
        if (
            currentState == null &&
            result.state != StoryReturnCapsuleState.MISSING
        ) {
            return null
        }
        val before = storyReturnCapsuleMapState(
            result.capsule.baselineEntryState
        )
        val current = currentState?.let(
            ::storyReturnCapsuleMapState
        ) ?: "нет нити"
        return "НИТЬ • тогда: $before → сейчас: $current"
    }

    private fun storyReturnCapsuleMapState(
        state: StoryThreadMapState
    ): String {
        return when (state) {
            StoryThreadMapState.EMPTY -> "нет"
            StoryThreadMapState.TAMPERED -> "не проверяется"
            StoryThreadMapState.DETACHED -> "вне каталога"
            StoryThreadMapState.MOVED -> "сдвинулась"
            StoryThreadMapState.MISSED -> "упущена"
            StoryThreadMapState.OPEN -> "открыта"
            StoryThreadMapState.RESOLVED -> "закрыта"
        }
    }

    private fun storyReturnCapsuleTone(
        state: StoryReturnCapsuleState?
    ): Tone {
        return when (state) {
            StoryReturnCapsuleState.SEALED,
            StoryReturnCapsuleState.UNCHANGED ->
                Tone(AppColors.signal, AppColors.signalSoft)
            StoryReturnCapsuleState.RESOLVED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            StoryReturnCapsuleState.LIMIT_REACHED,
            StoryReturnCapsuleState.POINT_MOVED,
            StoryReturnCapsuleState.CHANGED,
            StoryReturnCapsuleState.DETACHED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            StoryReturnCapsuleState.MISSED,
            StoryReturnCapsuleState.MISSING,
            StoryReturnCapsuleState.CURRENT_TAMPERED,
            null -> Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun quietWindowTime(timestamp: Long): String {
        return TimeBridgeEngine.formatInstant(
            startAt = timestamp,
            selectedZone = state.selectedRegionalZone
        )
    }

    private fun confirmCloseStoryReturnCapsule() {
        val now = System.currentTimeMillis()
        if (state.isPauseActive(now)) {
            Toast.makeText(
                this,
                "До конца паузы капсула остается запечатанной",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val read = state.storyReturnCapsule()
        if (read.integrity == StoryReturnCapsuleIntegrity.EMPTY) {
            rerenderContentPreservingScroll()
            return
        }
        val tampered = read.integrity ==
            StoryReturnCapsuleIntegrity.TAMPERED
        AlertDialog.Builder(this)
            .setTitle(
                if (tampered) {
                    "Удалить поврежденную капсулу?"
                } else {
                    "Закрыть Капсулу возврата?"
                }
            )
            .setMessage(
                if (tampered) {
                    "Будет удалена только нечитаемая локальная запись. Нити событий и другие данные не изменятся."
                } else {
                    "Локальный разбор возвращения будет удален. Исходная нить события останется на месте."
                }
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton(
                if (tampered) "Удалить" else "Закрыть"
            ) { _, _ ->
                closeStoryReturnCapsule()
            }
            .show()
    }

    private fun closeStoryReturnCapsule() {
        val now = System.currentTimeMillis()
        if (state.isPauseActive(now)) {
            Toast.makeText(
                this,
                "Пауза еще активна",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (
            state.storyReturnCapsule().integrity ==
            StoryReturnCapsuleIntegrity.EMPTY
        ) {
            rerenderContentPreservingScroll()
            return
        }
        state.clearStoryReturnCapsule(now)
        Toast.makeText(
            this,
            "Капсула закрыта, нить сохранена",
            Toast.LENGTH_SHORT
        ).show()
        rerenderContentPreservingScroll()
    }

    private fun confirmStoryQuietWindow() {
        val now = System.currentTimeMillis()
        if (state.isPauseActive(now)) {
            Toast.makeText(
                this,
                "Режим тишины уже активен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (
            state.storyReturnCapsule().integrity !=
            StoryReturnCapsuleIntegrity.EMPTY
        ) {
            Toast.makeText(
                this,
                "Сначала закройте предыдущую капсулу",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val current = StoryQuietWindowEngine.evaluate(
            threadMap = storyThreadMapResult(now),
            now = now
        )
        val returnAt = current.returnAt
        if (
            current.state != StoryQuietWindowState.AVAILABLE ||
            returnAt == null || returnAt <= now
        ) {
            Toast.makeText(
                this,
                "Точка изменилась — карта обновлена",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val pauseUntil = StoryQuietWindowPolicy.pauseUntil(
            now = now,
            returnAt = returnAt
        )
        val reachesReturn = pauseUntil == returnAt
        AlertDialog.Builder(this)
            .setTitle(
                if (reachesReturn) {
                    "Включить тишину до точки?"
                } else {
                    "Включить тишину на 24 часа?"
                }
            )
            .setMessage(
                buildString {
                    append("Новые решения и изменение карты будут заблокированы до ")
                    append(quietWindowTime(pauseUntil))
                    append(". Отключить режим раньше нельзя. Уведомление не создается.")
                    append(" Точка и состояние нити будут запечатаны в локальную Капсулу возврата.")
                    if (!reachesReturn) {
                        append(" Ближайшая опорная точка находится позже: ")
                        append(quietWindowTime(returnAt))
                        append('.')
                    }
                }
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Включить тишину") { _, _ ->
                activateStoryQuietWindow()
            }
            .show()
    }

    private fun activateStoryQuietWindow() {
        val now = System.currentTimeMillis()
        if (state.isPauseActive(now)) {
            Toast.makeText(
                this,
                "Режим тишины уже активен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (
            state.storyReturnCapsule().integrity !=
            StoryReturnCapsuleIntegrity.EMPTY
        ) {
            Toast.makeText(
                this,
                "Предыдущая капсула еще открыта",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val current = StoryQuietWindowEngine.evaluate(
            threadMap = storyThreadMapResult(now),
            now = now
        )
        val returnAt = current.returnAt
        if (
            current.state != StoryQuietWindowState.AVAILABLE ||
            returnAt == null || returnAt <= now
        ) {
            Toast.makeText(
                this,
                "Точка больше недоступна",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val pauseUntil = StoryQuietWindowPolicy.pauseUntil(
            now = now,
            returnAt = returnAt
        )
        val capsule = StoryReturnCapsuleFactory.create(
            quietWindow = current,
            activatedAt = now,
            pauseUntil = pauseUntil
        )
        state.activateStoryQuietWindow(
            capsule = capsule,
            now = now
        )
        Toast.makeText(
            this,
            "Тишина до ${quietWindowTime(pauseUntil)} • капсула запечатана",
            Toast.LENGTH_LONG
        ).show()
        rerenderContentPreservingScroll()
    }

    private fun revisionRadarResult(
        now: Long
    ): RevisionRadarResult? {
        val radarEvents = catalogEvents.mapIndexed { index, event ->
            val relay = EvidenceRelayEngine.evaluate(
                input = EvidenceRelayInput(
                    event = event,
                    assessment = state.assessment(event),
                    claimedEvidence =
                        state.claimedEvidence(event),
                    sourceAudit = state.sourceAudit(event.id),
                    timeline = state.evidenceTimelinePreview(
                        eventId = event.id,
                        now = now
                    )
                ),
                now = now
            )
            RevisionRadarEvent(
                eventId = event.id,
                match = event.match,
                sport = event.sport,
                region = event.region,
                catalogOrder = index,
                protocol = relay?.let {
                    PreflightProtocolEngine.evaluate(event, it)
                }
            )
        }
        return RevisionRadarEngine.evaluate(
            events = radarEvents,
            storedReceipts = state.preflightExportReceipts(),
            selectedZone = state.selectedRegionalZone,
            now = now
        )
    }

    private fun revisionRadarPanel(
        result: RevisionRadarResult,
        onEntrySelected: (RevisionRadarEntry) -> Unit,
        onWithdraw: (RevisionRadarEntry) -> Unit,
        onForget: (RevisionRadarEntry) -> Unit
    ): LinearLayout {
        val tone = revisionRadarTone(result.leadingState)
        return card().apply {
            addView(revisionRadarHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    revisionRadarStateTitle(result.leadingState),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    revisionRadarMetric(result),
                    18f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    revisionRadarSummary(result),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            result.visibleEntries.forEachIndexed { index, entry ->
                if (index > 0) {
                    addView(divider(), matchFixed(1, top = 7))
                }
                addView(
                    revisionRadarRow(
                        entry = entry,
                        onClick = {
                            when (entry.action) {
                                RevisionRadarAction.OPEN_EVENT ->
                                    onEntrySelected(entry)
                                RevisionRadarAction.WITHDRAW ->
                                    onWithdraw(entry)
                                RevisionRadarAction.FORGET ->
                                    onForget(entry)
                                RevisionRadarAction.NONE -> Unit
                            }
                        }
                    ),
                    matchWrap(top = 8)
                )
            }
            addView(
                text(
                    revisionRadarFooter(result),
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
        }
    }

    private fun revisionRadarHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.revision_radar)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Три календарные кассеты показывают актуальный, перенесенный и отзываемый планы"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "РАДАР РЕВИЗИЙ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Календарь не забывает",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun revisionRadarRow(
        entry: RevisionRadarEntry,
        onClick: () -> Unit
    ): LinearLayout {
        val tone = revisionRadarTone(entry.state)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                label(
                    revisionRadarStateTitle(entry.state),
                    tone.background,
                    tone.foreground
                )
            )
            addView(
                text(
                    entry.match,
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 6)
            )
            addView(
                text(
                    revisionRadarEntryMeta(entry),
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 2)
            )
            addView(
                text(
                    revisionRadarEntryBody(entry),
                    13f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            revisionRadarActionTitle(entry)?.let { title ->
                addView(
                    outlineButton(title, tone.foreground, onClick).apply {
                        contentDescription =
                            revisionRadarActionDescription(entry)
                    },
                    matchWrap(top = 7)
                )
            }
        }
    }

    private fun revisionRadarMetric(
        result: RevisionRadarResult
    ): String {
        return "Внимание ${result.attentionCount} • " +
            "актуально ${result.currentCount} • " +
            "отозвано ${result.withdrawnCount}"
    }

    private fun revisionRadarSummary(
        result: RevisionRadarResult
    ): String {
        return when {
            result.withdrawalCount > 0 ->
                "Радар нашел планы, которые больше нельзя честно обновить. Для них доступен переносимый отзыв без разрешения календаря."
            result.attentionCount > 0 ->
                "Изменившиеся и поврежденные планы подняты выше актуальных. Обычное течение минут не создает ложную тревогу."
            result.currentCount > 0 ->
                "Все активные квитанции совпадают с текущими календарными планами."
            result.withdrawnCount > 0 ->
                "Активных планов нет: последние ревизии были отозваны."
            else ->
                "Все сохраненные планы относятся к уже прошедшим событиям."
        }
    }

    private fun revisionRadarStateTitle(
        radarState: RevisionRadarState
    ): String {
        return when (radarState) {
            RevisionRadarState.TAMPERED -> "КВИТАНЦИЯ ПОВРЕЖДЕНА"
            RevisionRadarState.REMOVED -> "СОБЫТИЕ УДАЛЕНО"
            RevisionRadarState.UNRESOLVED -> "ВРЕМЯ ПОТЕРЯНО"
            RevisionRadarState.STALE -> "ПЛАН ИЗМЕНИЛСЯ"
            RevisionRadarState.WITHDRAWN -> "ПЛАН ОТОЗВАН"
            RevisionRadarState.CURRENT -> "ПЛАН АКТУАЛЕН"
            RevisionRadarState.EXPIRED -> "СОБЫТИЕ ЗАВЕРШЕНО"
        }
    }

    private fun revisionRadarEntryMeta(
        entry: RevisionRadarEntry
    ): String {
        val base = if (entry.presentInCatalog) {
            "${entry.sport} • ${entry.region}"
        } else {
            "Вне текущего каталога"
        }
        val startAt = entry.startAt ?: return base
        val zone = entry.receipt?.selectedZone
            ?: state.selectedRegionalZone
        return "$base • " + TimeBridgeEngine.formatInstant(
            startAt = startAt,
            selectedZone = zone
        )
    }

    private fun revisionRadarEntryBody(
        entry: RevisionRadarEntry
    ): String {
        return when (entry.state) {
            RevisionRadarState.TAMPERED ->
                "Локальная цепочка не прошла SHA-256-проверку. Автоматический отзыв заблокирован."
            RevisionRadarState.REMOVED ->
                "Матча больше нет в текущем каталоге, но внешние календарные события могли остаться."
            RevisionRadarState.UNRESOLVED ->
                "У события больше нет точного будущего старта. Старую ревизию можно отозвать."
            RevisionRadarState.STALE -> {
                val changes = entry.drift.joinToString(" • ") {
                    preflightDriftTitle(it)
                }
                "Изменено: $changes. Текущая квитанция больше не описывает новый план."
            }
            RevisionRadarState.WITHDRAWN ->
                "Последняя переданная ревизия ${entry.sequence} отменяет старые слоты и маркер старта."
            RevisionRadarState.CURRENT ->
                "Ревизия ${entry.sequence} совпадает с текущим seal расписания."
            RevisionRadarState.EXPIRED ->
                "Сохраненная ревизия относится к уже прошедшему старту и не требует действия."
        }
    }

    private fun revisionRadarActionTitle(
        entry: RevisionRadarEntry
    ): String? {
        return when (entry.action) {
            RevisionRadarAction.OPEN_EVENT -> {
                if (entry.state == RevisionRadarState.WITHDRAWN) {
                    "Открыть и восстановить"
                } else {
                    "Открыть в анализе"
                }
            }
            RevisionRadarAction.WITHDRAW -> "Отозвать план .ics"
            RevisionRadarAction.FORGET -> "Удалить локальную квитанцию"
            RevisionRadarAction.NONE -> null
        }
    }

    private fun revisionRadarActionDescription(
        entry: RevisionRadarEntry
    ): String {
        return when (entry.action) {
            RevisionRadarAction.OPEN_EVENT ->
                "Открыть предстартовый протокол ${entry.match}"
            RevisionRadarAction.WITHDRAW ->
                "Создать календарный отзыв для ${entry.match}"
            RevisionRadarAction.FORGET ->
                "Удалить локальную квитанцию ${entry.match}"
            RevisionRadarAction.NONE ->
                "Действие для плана недоступно"
        }
    }

    private fun revisionRadarFooter(
        result: RevisionRadarResult
    ): String {
        return "ЛОКАЛЬНО • КВИТАНЦИЙ ${result.entries.size} • " +
            "АРХИВ ${result.expiredCount} • SHA-256 ${result.shortFingerprint}"
    }

    private fun revisionRadarTone(
        radarState: RevisionRadarState
    ): Tone {
        return when (radarState) {
            RevisionRadarState.TAMPERED,
            RevisionRadarState.REMOVED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            RevisionRadarState.UNRESOLVED,
            RevisionRadarState.STALE,
            RevisionRadarState.WITHDRAWN ->
                Tone(AppColors.warning, AppColors.warningSoft)
            RevisionRadarState.CURRENT ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            RevisionRadarState.EXPIRED ->
                Tone(AppColors.muted, AppColors.background)
        }
    }

    private fun eventDispatchResult(
        events: List<SportEvent>,
        bookmarks: Set<String>,
        now: Long
    ): EventDispatchResult {
        val catalogOrder = catalogEvents.mapIndexed {
                index,
                event ->
            event.id to index
        }.toMap()
        return EventDispatchEngine.evaluate(
            events.map { event ->
                val snapshot = state.decisionSnapshot(event.id)
                val timeline = state.evidenceTimelinePreview(
                    eventId = event.id,
                    now = now
                )
                EventDispatchCandidate(
                    eventId = event.id,
                    sport = event.sport,
                    match = event.match,
                    region = event.region,
                    bookmarked = event.id in bookmarks,
                    initialized =
                        state.hasEvidenceHistory(event.id) ||
                            snapshot != null,
                    catalogOrder = requireNotNull(
                        catalogOrder[event.id]
                    ),
                    command = VerificationCommandEngine.evaluate(
                        input = VerificationCommandInput(
                            eventId = event.id,
                            sport = event.sport,
                            assessment = state.assessment(event),
                            claimedEvidence =
                                state.claimedEvidence(event),
                            sourceAudit =
                                state.sourceAudit(event.id),
                            timeline = timeline,
                            counterReview =
                                state.counterReview(event.id),
                            decisionSnapshot = snapshot,
                            decisionGuardBreach =
                                state.decisionGuardBreach(event.id)
                        ),
                        now = now
                    )
                )
            }
        )
    }

    private fun factExpressPanel(
        result: FactExpressResult,
        interactionLocked: Boolean,
        onEntrySelected: (FactExpressEntry) -> Unit,
        onManage: (Boolean) -> Unit,
        onBlindRound: () -> Unit,
        onShare: () -> Unit
    ): LinearLayout {
        val tone = factExpressTone(result.state)
        return card().apply {
            addView(factExpressHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    factExpressBadge(result),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    factExpressMetric(result),
                    19f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    factExpressSummary(result),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            addView(
                FactExpressView(this@MainActivity).apply {
                    setResult(result)
                },
                matchFixed(116, top = 4)
            )
            if (result.state != FactExpressState.TOO_MANY) {
                result.entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        addView(
                            divider(),
                            matchFixed(1, top = 4)
                        )
                    }
                    addView(
                        factExpressRow(
                            number = index + 1,
                            entry = entry,
                            selectedZone = result.selectedZone,
                            onClick = {
                                onEntrySelected(entry)
                            }
                        ),
                        matchWrap(top = 5)
                    )
                }
            }
            when (result.state) {
                FactExpressState.READY -> {
                    addView(
                        commandButton(
                            if (interactionLocked) {
                                "Пауза • раунд недоступен"
                            } else {
                                "Начать Слепой раунд"
                            },
                            if (interactionLocked) {
                                AppColors.muted
                            } else {
                                tone.foreground
                            },
                            onBlindRound
                        ).apply {
                            isEnabled = !interactionLocked
                            alpha = if (isEnabled) 1f else 0.55f
                            contentDescription =
                                "Начать Слепой раунд без названий команд"
                        },
                        matchWrap(top = 10)
                    )
                    addView(
                        outlineButton(
                            if (interactionLocked) {
                                "Пауза • экспорт недоступен"
                            } else {
                                "Создать маршрут фактов PNG"
                            },
                            tone.foreground
                        ) { onShare() }.apply {
                            isEnabled = !interactionLocked
                            alpha = if (isEnabled) 1f else 0.55f
                            contentDescription =
                                "Создать и отправить маршрут фактов"
                        },
                        matchWrap(top = 8)
                    )
                    addView(
                        outlineButton(
                            "Изменить состав",
                            tone.foreground
                        ) { onManage(true) },
                        matchWrap(top = 8)
                    )
                }
                FactExpressState.EMPTY,
                FactExpressState.NEED_MORE,
                FactExpressState.TOO_MANY -> {
                    addView(
                        outlineButton(
                            when (result.state) {
                                FactExpressState.EMPTY ->
                                    "Выбрать события ниже"
                                FactExpressState.NEED_MORE ->
                                    "Добавить еще событие"
                                FactExpressState.TOO_MANY ->
                                    "Открыть сохраненные"
                                FactExpressState.READY ->
                                    error("Handled above")
                            },
                            tone.foreground
                        ) {
                            onManage(
                                result.state ==
                                    FactExpressState.TOO_MANY
                            )
                        },
                        matchWrap(top = 10)
                    )
                }
            }
            addView(
                text(
                    "2–4 СОБЫТИЯ • БЕЗ КОЭФФИЦИЕНТОВ • " +
                        "SHA-256 ${result.shortFingerprint}",
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun collectionXrayPanel(
        result: CollectionXrayResult,
        interactionLocked: Boolean,
        onOpen: (CollectionXrayCell) -> Unit,
        onTimelapse: () -> Unit,
        onManage: () -> Unit
    ): LinearLayout {
        val tone = collectionXrayTone(result.state)
        return card().apply {
            addView(collectionXrayHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    collectionXrayBadge(result),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    collectionXrayMetric(result),
                    19f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    collectionXraySummary(result),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            if (result.isReady) {
                addView(
                    CollectionXrayView(this@MainActivity).apply {
                        setResult(result)
                    },
                    matchFixed(222, top = 5)
                )
                addView(collectionXrayLegend(), matchWrap(top = 4))
                result.leadingFactor?.let { leading ->
                    addView(
                        text(
                            "Чаще всего: ${leading.factor.title} • " +
                                "${leading.affectedEventCount} из " +
                                result.entries.size,
                            13f,
                            tone.foreground,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 9)
                    )
                }
                val focus = result.focus
                if (focus == null) {
                    addView(
                        text(
                            "Каждая ячейка укладывается в текущий свежий предел источников.",
                            13f,
                            AppColors.accentDark,
                            Typeface.BOLD
                        ).apply {
                            background = rounded(
                                AppColors.accentSoft,
                                8
                            )
                            setPadding(
                                dp(12),
                                dp(10),
                                dp(12),
                                dp(10)
                            )
                        },
                        matchWrap(top = 10)
                    )
                } else {
                    val entry = result.entries.single {
                        it.eventId == focus.eventId
                    }
                    val focusTone = collectionXrayCellTone(
                        focus.state
                    )
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            background = rounded(
                                focusTone.background,
                                8
                            )
                            setPadding(
                                dp(12),
                                dp(10),
                                dp(12),
                                dp(10)
                            )
                            addView(
                                text(
                                    "ГЛАВНЫЙ РАЗРЫВ • " +
                                        focus.factor.title.uppercase(
                                            Locale.getDefault()
                                        ),
                                    11f,
                                    focusTone.foreground,
                                    Typeface.BOLD
                                )
                            )
                            addView(
                                text(
                                    entry.match,
                                    17f,
                                    AppColors.ink,
                                    Typeface.BOLD
                                ),
                                matchWrap(top = 4)
                            )
                            addView(
                                text(
                                    "${focus.claimedScore} → " +
                                        "${focus.supportedScore} • " +
                                        collectionXrayCauseTitle(
                                            checkNotNull(focus.cause)
                                        ),
                                    13f,
                                    focusTone.foreground,
                                    Typeface.BOLD
                                ),
                                matchWrap(top = 4)
                            )
                            addView(
                                text(
                                    collectionXrayFocusExplanation(
                                        entry = entry,
                                        focus = focus
                                    ),
                                    12.5f,
                                    AppColors.ink
                                ),
                                matchWrap(top = 5)
                            )
                        },
                        matchWrap(top = 10)
                    )
                    addView(
                        commandButton(
                            if (interactionLocked) {
                                "Пауза • переход недоступен"
                            } else {
                                "Открыть разрыв: ${focus.factor.title}"
                            },
                            if (interactionLocked) {
                                AppColors.muted
                            } else {
                                focusTone.foreground
                            }
                        ) { onOpen(focus) }.apply {
                            isEnabled = !interactionLocked
                            alpha = if (isEnabled) 1f else 0.55f
                            contentDescription =
                                "Открыть ${entry.match}, " +
                                    "фактор ${focus.factor.title}"
                        },
                        matchWrap(top = 10)
                    )
                }
                addView(
                    outlineButton(
                        "Таймлапс доказательств",
                        AppColors.signal,
                        onTimelapse
                    ).apply {
                        contentDescription =
                            "Показать изменение свежести подборки " +
                                "через 6, 12 и 24 часа"
                    },
                    matchWrap(top = 10)
                )
            } else if (result.state == CollectionXrayState.TOO_MANY) {
                addView(
                    outlineButton(
                        "Открыть сохраненные",
                        tone.foreground,
                        onManage
                    ),
                    matchWrap(top = 10)
                )
            }
            addView(
                text(
                    "2–8 СОБЫТИЙ • БЕЗ РЕЙТИНГА КОМАНД • " +
                        "SHA-256 ${result.shortFingerprint}",
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun showCollectionXrayTimelapse() {
        val now = System.currentTimeMillis()
        val result = CollectionXrayTimelapseEngine.evaluate(
            candidates = collectionXrayCandidates(
                bookmarks = state.bookmarkedIds(),
                now = now
            ),
            now = now
        )
        if (!result.isAvailable) {
            Toast.makeText(
                this,
                "Таймлапс доступен для 2–8 сохраненных событий",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }

        val interactionLocked = state.isPauseActive(now)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
        }
        scroll.addView(
            body,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        body.addView(
            collectionXrayTimelapseHeader(),
            matchFixed(132)
        )
        body.addView(
            text(
                "Что устареет без перепроверки",
                20f,
                AppColors.ink,
                Typeface.BOLD
            ),
            matchWrap(top = 8)
        )
        body.addView(
            text(
                "Прокрутка меняет только время расчета. " +
                    "Оценки, источники и отметки проверки " +
                    "остаются неизменными.",
                13.5f,
                AppColors.ink
            ),
            matchWrap(top = 5)
        )

        var selectedHorizon = CollectionXrayTimelapseHorizon.NOW
        val horizonButtons = mutableMapOf<
            CollectionXrayTimelapseHorizon,
            TextView
            >()
        val horizonRow = AdaptiveWrapLayout(this).apply {
            tag = AdaptiveGroupTags.TIMELAPSE_HORIZONS
            lineSpacingPx = dp(6)
            setPadding(0, dp(2), 0, dp(2))
        }
        body.addView(horizonRow, matchWrap(top = 12))

        val frameHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(frameHost, matchWrap(top = 10))
        lateinit var dialog: AlertDialog

        fun refreshHorizonButtons() {
            horizonButtons.forEach { (horizon, button) ->
                val selected = horizon == selectedHorizon
                button.setTextColor(
                    if (selected) Color.WHITE else AppColors.signal
                )
                button.background = rippleRounded(
                    if (selected) {
                        AppColors.signal
                    } else {
                        AppColors.surface
                    },
                    8,
                    if (selected) {
                        AppColors.signal
                    } else {
                        AppColors.line
                    },
                    1
                )
                button.isSelected = selected
            }
        }

        fun renderFrame() {
            frameHost.removeAllViews()
            val frame = result.frame(selectedHorizon)
            val tone = collectionXrayTimelapseTone(frame.state)
            frameHost.addView(
                label(
                    collectionXrayTimelapseBadge(frame),
                    tone.background,
                    tone.foreground
                )
            )
            frameHost.addView(
                text(
                    collectionXrayTimelapseMetric(frame),
                    19f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            frameHost.addView(
                text(
                    collectionXrayTimelapseSummary(frame),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            val highlight = frame.focus?.let { focus ->
                frame.xray.entries.single {
                    it.eventId == focus.eventId
                }.cells[focus.factor.ordinal]
            }
            frameHost.addView(
                CollectionXrayView(this).apply {
                    setResult(frame.xray, highlight)
                },
                matchFixed(222, top = 7)
            )
            frameHost.addView(
                collectionXrayLegend(),
                matchWrap(top = 4)
            )
            if (selectedHorizon != CollectionXrayTimelapseHorizon.NOW) {
                frameHost.addView(
                    text(
                        "Новые разрывы: ${frame.newGapCellCount} • " +
                            "Ослабнут события: " +
                            "${frame.changedEventCount} • " +
                            "Потеря опоры: " +
                            frame.totalSupportedScoreLoss,
                        12.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 9)
                )
            }
            val focus = frame.focus
            if (focus == null) {
                frameHost.addView(
                    text(
                        if (
                            selectedHorizon ==
                            CollectionXrayTimelapseHorizon.NOW
                        ) {
                            "Выберите будущий срез, чтобы увидеть " +
                                "только доказуемую потерю свежести."
                        } else {
                            "К этому срезу доказательные пределы " +
                                "не изменятся."
                        },
                        13f,
                        AppColors.accentDark,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(
                            AppColors.accentSoft,
                            8
                        )
                        setPadding(
                            dp(12),
                            dp(10),
                            dp(12),
                            dp(10)
                        )
                    },
                    matchWrap(top = 10)
                )
            } else {
                val focusTone = if (
                    focus.kind ==
                    CollectionXrayTimelapseChangeKind.NEW_CRITICAL ||
                    focus.causesNewVerdictShift
                ) {
                    Tone(AppColors.danger, AppColors.dangerSoft)
                } else {
                    Tone(AppColors.warning, AppColors.warningSoft)
                }
                frameHost.addView(
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = rounded(
                            focusTone.background,
                            8
                        )
                        setPadding(
                            dp(12),
                            dp(10),
                            dp(12),
                            dp(10)
                        )
                        addView(
                            text(
                                collectionXrayTimelapseFocusLabel(
                                    focus
                                ),
                                11f,
                                focusTone.foreground,
                                Typeface.BOLD
                            )
                        )
                        addView(
                            text(
                                focus.match,
                                17f,
                                AppColors.ink,
                                Typeface.BOLD
                            ),
                            matchWrap(top = 4)
                        )
                        addView(
                            text(
                                "${focus.factor.title} • " +
                                    "${focus.beforeSupportedScore} → " +
                                    "${focus.afterSupportedScore} • " +
                                    collectionXrayCauseTitle(
                                        focus.cause
                                    ),
                                13f,
                                focusTone.foreground,
                                Typeface.BOLD
                            ),
                            matchWrap(top = 4)
                        )
                        addView(
                            text(
                                collectionXrayTimelapseFocusSummary(
                                    focus
                                ),
                                12.5f,
                                AppColors.ink
                            ),
                            matchWrap(top = 5)
                        )
                    },
                    matchWrap(top = 10)
                )
                frameHost.addView(
                    commandButton(
                        if (interactionLocked) {
                            "Пауза • переход недоступен"
                        } else {
                            "Открыть фактор: ${focus.factor.title}"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            focusTone.foreground
                        }
                    ) {
                        dialog.dismiss()
                        state.selectedEventId = focus.eventId
                        openPulseFactor(focus.factor)
                    }.apply {
                        isEnabled = !interactionLocked
                        alpha = if (isEnabled) 1f else 0.55f
                        contentDescription =
                            "Открыть ${focus.match}, " +
                                "фактор ${focus.factor.title}"
                    },
                    matchWrap(top = 10)
                )
            }
            frameHost.addView(
                text(
                    "4 СРЕЗА • БЕЗ ПРОГНОЗА ИСХОДА • " +
                        "SHA-256 ${result.shortFingerprint}",
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }

        CollectionXrayTimelapseHorizon.values().forEach { horizon ->
            val button = outlineButton(
                collectionXrayTimelapseHorizonTitle(horizon),
                AppColors.signal
            ) {
                selectedHorizon = horizon
                refreshHorizonButtons()
                renderFrame()
            }.apply {
                minWidth = dp(76)
                contentDescription =
                    "Срез ${collectionXrayTimelapseHorizonTitle(horizon)}"
            }
            horizonButtons[horizon] = button
            horizonRow.addView(
                button,
                wrapWrap(right = 6)
            )
        }
        refreshHorizonButtons()
        renderFrame()

        dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("Закрыть", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                min(
                    resources.displayMetrics.widthPixels - dp(24),
                    dp(720)
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun collectionXrayTimelapseHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(
                        R.drawable.collection_xray_timelapse
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Четыре временных шлюза сканируют " +
                            "четыре кассеты и пять каналов"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ТАЙМЛАПС ДОКАЗАТЕЛЬСТВ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Свежесть всей подборки во времени",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun collectionXrayTimelapseHorizonTitle(
        horizon: CollectionXrayTimelapseHorizon
    ): String {
        return if (horizon == CollectionXrayTimelapseHorizon.NOW) {
            "Сейчас"
        } else {
            "+${horizon.offsetHours} ч"
        }
    }

    private fun collectionXrayTimelapseBadge(
        frame: CollectionXrayTimelapseFrame
    ): String {
        val horizon = collectionXrayTimelapseHorizonTitle(
            frame.horizon
        ).uppercase(Locale.getDefault())
        return when (frame.state) {
            CollectionXrayTimelapseState.NOT_AVAILABLE ->
                error("Frame is always available")
            CollectionXrayTimelapseState.STABLE ->
                "БЕЗ НОВЫХ ПОТЕРЬ • $horizon"
            CollectionXrayTimelapseState.GAPS_GROW ->
                "ОПОРА СНИЗИТСЯ • $horizon"
            CollectionXrayTimelapseState.VERDICT_SHIFT ->
                "СТАТУС ДАННЫХ СМЕНИТСЯ • $horizon"
        }
    }

    private fun collectionXrayTimelapseMetric(
        frame: CollectionXrayTimelapseFrame
    ): String {
        return when (frame.state) {
            CollectionXrayTimelapseState.NOT_AVAILABLE ->
                error("Frame is always available")
            CollectionXrayTimelapseState.STABLE ->
                if (frame.horizon == CollectionXrayTimelapseHorizon.NOW) {
                    "Текущий доказательный срез"
                } else {
                    "Матрица не изменится"
                }
            CollectionXrayTimelapseState.GAPS_GROW ->
                "Ослабнут: ${frame.changedEventCount} из " +
                    frame.xray.entries.size
            CollectionXrayTimelapseState.VERDICT_SHIFT ->
                "Смена статуса: ${frame.newlyShiftedEventCount} из " +
                    frame.xray.entries.size
        }
    }

    private fun collectionXrayTimelapseSummary(
        frame: CollectionXrayTimelapseFrame
    ): String {
        return when (frame.state) {
            CollectionXrayTimelapseState.NOT_AVAILABLE ->
                error("Frame is always available")
            CollectionXrayTimelapseState.STABLE ->
                if (frame.horizon == CollectionXrayTimelapseHorizon.NOW) {
                    "Это исходная точка. Будущие срезы используют " +
                        "те же оценки и подтверждения."
                } else {
                    "Без новых проверок текущие доказательные " +
                        "пределы сохранятся до этого среза."
                }
            CollectionXrayTimelapseState.GAPS_GROW ->
                "Если не перепроверять факторы, часть текущей " +
                    "опоры потеряет свежесть. Исход события не " +
                    "моделируется."
            CollectionXrayTimelapseState.VERDICT_SHIFT ->
                "Если не перепроверять факторы, истечение " +
                    "свежести изменит статус данных, но не " +
                    "предсказывает результат события."
        }
    }

    private fun collectionXrayTimelapseFocusLabel(
        focus: CollectionXrayTimelapseChange
    ): String {
        return when (focus.kind) {
            CollectionXrayTimelapseChangeKind.WORSENED ->
                "ГЛАВНАЯ ПОТЕРЯ • РАЗРЫВ УСИЛИТСЯ"
            CollectionXrayTimelapseChangeKind.NEW_GAP ->
                "ГЛАВНАЯ ПОТЕРЯ • ПОЯВИТСЯ РАЗРЫВ"
            CollectionXrayTimelapseChangeKind.NEW_CRITICAL ->
                "ГЛАВНАЯ ПОТЕРЯ • СМЕНА СТАТУСА"
        }
    }

    private fun collectionXrayTimelapseFocusSummary(
        focus: CollectionXrayTimelapseChange
    ): String {
        return if (focus.causesNewVerdictShift) {
            "Без перепроверки этот фактор участвует в смене " +
                "статуса данных. Переход откроет текущий фактор, " +
                "не будущую оценку."
        } else {
            "Без перепроверки доказательный предел фактора " +
                "снизится на ${focus.supportedScoreLoss}. " +
                "Переход откроет текущий фактор."
        }
    }

    private fun collectionXrayTimelapseTone(
        state: CollectionXrayTimelapseState
    ): Tone {
        return when (state) {
            CollectionXrayTimelapseState.NOT_AVAILABLE ->
                Tone(AppColors.muted, AppColors.background)
            CollectionXrayTimelapseState.STABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CollectionXrayTimelapseState.GAPS_GROW ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CollectionXrayTimelapseState.VERDICT_SHIFT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun collectionXrayHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.collection_xray)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Пять каналов доказательств сканируют четыре кассеты и отмечают три разрыва"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "РЕНТГЕН ПОДБОРКИ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Где оценка опережает факты",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun collectionXrayLegend(): LinearLayout {
        val stack =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        return LinearLayout(this).apply {
            orientation = if (stack) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            listOf(
                Triple("Подтверждено", AppColors.accent, 0),
                Triple("Разрыв", AppColors.warning, 1),
                Triple("Меняет статус", AppColors.danger, 2)
            ).forEach { (title, color, index) ->
                addView(
                    collectionXrayLegendItem(title, color),
                    if (stack) {
                        matchWrap(
                            top = if (index == 0) 0 else 5
                        )
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            if (index < 2) rightMargin = dp(6)
                        }
                    }
                )
            }
        }
    }

    private fun collectionXrayLegendItem(
        title: String,
        color: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                View(this@MainActivity).apply {
                    background = rounded(color, 3)
                },
                LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    rightMargin = dp(6)
                }
            )
            addView(
                text(
                    title,
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun collectionXrayBadge(
        result: CollectionXrayResult
    ): String {
        return when (result.state) {
            CollectionXrayState.EMPTY -> "ПУСТО • 0/2"
            CollectionXrayState.NEED_MORE -> "НУЖНО ЕЩЕ • 1/2"
            CollectionXrayState.CLEAR ->
                "ПРЕДЕЛЫ СОВПАДАЮТ • ${result.candidateCount}"
            CollectionXrayState.GAPS ->
                "РАЗРЫВЫ ВИДНЫ • ${result.candidateCount}"
            CollectionXrayState.VERDICT_SHIFT ->
                "РАЗРЫВ МЕНЯЕТ СТАТУС"
            CollectionXrayState.TOO_MANY ->
                "АВТООТБОР ВЫКЛЮЧЕН • ${result.candidateCount}"
        }
    }

    private fun collectionXrayMetric(
        result: CollectionXrayResult
    ): String {
        val affected = result.entries.count { entry ->
            entry.cells.any {
                it.state != CollectionXrayCellState.SUPPORTED
            }
        }
        val shifted = result.entries.count {
            it.shadowStatus == ConfidenceShadowStatus.VERDICT_SHIFT
        }
        return when (result.state) {
            CollectionXrayState.EMPTY -> "Сохраните 2–8 событий"
            CollectionXrayState.NEED_MORE -> "Добавьте еще одно событие"
            CollectionXrayState.CLEAR ->
                "5 факторов выдерживают пределы"
            CollectionXrayState.GAPS ->
                "Разрывы: $affected из ${result.entries.size}"
            CollectionXrayState.VERDICT_SHIFT ->
                "Статус меняется: $shifted из ${result.entries.size}"
            CollectionXrayState.TOO_MANY ->
                "Уберите событий: " +
                    (result.candidateCount -
                        CollectionXrayPolicy.MAX_EVENTS)
        }
    }

    private fun collectionXraySummary(
        result: CollectionXrayResult
    ): String {
        return when (result.state) {
            CollectionXrayState.EMPTY,
            CollectionXrayState.NEED_MORE ->
                "Матрица сравнивает не команды, а пять открытых факторов: оценку и ее свежий доказательный предел."
            CollectionXrayState.CLEAR ->
                "Все текущие оценки укладываются в свежие пределы источников. Это не обещание исхода."
            CollectionXrayState.GAPS ->
                "Янтарные ячейки показывают часть оценки выше текущего доказательного предела, но не ранжируют события."
            CollectionXrayState.VERDICT_SHIFT ->
                "Красная ячейка отмечает главный разрыв события, где неподтвержденная часть меняет текущий статус данных."
            CollectionXrayState.TOO_MANY ->
                "Рентген не отбрасывает лишние события за вас. Оставьте от двух до восьми сохраненных карточек."
        }
    }

    private fun collectionXrayFocusExplanation(
        entry: CollectionXrayEntry,
        focus: CollectionXrayCell
    ): String {
        return if (focus.state == CollectionXrayCellState.CRITICAL) {
            "Без неподтвержденной части статус «${
                verdictTitle(entry.claimedVerdict).lowercase(
                    Locale.getDefault()
                )
            }» меняется на «${
                verdictTitle(entry.supportedVerdict).lowercase(
                    Locale.getDefault()
                )
            }». Переход откроет точный фактор."
        } else {
            "Разрыв снижает доказанную полноту на ${
                focus.readinessImpact
            }, но текущий статус сохраняется. Переход откроет точный фактор."
        }
    }

    private fun collectionXrayCauseTitle(
        cause: CollectionXrayGapCause
    ): String {
        return when (cause) {
            CollectionXrayGapCause.SOURCE_CONFLICT ->
                "расхождение источников"
            CollectionXrayGapCause.FRESHNESS_LOSS ->
                "истек срок подтверждения"
            CollectionXrayGapCause.SHARED_LINEAGE ->
                "общая первичная цепочка"
            CollectionXrayGapCause.UNAUDITED_QUORUM ->
                "независимость не проверена"
            CollectionXrayGapCause.UNCONFIRMED ->
                "факт не подтвержден"
            CollectionXrayGapCause.SINGLE_SOURCE_LIMIT ->
                "предел одного источника"
            CollectionXrayGapCause.EVIDENCE_LIMIT ->
                "текущий предел доказательств"
        }
    }

    private fun collectionXrayTone(
        state: CollectionXrayState
    ): Tone {
        return when (state) {
            CollectionXrayState.EMPTY ->
                Tone(AppColors.muted, AppColors.background)
            CollectionXrayState.NEED_MORE ->
                Tone(AppColors.signal, AppColors.signalSoft)
            CollectionXrayState.CLEAR ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CollectionXrayState.GAPS ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CollectionXrayState.VERDICT_SHIFT,
            CollectionXrayState.TOO_MANY ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun collectionXrayCellTone(
        state: CollectionXrayCellState
    ): Tone {
        return when (state) {
            CollectionXrayCellState.SUPPORTED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CollectionXrayCellState.GAP ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CollectionXrayCellState.CRITICAL ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun factExpressHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.fact_express)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Три спортивных модуля соединены рельсой с одной точкой проверки"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "МАРШРУТ ФАКТОВ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Соберите проверки, не исходы",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun factExpressRow(
        number: Int,
        entry: FactExpressEntry,
        selectedZone: RegionalZone,
        onClick: () -> Unit
    ): LinearLayout {
        val tone = factExpressEntryTone(entry.state)
        val stackHeading =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        val point = entry.nextMoment?.let {
            "${FactExpressText.momentTitle(it)} • ${
                FactExpressText.momentTime(it, selectedZone)
            }"
        } ?: when (entry.state) {
            FactExpressEntryState.COMPLETE ->
                "Все шесть глав локального сюжета завершены"
            FactExpressEntryState.UNSCHEDULED ->
                "Абсолютная следующая точка не доказана"
            FactExpressEntryState.ACTION_NOW,
            FactExpressEntryState.WAITING ->
                "Следующая абсолютная точка не определена"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(7), dp(3), dp(8))
            applyAccessibleAction(dp(48))
            background = rippleRounded(AppColors.surface, 6)
            setOnClickListener { onClick() }
            contentDescription =
                "Событие $number. ${entry.match}. ${
                    FactExpressText.stateTitle(entry.state)
                }. ${FactExpressText.actionTitle(entry)}. $point. " +
                    "Открыть сюжет события."
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackHeading) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackHeading) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        label(
                            "$number · ${FactExpressText.stateTitle(
                                entry.state
                            )}",
                            tone.background,
                            tone.foreground
                        ),
                        if (stackHeading) {
                            wrapWrap(bottom = 5)
                        } else {
                            wrapWrap(right = 9)
                        }
                    )
                    addView(
                        text(
                            entry.match,
                            15f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stackHeading) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                    )
                }
            )
            addView(
                text(
                    FactExpressText.actionTitle(entry),
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    point,
                    12f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 4)
            )
            addView(
                text(
                    "${entry.sport} • ${entry.region} • ${
                        EventStoryPosterFactory.chapterTitle(
                            entry.chapter
                        )
                    }",
                    11f,
                    AppColors.muted
                ),
                matchWrap(top = 4)
            )
        }
    }

    private fun factExpressBadge(result: FactExpressResult): String {
        return when (result.state) {
            FactExpressState.EMPTY -> "ПУСТО • 0/2"
            FactExpressState.NEED_MORE -> "НУЖНО ЕЩЕ • 1/2"
            FactExpressState.READY ->
                "МАРШРУТ ГОТОВ • ${result.entries.size}"
            FactExpressState.TOO_MANY ->
                "АВТООТБОР ВЫКЛЮЧЕН • ${result.entries.size}"
        }
    }

    private fun factExpressMetric(result: FactExpressResult): String {
        return when (result.state) {
            FactExpressState.EMPTY -> "Выберите 2–4 события"
            FactExpressState.NEED_MORE ->
                "Добавьте еще одно событие"
            FactExpressState.READY ->
                "${result.entries.size} события • проверок сейчас " +
                    result.actionNowCount
            FactExpressState.TOO_MANY ->
                "Уберите событий: ${result.overLimitCount}"
        }
    }

    private fun factExpressSummary(result: FactExpressResult): String {
        return when (result.state) {
            FactExpressState.EMPTY ->
                "Нажмите звезду на карточках событий. Вместо исходов маршрут соберет только действия и доказанные точки времени."
            FactExpressState.NEED_MORE ->
                "Первое событие уже на рельсе. Для маршрута нужно минимум два независимых выбора."
            FactExpressState.READY ->
                "Сначала идут действия сейчас, затем ближайшие абсолютные точки. Вероятности, коэффициенты и общая выплата не вычисляются."
            FactExpressState.TOO_MANY ->
                "Приложение не выбирает события за вас. Оставьте от двух до четырех сохраненных карточек."
        }
    }

    private fun factExpressTone(state: FactExpressState): Tone {
        return when (state) {
            FactExpressState.EMPTY ->
                Tone(AppColors.muted, AppColors.background)
            FactExpressState.NEED_MORE ->
                Tone(AppColors.signal, AppColors.signalSoft)
            FactExpressState.READY ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            FactExpressState.TOO_MANY ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun factExpressEntryTone(
        state: FactExpressEntryState
    ): Tone {
        return when (state) {
            FactExpressEntryState.ACTION_NOW ->
                Tone(AppColors.signal, AppColors.signalSoft)
            FactExpressEntryState.WAITING ->
                Tone(AppColors.warning, AppColors.warningSoft)
            FactExpressEntryState.UNSCHEDULED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            FactExpressEntryState.COMPLETE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun showBlindRound() {
        val now = System.currentTimeMillis()
        if (state.isPauseActive(now)) {
            Toast.makeText(
                this,
                "Во время паузы Слепой раунд недоступен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val result = factExpressResult(
            bookmarks = state.bookmarkedIds(),
            now = now
        )
        if (!result.isReady) {
            Toast.makeText(
                this,
                "Состав изменился — нужно 2–4 события",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val session = BlindRoundEngine.prepare(result)
        var reveal: BlindRoundReveal? = null
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                body,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        lateinit var dialog: AlertDialog
        lateinit var refresh: () -> Unit
        refresh = {
            body.removeAllViews()
            val currentReveal = reveal
            body.addView(
                blindRoundHeader(currentReveal != null),
                matchFixed(124)
            )
            val tone = currentReveal?.let {
                blindRoundTone(it.alignment)
            } ?: Tone(AppColors.signal, AppColors.signalSoft)
            body.addView(
                label(
                    currentReveal?.let {
                        BlindRoundText.resultTitle(it.alignment)
                            .uppercase()
                    } ?: "БРЕНДЫ СКРЫТЫ • ${session.cards.size}",
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            body.addView(
                text(
                    currentReveal?.let {
                        "Досье ${it.selectedCode} раскрыто"
                    } ?: "Что вы проверили бы первым?",
                    19f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            body.addView(
                text(
                    currentReveal?.let(
                        BlindRoundText::resultSummary
                    ) ?: "Команды, спорт и регион скрыты. Выберите только по действию и ближайшей точке. Это не прогноз исхода.",
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            body.addView(
                BlindRoundView(this).apply {
                    setState(
                        session = session,
                        selectedToken = currentReveal
                            ?.selectedCard
                            ?.token,
                        revealed = currentReveal != null
                    )
                },
                matchFixed(108, top = 5)
            )
            if (currentReveal == null) {
                session.cards.forEach { card ->
                    body.addView(
                        blindRoundChoiceCard(
                            card = card,
                            selectedZone = session.selectedZone,
                            onSelect = {
                                reveal = BlindRoundEngine.reveal(
                                    session = session,
                                    result = result,
                                    selectedToken = card.token
                                )
                                refresh()
                            }
                        ),
                        matchWrap(top = 8)
                    )
                }
                body.addView(
                    text(
                        "БЕЗ ИСХОДОВ • БЕЗ ОЧКОВ • " +
                            "SHA-256 ${session.shortFingerprint}",
                        11.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 11)
                )
            } else {
                currentReveal.cards.forEach { card ->
                    body.addView(
                        blindRoundRevealCard(
                            card = card,
                            selectedZone = session.selectedZone
                        ),
                        matchWrap(top = 8)
                    )
                }
                body.addView(
                    commandButton(
                        "Открыть выбранный сюжет",
                        tone.foreground
                    ) {
                        dialog.dismiss()
                        openPulseStory(
                            currentReveal.selectedCard.eventId
                        )
                    },
                    matchWrap(top = 10)
                )
                if (
                    currentReveal.alignment ==
                    BlindRoundAlignment.DIFFERENT
                ) {
                    body.addView(
                        outlineButton(
                            "Открыть первый по фактам",
                            tone.foreground
                        ) {
                            dialog.dismiss()
                            openPulseStory(
                                currentReveal
                                    .firstByFactsCard
                                    .eventId
                            )
                        },
                        matchWrap(top = 8)
                    )
                }
                body.addView(
                    text(
                        "ВЫБОР НЕ СОХРАНЕН • БЕЗ ОЦЕНКИ • " +
                            "SHA-256 ${currentReveal.shortFingerprint}",
                        11.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 11)
                )
            }
        }
        refresh()
        dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("Закрыть", null)
            .create()
        dialog.setOnShowListener {
            val targetWidth = min(
                resources.displayMetrics.widthPixels - dp(24),
                dp(720)
            )
            dialog.window?.setLayout(
                targetWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun blindRoundHeader(revealed: Boolean): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.blind_round)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Четыре закрытых безымянных досье соединены с одним шлюзом раскрытия"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "СЛЕПОЙ РАУНД",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            if (revealed) {
                                "Выбор сделан без названий"
                            } else {
                                "Сначала данные, потом команды"
                            },
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun blindRoundChoiceCard(
        card: BlindRoundCard,
        selectedZone: RegionalZone,
        onSelect: () -> Unit
    ): LinearLayout {
        val tone = factExpressEntryTone(card.state)
        val action = BlindRoundText.actionTitle(card)
        val point = BlindRoundText.pointTitle(card, selectedZone)
        return this@MainActivity.card().apply {
            applyAccessibleAction(dp(48))
            background = rippleRounded(AppColors.surface, 6)
            setOnClickListener { onSelect() }
            contentDescription =
                "Анонимное досье ${card.code}. ${
                    FactExpressText.stateTitle(card.state)
                }. $action. $point. Команда, спорт и регион скрыты. Выбрать досье ${card.code}."
            addView(
                label(
                    "ДОСЬЕ ${card.code} • ${
                        FactExpressText.stateTitle(card.state)
                    }",
                    tone.background,
                    tone.foreground
                )
            )
            addView(
                text(
                    "Глава: ${
                        EventStoryPosterFactory.chapterTitle(
                            card.chapter
                        )
                    }",
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 8)
            )
            addView(
                text(action, 13f, tone.foreground, Typeface.BOLD),
                matchWrap(top = 5)
            )
            addView(
                text(point, 12f, AppColors.ink, Typeface.BOLD),
                matchWrap(top = 4)
            )
            addView(
                text(
                    "КОМАНДА • СПОРТ • РЕГИОН СКРЫТЫ",
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 7)
            )
        }
    }

    private fun blindRoundRevealCard(
        card: BlindRoundRevealCard,
        selectedZone: RegionalZone
    ): LinearLayout {
        val tone = when {
            card.selected -> Tone(
                AppColors.accentDark,
                AppColors.accentSoft
            )
            card.firstByFacts -> Tone(
                AppColors.signal,
                AppColors.signalSoft
            )
            else -> Tone(AppColors.muted, AppColors.background)
        }
        val badge = when {
            card.selected && card.firstByFacts ->
                "ВАШ ВЫБОР • ПЕРВЫЙ ПО ФАКТАМ"
            card.selected -> "ВАШ ВЫБОР • ДОСЬЕ ${card.code}"
            card.firstByFacts -> "ПЕРВЫЙ ПО ФАКТАМ"
            else -> "ОТКРЫТЫЙ ПОРЯДОК • #${card.sourceRank}"
        }
        return this@MainActivity.card().apply {
            contentDescription =
                "$badge. ${card.match}. ${card.sport}, ${card.region}. ${
                    BlindRoundText.actionTitle(card)
                }. ${BlindRoundText.pointTitle(card, selectedZone)}."
            addView(label(badge, tone.background, tone.foreground))
            addView(
                text(
                    card.match,
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 8)
            )
            addView(
                text(
                    "${card.sport} • ${card.region} • очередь #${
                        card.sourceRank
                    }",
                    11f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
            addView(
                text(
                    BlindRoundText.actionTitle(card),
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    BlindRoundText.pointTitle(card, selectedZone),
                    12f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 4)
            )
        }
    }

    private fun blindRoundTone(
        alignment: BlindRoundAlignment
    ): Tone {
        return when (alignment) {
            BlindRoundAlignment.ALIGNED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            BlindRoundAlignment.DIFFERENT ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
    }

    private fun eventDispatchPanel(
        result: EventDispatchResult,
        now: Long,
        onEntrySelected: (EventDispatchEntry) -> Unit
    ): LinearLayout {
        val tone = eventDispatchTone(result.status)
        val badge = label(
            getString(
                R.string.event_dispatch_badge,
                result.entries.size
            ),
            tone.background,
            tone.foreground
        )
        val chart = EventDispatchView(this).apply {
            setResult(result)
        }
        return card().apply {
            addView(eventDispatchHeader(), matchFixed(imageHeaderHeight()))
            addView(badge, matchWrap(top = 12))
            addView(
                text(
                    getString(
                        R.string.event_dispatch_counts,
                        result.stopCount,
                        result.attentionCount,
                        result.activeCount,
                        result.stableCount
                    ),
                    18f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    eventDispatchSummary(result.status),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            addView(chart, matchFixed(132, top = 6))
            result.visibleEntries.forEachIndexed {
                    index,
                    entry ->
                if (index > 0) {
                    addView(
                        divider(),
                        matchFixed(1, top = 5)
                    )
                }
                addView(
                    eventDispatchRow(
                        number = index + 1,
                        entry = entry,
                        now = now,
                        onClick = {
                            onEntrySelected(entry)
                        }
                    ),
                    matchWrap(top = 6)
                )
            }
            addView(
                text(
                    getString(
                        R.string.event_dispatch_footer,
                        result.entries.size,
                        result.initializedCount,
                        result.bookmarkedCount,
                        result.shortFingerprint
                    ),
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 10)
            )
        }
    }

    private fun eventDispatchHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.event_dispatch)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Операторский центр контролирует несколько спортивных событий и три уровня срочности"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ДИСПЕТЧЕР СОБЫТИЙ",
                            11f,
                            Color.rgb(191, 238, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Срочное видно сразу",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun eventDispatchRow(
        number: Int,
        entry: EventDispatchEntry,
        now: Long,
        onClick: () -> Unit
    ): LinearLayout {
        val task = entry.primaryTask
        val tone = verificationCommandPriorityTone(
            task.priority
        )
        val stackHeading =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        val meta = buildList {
            add(entry.sport)
            add(entry.region)
            if (entry.bookmarked) add("Сохранено")
            add(
                if (entry.initialized) {
                    "Разбор начат"
                } else {
                    "Без истории"
                }
            )
            task.dueAt?.let { deadline ->
                add(
                    if (deadline <= now) {
                        getString(
                            R.string.event_dispatch_due_now
                        )
                    } else {
                        getString(
                            R.string.event_dispatch_due,
                            FreshnessFormatter.duration(
                                deadline - now
                            )
                        )
                    }
                )
            }
        }.joinToString("  •  ")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(7), dp(3), dp(8))
            applyAccessibleAction(dp(48))
            background = rippleRounded(AppColors.surface, 6)
            setOnClickListener { onClick() }
            contentDescription =
                "Событие $number. ${entry.match}. ${task.priority.title}. ${task.title}. ${task.reason} Открыть анализ события."
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackHeading) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackHeading) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        label(
                            "$number · ${task.priority.shortTitle}",
                            tone.background,
                            tone.foreground
                        ),
                        if (stackHeading) {
                            wrapWrap(bottom = 5)
                        } else {
                            wrapWrap(right = 9)
                        }
                    )
                    addView(
                        text(
                            entry.match,
                            15f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stackHeading) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                    )
                }
            )
            addView(
                text(
                    task.title,
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(meta, 11f, AppColors.muted),
                matchWrap(top = 4)
            )
            addView(
                text(
                    getString(
                        R.string.event_dispatch_action
                    ),
                    12f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 6)
            )
        }
    }

    private fun eventDispatchSummary(
        status: EventDispatchStatus
    ): String {
        return when (status) {
            EventDispatchStatus.EMPTY ->
                "В выбранной области нет событий."
            EventDispatchStatus.STOP ->
                "Есть событие со стоп-причиной. Оно остаётся первым независимо от закладки или порядка каталога."
            EventDispatchStatus.ATTENTION ->
                "Стоп-причин нет, но есть повреждённые или стареющие подтверждения."
            EventDispatchStatus.ACTIVE ->
                "Критических остановок нет. Первыми идут открытые проверки по строгому порядку диспетчера."
            EventDispatchStatus.STABLE ->
                "Все события области требуют только контроля ближайших сроков."
        }
    }

    private fun eventDispatchTone(
        status: EventDispatchStatus
    ): Tone {
        return when (status) {
            EventDispatchStatus.EMPTY ->
                Tone(AppColors.muted, AppColors.background)
            EventDispatchStatus.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            EventDispatchStatus.ATTENTION ->
                Tone(AppColors.warning, AppColors.warningSoft)
            EventDispatchStatus.ACTIVE ->
                Tone(AppColors.signal, AppColors.signalSoft)
            EventDispatchStatus.STABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun eventPackagePanel(): LinearLayout {
        val now = System.currentTimeMillis()
        val eventPackage = importedEventPackage
        val activePackage = eventPackage?.takeUnless {
            it.isExpired(now)
        }
        val readiness = sourceReadiness(now)
        val tone = sourceReadinessTone(readiness.level)
        val apiConfigured = apiFootballConfigured()
        return card(padding = 0).apply {
            clipToOutline = true
            addView(
                sourceReadinessHeader(),
                matchFixed(imageHeaderHeight(116))
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(16))
                    addView(
                        label(
                            readiness.badge,
                            tone.background,
                            tone.foreground
                        )
                    )
                    addView(
                        text(
                            readiness.verdict,
                            21f,
                            tone.foreground,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 10)
                    )
                    addView(
                        text(readiness.summary, 13.5f, AppColors.ink),
                        matchWrap(top = 5)
                    )
                    readiness.checks.forEach { check ->
                        addView(divider(), matchFixed(1, top = 12))
                        addView(
                            sourceReadinessCheckRow(check),
                            matchWrap(top = 10)
                        )
                    }
                    if (activePackage == null && apiConfigured) {
                        addView(
                            commandButton(
                                if (apiFootballRefreshInProgress) {
                                    "Обновляем расписание..."
                                } else {
                                    "Обновить расписание"
                                },
                                AppColors.accent
                            ) {
                                refreshApiFootball(force = true)
                            }.apply {
                                isEnabled = !apiFootballRefreshInProgress
                                alpha = if (isEnabled) 1f else 0.65f
                            },
                            matchWrap(top = 14)
                        )
                    }
                    val importTitle = when {
                        eventPackageImportInProgress ->
                            "Проверяем локальный пакет..."
                        activePackage != null ->
                            "Заменить локальный пакет"
                        else -> "Импортировать локальный пакет"
                    }
                    val importAction = if (
                        activePackage == null && !apiConfigured
                    ) {
                        commandButton(
                            importTitle,
                            AppColors.signal,
                            ::launchEventPackagePicker
                        )
                    } else {
                        outlineButton(
                            importTitle,
                            AppColors.signal,
                            ::launchEventPackagePicker
                        )
                    }
                    addView(
                        importAction.apply {
                            isEnabled = !eventPackageImportInProgress
                            alpha = if (isEnabled) 1f else 0.65f
                        },
                        matchWrap(top = 8)
                    )
                    if (eventPackage != null) {
                        addView(
                            outlineButton(
                                "Создать паспорт источника",
                                AppColors.signal
                            ) {
                                shareSourceAuthenticityPassport(
                                    eventPackage
                                )
                            },
                            matchWrap(top = 8)
                        )
                        addView(
                            outlineButton(
                                if (activePackage != null) {
                                    if (
                                        apiFootballFeed
                                            ?.fixtures
                                            ?.isNotEmpty() == true
                                    ) {
                                        "Вернуться к онлайн-каталогу"
                                    } else {
                                        "Вернуться к учебному каталогу"
                                    }
                                } else {
                                    "Удалить истёкший пакет"
                                },
                                AppColors.muted,
                                ::resetEventPackage
                            ),
                            matchWrap(top = 8)
                        )
                    }
                    addView(
                        outlineButton(
                            if (sourceReadinessDetailsExpanded) {
                                "Скрыть технические детали"
                            } else {
                                "Показать технические детали"
                            },
                            AppColors.muted
                        ) {
                            sourceReadinessDetailsExpanded =
                                !sourceReadinessDetailsExpanded
                            rerenderContentPreservingScroll()
                        },
                        matchWrap(top = 8)
                    )
                    if (sourceReadinessDetailsExpanded) {
                        addView(
                            text(
                                sourceReadinessTechnicalDetails(
                                    now = now,
                                    eventPackage = eventPackage
                                ),
                                11.5f,
                                AppColors.muted,
                                Typeface.BOLD
                            ).apply {
                                background = rounded(
                                    AppColors.background,
                                    8
                                )
                                setPadding(
                                    dp(12),
                                    dp(11),
                                    dp(12),
                                    dp(11)
                                )
                            },
                            matchWrap(top = 8)
                        )
                    }
                }
            )
        }
    }

    private fun sourceReadiness(
        now: Long
    ): SourceReadinessResult {
        return SourceReadinessEngine.evaluate(
            now = now,
            eventPackage = importedEventPackage,
            apiFeed = apiFootballFeed,
            apiActive =
                activeCatalogOrigin == SportEventOrigin.API_SPORTS,
            apiConfigured = apiFootballConfigured(),
            refreshing = apiFootballRefreshInProgress,
            apiError = apiFootballError
        )
    }

    private fun sourceReadinessTone(
        level: SourceReadinessLevel
    ): Tone {
        return when (level) {
            SourceReadinessLevel.READY ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            SourceReadinessLevel.VERIFY ->
                Tone(AppColors.warning, AppColors.warningSoft)
            SourceReadinessLevel.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            SourceReadinessLevel.DEMO ->
                Tone(AppColors.signal, AppColors.signalSoft)
        }
    }

    private fun sourceReadinessHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(
                        R.drawable.source_readiness_v3120
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Три станции контроля данных: источник, свежесть и граница вывода"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "КОНТРОЛЬ ДАННЫХ",
                            10.5f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Можно ли начинать разбор?",
                            19f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(14)
                    rightMargin = dp(14)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun sourceReadinessCheckRow(
        check: SourceReadinessCheck
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(
                    check.title.uppercase(Locale.getDefault()),
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    check.value,
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
            addView(
                text(check.detail, 12.5f, AppColors.muted),
                matchWrap(top = 2)
            )
        }
    }

    private fun sourceReadinessTechnicalDetails(
        now: Long,
        eventPackage: SportEventPackage?
    ): String {
        val activePackage = eventPackage?.takeUnless {
            it.isExpired(now)
        }
        return when {
            activePackage != null ->
                "ЛОКАЛЬНЫЙ ПАКЕТ\n" +
                    "Источник: ${activePackage.sourceLabel}\n" +
                    "Событий: ${activePackage.events.size} • " +
                    "действует до ${formatPackageDate(activePackage.validUntil)}\n" +
                    "Контрольная метка: ${activePackage.shortFingerprint}\n" +
                    "Доверие: ${packageTrustShort(activePackage)}"
            activeCatalogOrigin == SportEventOrigin.API_SPORTS &&
                apiFootballFeed != null -> {
                val feed = checkNotNull(apiFootballFeed)
                "ОНЛАЙН-РАСПИСАНИЕ\n" +
                    "Событий: ${feed.fixtures.size}\n" +
                    "Обновлено: ${formatPackageDate(feed.fetchedAt)}\n" +
                    "Окно: ${feed.fromDate}–${feed.toDate}\n" +
                    "Канал: HTTPS через сервер приложения"
            }
            eventPackage != null ->
                "ИСТЁКШИЙ ПАКЕТ\n" +
                    "Источник: ${eventPackage.sourceLabel}\n" +
                    "Истёк: ${formatPackageDate(eventPackage.validUntil)}\n" +
                    "Контрольная метка: ${eventPackage.shortFingerprint}"
            apiFootballFeed != null -> {
                val feed = checkNotNull(apiFootballFeed)
                "ПУСТОЕ ОНЛАЙН-ОКНО\n" +
                    "Обновлено: ${formatPackageDate(feed.fetchedAt)}\n" +
                    "Окно: ${feed.fromDate}–${feed.toDate}\n" +
                    "Подходящих событий: 0"
            }
            apiFootballError != null ->
                "ОНЛАЙН-ОБНОВЛЕНИЕ\n" +
                    "Последняя попытка завершилась ошибкой.\n" +
                    "Канал: HTTPS через сервер приложения"
            else ->
                "УЧЕБНЫЙ КАТАЛОГ\n" +
                    "Событий: ${DemoCatalog.events.size}\n" +
                    "Текущие матчи не подтверждены."
        }
    }

    private fun apiUpdatePulsePanel(
        delta: ApiFootballDelta
    ): LinearLayout {
        val hasChanges = delta.changes.isNotEmpty()
        val tone = if (hasChanges) {
            Tone(AppColors.signal, AppColors.signalSoft)
        } else {
            Tone(AppColors.accentDark, AppColors.accentSoft)
        }
        val visibleChanges = if (apiUpdatePulseExpanded) {
            delta.changes
        } else {
            delta.changes.take(COLLAPSED_API_CHANGES)
        }
        return card().apply {
            addView(
                apiUpdatePulseHeader(),
                matchFixed(imageHeaderHeight())
            )
            addView(
                label(
                    if (hasChanges) {
                        "ИЗМЕНЕНО СОБЫТИЙ • ${delta.changes.size}"
                    } else {
                        "БЕЗ ЗНАЧИМЫХ ИЗМЕНЕНИЙ"
                    },
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    if (hasChanges) {
                        "Выдача изменилась"
                    } else {
                        "Расписание стабильно"
                    },
                    20f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    apiUpdatePulseSummary(delta),
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    "СНИМКИ • ${formatPackageDate(delta.previousFetchedAt)} → " +
                        formatPackageDate(delta.currentFetchedAt),
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 9)
            )
            visibleChanges.forEachIndexed { index, change ->
                if (index > 0) {
                    addView(divider(), matchFixed(1, top = 7))
                }
                addView(
                    apiUpdatePulseChangeRow(change),
                    matchWrap(top = 7)
                )
            }
            if (delta.changes.size > COLLAPSED_API_CHANGES) {
                addView(
                    outlineButton(
                        if (apiUpdatePulseExpanded) {
                            "Свернуть изменения"
                        } else {
                            "Показать ещё ${
                                delta.changes.size - COLLAPSED_API_CHANGES
                            }"
                        },
                        AppColors.signal
                    ) {
                        apiUpdatePulseExpanded =
                            !apiUpdatePulseExpanded
                        rerenderContentPreservingScroll()
                    },
                    matchWrap(top = 11)
                )
            }
            addView(
                text(
                    "СРАВНЕНИЕ ВЫДАЧ • SHA-256 ${delta.shortFingerprint}\n" +
                        "Отсутствие в новой выдаче не означает отмену матча.",
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun apiUpdatePulseHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.api_update_pulse)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Три снимка футбольного события соединены линией изменений"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ПУЛЬС ОБНОВЛЕНИЯ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Матч меняется • след остаётся",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun apiUpdatePulseChangeRow(
        change: ApiFootballChange
    ): LinearLayout {
        val tone = apiUpdatePulseTone(change)
        val eventId = "api_football_${change.fixtureId}"
        val canOpen = change.current != null &&
            catalogEvents.any { it.id == eventId }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = if (canOpen) {
                rippleRounded(AppColors.background, 8)
            } else {
                rounded(AppColors.background, 8)
            }
            addView(
                label(
                    apiUpdatePulseKinds(change),
                    tone.background,
                    tone.foreground
                )
            )
            addView(
                text(
                    change.match,
                    15.5f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 7)
            )
            addView(
                text(
                    apiUpdatePulseDetails(change),
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    "${change.league} • ${
                        ApiFootballText.country(change.country)
                    }",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )
            if (canOpen) {
                applyAccessibleAction(dp(48))
                contentDescription =
                    "Открыть событие с изменениями: ${change.match}"
                setOnClickListener {
                    state.selectedEventId = eventId
                    openPulseStory(eventId)
                }
            }
        }
    }

    private fun apiUpdatePulseSummary(
        delta: ApiFootballDelta
    ): String {
        if (delta.changes.isEmpty()) {
            return "Сопоставлено событий: ${delta.comparedFixtureCount}. " +
                "Время, статус и счёт совпадают с прошлым снимком."
        }
        val liveChanges = delta.changes.count { change ->
            ApiFootballChangeKind.STATUS in change.kinds ||
                ApiFootballChangeKind.SCORE in change.kinds
        }
        val feedChanges = delta.changes.count { change ->
            ApiFootballChangeKind.NEW_IN_FEED in change.kinds ||
                ApiFootballChangeKind.MISSING_FROM_FEED in change.kinds
        }
        return "Переносы: ${delta.count(ApiFootballChangeKind.START_TIME)} • " +
            "статус или счёт: $liveChanges • " +
            "события в выдаче: $feedChanges."
    }

    private fun apiUpdatePulseKinds(
        change: ApiFootballChange
    ): String {
        return change.kinds
            .sortedBy(ApiFootballChangeKind::priority)
            .joinToString(" • ") { kind ->
                when (kind) {
                    ApiFootballChangeKind.SCORE -> "СЧЁТ"
                    ApiFootballChangeKind.STATUS -> "СТАТУС"
                    ApiFootballChangeKind.START_TIME -> "ВРЕМЯ"
                    ApiFootballChangeKind.NEW_IN_FEED ->
                        "ПОЯВИЛОСЬ В ВЫДАЧЕ"
                    ApiFootballChangeKind.MISSING_FROM_FEED ->
                        "НЕТ В НОВОЙ ВЫДАЧЕ"
                }
            }
    }

    private fun apiUpdatePulseDetails(
        change: ApiFootballChange
    ): String {
        return buildList {
            if (ApiFootballChangeKind.NEW_IN_FEED in change.kinds) {
                add("Событие появилось в обновлённом расписании.")
            }
            if (ApiFootballChangeKind.MISSING_FROM_FEED in change.kinds) {
                add("События нет в обновлённом расписании. Отмена не подтверждена.")
            }
            if (ApiFootballChangeKind.START_TIME in change.kinds) {
                add(
                    "Старт: ${apiUpdatePulseTime(change.previous)} → " +
                        apiUpdatePulseTime(change.current)
                )
            }
            if (ApiFootballChangeKind.STATUS in change.kinds) {
                add(
                    "Статус: ${apiUpdatePulseStatus(change.previous)} → " +
                        apiUpdatePulseStatus(change.current)
                )
            }
            if (ApiFootballChangeKind.SCORE in change.kinds) {
                add(
                    "Счёт: ${apiUpdatePulseScore(change.previous)} → " +
                        apiUpdatePulseScore(change.current)
                )
            }
        }.joinToString("\n")
    }

    private fun apiUpdatePulseTime(
        fixture: ApiFootballFixture?
    ): String {
        fixture ?: return "не указано"
        return SimpleDateFormat(
            "d MMM, HH:mm 'МСК'",
            Locale.forLanguageTag("ru-RU")
        ).apply {
            timeZone = TimeZone.getTimeZone("Europe/Moscow")
        }.format(Date(fixture.startAt))
    }

    private fun apiUpdatePulseStatus(
        fixture: ApiFootballFixture?
    ): String {
        fixture ?: return "не указано"
        return ApiFootballText.status(
            code = fixture.statusCode,
            fallback = fixture.statusLabel
        ).lowercase(Locale.forLanguageTag("ru-RU"))
    }

    private fun apiUpdatePulseScore(
        fixture: ApiFootballFixture?
    ): String {
        fixture ?: return "не указан"
        val home = fixture.homeScore ?: return "не указан"
        val away = fixture.awayScore ?: return "не указан"
        return "$home:$away"
    }

    private fun apiUpdatePulseTone(
        change: ApiFootballChange
    ): Tone {
        val status = change.current?.statusCode
            ?.uppercase(Locale.ROOT)
        return when {
            status == "CANC" ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            status in setOf("PST", "SUSP", "INT") ->
                Tone(AppColors.warning, AppColors.warningSoft)
            change.primaryKind == ApiFootballChangeKind.SCORE ||
                change.primaryKind == ApiFootballChangeKind.STATUS ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            change.primaryKind == ApiFootballChangeKind.START_TIME ->
                Tone(AppColors.warning, AppColors.warningSoft)
            change.primaryKind == ApiFootballChangeKind.NEW_IN_FEED ->
                Tone(AppColors.signal, AppColors.signalSoft)
            else -> Tone(AppColors.muted, AppColors.background)
        }
    }

    private fun packageTrustStatus(
        eventPackage: SportEventPackage?
    ): String {
        val authenticity = eventPackage?.authenticity
            ?: return "ПОДЛИННОСТЬ НЕ ПРОВЕРЯЕТСЯ\nДемо-каталог встроен в приложение и не выдает себя за внешний источник."
        if (!authenticity.isAuthenticated) {
            return "ПОДПИСИ НЕТ\nСтруктура, срок и SHA-256 проверены, но автор файла не подтвержден."
        }
        val title = when (authenticity.keyEnvironment) {
            EventPackageKeyEnvironment.PRODUCTION ->
                "КРИПТОПРОПУСК ПРОВЕРЕН"
            EventPackageKeyEnvironment.DEVELOPMENT ->
                "ДЕМО-ПОДПИСЬ ПРОВЕРЕНА"
            null ->
                "ПОДПИСЬ ПРОВЕРЕНА"
        }
        val limitation = if (
            authenticity.keyEnvironment ==
            EventPackageKeyEnvironment.DEVELOPMENT
        ) {
            "\nКлюч предназначен для разработки, не для реальных данных."
        } else {
            ""
        }
        return "$title\n${authenticity.keyLabel} • ${authenticity.keyId}\n" +
            "Ключ ${authenticity.shortKeyFingerprint} • " +
            "подпись ${authenticity.shortSignatureFingerprint}" +
            limitation
    }

    private fun timeBridgePanel(): LinearLayout {
        val selectedZone = state.selectedRegionalZone
        val result = TimeBridgeEngine.evaluate(
            selectedZone = selectedZone,
            nowMillis = System.currentTimeMillis()
        )
        val dayTone = if (result.dayShift == 0) {
            Tone(AppColors.accentDark, AppColors.accentSoft)
        } else {
            Tone(AppColors.danger, AppColors.dangerSoft)
        }
        val sourceClock = timeBridgeClock(
            result.moscowDateTime.hour,
            result.moscowDateTime.minute
        )
        val targetClock = timeBridgeClock(
            result.selectedDateTime.hour,
            result.selectedDateTime.minute
        )
        val image = FrameLayout(this).apply {
            background = rounded(AppColors.ink, 8)
            clipToOutline = true
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.time_bridge)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Синхронизированная шкала времени городов России и СНГ"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ЧАСОВОЙ МОСТ СНГ",
                            21f,
                            Color.WHITE,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Одно событие • ваше локальное время",
                            12f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(16)
                    rightMargin = dp(16)
                    bottomMargin = dp(14)
                }
            )
            addView(
                label(
                    result.selectedOffsetLabel,
                    Color.argb(215, 255, 255, 255),
                    AppColors.accentDark
                ),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = dp(12)
                    rightMargin = dp(12)
                }
            )
        }

        val cityScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            contentDescription = "Выбор локального времени города"
        }
        val cityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        var selectedCityChip: View? = null
        RegionalZone.entries.forEach { zone ->
            val chip = filterChip(
                title = zone.city,
                selected = zone == selectedZone
            ) {
                if (zone != state.selectedRegionalZone) {
                    state.selectedRegionalZone = zone
                    refreshHeroStatus()
                    rerenderContentPreservingScroll()
                }
            }.apply {
                contentDescription = if (zone == selectedZone) {
                    "${zone.city}, выбрано"
                } else {
                    "Показать время города ${zone.city}"
                }
            }
            if (zone == selectedZone) selectedCityChip = chip
            cityRow.addView(
                chip,
                wrapWrap(right = 7)
            )
        }
        cityScroller.addView(cityRow)
        cityScroller.post {
            selectedCityChip?.let { chip ->
                cityScroller.scrollTo(
                    (chip.left - dp(10)).coerceAtLeast(0),
                    0
                )
            }
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(
                            "Москва $sourceClock → " +
                                "${selectedZone.city} $targetClock",
                            18f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            rightMargin = dp(8)
                        }
                    )
                    addView(
                        label(
                            if (result.dayShift == 0) {
                                "ОДНА ДАТА"
                            } else {
                                if (result.dayShift > 0) {
                                    "+1 ДЕНЬ"
                                } else {
                                    "-1 ДЕНЬ"
                                }
                            },
                            dayTone.background,
                            dayTone.foreground
                        )
                    )
                }
            )
            addView(
                TimeBridgeView(this@MainActivity).apply {
                    background = rounded(AppColors.background, 8)
                    setResult(result)
                },
                matchFixed(138, top = 12)
            )
            addView(
                text(
                    "ГОРОД ДЛЯ ВСЕХ СОБЫТИЙ",
                    11f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 14)
            )
            addView(cityScroller, matchWrap(top = 7))
            addView(
                text(
                    "Точные даты Event Pack пересчитываются по IANA-зоне устройства. " +
                        "Демо-расписание остается честно отмечено; неизвестное время не угадывается.",
                    12.5f,
                    AppColors.muted
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    "БЕЗ ГЕОЛОКАЦИИ И СЕТИ • SHA-256 ${result.shortFingerprint}",
                    10.5f,
                    AppColors.signal,
                    Typeface.BOLD
                ),
                matchWrap(top = 9)
            )
        }

        return card(padding = 0).apply {
            clipToOutline = true
            addView(
                image,
                matchFixed(
                    142 + compactLargeTextExtraDp(48)
                )
            )
            addView(body, matchWrap())
        }
    }

    private fun timeBridgeClock(hour: Int, minute: Int): String {
        return hour.toString().padStart(2, '0') +
            ":" + minute.toString().padStart(2, '0')
    }

    private fun shareSourceAuthenticityPassport(
        eventPackage: SportEventPackage
    ) {
        if (passportExportInProgress) {
            Toast.makeText(
                this,
                "Изображение уже создается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        passportExportInProgress = true
        val passport =
            SourceAuthenticityPassportFactory.create(
                eventPackage
            )
        Toast.makeText(
            this,
            "Создаем паспорт источника…",
            Toast.LENGTH_SHORT
        ).show()
        passportExecutor.execute {
            runCatching {
                val file =
                    SourceAuthenticityPassportExporter(
                        applicationContext
                    ).export(passport)
                AnalysisImageProvider.uriFor(
                    applicationContext,
                    file
                )
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Паспорт источника: " +
                                eventPackage.sourceLabel
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            SourceAuthenticityPassportFactory
                                .shareText(passport)
                        )
                        clipData = ClipData.newRawUri(
                            "Паспорт источника",
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться паспортом"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать паспорт источника",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun eventPackageUpdatePanel(): LinearLayout {
        val current = requireNotNull(importedEventPackage)
        val previous = previousEventPackage
        val delta = eventPackageDelta
        val tone = when {
            previous == null || delta == null ->
                Tone(AppColors.signal, AppColors.signalSoft)
            delta.sourceChanged ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            !delta.hasChanges ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            else ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
        val badgeTitle = when {
            previous == null || delta == null ->
                "БАЗОВАЯ ВЕРСИЯ"
            delta.sourceChanged ->
                "СМЕНА ИСТОЧНИКА"
            !delta.hasChanges ->
                "БЕЗ ИЗМЕНЕНИЙ"
            else ->
                "ИЗМЕНЕНИЙ • ${delta.changeCount}"
        }
        val status = when {
            previous == null || delta == null ->
                "Зафиксирована исходная метка ${current.shortFingerprint}. Следующая версия будет сравнена с этой точкой."
            delta.sourceChanged ->
                "Источник изменен: «${previous.sourceLabel}» → «${current.sourceLabel}». Каталог учтен как полная замена."
            !delta.hasChanges ->
                "События совпадают. Изменились только версия файла или служебные поля пакета."
            else ->
                packageDeltaSummary(delta)
        }

        return card().apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(
                            "Радар обновлений",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        label(
                            badgeTitle,
                            tone.background,
                            tone.foreground
                        )
                    )
                }
            )
            addView(
                text(
                    status,
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ).apply {
                    background = rounded(tone.background, 8)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                },
                matchWrap(top = 11)
            )
            if (previous == null || delta == null) {
                addView(
                    text(
                        "Выпуск ${formatPackageDate(current.generatedAt)} • пакет ${current.packageId}",
                        12f,
                        AppColors.muted
                    ),
                    matchWrap(top = 9)
                )
            } else {
                addView(
                    text(
                        "Метки ${previous.shortFingerprint} → ${current.shortFingerprint}\nВыпуск ${formatPackageDate(previous.generatedAt)} → ${formatPackageDate(current.generatedAt)}",
                        12f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 9)
                )
                if (delta.trustChanged) {
                    addView(
                        text(
                            "Контур подлинности: ${
                                packageTrustShort(previous)
                            } → ${packageTrustShort(current)}",
                            12f,
                            AppColors.signal,
                            Typeface.BOLD
                        ).apply {
                            background = rounded(
                                AppColors.signalSoft,
                                8
                            )
                            setPadding(
                                dp(12),
                                dp(9),
                                dp(12),
                                dp(9)
                            )
                        },
                        matchWrap(top = 9)
                    )
                }
                if (delta.changes.isNotEmpty()) {
                    val visibleChanges = if (updateRadarExpanded) {
                        delta.changes
                    } else {
                        delta.changes.take(
                            COLLAPSED_PACKAGE_CHANGES
                        )
                    }
                    visibleChanges.forEachIndexed { index, change ->
                        addView(
                            divider(),
                            matchFixed(
                                1,
                                top = if (index == 0) 13 else 10,
                                bottom = 10
                            )
                        )
                        addView(packageChangeRow(change))
                    }
                    if (
                        delta.changes.size >
                        COLLAPSED_PACKAGE_CHANGES
                    ) {
                        addView(
                            outlineButton(
                                if (updateRadarExpanded) {
                                    "Свернуть изменения"
                                } else {
                                    "Показать все • ${delta.changes.size}"
                                },
                                AppColors.signal
                            ) {
                                updateRadarExpanded =
                                    !updateRadarExpanded
                                renderContent()
                            },
                            matchWrap(top = 12)
                        )
                    }
                }
            }
        }
    }

    private fun packageDeltaSummary(
        delta: EventPackageDelta
    ): String {
        val parts = buildList {
            if (delta.addedCount > 0) {
                add("Новые: ${delta.addedCount}")
            }
            if (delta.removedCount > 0) {
                add("Удалены: ${delta.removedCount}")
            }
            if (delta.rescheduledCount > 0) {
                add("Переносы: ${delta.rescheduledCount}")
            }
            if (delta.assessmentChangedCount > 0) {
                add("Оценки: ${delta.assessmentChangedCount}")
            }
            if (delta.detailsChangedCount > 0) {
                add("Уточнения: ${delta.detailsChangedCount}")
            }
            if (delta.trustChanged) {
                add("Подлинность: обновлена")
            }
        }
        return parts.joinToString(" • ")
    }

    private fun packageTrustShort(
        eventPackage: SportEventPackage
    ): String {
        return when (
            eventPackage.authenticity.keyEnvironment
        ) {
            EventPackageKeyEnvironment.PRODUCTION ->
                "подписан"
            EventPackageKeyEnvironment.DEVELOPMENT ->
                "демо-ключ"
            null ->
                "без подписи"
        }
    }

    private fun packageChangeRow(
        change: EventPackageEventChange
    ): LinearLayout {
        val tone = packageChangeTone(change)
        val badgeTitle = when {
            change.presence == EventPresenceChange.ADDED ->
                "НОВОЕ"
            change.presence == EventPresenceChange.REMOVED ->
                "УДАЛЕНО"
            change.isRescheduled ->
                "ПЕРЕНОС"
            change.assessmentChanges.isNotEmpty() ->
                "ОЦЕНКИ"
            else ->
                "УТОЧНЕНО"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    addView(
                        label(
                            badgeTitle,
                            tone.background,
                            tone.foreground
                        ),
                        wrapWrap(right = 9)
                    )
                    addView(
                        text(
                            change.match,
                            14f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                }
            )
            addView(
                text(
                    packageChangeDescription(change),
                    12f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )
        }
    }

    private fun packageChangeTone(
        change: EventPackageEventChange
    ): Tone {
        return when {
            change.presence == EventPresenceChange.ADDED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            change.presence == EventPresenceChange.REMOVED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            change.isRescheduled ->
                Tone(AppColors.warning, AppColors.warningSoft)
            change.assessmentChanges.isNotEmpty() ->
                Tone(AppColors.signal, AppColors.signalSoft)
            else ->
                Tone(AppColors.muted, AppColors.background)
        }
    }

    private fun packageChangeDescription(
        change: EventPackageEventChange
    ): String {
        if (change.presence == EventPresenceChange.ADDED) {
            return "Добавлено • ${
                formatPackageDate(
                    requireNotNull(change.currentStartAt)
                )
            }"
        }
        if (change.presence == EventPresenceChange.REMOVED) {
            return "Больше не входит в пакет • было ${
                formatPackageDate(
                    requireNotNull(change.previousStartAt)
                )
            }"
        }
        val parts = buildList {
            if (change.isRescheduled) {
                add(
                    "Время ${formatPackageDate(requireNotNull(change.previousStartAt))} → ${
                        formatPackageDate(
                            requireNotNull(change.currentStartAt)
                        )
                    }"
                )
            }
            if (change.assessmentChanges.isNotEmpty()) {
                add(
                    "Оценки ${
                        change.assessmentChanges.joinToString(
                            ", "
                        ) { assessmentChange ->
                            val signedDelta = if (
                                assessmentChange.delta > 0
                            ) {
                                "+${assessmentChange.delta}"
                            } else {
                                assessmentChange.delta.toString()
                            }
                            "${assessmentChange.factor.shortTitle} ${assessmentChange.previousValue}→${assessmentChange.currentValue} ($signedDelta)"
                        }
                    }"
                )
            }
            if (change.detailChanges.isNotEmpty()) {
                add(
                    "Уточнены ${
                        change.detailChanges.joinToString(
                            ", "
                        ) { it.title }
                    }"
                )
            }
        }
        return parts.joinToString("\n")
    }

    private fun filterBar(): HorizontalScrollView {
        val filterState = SportFilterPolicy.evaluate(
            catalogSports = catalogEvents.map(SportEvent::sport),
            catalogEventIds = catalogEvents.mapTo(linkedSetOf()) {
                it.id
            },
            bookmarkedIds = state.bookmarkedIds(),
            selectedFilter = activeSportFilter,
            savedOnly = savedOnly
        )
        val filters = filterState.filters
        activeSportFilter = filterState.selectedFilter
        savedOnly = filterState.savedOnly
        var selectedChip: View? = null
        val row = AdaptiveWrapLayout(this).apply {
            tag = AdaptiveGroupTags.SPORT_FILTERS
            filters.forEach { filter ->
                val selected =
                    !savedOnly && activeSportFilter == filter
                val chip = filterChip(
                    title = filter,
                    selected = selected
                ) {
                    activeSportFilter = filter
                    savedOnly = false
                    focusEventLimit = FOCUS_EVENT_PAGE_SIZE
                    renderContent()
                }.apply {
                    isSelected = selected
                    contentDescription = if (selected) {
                        "Вид спорта $filter, выбрано"
                    } else {
                        "Фильтр по виду спорта $filter"
                    }
                }
                if (selected) selectedChip = chip
                addView(chip, wrapWrap(right = 7))
            }
            if (filterState.showSavedFilter) {
                val chip = filterChip(
                    title = "★ Сохраненные",
                    selected = savedOnly
                ) {
                    savedOnly = !savedOnly
                    focusEventLimit = FOCUS_EVENT_PAGE_SIZE
                    renderContent()
                }.apply {
                    isSelected = savedOnly
                    contentDescription = if (savedOnly) {
                        "Только сохраненные события, выбрано"
                    } else {
                        "Показать только сохраненные события"
                    }
                }
                if (savedOnly) selectedChip = chip
                addView(chip, wrapWrap())
            }
        }
        return HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(12))
            addView(
                row,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            post {
                selectedChip?.let { chip ->
                    scrollTo(
                        (chip.left - dp(8)).coerceAtLeast(0),
                        0
                    )
                }
            }
        }
    }

    private fun eventSearchPanel(): LinearLayout {
        val matchingCount = visibleFeedEvents(
            state.bookmarkedIds()
        ).size
        val search = SearchView(this).apply {
            isIconifiedByDefault = false
            isSubmitButtonEnabled = true
            queryHint = "Поиск событий"
            maxWidth = Int.MAX_VALUE
            minimumHeight = dp(searchControlHeightDp())
            contentDescription =
                "Поиск по команде, турниру, региону или виду спорта"
            setQuery(eventSearchQuery, false)
            findFirstEditText(this)?.apply {
                layoutParams = layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                filters = arrayOf(
                    InputFilter.LengthFilter(
                        EventSearchPolicy.MAX_QUERY_LENGTH
                    )
                )
            }
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(
                        query: String?
                    ): Boolean {
                        eventSearchQuery = query.orEmpty().trim()
                        focusEventLimit = FOCUS_EVENT_PAGE_SIZE
                        clearFocus()
                        (getSystemService(INPUT_METHOD_SERVICE) as?
                            InputMethodManager)
                            ?.hideSoftInputFromWindow(
                                windowToken,
                                0
                            )
                        rerenderContentPreservingScroll()
                        return true
                    }

                    override fun onQueryTextChange(
                        newText: String?
                    ): Boolean {
                        val previous = eventSearchQuery
                        eventSearchQuery = newText.orEmpty()
                        if (
                            previous.isNotBlank() &&
                            eventSearchQuery.isBlank()
                        ) {
                            focusEventLimit =
                                FOCUS_EVENT_PAGE_SIZE
                            post {
                                rerenderContentPreservingScroll()
                            }
                        }
                        return true
                    }
                }
            )
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                AppColors.surface,
                8,
                AppColors.line,
                1
            )
            setPadding(dp(6), dp(2), dp(6), dp(7))
            addView(search, matchFixed(searchControlHeightDp()))
            if (eventSearchQuery.isNotBlank()) {
                addView(
                    text(
                        "НАЙДЕНО • $matchingCount • ПОРЯДОК КАТАЛОГА",
                        10.5f,
                        AppColors.signal,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 2)
                )
            }
        }
    }

    private fun visibleFeedEvents(
        bookmarks: Set<String>
    ): List<SportEvent> {
        val events = feedEventsBeforeTimeline(bookmarks)
        return FeedTimelinePolicy.filter(
            events = events,
            selected = activeFeedTimeFilter,
            now = System.currentTimeMillis(),
            zoneId = state.selectedRegionalZone.zoneId
        )
    }

    private fun feedEventsBeforeTimeline(
        bookmarks: Set<String>
    ): List<SportEvent> {
        val filtered = catalogEvents.filter { event ->
            matchesFilter(event) &&
                (!savedOnly || event.id in bookmarks)
        }
        return EventSearchPolicy.filter(
            events = filtered,
            query = eventSearchQuery
        )
    }

    private fun eventSearchExplanation(
        event: SportEvent
    ): TextView? {
        val explanation = EventSearchPolicy.explain(
            event = event,
            query = eventSearchQuery
        ) ?: return null
        return text(
            explanation.label,
            10.5f,
            AppColors.signal,
            Typeface.BOLD
        ).apply {
            background = rounded(AppColors.signalSoft, 6)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            contentDescription =
                "Почему найдено событие: ${
                    explanation.fields.joinToString(", ") {
                        it.title.lowercase(Locale.getDefault())
                    }
                }"
        }
    }

    private fun resetFeedFilters() {
        activeSportFilter = "Все"
        savedOnly = false
        eventSearchQuery = ""
        activeFeedTimeFilter = FeedTimelineFilter.ALL
        focusEventLimit = FOCUS_EVENT_PAGE_SIZE
    }

    private fun feedTimelineBar(): LinearLayout {
        val events = feedEventsBeforeTimeline(
            state.bookmarkedIds()
        )
        val summary = FeedTimelinePolicy.summary(
            events = events,
            now = System.currentTimeMillis(),
            zoneId = state.selectedRegionalZone.zoneId
        )
        val filters = FeedTimelineFilter.entries.filter { filter ->
            filter == FeedTimelineFilter.ALL ||
                summary.count(filter) > 0 ||
                filter == activeFeedTimeFilter
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(
                    "КОГДА • ${state.selectedRegionalZone.city.uppercase(Locale.getDefault())}",
                    11f,
                    AppColors.muted,
                    Typeface.BOLD
                )
            )
            var selectedChip: View? = null
            val row = AdaptiveWrapLayout(this@MainActivity).apply {
                tag = AdaptiveGroupTags.TIME_FILTERS
                filters.forEach { filter ->
                    val selected = filter == activeFeedTimeFilter
                    val chip = filterChip(
                        title = "${filter.title} ${summary.count(filter)}",
                        selected = selected
                    ) {
                        activeFeedTimeFilter = filter
                        focusEventLimit = FOCUS_EVENT_PAGE_SIZE
                        rerenderContentPreservingScroll()
                    }.apply {
                        isSelected = selected
                        contentDescription =
                            "Период ${filter.title.lowercase(Locale.getDefault())}: ${eventCountText(summary.count(filter))}"
                    }
                    if (selected) selectedChip = chip
                    addView(chip, wrapWrap(right = 7))
                }
            }
            addView(
                HorizontalScrollView(this@MainActivity).apply {
                    isFillViewport = true
                    isHorizontalScrollBarEnabled = false
                    clipToPadding = false
                    isHorizontalFadingEdgeEnabled = true
                    setFadingEdgeLength(dp(12))
                    addView(
                        row,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                    post {
                        selectedChip?.let { chip ->
                            scrollTo(
                                (chip.left - dp(8)).coerceAtLeast(0),
                                0
                            )
                        }
                    }
                },
                matchWrap(top = 7)
            )
        }
    }

    private fun feedTimelineExplanationBand(
        event: SportEvent,
        now: Long,
        alwaysForVerification: Boolean = false
    ): LinearLayout? {
        val explanation = FeedTimelinePolicy.explanation(
            event = event,
            now = now,
            zoneId = state.selectedRegionalZone.zoneId
        )
        val visible = explanation.filter == activeFeedTimeFilter ||
            (
                alwaysForVerification &&
                    explanation.filter == FeedTimelineFilter.VERIFY
                )
        if (!visible) return null
        val tone = feedTimelineExplanationTone(explanation.reason)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tone.background, 8)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            addView(
                text(
                    "ПОЧЕМУ ЗДЕСЬ • ${explanation.badge}",
                    10.5f,
                    tone.foreground,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    explanation.body,
                    12.5f,
                    AppColors.ink
                ),
                matchWrap(top = 3)
            )
            contentDescription =
                "Почему событие в группе ${explanation.filter.title}: " +
                    "${explanation.badge}. ${explanation.body}"
        }
    }

    private fun feedTimelineExplanationTone(
        reason: FeedTimelineReason
    ): Tone {
        return when (reason) {
            FeedTimelineReason.CANCELLED,
            FeedTimelineReason.ABANDONED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            FeedTimelineReason.POSTPONED,
            FeedTimelineReason.SUSPENDED,
            FeedTimelineReason.INTERRUPTED,
            FeedTimelineReason.AWARDED,
            FeedTimelineReason.WALKOVER,
            FeedTimelineReason.PAST_WITHOUT_FINAL_STATUS,
            FeedTimelineReason.UNKNOWN_START ->
                Tone(AppColors.warning, AppColors.warningSoft)
            FeedTimelineReason.LIVE_STATUS,
            FeedTimelineReason.FINAL_STATUS ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            FeedTimelineReason.TODAY_DATE,
            FeedTimelineReason.TOMORROW_DATE,
            FeedTimelineReason.LATER_DATE ->
                Tone(AppColors.signal, AppColors.signalSoft)
        }
    }

    private fun matchesFilter(event: SportEvent): Boolean {
        return when (activeSportFilter) {
            "Все" -> true
            "Другие" -> event.sport !in setOf("Футбол", "Хоккей", "Баскетбол", "Киберспорт")
            else -> event.sport == activeSportFilter
        }
    }

    private fun filterChip(
        title: String,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView {
        return text(
            title,
            13f,
            if (selected) Color.WHITE else AppColors.ink,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(14), 0, dp(14), 0)
            background = rippleRounded(
                if (selected) AppColors.signal else AppColors.surface,
                24,
                if (selected) AppColors.signal else AppColors.line,
                1
            )
            applyAccessibleAction(dp(48))
            isSelected = selected
            setOnClickListener { onClick() }
        }
    }

    private fun eventCard(event: SportEvent, initiallySaved: Boolean): LinearLayout {
        val now = System.currentTimeMillis()
        val displayedTime = TimeBridgeEngine.formatEventTime(
            event = event,
            selectedZone = state.selectedRegionalZone,
            referenceMillis = now
        )
        val story = eventStoryResult(event = event, now = now)
        val storyTone = eventStoryTone(story.phase)
        val card = card(padding = 0).apply {
            clipToOutline = true
        }
        val visual = FrameLayout(this).apply {
            background = rounded(AppColors.ink, 8)
            clipToOutline = true
        }
        visual.addView(
            ImageView(this).apply {
                setImageResource(event.imageRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Иллюстрация события: ${event.match}"
            },
            frameMatch()
        )
        visual.addView(
            View(this).apply { background = gradientScrim(compact = true) },
            frameMatch()
        )
        visual.addView(
            label(event.sport.uppercase(Locale.getDefault()), AppColors.signal, Color.WHITE),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(12)
                topMargin = dp(12)
            }
        )
        val bookmark = text(
            if (initiallySaved) "★" else "☆",
            fixedControlTextSize(24f),
            Color.WHITE,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            background = rippleRounded(
                Color.argb(185, 14, 20, 24),
                24
            )
            applyAccessibleAction(dp(48))
            contentDescription = if (initiallySaved) {
                "Убрать событие из сохраненных"
            } else {
                "Сохранить событие"
            }
        }
        bookmark.setOnClickListener {
            val saved = state.toggleBookmark(event.id)
            refreshHeroStatus()
            bookmark.text = if (saved) "★" else "☆"
            bookmark.contentDescription = if (saved) {
                "Убрать событие из сохраненных"
            } else {
                "Сохранить событие"
            }
            Toast.makeText(
                this,
                if (saved) "Событие сохранено" else "Событие удалено из сохраненных",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
        }
        visual.addView(
            bookmark,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                rightMargin = dp(10)
                topMargin = dp(8)
            }
        )
        visual.addView(
            text(event.tournament, 13f, Color.WHITE, Typeface.BOLD),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(12)
            }
        )
        card.addView(visual, matchFixed(164))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(16))
        }
        body.addView(text(event.match, 21f, AppColors.ink, Typeface.BOLD))
        eventSearchExplanation(event)?.let { explanation ->
            body.addView(explanation, matchWrap(top = 7))
        }
        body.addView(
            text(
                displayedTime,
                14f,
                AppColors.accent,
                Typeface.BOLD
            ),
            matchWrap(top = 5)
        )
        feedTimelineExplanationBand(
            event = event,
            now = now
        )?.let { explanation ->
            body.addView(explanation, matchWrap(top = 8))
        }
        body.addView(
            text(event.region, 12f, AppColors.muted, Typeface.BOLD),
            matchWrap(top = 5)
        )
        body.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    label(
                        "ГЛАВА ${story.currentChapterNumber}/6",
                        storyTone.background,
                        storyTone.foreground
                    ),
                    wrapWrap(right = 8)
                )
                addView(
                    text(
                        eventStoryChapterTitle(
                            story.currentChapter
                        ),
                        13f,
                        storyTone.foreground,
                        Typeface.BOLD
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            },
            matchWrap(top = 12)
        )
        body.addView(
            text(
                story.chapter(story.currentChapter).summary,
                12.5f,
                AppColors.muted
            ),
            matchWrap(top = 5)
        )
        body.addView(divider(), matchFixed(1, top = 12, bottom = 11))
        body.addView(text("ФОКУС", 11f, AppColors.muted, Typeface.BOLD))
        body.addView(text(event.focus, 15f, AppColors.ink, Typeface.BOLD), matchWrap(top = 3))
        body.addView(text(event.note, 14f, AppColors.muted), matchWrap(top = 7))
        body.addView(tagRow(event.tags), matchWrap(top = 11))
        body.addView(
            commandButton(
                if (story.phase == EventStoryPhase.COMPLETE) {
                    "Открыть завершенный сюжет"
                } else {
                    "Продолжить сюжет • ${
                        eventStoryChapterTitle(
                            story.currentChapter
                        )
                    }"
                },
                AppColors.accent
            ) {
                openPulseStory(event.id)
            },
            matchWrap(top = 14)
        )
        card.addView(body, matchWrap())
        return card
    }

    private fun tagRow(tags: List<String>): AdaptiveWrapLayout {
        return AdaptiveWrapLayout(this).apply {
            tag = AdaptiveGroupTags.EVENT_TAGS
            lineSpacingPx = dp(6)
            tags.forEach { tag ->
                addView(
                    label(
                        tag,
                        AppColors.accentSoft,
                        AppColors.accentDark
                    ),
                    wrapWrap(right = 6)
                )
            }
        }
    }

    private fun feedWorkspaceSwitcher(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            background = rounded(
                AppColors.surface,
                8,
                AppColors.line,
                1
            )
            setPadding(dp(4), dp(4), dp(4), dp(4))
            listOf(
                FeedWorkspaceMode.FOCUS to "Список",
                FeedWorkspaceMode.TOOLS to "Инструменты"
            ).forEachIndexed { index, (mode, title) ->
                val selected = mode == activeFeedWorkspaceMode
                addView(
                    text(
                        title,
                        13f,
                        if (selected) Color.WHITE else AppColors.ink,
                        Typeface.BOLD
                    ).apply {
                        gravity = Gravity.CENTER
                        minHeight = dp(48)
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        background = rippleRounded(
                            if (selected) {
                                AppColors.accent
                            } else {
                                AppColors.surface
                            },
                            6
                        )
                        applyAccessibleAction(dp(48))
                        isSelected = selected
                        contentDescription = "Режим раздела Матчи: $title"
                        setOnClickListener {
                            selectFeedWorkspaceMode(mode)
                        }
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        if (index == 0) rightMargin = dp(4)
                    }
                )
            }
        }
    }

    private fun selectFeedWorkspaceMode(
        mode: FeedWorkspaceMode
    ) {
        if (mode == activeFeedWorkspaceMode) return
        activeFeedWorkspaceMode = mode
        state.selectedFeedWorkspaceMode = mode
        renderContent()
        mainScroll.post {
            mainScroll.smoothScrollTo(
                0,
                content.top.coerceAtLeast(0)
            )
        }
    }

    private fun renderFeedFocusMode() {
        val bookmarks = state.bookmarkedIds()
        val visibleEvents = visibleFeedEvents(bookmarks)
        val now = System.currentTimeMillis()
        val orderedEvents = FeedFocusPolicy.order(
            events = visibleEvents,
            now = now
        )
        val focusCandidates = FeedFocusPolicy.focusScope(
            events = orderedEvents,
            bookmarkedIds = bookmarks,
            now = now
        )
        val dispatch = eventDispatchResult(
            events = focusCandidates,
            bookmarks = bookmarks,
            now = now
        )
        val leadEntry = dispatch.entries.firstOrNull()
        val leadEvent = leadEntry?.let { entry ->
            orderedEvents.firstOrNull { it.id == entry.eventId }
        }

        val selection = MatchCenterPolicy.select(
            events = orderedEvents,
            leadEventId = leadEntry?.eventId,
            visibleCount = focusEventLimit
        )
        content.addView(
            matchCenterPanel(
                selection = selection,
                bookmarks = bookmarks,
                leadEntry = leadEntry,
                now = now,
                totalEventCount = visibleEvents.size
            ),
            matchWrap(top = 6)
        )
        if (selection.hiddenCount > 0) {
            val nextPage = min(
                FOCUS_EVENT_PAGE_SIZE,
                selection.hiddenCount
            )
            content.addView(
                outlineButton(
                    "Показать ещё $nextPage",
                    AppColors.signal
                ) {
                    focusEventLimit += FOCUS_EVENT_PAGE_SIZE
                    rerenderContentPreservingScroll()
                },
                matchWrap(top = 12)
            )
        }
        if (leadEntry != null && leadEvent != null) {
            content.addView(
                feedFocusLanePanel(
                    entry = leadEntry,
                    event = leadEvent,
                    dispatch = dispatch,
                    totalEventCount = visibleEvents.size,
                    now = now
                ),
                matchWrap(top = 12)
            )
        }
        content.addView(
            feedFocusSourcePanel(now),
            matchWrap(top = 12)
        )
    }

    private fun feedFocusLanePanel(
        entry: EventDispatchEntry,
        event: SportEvent,
        dispatch: EventDispatchResult,
        totalEventCount: Int,
        now: Long
    ): LinearLayout {
        val task = entry.primaryTask
        val tone = verificationCommandPriorityTone(
            task.priority
        )
        return card(padding = 0).apply {
            clipToOutline = true
            addView(
                feedFocusLaneHeader(),
                matchFixed(imageHeaderHeight(104))
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(16))
                    addView(
                        label(
                            "${task.priority.shortTitle} • 1 ИЗ ${dispatch.entries.size}",
                            tone.background,
                            tone.foreground
                        )
                    )
                    addView(
                        text(
                            task.title,
                            17f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 9)
                    )
                    addView(
                        text(
                            "${event.match}. ${task.reason}",
                            13.5f,
                            AppColors.muted
                        ),
                        matchWrap(top = 5)
                    )
                    addView(
                        text(
                            feedFocusDeadline(task, now),
                            12f,
                            tone.foreground,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 7)
                    )
                    addView(
                        commandButton(
                            "Открыть короткий разбор",
                            AppColors.accent
                        ) {
                            openPulseStory(event.id)
                        },
                        matchWrap(top = 13)
                    )
                    addView(
                        text(
                            feedFocusReason(task.priority) +
                                " • выбрано ${dispatch.entries.size} из $totalEventCount" +
                                " • метка ${dispatch.shortFingerprint}",
                            10.5f,
                            AppColors.muted,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 9)
                    )
                },
                matchWrap()
            )
        }
    }

    private fun feedFocusLaneHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.feed_focus_lane)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Операторская линия ведёт к одному следующему моменту проверки"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ОДИН СЛЕДУЮЩИЙ ХОД",
                            10.5f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "С чего начать",
                            20f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(14)
                    rightMargin = dp(14)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun feedFocusDeadline(
        task: VerificationCommandTask,
        now: Long
    ): String {
        val dueAt = task.dueAt ?: return "Срок: без жёсткой границы"
        return if (dueAt <= now) {
            "Срок: проверить сейчас"
        } else {
            "Срок: через ${FreshnessFormatter.duration(dueAt - now)}"
        }
    }

    private fun feedFocusReason(
        priority: VerificationCommandPriority
    ): String {
        return when (priority) {
            VerificationCommandPriority.STOP ->
                "Выбран первым из-за стоп-причины"
            VerificationCommandPriority.REPAIR ->
                "Выбран первым из-за повреждённого факта"
            VerificationCommandPriority.REFRESH ->
                "Выбран первым по ближайшей потере свежести"
            VerificationCommandPriority.CHALLENGE ->
                "Выбран первым по открытой проверке"
            VerificationCommandPriority.UNBLOCK ->
                "Выбран первым по кратчайшему маршруту"
            VerificationCommandPriority.MAINTAIN ->
                "Выбран первым для планового контроля"
        }
    }

    private fun feedFocusSourcePanel(now: Long): LinearLayout {
        val activePackage = importedEventPackage?.takeUnless {
            it.isExpired(now)
        }
        val apiActive =
            activeCatalogOrigin == SportEventOrigin.API_SPORTS
        val readiness = sourceReadiness(now)
        val tone = sourceReadinessTone(readiness.level)
        val badge = readiness.badge
        val title = readiness.verdict
        val body = readiness.summary
        val stackHeader =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        return card().apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackHeader) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackHeader) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        text(
                            "Источник",
                            19f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stackHeader) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                    )
                    addView(
                        label(
                            badge,
                            tone.background,
                            tone.foreground
                        ),
                        if (stackHeader) {
                            wrapWrap(bottom = 2).apply {
                                topMargin = dp(8)
                            }
                        } else {
                            wrapWrap()
                        }
                    )
                }
            )
            addView(
                text(
                    title,
                    15f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 9)
            )
            addView(text(body, 12.5f, AppColors.muted), matchWrap(top = 4))
            addView(
                text(
                    "Источник: ${readiness.checks[0].value}\n" +
                        "Свежесть: ${readiness.checks[1].value}",
                    12f,
                    tone.foreground,
                    Typeface.BOLD
                ).apply {
                    background = rounded(tone.background, 8)
                    setPadding(dp(11), dp(9), dp(11), dp(9))
                },
                matchWrap(top = 9)
            )
            if (apiActive) {
                apiFootballDelta?.takeIf {
                    it.changes.isNotEmpty()
                }?.let { delta ->
                    addView(
                        text(
                            apiUpdatePulseSummary(delta),
                            12.5f,
                            AppColors.signal,
                            Typeface.BOLD
                        ).apply {
                            background = rounded(
                                AppColors.signalSoft,
                                8
                            )
                            setPadding(
                                dp(11),
                                dp(9),
                                dp(11),
                                dp(9)
                            )
                        },
                        matchWrap(top = 10)
                    )
                    addView(
                        outlineButton(
                            "Открыть изменения • ${delta.changes.size}",
                            AppColors.signal
                        ) {
                            apiUpdatePulseExpanded = false
                            selectFeedWorkspaceMode(
                                FeedWorkspaceMode.TOOLS
                            )
                        },
                        matchWrap(top = 8)
                    )
                }
            }
            if (activePackage == null) {
                if (apiFootballConfigured()) {
                    addView(
                        outlineButton(
                            if (apiFootballRefreshInProgress) {
                                "Обновляем расписание..."
                            } else {
                                "Обновить расписание"
                            },
                            AppColors.accent
                        ) {
                            refreshApiFootball(force = true)
                        }.apply {
                            isEnabled =
                                !apiFootballRefreshInProgress
                        },
                        matchWrap(top = 10)
                    )
                } else {
                    addView(
                        text(
                            "Онлайн-расписание не подключено",
                            12.5f,
                            AppColors.muted,
                            Typeface.BOLD
                        ).apply {
                            background = rounded(
                                AppColors.background,
                                8
                            )
                            setPadding(
                                dp(12),
                                dp(10),
                                dp(12),
                                dp(10)
                            )
                        },
                        matchWrap(top = 10)
                    )
                }
            }
            addView(
                outlineButton(
                    "Проверить источник и свежесть",
                    AppColors.muted
                ) {
                    selectFeedWorkspaceMode(
                        FeedWorkspaceMode.TOOLS
                    )
                },
                matchWrap(top = 8)
            )
        }
    }

    private fun matchCenterPanel(
        selection: MatchCenterSelection,
        bookmarks: Set<String>,
        leadEntry: EventDispatchEntry?,
        now: Long,
        totalEventCount: Int
    ): LinearLayout {
        val scopeTitle = when {
            savedOnly -> "СОХРАНЁННЫЕ • $totalEventCount"
            activeSportFilter == "Все" ->
                "СОБЫТИЯ • $totalEventCount"
            else ->
                "${activeSportFilter.uppercase(Locale.getDefault())} • $totalEventCount"
        }
        val stackHeader =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        return card(padding = 0).apply {
            clipToOutline = true
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackHeader) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackHeader) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    setPadding(dp(14), dp(13), dp(14), dp(11))
                    addView(
                        text(
                            "Ближайшие матчи",
                            18f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stackHeader) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(8)
                            }
                        }
                    )
                    addView(
                        text(
                            scopeTitle,
                            11f,
                            AppColors.muted,
                            Typeface.BOLD
                        ),
                        if (stackHeader) {
                            matchWrap(top = 5)
                        } else {
                            wrapWrap()
                        }
                    )
                }
            )
            addView(divider(), matchFixed(1))
            if (selection.events.isEmpty()) {
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(16), dp(18), dp(16), dp(18))
                        addView(
                            label(
                                feedEmptyBadge(),
                                AppColors.background,
                                AppColors.muted
                            )
                        )
                        addView(
                            text(
                                feedEmptyTitle(),
                                20f,
                                AppColors.ink,
                                Typeface.BOLD
                            ),
                            matchWrap(top = 10)
                        )
                        addView(
                            text(
                                feedEmptyDescription(),
                                13.5f,
                                AppColors.muted
                            ),
                            matchWrap(top = 5)
                        )
                        addView(
                            outlineButton(
                                feedEmptyActionTitle(),
                                AppColors.signal
                            ) {
                                resetEmptyFeedScope()
                                renderContent()
                            },
                            matchWrap(top = 12)
                        )
                    },
                    matchWrap()
                )
                return@apply
            }
            selection.events.forEachIndexed { index, event ->
                addView(
                    matchCenterEventRow(
                        event = event,
                        initiallySaved = event.id in bookmarks,
                        leadTask = leadEntry
                            ?.takeIf { it.eventId == event.id }
                            ?.primaryTask,
                        now = now
                    ),
                    matchWrap()
                )
                if (index != selection.events.lastIndex) {
                    addView(divider(), matchFixed(1))
                }
            }
        }
    }

    private fun matchCenterEventRow(
        event: SportEvent,
        initiallySaved: Boolean,
        leadTask: VerificationCommandTask?,
        now: Long
    ): LinearLayout {
        val displayedTime = TimeBridgeEngine.formatEventTime(
            event = event,
            selectedZone = state.selectedRegionalZone,
            referenceMillis = now
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(104)
            setPadding(dp(14), dp(11), dp(14), dp(12))
            background = rippleRounded(
                if (leadTask == null) {
                    AppColors.surface
                } else {
                    AppColors.signalSoft
                },
                0
            )
            applyAccessibleAction(dp(48))
            contentDescription = buildString {
                append("Открыть анализ: ${event.match}. ")
                append("$displayedTime. ${event.tournament}, ${event.region}.")
                leadTask?.let {
                    append(" Первая проверка: ${it.title}.")
                }
            }
            setOnClickListener { openPulseStory(event.id) }
        }
        val bookmark = text(
            if (initiallySaved) "★" else "☆",
            fixedControlTextSize(21f),
            AppColors.signal,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            background = rippleRounded(AppColors.background, 24)
            applyAccessibleAction(dp(48))
            contentDescription = if (initiallySaved) {
                "Убрать событие из сохраненных"
            } else {
                "Сохранить событие"
            }
            setOnClickListener {
                state.toggleBookmark(event.id)
                refreshHeroStatus()
                rerenderContentPreservingScroll()
            }
        }
        row.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    label(
                        event.providerStatus
                            ?: event.sport.uppercase(
                                Locale.getDefault()
                            ),
                        if (leadTask == null) {
                            AppColors.accentSoft
                        } else {
                            AppColors.surface
                        },
                        AppColors.accentDark
                    ),
                    wrapWrap(right = 8)
                )
                addView(
                    View(this@MainActivity),
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                    )
                )
                addView(
                    bookmark,
                    LinearLayout.LayoutParams(dp(48), dp(48))
                )
            }
        )
        row.addView(
            text(
                event.match,
                16.5f,
                AppColors.ink,
                Typeface.BOLD
            ),
            matchWrap(top = 6)
        )
        eventSearchExplanation(event)?.let { explanation ->
            row.addView(explanation, matchWrap(top = 6))
        }
        row.addView(
            matchCenterMetadata(
                displayedTime = displayedTime,
                event = event
            ),
            matchWrap(top = 4)
        )
        feedTimelineExplanationBand(
            event = event,
            now = now
        )?.let { explanation ->
            row.addView(explanation, matchWrap(top = 7))
        }
        leadTask?.let { task ->
            row.addView(
                text(
                    "ПЕРВАЯ ПРОВЕРКА • ${task.title}",
                    11.5f,
                    AppColors.signal,
                    Typeface.BOLD
                ),
                matchWrap(top = 7)
            )
        }
        row.addView(
            text(
                "Открыть анализ ›",
                12.5f,
                AppColors.signal,
                Typeface.BOLD
            ),
            matchWrap(top = 7)
        )
        return row
    }

    private fun matchCenterMetadata(
        displayedTime: String,
        event: SportEvent
    ): TextView {
        val value = "$displayedTime • ${event.tournament} • ${event.region}"
        val styled = SpannableString(value).apply {
            setSpan(
                ForegroundColorSpan(AppColors.accentDark),
                0,
                displayedTime.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                displayedTime.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return text(
            value,
            12f,
            AppColors.muted
        ).apply {
            text = styled
        }
    }

    private fun feedGettingStartedPanel(): LinearLayout {
        val stack =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        return card().apply {
            orientation = if (stack) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            gravity = if (stack) {
                Gravity.START
            } else {
                Gravity.CENTER_VERTICAL
            }
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "БЫСТРЫЙ СТАРТ",
                            10.5f,
                            AppColors.signal,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Событие → карта данных → факт → решение",
                            13f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 3)
                    )
                },
                if (stack) {
                    matchWrap()
                } else {
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        rightMargin = dp(12)
                    }
                }
            )
            addView(
                outlineButton(
                    "Обучение",
                    AppColors.signal
                ) {
                    showProductTour()
                },
                if (stack) {
                    matchWrap(top = 10)
                } else {
                    LinearLayout.LayoutParams(
                        dp(132),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
            )
        }
    }

    private fun plainAnalyticsPanel(event: SportEvent): LinearLayout {
        if (plainAnalyticsProtocolEventId != event.id) {
            plainAnalyticsProtocolExpanded = false
            plainAnalyticsProtocolEventId = event.id
        }
        val now = System.currentTimeMillis()
        val evidence = state.evidence(event)
        val timeline = state.evidenceTimelinePreview(
            eventId = event.id,
            now = now
        )
        val result = PlainAnalyticsEngine.evaluate(
            assessment = state.assessment(event),
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val effectiveLevel = FreshnessEngine.evaluate(
            evidence = evidence,
            timeline = timeline,
            now = now
        ).effectiveEvidence.level(result.actionFactor)
        val recipe = VerificationRecipeEngine.create(
            factor = result.actionFactor,
            evidenceLevel = effectiveLevel
        )
        val receiptReads = SignalFactor.values().associateWith { factor ->
            state.factReceipt(event.id, factor)
        }
        val factRegister = FactRegisterEngine.create(
            eventId = event.id,
            reads = receiptReads,
            now = now
        )
        val currentReceipt = checkNotNull(
            receiptReads[result.actionFactor]
        )
        val tone = plainAnalyticsTone(result.status)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                plainAnalyticsBoard(result, tone),
                matchWrap()
            )
            addView(
                plainAnalyticsProtocolPanel(
                    event = event,
                    result = result,
                    recipe = recipe,
                    currentReceipt = currentReceipt,
                    factRegister = factRegister,
                    tone = tone
                ),
                matchWrap(top = 12)
            )
        }
    }

    private fun plainAnalyticsBoard(
        result: PlainAnalyticsResult,
        tone: Tone
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(AppColors.field, 8)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(
                label(
                    when (result.status) {
                        PlainAnalyticsStatus.STOP ->
                            "СТОП • НЕ ХВАТАЕТ ФАКТОВ"
                        PlainAnalyticsStatus.CHECK ->
                            "НУЖНА ПРОВЕРКА"
                        PlainAnalyticsStatus.READY ->
                            "ДАННЫЕ СОБРАНЫ"
                    },
                    tone.foreground,
                    Color.WHITE,
                    tone.foreground
                )
            )
            addView(
                text(
                    result.headline,
                    26f,
                    Color.WHITE,
                    Typeface.BOLD
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    "ТАБЛО ДОКАЗАТЕЛЬСТВ • ПЯТЬ ФАКТОРОВ",
                    10.5f,
                    AppColors.fieldMuted,
                    Typeface.BOLD
                ),
                matchWrap(top = 7)
            )
            addView(
                plainAnalyticsScoreboard(result),
                matchWrap(top = 9)
            )
            addView(
                EvidenceRailView(this@MainActivity).apply {
                    submit(result.factorSummaries)
                },
                matchFixed(96, top = 9)
            )
            addView(
                plainAnalyticsFactorMap(result),
                matchWrap(top = 2)
            )
            addView(
                plainAnalyticsBoardNote(
                    title = "ГЛАВНЫЙ ПРОБЕЛ",
                    body = result.gapSummary,
                    color = Color.rgb(246, 152, 164),
                    highlighted = false
                ),
                matchWrap(top = 12)
            )
            addView(
                plainAnalyticsBoardNote(
                    title = "СЛЕДУЮЩИЙ ХОД",
                    body = result.actionSummary,
                    color = AppColors.fieldSignal,
                    highlighted = true
                ),
                matchWrap(top = 10)
            )
        }
    }

    private fun plainAnalyticsScoreboard(
        result: PlainAnalyticsResult
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(
                plainAnalyticsScoreMetric(
                    value = "%02d/%02d".format(
                        Locale.ROOT,
                        result.confirmedFactorCount,
                        result.totalFactorCount
                    ),
                    label = "С ИСТОЧНИКОМ"
                ),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            addView(
                View(this@MainActivity).apply {
                    setBackgroundColor(AppColors.fieldLine)
                },
                LinearLayout.LayoutParams(dp(1), dp(58)).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                }
            )
            addView(
                plainAnalyticsScoreMetric(
                    value = "%02d/%02d".format(
                        Locale.ROOT,
                        result.independentlyVerifiedCount,
                        result.totalFactorCount
                    ),
                    label = "СВЕРЕНО НЕЗАВИСИМО"
                ),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun plainAnalyticsScoreMetric(
        value: String,
        label: String
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(
                    value,
                    25f,
                    Color.WHITE,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    label,
                    10.5f,
                    AppColors.fieldMuted,
                    Typeface.BOLD
                ),
                matchWrap(top = 2)
            )
        }
    }

    private fun plainAnalyticsBoardNote(
        title: String,
        body: String,
        color: Int,
        highlighted: Boolean
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (highlighted) {
                background = rounded(AppColors.fieldRaised, 6)
                setPadding(dp(12), dp(11), dp(12), dp(11))
            } else {
                setPadding(0, dp(4), 0, dp(4))
            }
            addView(text(title, 10.5f, color, Typeface.BOLD))
            addView(
                text(body, 14f, Color.WHITE, Typeface.BOLD),
                matchWrap(top = 4)
            )
        }
    }

    private fun plainAnalyticsProtocolPanel(
        event: SportEvent,
        result: PlainAnalyticsResult,
        recipe: VerificationRecipe,
        currentReceipt: FactReceiptReadResult,
        factRegister: FactRegister,
        tone: Tone
    ): LinearLayout {
        return card().apply {
            addView(
                label(
                    "ПРОТОКОЛ • ${recipe.factor.title.uppercase(
                        Locale.getDefault()
                    )}",
                    tone.background,
                    tone.foreground
                )
            )
            addView(
                text(
                    "Одна проверка до ясности",
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    recipe.question,
                    14f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    "ГОТОВО, КОГДА\n${recipe.completionRule}",
                    12.5f,
                    tone.foreground,
                    Typeface.BOLD
                ).apply {
                    background = rounded(tone.background, 6)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                },
                matchWrap(top = 11)
            )
            addView(
                commandButton(
                    when (currentReceipt.integrity) {
                        FactReceiptIntegrity.EMPTY ->
                            "Записать факт и источники"
                        FactReceiptIntegrity.VALID ->
                            "Обновить факт-квитанцию"
                        FactReceiptIntegrity.TAMPERED ->
                            "Пересобрать факт-квитанцию"
                    },
                    tone.foreground
                ) {
                    showFactReceiptDialog(
                        event = event,
                        factor = result.actionFactor
                    )
                }.apply {
                    contentDescription =
                        "Факт-квитанция фактора ${result.actionFactor.title}"
                },
                matchWrap(top = 12)
            )
            addView(
                outlineButton(
                    if (plainAnalyticsProtocolExpanded) {
                        "Свернуть шаги проверки"
                    } else {
                        "Показать ${recipe.steps.size} шага проверки"
                    },
                    tone.foreground
                ) {
                    plainAnalyticsProtocolExpanded =
                        !plainAnalyticsProtocolExpanded
                    rerenderContentPreservingScroll()
                }.apply {
                    contentDescription = if (
                        plainAnalyticsProtocolExpanded
                    ) {
                        "Свернуть подробные шаги протокола"
                    } else {
                        "Открыть подробные шаги протокола"
                    }
                },
                matchWrap(top = 8)
            )
            if (plainAnalyticsProtocolExpanded) {
                addView(
                    divider(),
                    matchFixed(1, top = 14, bottom = 12)
                )
                addView(
                    text(
                        "Три шага проверки",
                        17f,
                        AppColors.ink,
                        Typeface.BOLD
                    )
                )
                recipe.steps.forEachIndexed { index, step ->
                    addView(
                        verificationRecipeStepRow(
                            number = index + 1,
                            step = step,
                            tone = tone
                        ),
                        matchWrap(top = 10)
                    )
                }
                addView(
                    outlineButton(
                        "Открыть расширенную форму: ${result.actionFactor.title}",
                        tone.foreground
                    ) {
                        openPulseFactor(result.actionFactor)
                    },
                    matchWrap(top = 12)
                )
                addView(
                    text(
                        "ПРОТОКОЛ ${recipe.shortFingerprint} • БЕЗ КОЭФФИЦИЕНТОВ",
                        10.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 9)
                )
            } else {
                addView(
                    text(
                        "${recipe.steps.size} ШАГА СВЁРНУТЫ • РАСЧЁТ НЕ МЕНЯЕТСЯ",
                        10.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 9)
                )
            }
            addView(
                factReceiptProgressBand(factRegister),
                matchWrap(top = 12)
            )
            addView(
                outlineButton(
                    "Открыть реестр фактов",
                    factRegisterTone(factRegister.status).foreground
                ) {
                    showFactRegisterDialog(event)
                }.apply {
                    contentDescription =
                        "Реестр пяти факт-квитанций события"
                },
                matchWrap(top = 8)
            )
        }
    }

    private fun verificationRecipeStepRow(
        number: Int,
        step: VerificationRecipeStep,
        tone: Tone
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(
                text(
                    number.toString(),
                    fixedControlTextSize(14f),
                    Color.WHITE,
                    Typeface.BOLD
                ).apply {
                    gravity = Gravity.CENTER
                    background = rounded(tone.foreground, 16)
                },
                LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    rightMargin = dp(10)
                }
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            step.title,
                            13.5f,
                            AppColors.ink,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(step.body, 12.5f, AppColors.muted),
                        matchWrap(top = 3)
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun factReceiptProgressBand(
        register: FactRegister
    ): LinearLayout {
        val tone = factRegisterTone(register.status)
        val damagedCount = register.entries.count {
            it.integrity == FactReceiptIntegrity.TAMPERED
        }
        val conflictCount = register.entries.count {
            it.state == FactRegisterEntryState.CONFLICT
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tone.background, 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                text(
                    "ФАКТ-КВИТАНЦИИ • СРОК ФАКТА",
                    11f,
                    tone.foreground,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    if (register.validCount == 0) {
                        "0 из 5 • начните с одного проверяемого тезиса"
                    } else {
                        "${register.validCount} из 5 • независимо сверено: ${register.quorumCount}"
                    },
                    13f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
            if (register.validCount > 0 || damagedCount > 0) {
                addView(
                    text(
                        when {
                            damagedCount > 0 ->
                                "Повреждено: $damagedCount • запись не участвует"
                            conflictCount > 0 ->
                                "Расхождений источников: $conflictCount"
                            register.expiredCount > 0 ->
                                "Истёк срок: ${register.expiredCount} • нужна новая проверка"
                            register.degradedCount > 0 ->
                                "Кворум ослаб по времени: ${register.degradedCount}"
                            register.expiringCount > 0 ->
                                "Скоро изменится уровень: ${register.expiringCount}"
                            else ->
                                "Происхождение и срок проверяются отдельно"
                        },
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 3)
                )
            }
        }
    }

    private fun factReceiptSummaryBand(
        read: FactReceiptReadResult
    ): LinearLayout {
        require(read.integrity != FactReceiptIntegrity.EMPTY)
        val receipt = read.receipt
        val damaged = read.integrity == FactReceiptIntegrity.TAMPERED
        val freshness = receipt?.let {
            FreshnessEngine.evaluateFactor(
                factor = it.factor,
                claimedLevel = it.effectiveEvidence,
                checkedAt = it.checkedAt,
                now = System.currentTimeMillis()
            )
        }
        val tone = when {
            damaged -> Tone(AppColors.danger, AppColors.dangerSoft)
            freshness?.status == FreshnessStatus.EXPIRED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            freshness?.status == FreshnessStatus.DEGRADED ||
                freshness?.status == FreshnessStatus.EXPIRING ->
                Tone(AppColors.warning, AppColors.warningSoft)
            receipt?.effectiveEvidence == EvidenceLevel.QUORUM ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            else -> Tone(AppColors.warning, AppColors.warningSoft)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tone.background, 8)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            if (receipt == null) {
                addView(
                    text(
                        "ФАКТ-КВИТАНЦИЯ ПОВРЕЖДЕНА",
                        11f,
                        tone.foreground,
                        Typeface.BOLD
                    )
                )
                addView(
                    text(
                        "Локальная SHA-256-пломба не совпала. Запись не участвует в уровне доказательств.",
                        12.5f,
                        AppColors.ink
                    ),
                    matchWrap(top = 3)
                )
            } else {
                addView(
                    text(
                        "КВИТАНЦИЯ ${receipt.shortFingerprint} • ${factReceiptSourceSummary(receipt).uppercase(Locale.getDefault())}",
                        10.5f,
                        tone.foreground,
                        Typeface.BOLD
                    )
                )
                addView(
                    text(
                        receipt.statement,
                        13f,
                        AppColors.ink,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 4)
                )
                addView(
                    text(
                        "${receipt.coverage.title} • ${receipt.coverage.score} из 100 • ${receipt.effectiveEvidence.title}",
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 4)
                )
                if (freshness != null) {
                    addView(
                        text(
                            factFreshnessExplanation(freshness),
                            11.5f,
                            tone.foreground,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 4)
                    )
                }
            }
        }
    }

    private fun factReceiptSourceSummary(
        receipt: FactReceipt
    ): String {
        return when {
            receipt.sourceCount == 1 -> "1 источник"
            receipt.sourceAuditState == SourceAuditState.INDEPENDENT ->
                "2 независимых"
            receipt.sourceAuditState == SourceAuditState.SHARED_LINEAGE ->
                "2 записи, одна цепочка"
            receipt.sourceAuditState == SourceAuditState.CONFLICT ->
                "2 источника, расхождение"
            else -> "2 источника, связь не проверена"
        }
    }

    private fun plainAnalyticsFactorMap(
        result: PlainAnalyticsResult
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(
                                text(
                                    "Карта данных",
                                    17f,
                                    Color.WHITE,
                                    Typeface.BOLD
                                )
                            )
                            addView(
                                text(
                                    "Статус источников, а не шанс победы",
                                    12f,
                                    AppColors.fieldMuted
                                ),
                                matchWrap(top = 2)
                            )
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        text(
                            "?",
                            fixedControlTextSize(16f),
                            AppColors.fieldSignal,
                            Typeface.BOLD
                        ).apply {
                            gravity = Gravity.CENTER
                            minWidth = dp(48)
                            minHeight = dp(48)
                            background = rippleRounded(
                                AppColors.field,
                                24,
                                AppColors.fieldSignal,
                                1
                            )
                            applyAccessibleAction(dp(48))
                            contentDescription =
                                "Как читать карту данных"
                            setOnClickListener {
                                showPlainAnalyticsGuide()
                            }
                        },
                        LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                            leftMargin = dp(10)
                        }
                    )
                }
            )
            result.factorSummaries.forEachIndexed { index, summary ->
                if (index == 0) {
                    addView(fieldDivider(), matchFixed(1, top = 11))
                }
                addView(
                    plainAnalyticsFactorRow(summary),
                    matchWrap()
                )
                addView(fieldDivider(), matchFixed(1))
            }
        }
    }

    private fun fieldDivider(): View {
        return View(this).apply {
            setBackgroundColor(AppColors.fieldLine)
        }
    }

    private fun plainAnalyticsFactorRow(
        summary: PlainAnalyticsFactorSummary
    ): LinearLayout {
        val tone = plainAnalyticsFactorTone(summary)
        val stack = resources.configuration.fontScale >= 1.3f ||
            resources.configuration.screenWidthDp < 380
        return LinearLayout(this).apply {
            orientation = if (stack) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            gravity = if (stack) {
                Gravity.START
            } else {
                Gravity.CENTER_VERTICAL
            }
            if (summary.isNextAction) {
                background = rounded(AppColors.fieldRaised, 6)
            }
            setPadding(dp(10), dp(9), dp(10), dp(9))
            contentDescription = buildString {
                append("Фактор ")
                append(summary.factor.title)
                append(": ")
                append(plainAnalyticsFactorStatus(summary))
                if (summary.isNextAction) {
                    append(". Следующий шаг")
                }
            }
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    if (summary.isNextAction) {
                        addView(
                            text(
                                "СЛЕДУЮЩИЙ ШАГ",
                                10.5f,
                                AppColors.fieldSignal,
                                Typeface.BOLD
                            )
                        )
                    }
                    addView(
                        text(
                            summary.factor.title,
                            14f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = if (summary.isNextAction) 2 else 0)
                    )
                },
                if (stack) {
                    matchWrap()
                } else {
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
            )
            addView(
                label(
                    plainAnalyticsFactorStatus(summary),
                    tone.background,
                    tone.foreground
                ),
                if (stack) {
                    matchWrap(top = 6)
                } else {
                    wrapWrap().apply {
                        leftMargin = dp(10)
                    }
                }
            )
        }
    }

    private fun plainAnalyticsFactorStatus(
        summary: PlainAnalyticsFactorSummary
    ): String {
        return when (summary.freshnessStatus) {
            FreshnessStatus.EXPIRED -> "ИСТЁК СРОК"
            FreshnessStatus.DEGRADED -> "ОСТАЛСЯ 1 ИСТОЧНИК"
            FreshnessStatus.EXPIRING -> when (summary.effectiveLevel) {
                EvidenceLevel.QUORUM -> "2 ИСТОЧНИКА • ОБНОВИТЬ"
                EvidenceLevel.SINGLE_SOURCE -> "1 ИСТОЧНИК • ОБНОВИТЬ"
                EvidenceLevel.UNCONFIRMED -> "НЕТ ИСТОЧНИКА"
            }
            FreshnessStatus.FRESH -> when (summary.effectiveLevel) {
                EvidenceLevel.QUORUM -> "2 ИСТОЧНИКА"
                EvidenceLevel.SINGLE_SOURCE -> "1 ИСТОЧНИК"
                EvidenceLevel.UNCONFIRMED -> "НЕТ ИСТОЧНИКА"
            }
            FreshnessStatus.UNCONFIRMED -> "НЕТ ИСТОЧНИКА"
        }
    }

    private fun plainAnalyticsFactorTone(
        summary: PlainAnalyticsFactorSummary
    ): Tone {
        return when {
            summary.freshnessStatus == FreshnessStatus.EXPIRED ||
                summary.effectiveLevel == EvidenceLevel.UNCONFIRMED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            summary.freshnessStatus == FreshnessStatus.EXPIRING ||
                summary.freshnessStatus == FreshnessStatus.DEGRADED ||
                summary.effectiveLevel == EvidenceLevel.SINGLE_SOURCE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            else -> Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun showPlainAnalyticsGuide() {
        AlertDialog.Builder(this)
            .setTitle("Как читать карту данных")
            .setMessage(
                "Нет источника — тезис пока не подтверждён.\n\n" +
                    "1 источник — факт записан, но независимой сверки нет.\n\n" +
                    "2 источника — факт сверён по двум независимым цепочкам.\n\n" +
                    "Истёк срок — старый факт больше не участвует в выводе.\n\n" +
                    "Выделенная строка показывает только следующий шаг проверки. Статусы не являются вероятностью исхода или советом по ставке."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun plainAnalyticsTone(
        status: PlainAnalyticsStatus
    ): Tone {
        return when (status) {
            PlainAnalyticsStatus.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            PlainAnalyticsStatus.CHECK ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PlainAnalyticsStatus.READY ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun emptyFeedState(): LinearLayout {
        return card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            addView(
                text(
                    feedEmptyTitle(),
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    feedEmptyDescription(),
                    14f,
                    AppColors.muted
                ).apply { gravity = Gravity.CENTER },
                matchWrap(top = 6)
            )
            if (
                eventSearchQuery.isNotBlank() ||
                savedOnly ||
                activeSportFilter != "Все" ||
                activeFeedTimeFilter != FeedTimelineFilter.ALL
            ) {
                addView(
                    outlineButton(
                        feedEmptyActionTitle(),
                        AppColors.signal
                    ) {
                        resetEmptyFeedScope()
                        renderContent()
                    },
                    matchWrap(top = 13)
                )
            }
        }
    }

    private fun feedEmptyBadge(): String {
        return when {
            timelineFilterCausesEmptyState() ->
                "ПЕРИОД ПУСТ"
            eventSearchQuery.isNotBlank() -> "ПОИСК ПУСТ"
            savedOnly -> "СОХРАНЁННЫХ НЕТ"
            activeSportFilter != "Все" -> "ФИЛЬТР ПУСТ"
            else -> "КАТАЛОГ ПУСТ"
        }
    }

    private fun feedEmptyTitle(): String {
        return when {
            timelineFilterCausesEmptyState() ->
                "Нет событий в этой группе"
            eventSearchQuery.isNotBlank() -> "Ничего не найдено"
            savedOnly -> "Нет сохранённых событий"
            activeSportFilter != "Все" ->
                "Нет событий в этом виде спорта"
            else -> "Событий пока нет"
        }
    }

    private fun feedEmptyDescription(): String {
        return when {
            timelineFilterCausesEmptyState() ->
                "В группе «${activeFeedTimeFilter.title}» нет событий с текущими фильтрами. Выберите все даты или измените условия поиска."
            eventSearchQuery.isNotBlank() ->
                "По запросу «${eventSearchQuery.trim()}» нет совпадений в текущем каталоге и фильтрах."
            savedOnly ->
                "Сохраните нужное событие звездой, чтобы быстро вернуться к нему."
            activeSportFilter != "Все" ->
                "Сбросьте спортивный фильтр, чтобы вернуться ко всему каталогу."
            else ->
                "Обновите источник событий или проверьте его состояние."
        }
    }

    private fun timelineFilterCausesEmptyState(): Boolean {
        if (activeFeedTimeFilter == FeedTimelineFilter.ALL) {
            return false
        }
        val events = feedEventsBeforeTimeline(
            state.bookmarkedIds()
        )
        if (events.isEmpty()) return false
        return FeedTimelinePolicy.filter(
            events = events,
            selected = activeFeedTimeFilter,
            now = System.currentTimeMillis(),
            zoneId = state.selectedRegionalZone.zoneId
        ).isEmpty()
    }

    private fun feedEmptyActionTitle(): String {
        return if (timelineFilterCausesEmptyState()) {
            "Показать все даты"
        } else {
            "Сбросить поиск и фильтры"
        }
    }

    private fun resetEmptyFeedScope() {
        if (timelineFilterCausesEmptyState()) {
            activeFeedTimeFilter = FeedTimelineFilter.ALL
            focusEventLimit = FOCUS_EVENT_PAGE_SIZE
        } else {
            resetFeedFilters()
        }
    }

    private fun pulseWorkspaceControls(): LinearLayout {
        val imageHeight = if (effectiveFontScale() >= 1.8f) {
            72
        } else {
            96
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                imageFrame().apply {
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(
                                R.drawable.workspace_depth_v380
                            )
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            contentDescription =
                                "Два режима глубины: общий обзор и подробная проверка. Данные остаются теми же"
                        },
                        frameMatch()
                    )
                },
                matchFixed(imageHeight)
            )
            addView(
                text(
                    "Глубина разбора",
                    16f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    "Выберите короткий итог или полный аудит. Данные не меняются.",
                    12.5f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
            addView(
                pulseWorkspaceSwitcher(),
                matchWrap(top = 9)
            )
            addView(
                pulseWorkspacePrimer(),
                matchWrap(top = 8)
            )
        }
    }

    private fun pulseWorkspaceSwitcher(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            background = rounded(
                AppColors.surface,
                8,
                AppColors.line,
                1
            )
            setPadding(dp(4), dp(4), dp(4), dp(4))
            listOf(
                PulseWorkspaceMode.STORY to "Коротко",
                PulseWorkspaceMode.LAB to "Подробно"
            ).forEachIndexed { index, (mode, title) ->
                val selected = mode == activePulseWorkspaceMode
                addView(
                    text(
                        title,
                        13f,
                        if (selected) {
                            Color.WHITE
                        } else {
                            AppColors.ink
                        },
                        Typeface.BOLD
                    ).apply {
                        gravity = Gravity.CENTER
                        minHeight = dp(48)
                        background = rippleRounded(
                            if (selected) {
                                AppColors.accent
                            } else {
                                AppColors.surface
                            },
                            6
                        )
                        applyAccessibleAction(dp(48))
                        isSelected = selected
                        contentDescription =
                            "Режим анализа: $title"
                        setOnClickListener {
                            selectPulseWorkspaceMode(mode)
                        }
                    },
                    LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f
                    ).apply {
                        if (index == 0) rightMargin = dp(4)
                    }
                )
            }
        }
    }

    private fun pulseWorkspacePrimer(): LinearLayout {
        val isShort = activePulseWorkspaceMode ==
            PulseWorkspaceMode.STORY
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                if (isShort) {
                    AppColors.signalSoft
                } else {
                    AppColors.background
                },
                8,
                if (isShort) {
                    AppColors.signal
                } else {
                    AppColors.line
                },
                1
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                text(
                    if (isShort) {
                        "КОРОТКО • ИТОГ И ОДИН ШАГ"
                    } else {
                        "ПОДРОБНО • ПОЛНЫЙ АУДИТ ФАКТОВ"
                    },
                    12f,
                    if (isShort) {
                        AppColors.signal
                    } else {
                        AppColors.ink
                    },
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    if (isShort) {
                        "Главный пробел и ближайшее действие без лишних инструментов."
                    } else {
                        "Навигатор разделяет аудит на маршрут, факты и решение и рекомендует следующий раздел."
                    },
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
        }
    }

    private fun pulseLabNavigatorSummary(
        event: SportEvent,
        now: Long
    ): PulseLabNavigatorSummary {
        val evidence = state.evidence(event)
        val timeline = state.evidenceTimelinePreview(
            eventId = event.id,
            now = now
        )
        val analytics = PlainAnalyticsEngine.evaluate(
            assessment = state.assessment(event),
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val snapshot = state.decisionSnapshot(event.id)
        return PulseLabNavigatorEngine.evaluate(
            PulseLabNavigatorInput(
                confirmedFactorCount =
                    analytics.confirmedFactorCount,
                independentlyVerifiedCount =
                    analytics.independentlyVerifiedCount,
                totalFactorCount = analytics.totalFactorCount,
                hasDecisionSnapshot = snapshot != null,
                eventStarted = event.startAt?.let {
                    it <= now
                } ?: false,
                reviewFinalized = state.postEventReview(event.id)
                    ?.isFinalized == true
            )
        )
    }

    private fun pulseLabNavigatorPanel(
        summary: PulseLabNavigatorSummary
    ): LinearLayout {
        val recommendationTone = pulseLabSectionTone(
            summary.recommendedSection
        )
        val stackSections =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        return card().apply {
            addView(
                label(
                    summary.badge,
                    recommendationTone.background,
                    recommendationTone.foreground
                )
            )
            addView(
                text(
                    "Навигатор подробного разбора",
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    summary.headline,
                    16f,
                    recommendationTone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 6)
            )
            addView(
                text(summary.body, 13.5f, AppColors.muted),
                matchWrap(top = 5)
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackSections) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    PulseLabSection.values().forEachIndexed {
                            index,
                            section ->
                        val selected =
                            section == activePulseLabSection
                        val tone = pulseLabSectionTone(section)
                        addView(
                            text(
                                "${section.title}\n${summary.status(section)}",
                                12.5f,
                                if (selected) {
                                    Color.WHITE
                                } else {
                                    AppColors.ink
                                },
                                Typeface.BOLD
                            ).apply {
                                gravity = Gravity.CENTER
                                minHeight = dp(64)
                                background = rippleRounded(
                                    if (selected) {
                                        tone.foreground
                                    } else {
                                        AppColors.surface
                                    },
                                    7,
                                    if (selected) {
                                        tone.foreground
                                    } else if (
                                        section ==
                                        summary.recommendedSection
                                    ) {
                                        tone.foreground
                                    } else {
                                        AppColors.line
                                    },
                                    1
                                )
                                applyAccessibleAction(dp(48))
                                isSelected = selected
                                contentDescription = buildString {
                                    append(
                                        "Раздел подробного анализа: "
                                    )
                                    append(section.title)
                                    append(". ")
                                    append(summary.status(section))
                                    if (
                                        section ==
                                        summary.recommendedSection
                                    ) {
                                        append(". Рекомендуется следующим")
                                    }
                                }
                                setOnClickListener {
                                    selectPulseLabSection(section)
                                }
                            },
                            if (stackSections) {
                                matchWrap(
                                    top = if (index == 0) 0 else 7
                                )
                            } else {
                                LinearLayout.LayoutParams(
                                    0,
                                    dp(68),
                                    1f
                                ).apply {
                                    if (index > 0) {
                                        leftMargin = dp(7)
                                    }
                                }
                            }
                        )
                    }
                },
                matchWrap(top = 14)
            )
            addView(
                text(
                    "РАЗДЕЛЫ МЕНЯЮТ ТОЛЬКО ВИД ЭКРАНА • ДАННЫЕ НЕ МЕНЯЮТСЯ",
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
        }
    }

    private fun pulseLabSectionTone(
        section: PulseLabSection
    ): Tone {
        return when (section) {
            PulseLabSection.ROUTE ->
                Tone(AppColors.signal, AppColors.signalSoft)
            PulseLabSection.FACTS ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PulseLabSection.DECISION ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun selectPulseLabSection(
        section: PulseLabSection
    ) {
        if (section == activePulseLabSection) return
        activePulseLabSection = section
        pendingPulseFactor = null
        pendingPulseStoryAction = null
        renderContent()
        pulseLabNavigatorAnchor?.let { navigator ->
            scrollToAppView(navigator, topOffsetDp = 10)
        }
    }

    private fun selectPulseWorkspaceMode(
        mode: PulseWorkspaceMode
    ) {
        if (mode == activePulseWorkspaceMode) return
        flushAttentionTracking()
        activePulseWorkspaceMode = mode
        state.selectedPulseWorkspaceMode = mode
        if (mode == PulseWorkspaceMode.STORY) {
            pulseStoryControlsExpanded = false
        } else {
            activePulseLabSection = PulseLabSection.ROUTE
        }
        pendingPulseFactor = null
        pendingPulseStoryAction = null
        renderContent()
        startAttentionTrackingIfNeeded()
        pulseWorkspaceControlsAnchor?.let { target ->
            scrollToAppView(target, topOffsetDp = 10)
        } ?: mainScroll.scrollTo(
            0,
            content.top.coerceAtLeast(0)
        )
    }

    private fun renderPulseStoryMode(event: SportEvent) {
        val now = System.currentTimeMillis()
        val interactionLocked = state.isPauseActive(now)
        val story = eventStoryResult(event = event, now = now)
        val beacon = storyBeaconResult(
            event = event,
            story = story,
            now = now
        )
        val currentCheckpoint = StoryCheckpointFactory.create(
            story = story,
            beacon = beacon,
            savedAt = now
        )
        val checkpointRead = state.storyCheckpoint(event.id)
        val checkpointComparison = checkpointRead.checkpoint?.let {
            StoryCheckpointEngine.compare(
                checkpoint = it,
                current = currentCheckpoint
            )
        }
        val threadRead = state.storyThread(event.id)
        val threadResult = threadRead.thread?.let {
            StoryThreadEngine.evaluate(
                thread = it,
                story = story
            )
        }
        val disclosure = PulseStoryDisclosureEngine.evaluate(
            PulseStoryDisclosureInput(
                checkpointIntegrity = checkpointRead.integrity,
                checkpointChangeCount = checkpointComparison
                    ?.changeCount,
                threadIntegrity = threadRead.integrity,
                threadStatus = threadResult?.status,
                beaconState = beacon.state,
                beaconMomentCount = beacon.moments.size,
                storyPhase = story.phase,
                completedChapterCount = story.completedCount
            )
        )
        val panel = eventStoryPanel(
            onAction = { action, factor ->
                openEventStoryAction(
                    action = action,
                    factor = factor
                )
            },
            onShare = {
                val posterNow = System.currentTimeMillis()
                shareEventStoryPoster(
                    event = event,
                    story = eventStoryResult(
                        event = event,
                        now = posterNow
                    ),
                    generatedAt = posterNow
                )
            }
        )
        renderEventStory(
            panel = panel,
            result = story,
            event = event,
            now = now,
            interactionLocked = interactionLocked
        )
        content.addView(panel.root, matchWrap(top = 12))
        content.addView(
            pulseStoryDisclosurePanel(disclosure),
            matchWrap(top = 12)
        )
        if (!pulseStoryControlsExpanded) {
            if (interactionLocked) {
                content.addView(
                    pauseLockedCard(),
                    matchWrap(top = 12)
                )
            }
            return
        }
        content.addView(
            storyCheckpointPanel(
                read = checkpointRead,
                current = currentCheckpoint,
                comparison = checkpointComparison,
                interactionLocked = interactionLocked,
                onSave = {
                    val saveNow = System.currentTimeMillis()
                    val saveStory = eventStoryResult(
                        event = event,
                        now = saveNow
                    )
                    val saveBeacon = storyBeaconResult(
                        event = event,
                        story = saveStory,
                        now = saveNow
                    )
                    state.saveStoryCheckpoint(
                        StoryCheckpointFactory.create(
                            story = saveStory,
                            beacon = saveBeacon,
                            savedAt = saveNow
                        )
                    )
                    Toast.makeText(
                        this,
                        "Контрольная точка сохранена",
                        Toast.LENGTH_SHORT
                    ).show()
                    rerenderContentPreservingScroll()
                },
                onClear = {
                    state.clearStoryCheckpoint(event.id)
                    Toast.makeText(
                        this,
                        "Контрольная точка удалена",
                        Toast.LENGTH_SHORT
                    ).show()
                    rerenderContentPreservingScroll()
                }
            ),
            matchWrap(top = 12)
        )
        content.addView(
            storyThreadPanel(
                read = threadRead,
                result = threadResult,
                story = story,
                beacon = beacon,
                interactionLocked = interactionLocked,
                onChoose = {
                    openStoryThreadPicker(
                        event = event,
                        currentChapter = threadRead.thread?.chapter,
                        allowClear = threadRead.integrity !=
                            StoryThreadIntegrity.EMPTY
                    )
                },
                onClear = {
                    state.clearStoryThread(event.id)
                    Toast.makeText(
                        this,
                        "Нить события удалена",
                        Toast.LENGTH_SHORT
                    ).show()
                    rerenderContentPreservingScroll()
                },
                onAction = {
                    openEventStoryAction(
                        action = story.action,
                        factor = story.actionFactor
                    )
                },
                onShare = {
                    shareStoryThreadPoster(event)
                }
            ),
            matchWrap(top = 12)
        )
        content.addView(
            storyBeaconPanel(
                result = beacon
            ),
            matchWrap(top = 12)
        )
        content.addView(
            eventStoryDossier(story),
            matchWrap(top = 12)
        )
        if (interactionLocked) {
            content.addView(
                pauseLockedCard(),
                matchWrap(top = 12)
            )
        }
    }

    private fun pulseStoryDisclosurePanel(
        summary: PulseStoryDisclosureSummary
    ): LinearLayout {
        val summaryTone = when {
            summary.dangerCount > 0 ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            summary.alertCount > 0 ->
                Tone(AppColors.warning, AppColors.warningSoft)
            else -> Tone(AppColors.accentDark, AppColors.accentSoft)
        }
        return card().apply {
            addView(
                label(
                    summary.badge,
                    summaryTone.background,
                    summaryTone.foreground
                )
            )
            addView(
                text(
                    "История и контроль",
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    summary.headline,
                    14f,
                    summaryTone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(summary.body, 13f, AppColors.muted),
                matchWrap(top = 5)
            )
            summary.rows.forEachIndexed { index, row ->
                addView(
                    divider(),
                    matchFixed(
                        1,
                        top = if (index == 0) 12 else 9,
                        bottom = 8
                    )
                )
                addView(pulseStoryDisclosureRow(row))
            }
            addView(
                if (pulseStoryControlsExpanded) {
                    outlineButton(
                        "Свернуть историю и контроль",
                        AppColors.signal
                    ) {
                        pulseStoryControlsExpanded = false
                        rerenderContentPreservingScroll()
                    }
                } else {
                    commandButton(
                        "Открыть историю и контроль",
                        AppColors.signal
                    ) {
                        pulseStoryControlsExpanded = true
                        rerenderContentPreservingScroll()
                    }
                },
                matchWrap(top = 13)
            )
            addView(
                text(
                    if (pulseStoryControlsExpanded) {
                        "ОТКРЫТО • 4 ДОПОЛНИТЕЛЬНЫХ БЛОКА"
                    } else {
                        "СВЁРНУТО • КОРОТКИЙ ИТОГ НЕ МЕНЯЕТСЯ"
                    },
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 9)
            )
        }
    }

    private fun pulseStoryDisclosureRow(
        row: PulseStoryDisclosureRow
    ): LinearLayout {
        val tone = pulseStoryDisclosureTone(row.tone)
        val stack =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        return LinearLayout(this).apply {
            orientation = if (stack) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            gravity = if (stack) {
                Gravity.START
            } else {
                Gravity.CENTER_VERTICAL
            }
            addView(
                text(
                    row.title,
                    13f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                if (stack) {
                    matchWrap()
                } else {
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        rightMargin = dp(10)
                    }
                }
            )
            addView(
                label(
                    row.value.uppercase(Locale.getDefault()),
                    tone.background,
                    tone.foreground
                ),
                if (stack) {
                    wrapWrap().apply {
                        topMargin = dp(5)
                    }
                } else {
                    wrapWrap()
                }
            )
            contentDescription = "${row.title}: ${row.value}"
        }
    }

    private fun pulseStoryDisclosureTone(
        tone: PulseStoryDisclosureTone
    ): Tone {
        return when (tone) {
            PulseStoryDisclosureTone.NEUTRAL ->
                Tone(AppColors.muted, AppColors.background)
            PulseStoryDisclosureTone.INFO ->
                Tone(AppColors.signal, AppColors.signalSoft)
            PulseStoryDisclosureTone.READY ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            PulseStoryDisclosureTone.ATTENTION ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PulseStoryDisclosureTone.DANGER ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun storyCheckpointPanel(
        read: StoryCheckpointReadResult,
        current: StoryCheckpoint,
        comparison: StoryCheckpointComparison?,
        interactionLocked: Boolean,
        onSave: () -> Unit,
        onClear: () -> Unit
    ): LinearLayout {
        require(
            (read.integrity == StoryCheckpointIntegrity.VALID) ==
                (comparison != null)
        )
        val changed = comparison?.hasChanges == true
        val tone = when (read.integrity) {
            StoryCheckpointIntegrity.EMPTY ->
                Tone(AppColors.signal, AppColors.signalSoft)
            StoryCheckpointIntegrity.VALID -> if (changed) {
                Tone(AppColors.warning, AppColors.warningSoft)
            } else {
                Tone(AppColors.accentDark, AppColors.accentSoft)
            }
            StoryCheckpointIntegrity.TAMPERED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
        val badgeTitle = when (read.integrity) {
            StoryCheckpointIntegrity.EMPTY -> "ТОЧКА НЕ СОЗДАНА"
            StoryCheckpointIntegrity.VALID -> if (changed) {
                "ИЗМЕНЕНИЙ • ${comparison?.changeCount}"
            } else {
                "БЕЗ ИЗМЕНЕНИЙ"
            }
            StoryCheckpointIntegrity.TAMPERED ->
                "ТОЧКА ПОВРЕЖДЕНА"
        }
        val title = when (read.integrity) {
            StoryCheckpointIntegrity.EMPTY ->
                "Что изменилось с прошлого визита"
            StoryCheckpointIntegrity.VALID -> if (changed) {
                "Сюжет изменился"
            } else {
                "Сюжет совпадает с точкой"
            }
            StoryCheckpointIntegrity.TAMPERED ->
                "Сравнение заблокировано"
        }
        val body = when (read.integrity) {
            StoryCheckpointIntegrity.EMPTY ->
                "Запомните текущую версию по явной команде. Приложение не ведет скрытую историю посещений."
            StoryCheckpointIntegrity.VALID ->
                "Точка сохранена ${formatDateTime(checkNotNull(read.checkpoint).savedAt)}. Сравниваются только смысловые изменения."
            StoryCheckpointIntegrity.TAMPERED ->
                "Локальная запись не прошла проверку SHA-256 и не участвует в выводах."
        }
        return card().apply {
            addView(storyCheckpointHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(badgeTitle, tone.background, tone.foreground),
                matchWrap(top = 12)
            )
            addView(
                text(title, 20f, tone.foreground, Typeface.BOLD),
                matchWrap(top = 12)
            )
            addView(
                text(body, 14f, AppColors.muted),
                matchWrap(top = 5)
            )
            comparison?.takeIf { it.hasChanges }
                ?.let(::storyCheckpointChangeRows)
                ?.forEachIndexed { index, row ->
                    addView(
                        divider(),
                        matchFixed(
                            1,
                            top = if (index == 0) 12 else 10,
                            bottom = 9
                        )
                    )
                    addView(
                        storyCheckpointChangeRow(
                            title = row.first,
                            detail = row.second,
                            tone = tone
                        )
                    )
                }
            if (
                read.integrity == StoryCheckpointIntegrity.EMPTY ||
                changed
            ) {
                addView(
                    commandButton(
                        if (interactionLocked) {
                            "Пауза • точка только для чтения"
                        } else if (changed) {
                            "Принять текущую версию"
                        } else {
                            "Запомнить эту версию"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            tone.foreground
                        },
                        onSave
                    ).apply {
                        isEnabled = !interactionLocked
                        alpha = if (interactionLocked) 0.55f else 1f
                    },
                    matchWrap(top = 12)
                )
            }
            if (read.integrity != StoryCheckpointIntegrity.EMPTY) {
                addView(
                    outlineButton(
                        if (interactionLocked) {
                            "Пауза • удаление недоступно"
                        } else if (
                            read.integrity ==
                            StoryCheckpointIntegrity.TAMPERED
                        ) {
                            "Удалить поврежденную точку"
                        } else {
                            "Удалить контрольную точку"
                        },
                        if (interactionLocked) {
                            AppColors.muted
                        } else {
                            tone.foreground
                        },
                        onClear
                    ).apply {
                        isEnabled = !interactionLocked
                        alpha = if (interactionLocked) 0.55f else 1f
                    },
                    matchWrap(top = 8)
                )
            }
            addView(
                text(
                    when (read.integrity) {
                        StoryCheckpointIntegrity.EMPTY ->
                            "ТОЛЬКО ПО КОМАНДЕ • ЛОКАЛЬНО"
                        StoryCheckpointIntegrity.VALID ->
                            "СРАВНЕНИЕ • SHA-256 ${comparison?.shortFingerprint}"
                        StoryCheckpointIntegrity.TAMPERED ->
                            "FAIL-CLOSED • ЛОКАЛЬНЫЕ ДАННЫЕ ОТКЛОНЕНЫ"
                    },
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun storyCheckpointHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.story_checkpoint)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Шесть пар физических контрольных станций показывают сохраненную и текущую версии сюжета"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "КОНТРОЛЬНАЯ ТОЧКА",
                            11f,
                            Color.rgb(255, 219, 158),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Что изменилось",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun storyCheckpointChangeRows(
        comparison: StoryCheckpointComparison
    ): List<Pair<String, String>> {
        return buildList {
            if (comparison.labelChanged || comparison.sourceChanged) {
                add(
                    "АФИША / ИСТОЧНИК" to buildList {
                        if (comparison.labelChanged) {
                            add(
                                "${comparison.checkpoint.eventLabel} → " +
                                    comparison.current.eventLabel
                            )
                        }
                        if (comparison.sourceChanged) {
                            add(
                                "${storyCheckpointSourceTitle(comparison.checkpoint.sourceState)} → " +
                                    storyCheckpointSourceTitle(
                                        comparison.current.sourceState
                                    )
                            )
                        }
                    }.joinToString("\n")
                )
            }
            if (comparison.chapterDeltas.isNotEmpty()) {
                add(
                    "ГЛАВЫ" to comparison.chapterDeltas.joinToString(
                        "\n"
                    ) { delta ->
                        "${eventStoryChapterTitle(delta.chapter)}: " +
                            "${eventStoryChapterStateTitle(delta.before)} → " +
                            eventStoryChapterStateTitle(delta.current)
                    }
                )
            }
            if (comparison.phaseChanged || comparison.actionChanged) {
                add(
                    "МАРШРУТ" to buildList {
                        if (comparison.phaseChanged) {
                            add(
                                "${eventStoryPhaseTitle(comparison.checkpoint.phase)} → " +
                                    eventStoryPhaseTitle(
                                        comparison.current.phase
                                    )
                            )
                        }
                        if (comparison.actionChanged) {
                            add(
                                "${storyCheckpointActionTitle(comparison.checkpoint)} → " +
                                    storyCheckpointActionTitle(
                                        comparison.current
                                    )
                            )
                        }
                    }.joinToString("\n")
                )
            }
            if (comparison.startChanged || comparison.reviewChanged) {
                add(
                    "ВРЕМЯ" to buildList {
                        if (comparison.startChanged) {
                            add(
                                "Старт: ${storyCheckpointTime(comparison.checkpoint.startAt)} → " +
                                    storyCheckpointTime(
                                        comparison.current.startAt
                                    )
                            )
                        }
                        if (comparison.reviewChanged) {
                            add(
                                "Разбор: ${storyCheckpointTime(comparison.checkpoint.reviewOpensAt)} → " +
                                    storyCheckpointTime(
                                        comparison.current.reviewOpensAt
                                    )
                            )
                        }
                    }.joinToString("\n")
                )
            }
            if (
                comparison.beaconStateChanged ||
                comparison.beaconMomentsChanged
            ) {
                add(
                    "МАЯК" to buildList {
                        if (comparison.beaconStateChanged) {
                            add(
                                "${storyBeaconStateTitle(comparison.checkpoint.beaconState)} → " +
                                    storyBeaconStateTitle(
                                        comparison.current.beaconState
                                    )
                            )
                        }
                        if (comparison.beaconMomentsChanged) {
                            add(
                                "Опорных моментов: " +
                                    "${comparison.checkpoint.beaconMoments.size} → " +
                                    comparison.current.beaconMoments.size
                            )
                        }
                    }.joinToString("\n")
                )
            }
        }
    }

    private fun storyCheckpointChangeRow(
        title: String,
        detail: String,
        tone: Tone
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(title, 12f, tone.foreground, Typeface.BOLD)
            )
            addView(
                text(detail, 13.5f, AppColors.ink),
                matchWrap(top = 4)
            )
        }
    }

    private fun storyCheckpointSourceTitle(
        sourceState: EventStorySourceState
    ): String {
        return when (sourceState) {
            EventStorySourceState.DEMO -> "Демо-каталог"
            EventStorySourceState.UNSIGNED -> "Без подписи"
            EventStorySourceState.API_PROVIDER ->
                "Внешнее расписание по HTTPS"
            EventStorySourceState.DEVELOPMENT_SIGNED ->
                "Подписано dev-ключом"
            EventStorySourceState.PRODUCTION_SIGNED ->
                "Подписано production-ключом"
        }
    }

    private fun storyCheckpointActionTitle(
        checkpoint: StoryCheckpoint
    ): String {
        return if (checkpoint.action == EventStoryAction.NONE) {
            "Следующего действия нет"
        } else {
            eventStoryActionTitle(
                checkpoint.action,
                checkpoint.actionFactor
            )
        }
    }

    private fun storyCheckpointTime(timestamp: Long?): String {
        return timestamp?.let {
            TimeBridgeEngine.formatInstant(
                startAt = it,
                selectedZone = state.selectedRegionalZone
            )
        } ?: "не подтвержден"
    }

    private fun storyThreadPanel(
        read: StoryThreadReadResult,
        result: StoryThreadResult?,
        story: EventStoryResult,
        beacon: StoryBeaconResult,
        interactionLocked: Boolean,
        onChoose: () -> Unit,
        onClear: () -> Unit,
        onAction: () -> Unit,
        onShare: () -> Unit
    ): LinearLayout {
        require(
            (read.integrity == StoryThreadIntegrity.VALID) ==
                (result != null)
        )
        val tone = storyThreadTone(read.integrity, result?.status)
        val recommended = if (
            read.integrity == StoryThreadIntegrity.EMPTY
        ) {
            StoryThreadPolicy.recommended(story)
        } else {
            null
        }
        val actionMatches = result?.let {
            storyThreadActionChapter(story.action) ==
                it.thread.chapter
        } == true
        val nextMoment = result?.let {
            StoryThreadPolicy.relevantMoment(
                chapter = it.thread.chapter,
                beacon = beacon
            )
        }

        return card().apply {
            addView(storyThreadHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    storyThreadBadge(read.integrity, result?.status),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    when (read.integrity) {
                        StoryThreadIntegrity.EMPTY ->
                            "За чем вы хотите следить"
                        StoryThreadIntegrity.VALID ->
                            storyThreadQuestion(
                                checkNotNull(result).thread.chapter
                            )
                        StoryThreadIntegrity.TAMPERED ->
                            "Вопрос нельзя проверить"
                    },
                    20f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    storyThreadSummary(
                        integrity = read.integrity,
                        result = result
                    ),
                    14f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )

            recommended?.let { chapter ->
                addView(
                    divider(),
                    matchFixed(1, top = 12, bottom = 10)
                )
                addView(
                    text(
                        "РЕКОМЕНДУЕТСЯ СЕЙЧАС",
                        11.5f,
                        tone.foreground,
                        Typeface.BOLD
                    )
                )
                addView(
                    text(
                        storyThreadQuestion(chapter),
                        15f,
                        AppColors.ink,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 4)
                )
                addView(
                    text(
                        "${eventStoryChapterTitle(chapter)} • ${
                            eventStoryChapterStateTitle(
                                story.chapter(chapter).state
                            )
                        }",
                        12.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 3)
                )
            }

            result?.let { current ->
                addView(
                    StoryThreadView(this@MainActivity).apply {
                        setResult(current)
                    },
                    matchFixed(108, top = 3)
                )
                nextMoment?.let { moment ->
                    addView(
                        divider(),
                        matchFixed(1, top = 2, bottom = 9)
                    )
                    addView(
                        text(
                            "СЛЕДУЮЩАЯ ОПОРНАЯ ТОЧКА",
                            11.5f,
                            tone.foreground,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "${storyBeaconMomentTitle(moment)} • ${
                                storyBeaconMomentTime(moment)
                            }",
                            13.5f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 4)
                    )
                }
            }

            when (read.integrity) {
                StoryThreadIntegrity.EMPTY -> {
                    addView(
                        commandButton(
                            if (interactionLocked) {
                                "Пауза • нить только для чтения"
                            } else {
                                "Выбрать нить"
                            },
                            if (interactionLocked) {
                                AppColors.muted
                            } else {
                                tone.foreground
                            },
                            onChoose
                        ).apply {
                            isEnabled = !interactionLocked &&
                                recommended != null
                            alpha = if (isEnabled) 1f else 0.55f
                        },
                        matchWrap(top = 12)
                    )
                }
                StoryThreadIntegrity.VALID -> {
                    val current = checkNotNull(result)
                    val terminal = current.status ==
                        StoryThreadStatus.RESOLVED ||
                        current.status == StoryThreadStatus.MISSED
                    val primaryTitle = when {
                        interactionLocked ->
                            "Пауза • нить только для чтения"
                        terminal -> "Выбрать новую нить"
                        actionMatches ->
                            "Открыть: ${eventStoryActionTitle(
                                story.action,
                                story.actionFactor
                            )}"
                        else -> "Изменить нить"
                    }
                    addView(
                        commandButton(
                            primaryTitle,
                            if (interactionLocked) {
                                AppColors.muted
                            } else {
                                tone.foreground
                            },
                            if (actionMatches && !terminal) {
                                onAction
                            } else {
                                onChoose
                            }
                        ).apply {
                            isEnabled = !interactionLocked
                            alpha = if (isEnabled) 1f else 0.55f
                        },
                        matchWrap(top = 12)
                    )
                    if (actionMatches && !terminal) {
                        addView(
                            outlineButton(
                                "Изменить нить",
                                tone.foreground,
                                onChoose
                            ).apply {
                                isEnabled = !interactionLocked
                                alpha = if (isEnabled) 1f else 0.55f
                            },
                            matchWrap(top = 8)
                        )
                    }
                    addView(
                        outlineButton(
                            if (interactionLocked) {
                                "Пауза • экспорт недоступен"
                            } else {
                                "Создать карточку нити"
                            },
                            if (interactionLocked) {
                                AppColors.muted
                            } else {
                                tone.foreground
                            },
                            onShare
                        ).apply {
                            isEnabled = !interactionLocked
                            alpha = if (isEnabled) 1f else 0.55f
                            contentDescription =
                                "Создать PNG-карточку выбранной нити события"
                        },
                        matchWrap(top = 8)
                    )
                }
                StoryThreadIntegrity.TAMPERED -> {
                    addView(
                        outlineButton(
                            if (interactionLocked) {
                                "Пауза • удаление недоступно"
                            } else {
                                "Удалить поврежденную нить"
                            },
                            if (interactionLocked) {
                                AppColors.muted
                            } else {
                                tone.foreground
                            },
                            onClear
                        ).apply {
                            isEnabled = !interactionLocked
                            alpha = if (isEnabled) 1f else 0.55f
                        },
                        matchWrap(top = 12)
                    )
                }
            }
            addView(
                text(
                    when (read.integrity) {
                        StoryThreadIntegrity.EMPTY ->
                            "ТОЛЬКО ПО КОМАНДЕ • БЕЗ УВЕДОМЛЕНИЙ"
                        StoryThreadIntegrity.VALID ->
                            "ЛОКАЛЬНО • SHA-256 ${result?.shortFingerprint}"
                        StoryThreadIntegrity.TAMPERED ->
                            "FAIL-CLOSED • ЛОКАЛЬНАЯ ЗАПИСЬ ОТКЛОНЕНА"
                    },
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun storyThreadHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.story_thread)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Шесть станций сюжета и одна выбранная нить ведут к следующей контрольной точке"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "НИТЬ СОБЫТИЯ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Один вопрос к матчу",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun storyThreadBadge(
        integrity: StoryThreadIntegrity,
        status: StoryThreadStatus?
    ): String {
        return when (integrity) {
            StoryThreadIntegrity.EMPTY -> "НИТЬ НЕ ВЫБРАНА"
            StoryThreadIntegrity.TAMPERED -> "НИТЬ ПОВРЕЖДЕНА"
            StoryThreadIntegrity.VALID -> when (checkNotNull(status)) {
                StoryThreadStatus.OPEN -> "ВОПРОС ОТКРЫТ"
                StoryThreadStatus.MOVED -> "НИТЬ СДВИНУЛАСЬ"
                StoryThreadStatus.RESOLVED -> "ВОПРОС ЗАКРЫТ"
                StoryThreadStatus.MISSED -> "МОМЕНТ УПУЩЕН"
            }
        }
    }

    private fun storyThreadSummary(
        integrity: StoryThreadIntegrity,
        result: StoryThreadResult?
    ): String {
        return when (integrity) {
            StoryThreadIntegrity.EMPTY ->
                "Закрепите одну незавершенную главу. Приложение проверит ее только при следующем открытии."
            StoryThreadIntegrity.TAMPERED ->
                "Локальная запись не прошла проверку SHA-256 и не участвует в выводах."
            StoryThreadIntegrity.VALID -> {
                val current = checkNotNull(result)
                val started = formatDateTime(current.thread.startedAt)
                when (current.status) {
                    StoryThreadStatus.OPEN ->
                        "Состояние не изменилось с $started: ${
                            eventStoryChapterStateTitle(
                                current.currentState
                            )
                        }."
                    StoryThreadStatus.MOVED ->
                        "С $started состояние изменилось: ${
                            eventStoryChapterStateTitle(
                                current.thread.initialState
                            )
                        } → ${
                            eventStoryChapterStateTitle(
                                current.currentState
                            )
                        }."
                    StoryThreadStatus.RESOLVED ->
                        "Выбранная глава завершена. Нить закрыта проверяемым состоянием сюжета."
                    StoryThreadStatus.MISSED ->
                        "Предстартовый момент прошел без завершения выбранной главы."
                }
            }
        }
    }

    private fun storyThreadQuestion(
        chapter: EventStoryChapter
    ): String {
        return when (chapter) {
            EventStoryChapter.SOURCE ->
                "Можно ли доверять текущей афише?"
            EventStoryChapter.FACTS ->
                "Хватит ли подтвержденных фактов?"
            EventStoryChapter.PLAN ->
                "Готов ли план проверок к старту?"
            EventStoryChapter.DECISION ->
                "Зафиксирован ли вывод до старта?"
            EventStoryChapter.START ->
                "Наступил ли указанный старт?"
            EventStoryChapter.REVIEW ->
                "Готов ли разбор процесса?"
        }
    }

    private fun storyThreadTone(
        integrity: StoryThreadIntegrity,
        status: StoryThreadStatus?
    ): Tone {
        return when (integrity) {
            StoryThreadIntegrity.EMPTY ->
                Tone(AppColors.signal, AppColors.signalSoft)
            StoryThreadIntegrity.TAMPERED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            StoryThreadIntegrity.VALID -> when (checkNotNull(status)) {
                StoryThreadStatus.OPEN ->
                    Tone(AppColors.signal, AppColors.signalSoft)
                StoryThreadStatus.MOVED ->
                    Tone(AppColors.warning, AppColors.warningSoft)
                StoryThreadStatus.RESOLVED ->
                    Tone(AppColors.accentDark, AppColors.accentSoft)
                StoryThreadStatus.MISSED ->
                    Tone(AppColors.danger, AppColors.dangerSoft)
            }
        }
    }

    private fun storyThreadActionChapter(
        action: EventStoryAction
    ): EventStoryChapter? {
        return when (action) {
            EventStoryAction.OPEN_SOURCE -> EventStoryChapter.SOURCE
            EventStoryAction.OPEN_FACTS -> EventStoryChapter.FACTS
            EventStoryAction.OPEN_PLAN -> EventStoryChapter.PLAN
            EventStoryAction.OPEN_DECISION ->
                EventStoryChapter.DECISION
            EventStoryAction.OPEN_REVIEW -> EventStoryChapter.REVIEW
            EventStoryAction.NONE -> null
        }
    }

    private fun openStoryThreadPicker(
        event: SportEvent,
        currentChapter: EventStoryChapter?,
        allowClear: Boolean
    ) {
        val dialogNow = System.currentTimeMillis()
        val dialogStory = eventStoryResult(
            event = event,
            now = dialogNow
        )
        val chapters = StoryThreadPolicy.choices(dialogStory)
        if (chapters.isEmpty()) {
            Toast.makeText(
                this,
                "Все главы уже закрыты",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val items = chapters.mapIndexed { index, chapter ->
            buildString {
                if (index == 0 && chapter != currentChapter) {
                    append("РЕКОМЕНДУЕТСЯ • ")
                }
                append(eventStoryChapterTitle(chapter))
                append(" • ")
                append(
                    eventStoryChapterStateTitle(
                        dialogStory.chapter(chapter).state
                    )
                )
                append("\n")
                append(storyThreadQuestion(chapter))
            }
        }.toTypedArray()
        val builder = AlertDialog.Builder(this)
            .setTitle("Выберите один вопрос")
            .setSingleChoiceItems(
                items,
                chapters.indexOf(currentChapter)
            ) { dialog, which ->
                val selected = chapters[which]
                val saveNow = System.currentTimeMillis()
                val saveStory = eventStoryResult(
                    event = event,
                    now = saveNow
                )
                if (
                    !StoryThreadPolicy.isTrackable(
                        saveStory.chapter(selected).state
                    )
                ) {
                    Toast.makeText(
                        this,
                        "Глава уже закрыта — выберите другую",
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    rerenderContentPreservingScroll()
                    return@setSingleChoiceItems
                }
                state.saveStoryThread(
                    StoryThreadFactory.create(
                        story = saveStory,
                        chapter = selected,
                        startedAt = saveNow
                    )
                )
                Toast.makeText(
                    this,
                    "Нить события закреплена",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
                rerenderContentPreservingScroll()
            }
            .setNegativeButton("Отмена", null)
        if (allowClear) {
            builder.setNeutralButton("Удалить нить") { _, _ ->
                state.clearStoryThread(event.id)
                Toast.makeText(
                    this,
                    "Нить события удалена",
                    Toast.LENGTH_SHORT
                ).show()
                rerenderContentPreservingScroll()
            }
        }
        builder.show()
    }

    private fun storyBeaconResult(
        event: SportEvent,
        story: EventStoryResult,
        now: Long
    ): StoryBeaconResult {
        val relay = if (
            story.startAt != null && now < story.startAt
        ) {
            EvidenceRelayEngine.evaluate(
                input = EvidenceRelayInput(
                    event = event,
                    assessment = state.assessment(event),
                    claimedEvidence =
                        state.claimedEvidence(event),
                    sourceAudit = state.sourceAudit(event.id),
                    timeline = state.evidenceTimelinePreview(
                        eventId = event.id,
                        now = now
                    )
                ),
                now = now
            )
        } else {
            null
        }
        val protocol = relay?.let {
            PreflightProtocolEngine.evaluate(event, it)
        }
        return StoryBeaconEngine.evaluate(
            StoryBeaconInput(
                story = story,
                checkSlots = protocol?.slots.orEmpty(),
                factorTransitions = relay?.factors
                    ?.mapNotNull { factor ->
                        factor.firstTransitionAt
                            ?.takeIf {
                                state.hasEvidenceHistory(
                                    eventId = event.id,
                                    factor = factor.factor
                                )
                            }
                            ?.let { at ->
                                StoryBeaconFactorTransition(
                                    factor = factor.factor,
                                    at = at
                                )
                            }
                    }
                    .orEmpty(),
                now = now
            )
        )
    }

    private fun storyBeaconPanel(
        result: StoryBeaconResult
    ): LinearLayout {
        val tone = storyBeaconTone(result.state)
        return card().apply {
            addView(storyBeaconHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    storyBeaconStateTitle(result.state),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    storyBeaconMetric(result),
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    storyBeaconSummary(result),
                    14f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )
            addView(
                StoryBeaconView(this@MainActivity).apply {
                    setResult(result)
                },
                matchFixed(104, top = 4)
            )
            result.moments.forEachIndexed { index, moment ->
                if (index > 0) {
                    addView(
                        divider(),
                        matchFixed(1, top = 10, bottom = 9)
                    )
                }
                addView(
                    storyBeaconMomentRow(
                        index = index,
                        moment = moment
                    )
                )
            }
            if (result.moments.isEmpty()) {
                addView(
                    text(
                        "Новая точка появится только после подтвержденного изменения афиши или локальных данных.",
                        13f,
                        AppColors.muted
                    ),
                    matchWrap(top = 2)
                )
            }
            addView(
                text(
                    "READ-ONLY • БУДУЩИХ ТОЧЕК ${result.timedCount} • " +
                        "SHA-256 ${result.shortFingerprint}",
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 13)
            )
        }
    }

    private fun storyBeaconHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.story_beacon)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Четыре физических временных шлюза связывают проверку, срок факта, старт и разбор"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "МАЯК СОБЫТИЯ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Когда сюжет изменится",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun storyBeaconMomentRow(
        index: Int,
        moment: StoryBeaconMoment
    ): LinearLayout {
        val tone = storyBeaconMomentTone(moment.kind)
        val primary = index == 0
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(
                text(
                    (index + 1).toString(),
                    fixedControlTextSize(13f),
                    if (primary) Color.WHITE else tone.foreground,
                    Typeface.BOLD
                ).apply {
                    gravity = Gravity.CENTER
                    background = rounded(
                        if (primary) {
                            tone.foreground
                        } else {
                            tone.background
                        },
                        16,
                        tone.foreground,
                        1
                    )
                },
                LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    rightMargin = dp(11)
                }
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            storyBeaconMomentTitle(moment),
                            15f,
                            if (primary) {
                                tone.foreground
                            } else {
                                AppColors.ink
                            },
                            Typeface.BOLD
                        )
                    )
                    addView(
                        label(
                            storyBeaconMomentTime(moment),
                            tone.background,
                            tone.foreground
                        ),
                        matchWrap(top = 4)
                    )
                    addView(
                        text(
                            storyBeaconMomentDetail(moment),
                            13f,
                            AppColors.muted
                        ),
                        matchWrap(top = 5)
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun storyBeaconStateTitle(
        beaconState: StoryBeaconState
    ): String {
        return when (beaconState) {
            StoryBeaconState.NO_TIMELINE ->
                "ВРЕМЯ НЕ ПОДТВЕРЖДЕНО"
            StoryBeaconState.ACTION_NOW -> "СИГНАЛ УЖЕ ОТКРЫТ"
            StoryBeaconState.WATCHING -> "БЛИЖАЙШЕЕ ИЗМЕНЕНИЕ"
            StoryBeaconState.EVENT_ACTIVE -> "ИДЕТ ОКНО СОБЫТИЯ"
            StoryBeaconState.REVIEW_DUE -> "ПОРА К РАЗБОРУ"
            StoryBeaconState.COMPLETE -> "МАРШРУТ ЗАКРЫТ"
            StoryBeaconState.INCOMPLETE -> "БУДУЩИХ ТОЧЕК НЕТ"
        }
    }

    private fun storyBeaconMetric(
        result: StoryBeaconResult
    ): String {
        val count = result.moments.size
        val noun = when {
            count == 1 -> "опорный момент"
            count in 2..4 -> "опорных момента"
            else -> "опорных моментов"
        }
        return "$count $noun"
    }

    private fun storyBeaconSummary(
        result: StoryBeaconResult
    ): String {
        return when (result.state) {
            StoryBeaconState.NO_TIMELINE ->
                "Без проверяемого старта Маяк не придумывает календарь."
            StoryBeaconState.ACTION_NOW ->
                "Сначала показано действие сейчас, затем только реальные временные границы."
            StoryBeaconState.WATCHING ->
                "Следующая точка появится из политики свежести или указанного расписания."
            StoryBeaconState.EVENT_ACTIVE ->
                "Предстартовые окна закрыты; впереди только консервативная граница разбора."
            StoryBeaconState.REVIEW_DUE ->
                "Минимальное окно прошло, но фактическое завершение события нужно сверить отдельно."
            StoryBeaconState.COMPLETE ->
                "Сюжет завершен локальной ретроспективой, связанной с предстартовым снимком."
            StoryBeaconState.INCOMPLETE ->
                "Хронология закончилась без доступной следующей главы."
        }
    }

    private fun storyBeaconMomentTitle(
        moment: StoryBeaconMoment
    ): String {
        return when (moment.kind) {
            StoryBeaconMomentKind.ACTION_NOW ->
                eventStoryActionTitle(
                    action = checkNotNull(moment.action),
                    factor = moment.factors.singleOrNull()
                )
            StoryBeaconMomentKind.CHECK_WINDOW ->
                "Окно проверки: ${storyBeaconFactors(moment.factors)}"
            StoryBeaconMomentKind.FACT_EXPIRY ->
                "Срок факта: ${storyBeaconFactors(moment.factors)}"
            StoryBeaconMomentKind.START -> "Указанный старт"
            StoryBeaconMomentKind.REVIEW_OPEN -> "Откроется разбор"
            StoryBeaconMomentKind.COMPLETE -> "История закрыта"
        }
    }

    private fun storyBeaconMomentTime(
        moment: StoryBeaconMoment
    ): String {
        return moment.at?.let { at ->
            TimeBridgeEngine.formatInstant(
                startAt = at,
                selectedZone = state.selectedRegionalZone
            )
        } ?: if (
            moment.kind == StoryBeaconMomentKind.COMPLETE
        ) {
            "ГОТОВО"
        } else {
            "СЕЙЧАС"
        }
    }

    private fun storyBeaconMomentDetail(
        moment: StoryBeaconMoment
    ): String {
        return when (moment.kind) {
            StoryBeaconMomentKind.ACTION_NOW ->
                "Действие доступно в текущем маршруте; Маяк ничего не меняет автоматически."
            StoryBeaconMomentKind.CHECK_WINDOW ->
                "С этого момента повторная проверка сохранит подтверждение до старта."
            StoryBeaconMomentKind.FACT_EXPIRY ->
                "Без нового подтверждения эффективный уровень снизится по открытой политике свежести."
            StoryBeaconMomentKind.START ->
                "Это время из текущей афиши; перенос требует новой проверенной версии."
            StoryBeaconMomentKind.REVIEW_OPEN ->
                "Граница равна старту плюс 4 часа и не считается фактическим финальным свистком."
            StoryBeaconMomentKind.COMPLETE ->
                "Предстартовый снимок и финальный разбор связаны локальной контрольной меткой."
        }
    }

    private fun storyBeaconFactors(
        factors: List<SignalFactor>
    ): String {
        return factors.joinToString(", ") { it.title }
    }

    private fun storyBeaconTone(
        beaconState: StoryBeaconState
    ): Tone {
        return when (beaconState) {
            StoryBeaconState.ACTION_NOW,
            StoryBeaconState.WATCHING ->
                Tone(AppColors.signal, AppColors.signalSoft)
            StoryBeaconState.EVENT_ACTIVE,
            StoryBeaconState.REVIEW_DUE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            StoryBeaconState.COMPLETE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            StoryBeaconState.NO_TIMELINE,
            StoryBeaconState.INCOMPLETE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun storyBeaconMomentTone(
        kind: StoryBeaconMomentKind
    ): Tone {
        return when (kind) {
            StoryBeaconMomentKind.ACTION_NOW,
            StoryBeaconMomentKind.CHECK_WINDOW ->
                Tone(AppColors.signal, AppColors.signalSoft)
            StoryBeaconMomentKind.FACT_EXPIRY ->
                Tone(AppColors.warning, AppColors.warningSoft)
            StoryBeaconMomentKind.START,
            StoryBeaconMomentKind.COMPLETE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            StoryBeaconMomentKind.REVIEW_OPEN ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun openEventStoryAction(
        action: EventStoryAction,
        factor: SignalFactor?
    ) {
        when (action) {
            EventStoryAction.OPEN_SOURCE -> selectTab(0)
            EventStoryAction.NONE -> Unit
            else -> {
                flushAttentionTracking()
                activePulseWorkspaceMode =
                    PulseWorkspaceMode.LAB
                activePulseLabSection = when (action) {
                    EventStoryAction.OPEN_FACTS ->
                        PulseLabSection.FACTS
                    EventStoryAction.OPEN_DECISION,
                    EventStoryAction.OPEN_REVIEW ->
                        PulseLabSection.DECISION
                    EventStoryAction.OPEN_PLAN ->
                        PulseLabSection.ROUTE
                    EventStoryAction.OPEN_SOURCE,
                    EventStoryAction.NONE ->
                        PulseLabSection.ROUTE
                }
                state.selectedPulseWorkspaceMode =
                    PulseWorkspaceMode.LAB
                pendingPulseStoryAction = action
                pendingPulseFactor = factor
                renderContent()
                startAttentionTrackingIfNeeded()
            }
        }
    }

    private fun eventStoryDossier(
        story: EventStoryResult
    ): LinearLayout {
        val tone = eventStoryTone(story.phase)
        return card().apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(
                            "Главы маршрута",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        label(
                            "ГОТОВО ${story.completedCount}/6",
                            tone.background,
                            tone.foreground
                        )
                    )
                }
            )
            story.chapters.forEachIndexed { index, chapter ->
                if (index > 0) {
                    addView(
                        divider(),
                        matchFixed(1, top = 11, bottom = 10)
                    )
                }
                addView(
                    eventStoryDossierRow(
                        chapter = chapter,
                        current = chapter.chapter ==
                            story.currentChapter
                    )
                )
            }
            addView(
                text(
                    "SHA-256 ${story.shortFingerprint}",
                    11.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 13)
            )
        }
    }

    private fun eventStoryDossierRow(
        chapter: EventStoryChapterResult,
        current: Boolean
    ): LinearLayout {
        val tone = eventStoryChapterTone(chapter.state)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(
                text(
                    (chapter.chapter.ordinal + 1).toString(),
                    fixedControlTextSize(13f),
                    if (current) {
                        Color.WHITE
                    } else {
                        tone.foreground
                    },
                    Typeface.BOLD
                ).apply {
                    gravity = Gravity.CENTER
                    background = rounded(
                        if (current) {
                            tone.foreground
                        } else {
                            tone.background
                        },
                        16,
                        tone.foreground,
                        1
                    )
                },
                LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    rightMargin = dp(11)
                }
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            eventStoryChapterTitle(chapter.chapter),
                            15f,
                            if (current) {
                                tone.foreground
                            } else {
                                AppColors.ink
                            },
                            Typeface.BOLD
                        )
                    )
                    addView(
                        label(
                            eventStoryChapterStateTitle(chapter.state),
                            tone.background,
                            tone.foreground
                        ),
                        matchWrap(top = 4)
                    )
                    addView(
                        text(
                            chapter.summary,
                            13f,
                            AppColors.muted
                        ),
                        matchWrap(top = 5)
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun decisionDeskPanel(
        event: SportEvent
    ): LinearLayout {
        val now = System.currentTimeMillis()
        val assessment = state.assessment(event)
        val evidence = state.evidence(event)
        val timeline = state.evidenceTimelinePreview(
            eventId = event.id,
            now = now
        )
        val lens = MarketLensEngine.evaluate(
            sport = event.sport,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val storedDraft = state.decisionDeskDraft(event.id)
        val suggestedMarket = lens.item(activeMarketLensKind)
            ?.takeIf {
                it.status != MarketLensStatus.NOT_APPLICABLE
            }
            ?.guide
            ?.kind
            ?: lens.items.firstOrNull {
                it.status != MarketLensStatus.NOT_APPLICABLE
            }?.guide?.kind
            ?: activeMarketLensKind
        val draft = storedDraft ?: DecisionDeskDraftFactory.create(
            eventId = event.id,
            marketKind = suggestedMarket,
            thesis = "",
            counterargument = "",
            stopCondition = "",
            updatedAt = now
        )
        activeMarketLensKind = draft.marketKind
        val counterView = CounterViewEngine.evaluate(
            assessment = assessment,
            evidence = evidence,
            review = state.counterReview(event.id)
        )
        val result = DecisionDeskEngine.evaluate(
            draft = draft,
            market = lens.item(draft.marketKind),
            counterView = counterView
        )
        decisionDeskOverviewAnchor = null
        decisionDeskWorkspaceAnchor = null
        decisionDeskSectionAnchor = null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                decisionDeskSectionSwitcher(),
                matchWrap()
            )
            when (activeDecisionDeskSection) {
                DecisionDeskSection.DECISION -> {
                    val overview = decisionDeskOverviewPanel(
                        event = event,
                        result = result
                    )
                    decisionDeskOverviewAnchor = overview
                    decisionDeskSectionAnchor = overview
                    addView(overview, matchWrap(top = 10))
                    if (decisionDeskWorkspaceExpanded) {
                        val workspace = decisionDeskDecisionPanel(
                            event = event,
                            draft = draft,
                            result = result,
                            lens = lens
                        )
                        decisionDeskWorkspaceAnchor = workspace
                        addView(workspace, matchWrap(top = 16))
                    }
                }
                DecisionDeskSection.HISTORY -> {
                    val history = decisionDeskHistoryPanel()
                    decisionDeskSectionAnchor = history
                    addView(history, matchWrap(top = 10))
                }
                DecisionDeskSection.PROFILE -> {
                    val profile = decisionDeskProfilePanel()
                    decisionDeskSectionAnchor = profile
                    addView(profile, matchWrap(top = 10))
                }
            }
        }
    }

    private fun decisionDeskOverviewPanel(
        event: SportEvent,
        result: DecisionDeskResult
    ): LinearLayout {
        val tone = decisionDeskTone(result.status)
        val completedFields = DecisionDeskField.values().size -
            result.missingFields.size
        val imageHeight = if (effectiveFontScale() >= 1.8f) {
            164
        } else {
            116
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                imageFrame().apply {
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(
                                R.drawable.decision_preflight_v3130
                            )
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            contentDescription =
                                "Три шага проверки: идея матча, возможное опровержение и условие отказа"
                        },
                        frameMatch()
                    )
                    addView(
                        View(this@MainActivity).apply {
                            setBackgroundColor(
                                Color.argb(82, 8, 15, 17)
                            )
                        },
                        frameMatch()
                    )
                    addView(
                        label(
                            result.status.title,
                            tone.foreground,
                            Color.WHITE
                        ),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.START
                        ).apply {
                            leftMargin = dp(12)
                            topMargin = dp(12)
                        }
                    )
                    addView(
                        label(
                            "ВОПРОСЫ • $completedFields/3",
                            Color.argb(215, 13, 30, 33),
                            Color.WHITE,
                            Color.argb(110, 255, 255, 255)
                        ),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.END
                        ).apply {
                            rightMargin = dp(12)
                            topMargin = dp(12)
                        }
                    )
                    addView(
                        text(
                            result.headline,
                            20f,
                            Color.WHITE,
                            Typeface.BOLD
                        ).apply {
                            maxLines = 2
                            setTextSize(
                                TypedValue.COMPLEX_UNIT_PX,
                                20f *
                                    resources.displayMetrics.density *
                                    min(effectiveFontScale(), 1.35f)
                            )
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM
                        ).apply {
                            leftMargin = dp(12)
                            rightMargin = dp(12)
                            bottomMargin = dp(11)
                        }
                    )
                },
                matchFixed(imageHeight)
            )
            addView(
                text(
                    result.explanation,
                    13.5f,
                    AppColors.ink
                ),
                matchWrap(top = 9)
            )
            addView(
                text(
                    "1. Что ожидаете?  2. Что может опровергнуть?  " +
                        "3. Когда отказаться?",
                    12.5f,
                    AppColors.ink,
                    Typeface.BOLD
                ).apply {
                    background = rounded(AppColors.background, 7)
                    setPadding(dp(11), dp(9), dp(11), dp(9))
                },
                matchWrap(top = 9)
            )
            addView(
                commandButton(
                    result.actionTitle,
                    AppColors.signal
                ) {
                    performDecisionDeskPrimaryAction(
                        event = event,
                        result = result
                    )
                },
                matchWrap(top = 8)
            )
            if (
                result.missingFields.isEmpty() ||
                decisionDeskWorkspaceExpanded
            ) {
                addView(
                    outlineButton(
                        if (decisionDeskWorkspaceExpanded) {
                            "Свернуть рабочую форму"
                        } else {
                            "Открыть рабочую форму"
                        },
                        AppColors.signal
                    ) {
                        decisionDeskWorkspaceExpanded =
                            !decisionDeskWorkspaceExpanded
                        pendingDecisionDeskField = null
                        renderContent()
                        val target = if (
                            decisionDeskWorkspaceExpanded
                        ) {
                            decisionDeskWorkspaceAnchor
                        } else {
                            decisionDeskOverviewAnchor
                        }
                        target?.let {
                            scrollToAppView(it, topOffsetDp = 10)
                        }
                    },
                    matchWrap(top = 7)
                )
            }
        }
    }

    private fun performDecisionDeskPrimaryAction(
        event: SportEvent,
        result: DecisionDeskResult
    ) {
        val field = when {
            result.missingFields.isNotEmpty() ->
                result.missingFields.first()
            result.counterVerdict == CounterViewVerdict.REFUTED ->
                DecisionDeskField.THESIS
            else -> null
        }
        if (
            field != null ||
            result.marketStatus == null ||
            result.marketStatus == MarketLensStatus.NOT_APPLICABLE
        ) {
            decisionDeskWorkspaceExpanded = true
            pendingDecisionDeskField = field
            renderContent()
            decisionDeskWorkspaceAnchor?.let {
                scrollToAppView(it, topOffsetDp = 10)
            }
            return
        }
        result.nextFactor?.let(::openPulseFactor)
            ?: openDecisionDeskLab(
                if (
                    result.status ==
                    DecisionDeskStatus.FACTS_READY
                ) {
                    PulseLabSection.DECISION
                } else {
                    PulseLabSection.FACTS
                }
            )
    }

    private fun decisionDeskSectionSwitcher(): LinearLayout {
        val stackSections =
            resources.configuration.fontScale >= 1.8f ||
                resources.configuration.screenWidthDp < 360
        return LinearLayout(this).apply {
            orientation = if (stackSections) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            if (!stackSections) {
                weightSum = DecisionDeskSection.values().size.toFloat()
            }
            background = rounded(AppColors.field, 8)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            DecisionDeskSection.values().forEachIndexed { index, section ->
                val selected = section == activeDecisionDeskSection
                addView(
                    text(
                        section.title,
                        13f,
                        if (selected) {
                            Color.WHITE
                        } else {
                            AppColors.fieldMuted
                        },
                        Typeface.BOLD
                    ).apply {
                        gravity = Gravity.CENTER
                        minHeight = dp(48)
                        maxLines = 2
                        setPadding(dp(5), 0, dp(5), 0)
                        background = rippleRounded(
                            if (selected) {
                                AppColors.signal
                            } else {
                                AppColors.field
                            },
                            5
                        )
                        applyAccessibleAction(dp(48))
                        isSelected = selected
                        contentDescription =
                            "Штаб: ${section.title}"
                        setOnClickListener {
                            if (section != activeDecisionDeskSection) {
                                activeDecisionDeskSection = section
                                renderContent()
                            }
                        }
                    },
                    if (stackSections) {
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (index > 0) topMargin = dp(4)
                        }
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            dp(48),
                            1f
                        ).apply {
                            if (
                                section !=
                                DecisionDeskSection.PROFILE
                            ) {
                                rightMargin = dp(4)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun decisionDeskDecisionPanel(
        event: SportEvent,
        draft: DecisionDeskDraft,
        result: DecisionDeskResult,
        lens: MarketLensResult
    ): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tone = decisionDeskTone(result.status)
        lateinit var thesisInput: EditText
        lateinit var counterargumentInput: EditText
        lateinit var stopConditionInput: EditText

        panel.addView(
            text(
                "Рабочая форма",
                22f,
                AppColors.ink,
                Typeface.BOLD
            ),
            matchWrap()
        )
        panel.addView(
            text(
                "Ответьте на три вопроса. Сохранение обновит карту данных, но не создаст прогноз.",
                13.5f,
                AppColors.muted
            ),
            matchWrap(top = 6)
        )
        panel.addView(
            decisionDeskQuestionBand(
                number = "01",
                title = "Что изменилось?",
                body = decisionDeskChangeSummary(event),
                color = AppColors.signal
            ),
            matchWrap(top = 14)
        )
        panel.addView(
            decisionDeskQuestionBand(
                number = "02",
                title = "Чего не хватает?",
                body = decisionDeskMissingSummary(
                    draft = draft,
                    result = result
                ),
                color = tone.foreground
            ),
            matchWrap(top = 7)
        )
        panel.addView(
            decisionDeskQuestionBand(
                number = "03",
                title = "Что делать сейчас?",
                body = result.actionTitle,
                color = AppColors.accentDark
            ),
            matchWrap(top = 7)
        )
        panel.addView(
            decisionDeskScenarioFork(
                draft = draft,
                result = result,
                onFieldSelected = { field ->
                    when (field) {
                        DecisionDeskField.THESIS ->
                            focusDecisionDeskInput(thesisInput)
                        DecisionDeskField.COUNTERARGUMENT ->
                            focusDecisionDeskInput(
                                counterargumentInput
                            )
                        DecisionDeskField.STOP_CONDITION ->
                            focusDecisionDeskInput(
                                stopConditionInput
                            )
                    }
                },
                onFactorSelected = ::openPulseFactor
            ),
            matchWrap(top = 18)
        )

        panel.addView(
            text(
                "ТИП ПРОВЕРКИ",
                11f,
                AppColors.muted,
                Typeface.BOLD
            ),
            matchWrap(top = 18)
        )
        panel.addView(
            AdaptiveWrapLayout(this).apply {
                tag = AdaptiveGroupTags.DECISION_MARKETS
                lineSpacingPx = dp(7)
                lens.items.forEach { item ->
                    val kind = item.guide.kind
                    val selected = kind == draft.marketKind
                    val applicable = item.status !=
                        MarketLensStatus.NOT_APPLICABLE
                    addView(
                        text(
                            kind.shortTitle,
                            13f,
                            if (selected) {
                                Color.WHITE
                            } else if (applicable) {
                                AppColors.signal
                            } else {
                                AppColors.muted
                            },
                            Typeface.BOLD
                        ).apply {
                            gravity = Gravity.CENTER
                            minWidth = dp(64)
                            minHeight = dp(48)
                            setPadding(dp(12), 0, dp(12), 0)
                            background = rippleRounded(
                                if (selected) {
                                    AppColors.signal
                                } else {
                                    AppColors.surface
                                },
                                8,
                                if (selected) {
                                    AppColors.signal
                                } else {
                                    AppColors.line
                                },
                                1
                            )
                            applyAccessibleAction(dp(48))
                            isEnabled = applicable
                            alpha = if (applicable) 1f else 0.45f
                            isClickable = applicable
                            isFocusable = applicable
                            isSelected = selected
                            contentDescription = if (applicable) {
                                if (selected) {
                                    "Тип проверки ${kind.shortTitle}, выбрано"
                                } else {
                                    "Тип проверки ${kind.shortTitle}"
                                }
                            } else {
                                "${kind.shortTitle}: не подходит событию"
                            }
                            setOnClickListener {
                                val updated =
                                    decisionDeskDraftFromInputs(
                                        event = event,
                                        marketKind = kind,
                                        thesisInput = thesisInput,
                                        counterargumentInput =
                                            counterargumentInput,
                                        stopConditionInput =
                                            stopConditionInput
                                    )
                                state.saveDecisionDeskDraft(updated)
                                activeMarketLensKind = kind
                                state.selectedMarketKind = kind
                                rerenderContentPreservingScroll()
                            }
                        },
                        wrapWrap(right = 7)
                    )
                }
            },
            matchWrap(top = 7)
        )

        thesisInput = decisionDeskInput(
            value = draft.thesis,
            hint = "Например: хозяева сохранят высокий темп после перерыва",
            maxLength = DecisionDeskDraft.MAX_THESIS_LENGTH
        )
        counterargumentInput = decisionDeskInput(
            value = draft.counterargument,
            hint = "Какой факт сильнее всего опровергнет эту идею?",
            maxLength =
                DecisionDeskDraft.MAX_COUNTERARGUMENT_LENGTH
        )
        stopConditionInput = decisionDeskInput(
            value = draft.stopCondition,
            hint = "Какой наблюдаемый факт сразу отменяет идею?",
            maxLength =
                DecisionDeskDraft.MAX_STOP_CONDITION_LENGTH
        )
        panel.addView(
            decisionDeskInputBlock(
                title = "1. Что ожидаете от матча?",
                input = thesisInput
            ),
            matchWrap(top = 16)
        )
        panel.addView(
            decisionDeskInputBlock(
                title = "2. Что может это опровергнуть?",
                input = counterargumentInput
            ),
            matchWrap(top = 11)
        )
        panel.addView(
            decisionDeskInputBlock(
                title = "3. При каком факте откажетесь?",
                input = stopConditionInput
            ),
            matchWrap(top = 11)
        )
        panel.addView(
            commandButton(
                "Сохранить и пересчитать",
                AppColors.signal
            ) {
                val updated = decisionDeskDraftFromInputs(
                    event = event,
                    marketKind = draft.marketKind,
                    thesisInput = thesisInput,
                    counterargumentInput = counterargumentInput,
                    stopConditionInput = stopConditionInput
                )
                state.saveDecisionDeskDraft(updated)
                hideKeyboard()
                renderContent()
                decisionDeskOverviewAnchor?.let {
                    scrollToAppView(it, topOffsetDp = 10)
                }
            },
            matchWrap(top = 15)
        )
        panel.addView(
            outlineButton(
                "Свернуть рабочую форму",
                AppColors.signal
            ) {
                decisionDeskWorkspaceExpanded = false
                pendingDecisionDeskField = null
                renderContent()
                decisionDeskOverviewAnchor?.let {
                    scrollToAppView(it, topOffsetDp = 10)
                }
            },
            matchWrap(top = 8)
        )
        if (state.decisionDeskDraft(event.id) != null) {
            panel.addView(
                text(
                    "План ${draft.shortFingerprint} • хранится только на устройстве.",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 9)
            )
        }
        panel.addView(
            decisionDeskGuidePanel(),
            matchWrap(top = 16)
        )
        pendingDecisionDeskField?.let { field ->
            val target = when (field) {
                DecisionDeskField.THESIS -> thesisInput
                DecisionDeskField.COUNTERARGUMENT ->
                    counterargumentInput
                DecisionDeskField.STOP_CONDITION ->
                    stopConditionInput
            }
            pendingDecisionDeskField = null
            panel.post {
                focusDecisionDeskInput(target)
                scrollToAppView(target, topOffsetDp = 18)
            }
        }
        return panel
    }

    private fun decisionDeskScenarioFork(
        draft: DecisionDeskDraft,
        result: DecisionDeskResult,
        onFieldSelected: (DecisionDeskField) -> Unit,
        onFactorSelected: (SignalFactor) -> Unit
    ): LinearLayout {
        val fork = ScenarioForkEngine.evaluate(
            draft = draft,
            decision = result
        )
        val tone = scenarioForkTone(fork.state)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                label(
                    fork.state.badgeTitle,
                    tone.background,
                    tone.foreground
                )
            )
            addView(
                text(
                    "Развилка матча",
                    21f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 7)
            )
            addView(
                text(
                    fork.headline,
                    15f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
            addView(
                text(
                    fork.explanation,
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
            addView(
                imageFrame().apply {
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(
                                R.drawable.scenario_fork
                            )
                            scaleType =
                                ImageView.ScaleType.CENTER_CROP
                            contentDescription =
                                "Два равноправных сценария матча, общий центр доказательств и красная стоп-линия"
                        },
                        frameMatch()
                    )
                },
                matchFixed(176, top = 12)
            )
            addView(
                scenarioForkRow(
                    marker = "A",
                    title = "Сценарий A",
                    body = fork.primaryScenario,
                    color = AppColors.accentDark,
                    onClick = {
                        onFieldSelected(DecisionDeskField.THESIS)
                    }
                ),
                matchWrap(top = 9)
            )
            addView(
                scenarioForkRow(
                    marker = "B",
                    title = "Сценарий B",
                    body = fork.alternativeScenario,
                    color = AppColors.signal,
                    onClick = {
                        onFieldSelected(
                            DecisionDeskField.COUNTERARGUMENT
                        )
                    }
                ),
                matchWrap(top = 7)
            )
            addView(
                scenarioForkRow(
                    marker = "!",
                    title = "Стоп-линия",
                    body = fork.stopCondition,
                    color = AppColors.danger,
                    onClick = {
                        onFieldSelected(
                            DecisionDeskField.STOP_CONDITION
                        )
                    }
                ),
                matchWrap(top = 7)
            )
            fork.distinguishingFactor?.let { factor ->
                addView(
                    scenarioForkRow(
                        marker = "04",
                        title = "Различающий факт",
                        body = factor.title,
                        color = tone.foreground,
                        onClick = {
                            onFactorSelected(factor)
                        }
                    ),
                    matchWrap(top = 7)
                )
            }
        }
    }

    private fun scenarioForkRow(
        marker: String,
        title: String,
        body: String,
        color: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = rippleRounded(
                AppColors.background,
                6,
                AppColors.line,
                1
            )
            setPadding(dp(11), dp(10), dp(11), dp(10))
            applyAccessibleAction(dp(48))
            contentDescription = "$title: $body"
            setOnClickListener { onClick() }
            addView(
                text(
                    marker,
                    fixedControlTextSize(11.5f),
                    color,
                    Typeface.BOLD
                ).apply {
                    gravity = Gravity.CENTER
                    background = rounded(
                        Color.TRANSPARENT,
                        16,
                        color,
                        1
                    )
                },
                LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    rightMargin = dp(10)
                }
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            title.uppercase(
                                Locale.getDefault()
                            ),
                            10.5f,
                            color,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            body,
                            13.5f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun decisionDeskQuestionBand(
        number: String,
        title: String,
        body: String,
        color: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = rounded(AppColors.background, 6)
            setPadding(dp(11), dp(10), dp(11), dp(10))
            addView(
                text(
                    number,
                    11f,
                    color,
                    Typeface.BOLD
                ).apply {
                    gravity = Gravity.CENTER
                    minWidth = dp(32)
                    minHeight = dp(28)
                    background = rounded(
                        Color.TRANSPARENT,
                        14,
                        color,
                        1
                    )
                },
                wrapWrap(right = 10)
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            title,
                            12f,
                            AppColors.muted,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            body,
                            14f,
                            AppColors.ink,
                            Typeface.BOLD
                        ).apply {
                            maxLines = 5
                        },
                        matchWrap(top = 2)
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun decisionDeskInputBlock(
        title: String,
        input: EditText
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(
                    title.uppercase(Locale.getDefault()),
                    11f,
                    AppColors.muted,
                    Typeface.BOLD
                )
            )
            addView(input, matchWrap(top = 6))
        }
    }

    private fun decisionDeskInput(
        value: String,
        hint: String,
        maxLength: Int
    ): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
            textSize = 14f
            setTextColor(AppColors.ink)
            setHintTextColor(AppColors.muted)
            typeface = AppTypography.forText(
                this@MainActivity,
                Typeface.NORMAL
            )
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            minLines = 2
            maxLines = 5
            isVerticalScrollBarEnabled = true
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(
                AppColors.background,
                7,
                AppColors.line,
                1
            )
        }
    }

    private fun decisionDeskDraftFromInputs(
        event: SportEvent,
        marketKind: MarketKind,
        thesisInput: EditText,
        counterargumentInput: EditText,
        stopConditionInput: EditText
    ): DecisionDeskDraft {
        return DecisionDeskDraftFactory.create(
            eventId = event.id,
            marketKind = marketKind,
            thesis = thesisInput.text?.toString().orEmpty(),
            counterargument =
                counterargumentInput.text?.toString().orEmpty(),
            stopCondition =
                stopConditionInput.text?.toString().orEmpty(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun decisionDeskHistoryPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                card().apply {
                    addView(
                        text(
                            "История процесса, а не выигрышей",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Здесь видны решения, принятые до события. Коэффициенты, суммы и результат матча в журнал не входят.",
                            13f,
                            AppColors.muted
                        ),
                        matchWrap(top = 5)
                    )
                }
            )
            addView(
                decisionLedgerPanel(),
                matchWrap(top = 10)
            )
        }
    }

    private fun decisionDeskProfilePanel(): LinearLayout {
        val read = state.decisionLedger()
        val ledger = read.ledger
        val profile = ledger?.let {
            DecisionDeskProfileEngine.create(
                ledger = it,
                calibrationRecords = state.calibrationRecords()
            )
        }
        return card(padding = 12).apply {
            if (
                read.integrity ==
                DecisionLedgerIntegrity.TAMPERED ||
                profile == null
            ) {
                addView(
                    text(
                        "Профиль проверки",
                        22f,
                        AppColors.ink,
                        Typeface.BOLD
                    )
                )
                addView(
                    text(
                        "Журнал недоступен: локальная цепочка не прошла проверку целостности.",
                        14f,
                        AppColors.danger,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(
                            AppColors.dangerSoft,
                            7
                        )
                        setPadding(
                            dp(12),
                            dp(11),
                            dp(12),
                            dp(11)
                        )
                    },
                    matchWrap(top = 14)
                )
                return@apply
            }

            val tone = if (profile.reviewedEvents == 0) {
                Tone(AppColors.signal, AppColors.signalSoft)
            } else {
                calibrationMemoryTone(
                    profile.calibrationMemory.status
                )
            }
            addView(
                decisionDeskProfileHero(profile, tone),
                matchFixed(
                    if (effectiveFontScale() >= 1.8f) 176 else 144
                )
            )
            addView(
                text(
                    "Профиль проверки",
                    22f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 14)
            )
            addView(
                text(
                    "Показывает, замыкаете ли вы цикл до и после матча и какой навык проверки взять следующим. Доходность, коэффициенты и угаданные исходы не считаются.",
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    "ЦИКЛ РЕШЕНИЯ",
                    11f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 16)
            )
            addView(
                decisionDeskProfileCycle(profile),
                matchWrap(top = 7)
            )
            if (profile.visibleDecisionCount > 0) {
                addView(
                    text(
                        "Замкнут цикл • ${profile.reviewCoveragePercent}%",
                        12.5f,
                        AppColors.ink,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 10)
                )
                addView(
                    horizontalProgress().apply {
                        max = profile.visibleDecisionCount
                        progress = profile.linkedReviewCount
                        progressTintList =
                            ColorStateList.valueOf(tone.foreground)
                    },
                    matchFixed(7, top = 5)
                )
                addView(
                    text(
                        "Связано с завершённым разбором того же снимка: ${profile.linkedReviewCount} из ${profile.visibleDecisionCount} решений в доступном окне.",
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 5)
                )
            }
            addView(
                text(
                    "СТАТУСЫ В ДОСТУПНОМ ОКНЕ • ${profile.visibleDecisionCount}",
                    11f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 17)
            )
            addView(
                decisionDeskProfileBar(
                    title = "Стоп",
                    count = profile.stopCount,
                    total = profile.visibleDecisionCount,
                    color = AppColors.danger
                ),
                matchWrap(top = 8)
            )
            addView(
                decisionDeskProfileBar(
                    title = "Наблюдать",
                    count = profile.observeCount,
                    total = profile.visibleDecisionCount,
                    color = AppColors.warning
                ),
                matchWrap(top = 9)
            )
            addView(
                decisionDeskProfileBar(
                    title = "Факты готовы",
                    count = profile.readyCount,
                    total = profile.visibleDecisionCount,
                    color = AppColors.accent
                ),
                matchWrap(top = 9)
            )
            addView(
                decisionDeskProfileInsight(profile, tone),
                matchWrap(top = 15)
            )
            addView(
                commandButton(
                    if (profile.totalDecisions == 0L) {
                        "Собрать первое решение"
                    } else {
                        "Добавить разбор после матча"
                    },
                    AppColors.signal
                ) {
                    if (profile.totalDecisions == 0L) {
                        activeDecisionDeskSection =
                            DecisionDeskSection.DECISION
                        renderContent()
                    } else {
                        selectTab(1)
                    }
                },
                matchWrap(top = 12)
            )
            addView(
                outlineButton(
                    if (profile.totalDecisions == 0L) {
                        "Открыть гайд"
                    } else {
                        "Открыть историю решений"
                    },
                    AppColors.signal
                ) {
                    if (profile.totalDecisions == 0L) {
                        selectTab(3)
                    } else {
                        activeDecisionDeskSection =
                            DecisionDeskSection.HISTORY
                        renderContent()
                    }
                },
                matchWrap(top = 8)
            )
        }
    }

    private fun decisionDeskProfileHero(
        profile: DecisionDeskProfile,
        tone: Tone
    ): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(
                        R.drawable.process_profile_cycle_v3140
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Цикл дисциплины: решение до матча, проверка после матча и корректировка следующего подхода"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                label(
                    decisionDeskProfileBadge(profile),
                    tone.foreground,
                    Color.WHITE
                ),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    leftMargin = dp(12)
                    topMargin = dp(12)
                }
            )
            addView(
                text(
                    "Решение → разбор → следующий навык",
                    19f,
                    Color.WHITE,
                    Typeface.BOLD
                ).apply {
                    maxLines = 2
                    setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        19f *
                            resources.displayMetrics.density *
                            min(effectiveFontScale(), 1.35f)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun decisionDeskProfileBadge(
        profile: DecisionDeskProfile
    ): String {
        if (profile.totalDecisions == 0L) return "СТАРТ"
        if (profile.reviewedEvents == 0) return "НУЖЕН РАЗБОР"
        return when (profile.calibrationMemory.status) {
            CalibrationMemoryStatus.LEARNING ->
                "БАЗА • ${profile.reviewedEvents.coerceAtMost(3)}/3"
            CalibrationMemoryStatus.STABLE -> "УСТОЙЧИВО"
            CalibrationMemoryStatus.UNEVEN -> "НУЖЕН ФОКУС"
            CalibrationMemoryStatus.BLIND_SPOT -> "СЛЕПАЯ ЗОНА"
        }
    }

    private fun decisionDeskProfileCycle(
        profile: DecisionDeskProfile
    ): LinearLayout {
        val stack =
            resources.configuration.screenWidthDp < 380 ||
                effectiveFontScale() >= 1.3f
        val metrics = listOf(
            Triple(
                profile.visibleDecisionCount.toString(),
                "До матча",
                "решений в окне"
            ),
            Triple(
                profile.linkedReviewCount.toString(),
                "После матча",
                "связанных разборов"
            ),
            Triple(
                "${profile.reviewCoveragePercent}%",
                "Связь",
                if (profile.visibleDecisionCount == 0) {
                    "появится после записи"
                } else {
                    "цикла замкнуто"
                }
            )
        )
        return LinearLayout(this).apply {
            orientation = if (stack) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            background = rounded(AppColors.background, 7)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            metrics.forEachIndexed { index, metric ->
                addView(
                    decisionDeskProfileCycleMetric(
                        value = metric.first,
                        title = metric.second,
                        caption = metric.third,
                        color = when (index) {
                            0 -> AppColors.signal
                            1 -> AppColors.accentDark
                            else -> AppColors.ink
                        }
                    ),
                    if (stack) {
                        matchWrap(top = if (index == 0) 0 else 10)
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            if (index > 0) leftMargin = dp(8)
                        }
                    }
                )
            }
        }
    }

    private fun decisionDeskProfileCycleMetric(
        value: String,
        title: String,
        caption: String,
        color: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(
                    value,
                    25f,
                    color,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    title,
                    12f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 1)
            )
            addView(
                text(
                    caption,
                    11f,
                    AppColors.muted
                ),
                matchWrap(top = 1)
            )
        }
    }

    private fun decisionDeskProfileInsight(
        profile: DecisionDeskProfile,
        tone: Tone
    ): LinearLayout {
        val memory = profile.calibrationMemory
        val message = when {
            profile.totalDecisions == 0L ->
                "Сначала зафиксируйте одно решение до матча: ожидание, возможное опровержение и условие отказа."
            profile.reviewedEvents == 0 ->
                "Вернитесь после завершившегося матча и разберите те же пять факторов. Только так решение превращается в опыт."
            profile.reviewCoveragePercent < 50 ->
                "Закройте ещё ${profile.openCycleCount} незавершённых циклов в текущем окне. Сравнивайте разбор с исходным снимком, а не с памятью о нём."
            memory.status == CalibrationMemoryStatus.LEARNING -> {
                val remaining = (3 - memory.reviewCount)
                    .coerceAtLeast(0)
                if (remaining > 0) {
                    "Завершите ещё $remaining ${calibrationReviewWord(remaining)}: после трёх разборов профиль сможет показать повторяющийся слабый фактор."
                } else {
                    "Заполняйте все пять факторов после матча: данных пока недостаточно, чтобы назвать устойчивую слепую зону."
                }
            }
            memory.status == CalibrationMemoryStatus.STABLE ->
                "Процесс устойчив на доступной серии. Сохраняйте тот же порядок и проверяйте, не появляется ли новый слабый фактор."
            else -> {
                val focus = memory.focusProfile
                if (focus == null) {
                    "В следующем разборе заполните все пять факторов и отдельно отметьте критический промах."
                } else {
                    "Перепроверьте фактор «${focus.factor.title}»: заранее запишите источник, альтернативное объяснение и условие отказа."
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tone.background, 7)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            addView(
                text(
                    "СЛЕДУЮЩИЙ НАВЫК",
                    10.5f,
                    tone.foreground,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    message,
                    13f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 4)
            )
        }
    }

    private fun decisionDeskProfileBar(
        title: String,
        count: Int,
        total: Int,
        color: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(
                            title,
                            13f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        text(
                            count.toString(),
                            13f,
                            color,
                            Typeface.BOLD
                        )
                    )
                }
            )
            addView(
                horizontalProgress().apply {
                    max = total.coerceAtLeast(1)
                    progress = count
                    progressTintList =
                        ColorStateList.valueOf(color)
                },
                matchFixed(7, top = 5)
            )
        }
    }

    private fun decisionDeskGuidePanel(): LinearLayout {
        val steps = listOf(
            "Выберите тип проверки",
            "Запишите, что ожидаете от матча",
            "Укажите факт, который может это опровергнуть",
            "Задайте наблюдаемое условие отказа",
            "Проверьте пять факторов и зафиксируйте статус"
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                AppColors.field,
                7
            )
            setPadding(dp(13), dp(13), dp(13), dp(13))
            addView(
                text(
                    "МАРШРУТ ШТАБА",
                    11f,
                    AppColors.fieldSignal,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    "Пять шагов до проверяемого решения",
                    17f,
                    Color.WHITE,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
            steps.forEachIndexed { index, step ->
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                        addView(
                            text(
                                (index + 1).toString(),
                                11f,
                                Color.WHITE,
                                Typeface.BOLD
                            ).apply {
                                gravity = Gravity.CENTER
                                minWidth = dp(25)
                                minHeight = dp(25)
                                background = rounded(
                                    AppColors.signal,
                                    13
                                )
                            },
                            wrapWrap(right = 9)
                        )
                        addView(
                            text(
                                step,
                                13f,
                                Color.WHITE,
                                Typeface.BOLD
                            ),
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        )
                    },
                    matchWrap(top = if (index == 0) 11 else 8)
                )
            }
            addView(
                outlineButton(
                    "Открыть полный гид",
                    AppColors.fieldSignal
                ) {
                    selectTab(3)
                }.apply {
                    setTextColor(Color.WHITE)
                    background = rippleRounded(
                        AppColors.fieldRaised,
                        7,
                        AppColors.fieldSignal,
                        1
                    )
                },
                matchWrap(top = 13)
            )
        }
    }

    private fun decisionDeskChangeSummary(
        event: SportEvent
    ): String {
        val fixtureId = event.providerRef?.toLongOrNull()
        val apiChange = fixtureId?.let { id ->
            apiFootballDelta?.changes?.firstOrNull {
                it.fixtureId == id
            }
        }
        if (apiChange != null) {
            return apiChange.kinds.joinToString(" • ") { kind ->
                when (kind) {
                    ApiFootballChangeKind.SCORE ->
                        "обновлён счёт"
                    ApiFootballChangeKind.STATUS ->
                        "изменился статус"
                    ApiFootballChangeKind.START_TIME ->
                        "перенесено время"
                    ApiFootballChangeKind.NEW_IN_FEED ->
                        "матч появился в ленте"
                    ApiFootballChangeKind.MISSING_FROM_FEED ->
                        "матч исчез из ленты"
                }
            }.replaceFirstChar { it.uppercase() }
        }
        val packageChange = eventPackageDelta?.changes
            ?.firstOrNull { it.eventId == event.id }
        if (packageChange != null) {
            val details = buildList {
                if (packageChange.isRescheduled) {
                    add("перенесено время")
                }
                if (packageChange.assessmentChanges.isNotEmpty()) {
                    add("изменена карта факторов")
                }
                if (packageChange.detailChanges.isNotEmpty()) {
                    add("обновлены детали")
                }
                packageChange.presence?.let {
                    add("изменился состав ленты")
                }
            }
            if (details.isNotEmpty()) {
                return details.joinToString(" • ")
                    .replaceFirstChar { it.uppercase() }
            }
        }
        return when (event.origin) {
            SportEventOrigin.API_SPORTS ->
                "После последней синхронизации заметных изменений нет."
            SportEventOrigin.EVENT_PACKAGE ->
                "В последнем пакете заметных изменений нет."
            SportEventOrigin.DEMO ->
                "Внешних обновлений нет: проверьте свежесть источников вручную."
        }
    }

    private fun decisionDeskMissingSummary(
        draft: DecisionDeskDraft,
        result: DecisionDeskResult
    ): String {
        result.missingFields.firstOrNull()?.let {
            return "Не заполнено: ${it.title}."
        }
        if (
            result.marketStatus ==
            MarketLensStatus.NOT_APPLICABLE
        ) {
            return "Выбранный тип рынка не подходит этому виду спорта."
        }
        result.nextFactor?.let {
            return "Нужна проверка: ${it.title.lowercase(Locale.getDefault())}."
        }
        return when (result.status) {
            DecisionDeskStatus.STOP ->
                "Исходная идея не прошла независимую проверку."
            DecisionDeskStatus.OBSERVE ->
                "Нужно разрешить спор между фактами."
            DecisionDeskStatus.FACTS_READY ->
                "Критических пробелов сейчас нет."
        }
    }

    private fun decisionDeskTone(
        status: DecisionDeskStatus
    ): Tone {
        return when (status) {
            DecisionDeskStatus.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            DecisionDeskStatus.OBSERVE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            DecisionDeskStatus.FACTS_READY ->
                Tone(AppColors.accent, AppColors.accentSoft)
        }
    }

    private fun scenarioForkTone(
        state: ScenarioForkState
    ): Tone {
        return when (state) {
            ScenarioForkState.PRIMARY_REQUIRED,
            ScenarioForkState.ALTERNATIVE_REQUIRED,
            ScenarioForkState.STOP_REQUIRED,
            ScenarioForkState.OPEN ->
                Tone(AppColors.warning, AppColors.warningSoft)
            ScenarioForkState.EVIDENCE_REQUIRED ->
                Tone(AppColors.signal, AppColors.signalSoft)
            ScenarioForkState.BLOCKED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            ScenarioForkState.VERIFIED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun focusDecisionDeskInput(input: EditText) {
        input.requestFocus()
        input.post {
            val manager = getSystemService(
                INPUT_METHOD_SERVICE
            ) as InputMethodManager
            manager.showSoftInput(input, 0)
        }
    }

    private fun hideKeyboard() {
        currentFocus?.windowToken?.let { token ->
            val manager = getSystemService(
                INPUT_METHOD_SERVICE
            ) as InputMethodManager
            manager.hideSoftInputFromWindow(token, 0)
        }
    }

    private fun openDecisionDeskLab(
        section: PulseLabSection
    ) {
        activeDecisionDeskSection =
            DecisionDeskSection.DECISION
        decisionDeskWorkspaceExpanded = false
        pendingDecisionDeskField = null
        activePulseWorkspaceMode = PulseWorkspaceMode.LAB
        activePulseLabSection = section
        state.selectedPulseWorkspaceMode =
            PulseWorkspaceMode.LAB
        pendingPulseFactor = null
        pendingPulseStoryAction = null
        selectTab(1, scrollToContent = false)
        pulseLabNavigatorAnchor?.let { target ->
            scrollToAppView(target, topOffsetDp = 10)
        }
    }

    private fun renderPulse() {
        pulseWorkspaceControlsAnchor = null
        pulseLabNavigatorAnchor = null
        content.addView(
            sectionTitle(
                "Штаб решения",
                "Идея, возражение, условие отказа. Один проверяемый шаг."
            )
        )

        val event = catalogEvent(state.selectedEventId)
        if (state.selectedEventId == null) state.selectedEventId = event.id
        analysisEventAnchor = analysisEventHeader(event)
        content.addView(
            checkNotNull(analysisEventAnchor),
            matchWrap(top = 12)
        )
        content.addView(
            decisionDeskPanel(event),
            matchWrap(top = 12)
        )
        if (
            activeDecisionDeskSection !=
            DecisionDeskSection.DECISION
        ) {
            return
        }
        val workspaceControls = pulseWorkspaceControls()
        pulseWorkspaceControlsAnchor = workspaceControls
        content.addView(
            workspaceControls,
            matchWrap(top = 12)
        )
        content.addView(plainAnalyticsPanel(event), matchWrap(top = 12))
        if (
            activePulseWorkspaceMode ==
            PulseWorkspaceMode.STORY
        ) {
            renderPulseStoryMode(event)
            return
        }

        val labNow = System.currentTimeMillis()
        val labPaused = state.isPauseActive(labNow)
        val labSections = PulseLabSection.values().associateWith {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
        }
        if (!labPaused) {
            val navigator = pulseLabNavigatorPanel(
                pulseLabNavigatorSummary(event, labNow)
            )
            pulseLabNavigatorAnchor = navigator
            content.addView(
                navigator,
                matchWrap(top = 12)
            )
            content.addView(
                checkNotNull(labSections[activePulseLabSection]),
                matchWrap()
            )
        }
        fun addLabPanel(
            section: PulseLabSection,
            view: View
        ) {
            val parent = if (labPaused) {
                content
            } else {
                checkNotNull(labSections[section])
            }
            parent.addView(view, matchWrap(top = 12))
        }

        val factorAnchors = mutableMapOf<SignalFactor, View>()
        var decisionJournalTarget: View? = null
        var postEventReviewTarget: View? = null

        val evidenceRelayPanel = evidenceRelayPanel(
            onAction = ::openPulseFactor
        )
        val initialRelayNow = System.currentTimeMillis()
        val initialRelayResult = EvidenceRelayEngine.evaluate(
            input = EvidenceRelayInput(
                event = event,
                assessment = state.assessment(event),
                claimedEvidence = state.claimedEvidence(event),
                sourceAudit = state.sourceAudit(event.id),
                timeline = state.evidenceTimelinePreview(
                    eventId = event.id,
                    now = initialRelayNow
                )
            ),
            now = initialRelayNow
        )
        renderEvidenceRelay(
            panel = evidenceRelayPanel,
            result = initialRelayResult,
            event = event,
            now = initialRelayNow
        )
        val preflightProtocolPanel = preflightProtocolPanel(
            onExport = { protocol ->
                sharePreflightProtocol(
                    event = event,
                    protocol = protocol
                )
            }
        )
        renderPreflightProtocol(
            panel = preflightProtocolPanel,
            protocol = initialRelayResult?.let { relay ->
                PreflightProtocolEngine.evaluate(
                    event = event,
                    relay = relay
                )
            },
            event = event,
            now = initialRelayNow
        )
        val eventStoryPanel = eventStoryPanel(
            onAction = eventStoryAction@{ action, factor ->
                val targetSection = when (action) {
                    EventStoryAction.OPEN_FACTS ->
                        PulseLabSection.FACTS
                    EventStoryAction.OPEN_DECISION,
                    EventStoryAction.OPEN_REVIEW ->
                        PulseLabSection.DECISION
                    EventStoryAction.OPEN_PLAN ->
                        PulseLabSection.ROUTE
                    EventStoryAction.OPEN_SOURCE,
                    EventStoryAction.NONE -> null
                }
                if (
                    targetSection != null &&
                    targetSection != activePulseLabSection
                ) {
                    activePulseLabSection = targetSection
                    pendingPulseFactor = factor
                    pendingPulseStoryAction = action
                    renderContent()
                    return@eventStoryAction
                }
                val target = when (action) {
                    EventStoryAction.OPEN_SOURCE -> {
                        selectTab(0)
                        null
                    }
                    EventStoryAction.OPEN_FACTS ->
                        factor?.let { factorAnchors[it] }
                            ?: evidenceRelayPanel.root
                    EventStoryAction.OPEN_PLAN ->
                        preflightProtocolPanel.root
                    EventStoryAction.OPEN_DECISION ->
                        decisionJournalTarget
                    EventStoryAction.OPEN_REVIEW ->
                        postEventReviewTarget
                    EventStoryAction.NONE -> null
                }
                if (action != EventStoryAction.OPEN_SOURCE) {
                    target?.let(::scrollToPulseFactor) ?: run {
                        if (action != EventStoryAction.NONE) {
                            Toast.makeText(
                                this,
                                "Глава сейчас недоступна",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onShare = {
                val posterNow = System.currentTimeMillis()
                shareEventStoryPoster(
                    event = event,
                    story = eventStoryResult(
                        event = event,
                        now = posterNow
                    ),
                    generatedAt = posterNow
                )
            }
        )
        renderEventStory(
            panel = eventStoryPanel,
            result = eventStoryResult(
                event = event,
                now = initialRelayNow
            ),
            event = event,
            now = initialRelayNow,
            interactionLocked = state.isPauseActive(initialRelayNow)
        )
        addLabPanel(PulseLabSection.ROUTE, eventStoryPanel.root)
        addLabPanel(PulseLabSection.ROUTE, evidenceRelayPanel.root)
        addLabPanel(PulseLabSection.ROUTE, preflightProtocolPanel.root)

        if (labPaused) {
            content.addView(pauseLockedCard(), matchWrap(top = 12))
            val reviewPanel = postEventReviewPanel(
                event = event,
                onReviewFinalized =
                    ::rerenderContentPreservingScroll
            )
            postEventReviewTarget = reviewPanel
            content.addView(reviewPanel, matchWrap(top = 12))
            return
        }

        var assessment = state.assessment(event)
        var claimedEvidence = state.claimedEvidence(event)
        var sourceAudit = state.sourceAudit(event.id)
        var sourceIntegrity = SourceIntegrityEngine.evaluate(
            claimedEvidence = claimedEvidence,
            audit = sourceAudit
        )
        var evidence = sourceIntegrity.effectiveEvidence
        var evidenceTimeline = state.evidenceTimeline(event.id)
        var counterReview = state.counterReview(event.id)
        var counterView = CounterViewEngine.evaluate(
            assessment = assessment,
            evidence = FreshnessEngine.evaluate(
                evidence = evidence,
                timeline = evidenceTimeline,
                now = System.currentTimeMillis()
            ).effectiveEvidence,
            review = counterReview
        )
        lateinit var refreshSignal: () -> Unit
        val verificationCommandPanel =
            verificationCommandPanel { task ->
                task.factor?.let(::openPulseFactor)
            }
        addLabPanel(
            PulseLabSection.ROUTE,
            verificationCommandPanel.root
        )
        val dataDuelOpponent = dataDuelOpponent(event)
        val dataDuelPanel = dataDuelOpponent?.let { opponent ->
            dataDuelPanel(
                leftEvent = event,
                rightEvent = opponent,
                onChooseOpponent = {
                    showDataDuelOpponentDialog(event)
                },
                onSwap = {
                    state.selectedEventId = opponent.id
                    state.dataDuelOpponentId = event.id
                    rerenderContentPreservingScroll()
                }
            )
        }
        addLabPanel(
            PulseLabSection.FACTS,
            dataDuelPanel?.root
                ?: dataDuelUnavailablePanel(event)
        )
        val radar = SignalRadarView(this)
        val readinessValue = text("0", 44f, AppColors.ink, Typeface.BOLD)
        val verdictBadge = label("", AppColors.warningSoft, AppColors.warning)
        val noiseValue = text("", 13f, AppColors.muted, Typeface.BOLD)
        val noiseBar = horizontalProgress()
        val quorumStatus = text("", 13f, AppColors.ink, Typeface.BOLD)
        val freshnessStatus = text("", 13f, AppColors.ink, Typeface.BOLD)
        val explanation = text("", 14f, AppColors.ink)

        val signalCard = card()
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text("Ручная оценка проверки", 19f, AppColors.ink, Typeface.BOLD))
                    addView(text("0–100 • не вероятность", 12f, AppColors.muted), matchWrap(top = 1))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(verdictBadge)
        }
        signalCard.addView(heading)
        signalCard.addView(readinessValue, matchWrap(top = 2))
        signalCard.addView(
            text(
                "Число складывается из ваших оценок пяти заметок и ограничивается подтверждениями. Оно не показывает шанс исхода или силу команды.",
                12.5f,
                AppColors.muted
            ),
            matchWrap(top = 4)
        )
        signalCard.addView(
            radar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(286)
            )
        )
        signalCard.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    text("Шум данных", 13f, AppColors.muted, Typeface.BOLD),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(noiseValue)
            }
        )
        signalCard.addView(noiseBar, matchFixed(7, top = 7))
        signalCard.addView(quorumStatus, matchWrap(top = 12))
        signalCard.addView(freshnessStatus, matchWrap(top = 8))
        signalCard.addView(explanation, matchWrap(top = 8))
        addLabPanel(PulseLabSection.FACTS, signalCard)

        var chronoOffsetMinutes = 0
        var chronoNextOffsetMinutes: Int? = null
        val chronoLensPanel = chronoLensPanel(
            onOffsetSelected = { minutes ->
                chronoOffsetMinutes = minutes
                refreshSignal()
            },
            onNow = {
                chronoOffsetMinutes = 0
                refreshSignal()
            },
            onNext = {
                chronoNextOffsetMinutes?.let { minutes ->
                    chronoOffsetMinutes = minutes
                    refreshSignal()
                }
            }
        )
        addLabPanel(PulseLabSection.FACTS, chronoLensPanel.root)

        addLabPanel(
            PulseLabSection.FACTS,
            sourceIntegrityPanel(
                result = sourceIntegrity,
                onAuditChanged = { factor, auditState ->
                    state.invalidateFactReceipt(event.id, factor)
                    sourceAudit = sourceAudit.withState(
                        factor,
                        auditState
                    )
                    state.saveSourceAuditState(
                        eventId = event.id,
                        factor = factor,
                        auditState = auditState
                    )
                    sourceIntegrity = SourceIntegrityEngine.evaluate(
                        claimedEvidence = claimedEvidence,
                        audit = sourceAudit
                    )
                    evidence = sourceIntegrity.effectiveEvidence
                    rerenderContentPreservingScroll()
                }
            )
        )

        val counterViewPanel = counterViewPanel(
            result = counterView,
            onReviewChanged = { factor, reviewState ->
                counterReview = counterReview.withState(
                    factor,
                    reviewState
                )
                state.saveCounterReviewState(
                    eventId = event.id,
                    factor = factor,
                    reviewState = reviewState
                )
                rerenderContentPreservingScroll()
            }
        )
        addLabPanel(PulseLabSection.FACTS, counterViewPanel.root)

        var shadowActionFactor: SignalFactor? = null
        val shadowPanel = confidenceShadowPanel {
            shadowActionFactor?.let { factor ->
                factorAnchors[factor]?.let(::scrollToPulseFactor)
            }
        }
        addLabPanel(PulseLabSection.FACTS, shadowPanel.root)

        var corridorActionFactor: SignalFactor? = null
        val corridorPanel = decisionCorridorPanel {
            corridorActionFactor?.let { factor ->
                factorAnchors[factor]?.let(::scrollToPulseFactor)
            }
        }
        addLabPanel(PulseLabSection.FACTS, corridorPanel.root)

        var routeActionFactor: SignalFactor? = null
        val routeBadge = label("", AppColors.signalSoft, AppColors.signal)
        val routeMetric = text("", 21f, AppColors.ink, Typeface.BOLD)
        val routeGauge = VerificationRouteView(this)
        val routeBody = text("", 14f, AppColors.ink)
        val routeRule = text(
            "Оценки факторов зафиксированы: маршрут меняет только уровень подтверждения.",
            12f,
            AppColors.muted
        )
        val routeAction = outlineButton("", AppColors.signal) {
            routeActionFactor?.let { factor ->
                factorAnchors[factor]?.let(::scrollToPulseFactor)
            }
        }.apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        val routeCard = card().apply {
            addView(
                text("Маршрут проверки", 20f, AppColors.ink, Typeface.BOLD)
            )
            addView(routeBadge, matchWrap(top = 7))
            addView(routeMetric, matchWrap(top = 11))
            addView(routeGauge, matchFixed(122, top = 3))
            addView(routeBody, matchWrap(top = 3))
            addView(routeRule, matchWrap(top = 7))
            addView(routeAction, matchWrap(top = 12))
        }
        addLabPanel(PulseLabSection.FACTS, routeCard)

        var stressActionFactor: SignalFactor? = null
        val stressPanel = signalStressPanel {
            stressActionFactor?.let { factor ->
                factorAnchors[factor]?.let(::scrollToPulseFactor)
            }
        }
        addLabPanel(PulseLabSection.FACTS, stressPanel.root)

        val decisionTracePanel = decisionTracePanel()
        addLabPanel(
            PulseLabSection.DECISION,
            decisionTracePanel.root
        )
        var guardActionFactor: SignalFactor? = null
        var currentGuardResult: DecisionGuardResult? = null
        val decisionGuardPanel = decisionGuardPanel(
            onAction = {
                guardActionFactor?.let { factor ->
                    factorAnchors[factor]?.let(
                        ::scrollToPulseFactor
                    )
                }
            },
            onShare = {
                currentGuardResult?.let { guard ->
                    shareDecisionGuardPassport(
                        event = event,
                        guard = guard
                    )
                }
            }
        )
        addLabPanel(
            PulseLabSection.DECISION,
            decisionGuardPanel.root
        )

        val controls = card()
        controls.addView(text("Пять ручных оценок", 20f, AppColors.ink, Typeface.BOLD))
        controls.addView(
            text(
                "Содержание заметки 0–100 • источники • срок действия. Балл не относится к шансам команд.",
                13f,
                AppColors.muted
            ),
            matchWrap(top = 5, bottom = 8)
        )
        SignalFactor.values().forEachIndexed { index, factor ->
            val factorView = factorControl(
                factor = factor,
                initialValue = assessment.value(factor),
                initialEvidence = claimedEvidence.level(factor),
                effectiveEvidence = evidence.level(factor),
                sourceAuditState = sourceAudit.state(factor),
                initialCheckedAt = evidenceTimeline.checkedAt(factor),
                receiptRead = state.factReceipt(event.id, factor),
                onOpenReceipt = {
                    showFactReceiptDialog(
                        event = event,
                        factor = factor
                    )
                },
                onChanged = { newValue ->
                    state.invalidateFactReceipt(event.id, factor)
                    assessment = assessment.withValue(factor, newValue)
                    state.saveAssessment(event.id, assessment)
                    refreshSignal()
                },
                onEvidenceChanged = { level, checkedAt ->
                    state.invalidateFactReceipt(event.id, factor)
                    claimedEvidence = claimedEvidence.withLevel(
                        factor,
                        level
                    )
                    sourceIntegrity = SourceIntegrityEngine.evaluate(
                        claimedEvidence = claimedEvidence,
                        audit = sourceAudit
                    )
                    evidence = sourceIntegrity.effectiveEvidence
                    evidenceTimeline = evidenceTimeline.withCheckedAt(
                        factor,
                        checkedAt
                    )
                    state.saveEvidenceLevel(
                        event.id,
                        factor,
                        level,
                        checkedAt
                    )
                    rerenderContentPreservingScroll()
                }
            )
            factorAnchors[factor] = factorView
            controls.addView(
                factorView,
                matchWrap(top = if (index == 0) 4 else 10)
            )
            if (index < SignalFactor.values().lastIndex) {
                controls.addView(divider(), matchFixed(1, top = 8))
            }
        }
        addLabPanel(PulseLabSection.FACTS, controls)

        refreshSignal = {
            val now = System.currentTimeMillis()
            val freshnessResult = FreshnessEngine.evaluate(
                evidence = evidence,
                timeline = evidenceTimeline,
                now = now
            )
            val evidenceResult = EvidenceEngine.evaluate(
                assessment,
                freshnessResult.effectiveEvidence
            )
            val relayResult = EvidenceRelayEngine.evaluate(
                input = EvidenceRelayInput(
                    event = event,
                    assessment = assessment,
                    claimedEvidence = claimedEvidence,
                    sourceAudit = sourceAudit,
                    timeline = evidenceTimeline
                ),
                now = now
            )
            renderEvidenceRelay(
                panel = evidenceRelayPanel,
                result = relayResult,
                event = event,
                now = now
            )
            renderPreflightProtocol(
                panel = preflightProtocolPanel,
                protocol = relayResult?.let { relay ->
                    PreflightProtocolEngine.evaluate(
                        event = event,
                        relay = relay
                    )
                },
                event = event,
                now = now
            )
            renderEventStory(
                panel = eventStoryPanel,
                result = eventStoryResult(
                    event = event,
                    now = now,
                    assessment = assessment,
                    claimedEvidence = claimedEvidence,
                    sourceAudit = sourceAudit,
                    timeline = evidenceTimeline
                ),
                event = event,
                now = now,
                interactionLocked = state.isPauseActive(now)
            )
            counterView = CounterViewEngine.evaluate(
                assessment = assessment,
                evidence = freshnessResult.effectiveEvidence,
                review = counterReview
            )
            renderVerificationCommand(
                panel = verificationCommandPanel,
                result = VerificationCommandEngine.evaluate(
                    input = VerificationCommandInput(
                        eventId = event.id,
                        sport = event.sport,
                        assessment = assessment,
                        claimedEvidence = claimedEvidence,
                        sourceAudit = sourceAudit,
                        timeline = evidenceTimeline,
                        counterReview = counterReview,
                        decisionSnapshot =
                            state.decisionSnapshot(event.id),
                        decisionGuardBreach =
                            state.decisionGuardBreach(event.id)
                    ),
                    now = now
                ),
                now = now
            )
            val chronoLens = ChronoLensEngine.evaluate(
                input = ChronoLensInput(
                    eventId = event.id,
                    sport = event.sport,
                    assessment = assessment,
                    claimedEvidence = claimedEvidence,
                    sourceAudit = sourceAudit,
                    timeline = evidenceTimeline,
                    counterReview = counterReview,
                    decisionSnapshot =
                        state.decisionSnapshot(event.id)
                ),
                now = now,
                selectedAt = now +
                    chronoOffsetMinutes.toLong() *
                    ChronoLensPolicy.MINUTE_MILLIS
            )
            chronoOffsetMinutes = (
                (chronoLens.selectedAt - now) /
                    ChronoLensPolicy.MINUTE_MILLIS
                ).toInt()
            chronoNextOffsetMinutes =
                chronoLens.nextCheckpoint?.let {
                    (
                        (it.at - now +
                            ChronoLensPolicy.MINUTE_MILLIS - 1L) /
                            ChronoLensPolicy.MINUTE_MILLIS
                        ).toInt()
                }
            renderChronoLens(
                panel = chronoLensPanel,
                result = chronoLens
            )
            dataDuelPanel?.let { panel ->
                val opponent = checkNotNull(
                    dataDuelOpponent
                )
                renderDataDuel(
                    panel = panel,
                    result = DataDuelEngine.evaluate(
                        left = DataDuelInput(
                            eventId = event.id,
                            assessment = assessment,
                            claimedEvidence = claimedEvidence,
                            sourceAudit = sourceAudit,
                            timeline = evidenceTimeline,
                            counterReview = counterReview
                        ),
                        right = dataDuelInput(
                            event = opponent,
                            now = now
                        ),
                        now = now
                    )
                )
            }
            renderCounterView(
                panel = counterViewPanel,
                result = counterView
            )
            val confidenceShadow = ConfidenceShadowEngine.evaluate(
                assessment = assessment,
                evidence = freshnessResult.effectiveEvidence
            )
            val decisionCorridor = DecisionCorridorEngine.evaluate(
                assessment = assessment,
                evidence = freshnessResult.effectiveEvidence
            )
            val result = evidenceResult.effectiveSignal
            val tone = verdictTone(result.verdict)
            readinessValue.text = getString(R.string.number_value, result.readiness)
            readinessValue.setTextColor(tone.foreground)
            verdictBadge.text = verdictTitle(result.verdict)
            verdictBadge.setTextColor(tone.foreground)
            verdictBadge.background = rounded(tone.background, 14)
            noiseValue.text = getString(R.string.score_out_of_100, result.noise)
            noiseBar.progress = result.noise
            noiseBar.progressTintList = ColorStateList.valueOf(
                when {
                    result.noise >= 65 -> AppColors.danger
                    result.noise >= 35 -> AppColors.warning
                    else -> AppColors.accent
                }
            )
            radar.setComparison(confidenceShadow)
            shadowActionFactor = confidenceShadow.criticalFactor?.factor
            renderConfidenceShadow(
                panel = shadowPanel,
                result = confidenceShadow,
                actionFactor = shadowActionFactor
            )
            corridorActionFactor =
                decisionCorridor.nearestBoundary?.factor
            renderDecisionCorridor(
                panel = corridorPanel,
                corridor = decisionCorridor,
                actionFactor = corridorActionFactor
            )
            val quorumTone = evidenceTone(freshnessResult.effectiveEvidence)
            quorumStatus.text = evidenceSummary(evidenceResult)
            quorumStatus.setTextColor(quorumTone.foreground)
            quorumStatus.background = rounded(quorumTone.background, 8)
            quorumStatus.setPadding(dp(12), dp(10), dp(12), dp(10))
            val freshnessTone = freshnessTone(freshnessResult)
            freshnessStatus.text = freshnessSummary(freshnessResult, now)
            freshnessStatus.setTextColor(freshnessTone.foreground)
            freshnessStatus.background = rounded(freshnessTone.background, 8)
            freshnessStatus.setPadding(dp(12), dp(10), dp(12), dp(10))
            explanation.text = verdictExplanation(result)
            explanation.setTextColor(tone.foreground)
            explanation.background = rounded(tone.background, 8)
            explanation.setPadding(dp(12), dp(11), dp(12), dp(11))

            val route = VerificationRouteEngine.evaluate(
                assessment = assessment,
                evidence = freshnessResult.effectiveEvidence
            )
            val routeTone = verificationRouteTone(route)
            routeBadge.text = verificationRouteBadge(route)
            routeBadge.setTextColor(routeTone.foreground)
            routeBadge.background = rounded(routeTone.background, 14)
            routeMetric.text = verificationRouteMetric(route)
            routeMetric.setTextColor(routeTone.foreground)
            routeGauge.setRoute(route)
            routeBody.text = verificationRouteExplanation(route)
            routeActionFactor = when (route.status) {
                VerificationRouteStatus.REACHABLE,
                VerificationRouteStatus.FACTS_LIMIT ->
                    route.bestCheck?.factor
                VerificationRouteStatus.READY_MAINTAIN ->
                    freshnessResult.nextTransitionFactor
            }
            routeActionFactor?.let { factor ->
                routeAction.visibility = View.VISIBLE
                routeAction.text = verificationRouteAction(
                    route,
                    factor
                )
                routeAction.setTextColor(routeTone.foreground)
                routeAction.background = rippleRounded(
                    AppColors.surface,
                    8,
                    routeTone.foreground,
                    1
                )
                routeAction.contentDescription =
                    "${routeAction.text}. Перейти к фактору."
            } ?: run {
                routeAction.visibility = View.GONE
            }

            val stressResult = SignalStressEngine.evaluate(
                assessment = assessment,
                evidence = evidence,
                timeline = evidenceTimeline,
                now = now
            )
            stressActionFactor = signalStressActionFactor(stressResult)
            renderSignalStress(
                panel = stressPanel,
                result = stressResult,
                now = now,
                actionFactor = stressActionFactor
            )

            val snapshot = state.decisionSnapshot(event.id)
            if (snapshot == null) {
                decisionTracePanel.root.visibility = View.GONE
                decisionGuardPanel.root.visibility = View.GONE
                currentGuardResult = null
                guardActionFactor = null
            } else {
                renderDecisionTrace(
                    panel = decisionTracePanel,
                    trace = DecisionTraceEngine.compare(
                        snapshot = snapshot,
                        currentAssessment = assessment,
                        currentEvidence = evidence,
                        currentTimeline = evidenceTimeline,
                        currentCounterReview = counterReview,
                        now = now
                    )
                )
                val liveGuard = DecisionGuardEngine.evaluate(
                    snapshot = snapshot,
                    currentAssessment = assessment,
                    currentEvidence = evidence,
                    currentTimeline = evidenceTimeline,
                    currentCounterReview = counterReview,
                    now = now
                )
                val guard = latchDecisionGuard(
                    liveGuard,
                    now
                )
                currentGuardResult = guard
                guardActionFactor =
                    guard.plan.condition?.factor
                renderDecisionGuard(
                    panel = decisionGuardPanel,
                    result = guard,
                    now = now,
                    actionFactor = guardActionFactor
                )
            }
        }
        refreshSignal()

        val decisionJournal = decisionJournal(
            event = event,
            assessment = { assessment },
            evidence = { evidence },
            timeline = { evidenceTimeline },
            counterReview = { counterReview },
            counterView = { counterView },
            onDecisionSaved = ::rerenderContentPreservingScroll
        )
        decisionJournalTarget = decisionJournal
        addLabPanel(PulseLabSection.DECISION, decisionJournal)
        val reviewPanel = postEventReviewPanel(
            event = event,
            onNeedDecision = {
                scrollToPulseFactor(decisionJournal)
            },
            onReviewFinalized =
                ::rerenderContentPreservingScroll
        )
        postEventReviewTarget = reviewPanel
        addLabPanel(PulseLabSection.DECISION, reviewPanel)
        addLabPanel(
            PulseLabSection.DECISION,
            passportPanel(
                event = event,
                assessment = { assessment },
                evidence = { evidence },
                timeline = { evidenceTimeline },
                sourceIntegrity = { sourceIntegrity },
                counterReview = { counterReview }
            )
        )
        var storyFactorRouted = false
        pendingPulseFactor?.let { factor ->
            factorAnchors[factor]?.let { target ->
                pendingPulseFactor = null
                storyFactorRouted = true
                scrollToPulseFactor(target)
            }
        }
        pendingPulseStoryAction?.let { action ->
            pendingPulseStoryAction = null
            val target = when (action) {
                EventStoryAction.OPEN_FACTS -> {
                    if (storyFactorRouted) {
                        null
                    } else {
                        evidenceRelayPanel.root
                    }
                }
                EventStoryAction.OPEN_PLAN ->
                    preflightProtocolPanel.root
                EventStoryAction.OPEN_DECISION ->
                    decisionJournalTarget
                EventStoryAction.OPEN_REVIEW ->
                    postEventReviewTarget
                EventStoryAction.OPEN_SOURCE,
                EventStoryAction.NONE -> null
            }
            target?.let(::scrollToPulseFactor)
        }
    }

    private fun eventStoryResult(
        event: SportEvent,
        now: Long,
        assessment: SignalAssessment = state.assessment(event),
        claimedEvidence: EvidenceAssessment =
            state.claimedEvidence(event),
        sourceAudit: SourceAuditAssessment =
            state.sourceAudit(event.id),
        timeline: EvidenceTimeline = state.evidenceTimelinePreview(
            eventId = event.id,
            now = now
        )
    ): EventStoryResult {
        return EventStoryEngine.evaluate(
            EventStoryInput(
                event = event,
                sourceState = eventStorySourceState(event, now),
                assessment = assessment,
                claimedEvidence = claimedEvidence,
                sourceAudit = sourceAudit,
                timeline = timeline,
                selectedZone = state.selectedRegionalZone,
                storedReceipt =
                    state.preflightExportReceipt(event.id),
                snapshot = state.decisionSnapshot(event.id),
                review = state.postEventReview(event.id),
                now = now
            )
        )
    }

    private fun eventStorySourceState(
        event: SportEvent,
        now: Long
    ): EventStorySourceState {
        if (event.origin == SportEventOrigin.API_SPORTS) {
            return EventStorySourceState.API_PROVIDER
        }
        val activePackage = importedEventPackage?.takeUnless {
            it.isExpired(now)
        } ?: return EventStorySourceState.DEMO
        return when (
            activePackage.authenticity.keyEnvironment
        ) {
            EventPackageKeyEnvironment.PRODUCTION ->
                EventStorySourceState.PRODUCTION_SIGNED
            EventPackageKeyEnvironment.DEVELOPMENT ->
                EventStorySourceState.DEVELOPMENT_SIGNED
            null -> EventStorySourceState.UNSIGNED
        }
    }

    private fun eventStoryPanel(
        onAction: (EventStoryAction, SignalFactor?) -> Unit,
        onShare: () -> Unit
    ): EventStoryPanel {
        val badge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        )
        val metric = text("", 21f, AppColors.ink, Typeface.BOLD)
        val time = text("", 12f, AppColors.muted, Typeface.BOLD)
        val body = text("", 14f, AppColors.ink)
        val rail = EventStoryView(this)
        val chapterLabels = linkedMapOf<
            EventStoryChapter,
            TextView
            >()
        val chapterColumns = if (
            resources.configuration.fontScale >= 1.8f
        ) 2 else 3
        val chapterGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            EventStoryChapter.values()
                .toList()
                .chunked(chapterColumns)
                .forEachIndexed { rowIndex, chapters ->
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            weightSum = chapterColumns.toFloat()
                            chapters.forEachIndexed {
                                    index,
                                    chapter ->
                                val chapterLabel = label(
                                    "",
                                    AppColors.background,
                                    AppColors.muted
                                ).apply {
                                    minHeight = dp(42)
                                    setPadding(
                                        dp(4),
                                        dp(3),
                                        dp(4),
                                        dp(3)
                                    )
                                }
                                chapterLabels[chapter] =
                                    chapterLabel
                                addView(
                                    chapterLabel,
                                    LinearLayout.LayoutParams(
                                        0,
                                        LinearLayout.LayoutParams
                                            .WRAP_CONTENT,
                                        1f
                                    ).apply {
                                        if (index < chapters.lastIndex) {
                                            rightMargin = dp(6)
                                        }
                                    }
                                )
                            }
                        },
                        matchWrap(
                            top = if (rowIndex == 0) 0 else 6
                        )
                    )
                }
        }
        val action = commandButton(
            "",
            AppColors.signal
        ) {}
        val share = outlineButton(
            "Создать постер сюжета",
            AppColors.signal,
            onShare
        ).apply {
            contentDescription =
                "Создать PNG-постер текущего сюжета события"
        }
        val footer = text(
            "",
            11.5f,
            AppColors.muted,
            Typeface.BOLD
        )
        val root = card().apply {
            addView(eventStoryHeader(), matchFixed(imageHeaderHeight()))
            addView(badge, matchWrap(top = 12))
            addView(metric, matchWrap(top = 12))
            addView(time, matchWrap(top = 4))
            addView(body, matchWrap(top = 8))
            addView(rail, matchFixed(108, top = 5))
            addView(chapterGrid, matchWrap(top = 2))
            addView(action, matchWrap(top = 12))
            addView(share, matchWrap(top = 8))
            addView(footer, matchWrap(top = 10))
        }
        return EventStoryPanel(
            root = root,
            badge = badge,
            metric = metric,
            time = time,
            body = body,
            rail = rail,
            chapterLabels = chapterLabels,
            action = action,
            share = share,
            footer = footer,
            onAction = onAction
        )
    }

    private fun eventStoryHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.event_story)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Шесть физических станций связывают источник, факты, план, решение, старт и разбор"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "СЮЖЕТ СОБЫТИЯ",
                            11f,
                            Color.rgb(187, 239, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "От источника до разбора",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun renderEventStory(
        panel: EventStoryPanel,
        result: EventStoryResult,
        event: SportEvent,
        now: Long,
        interactionLocked: Boolean
    ) {
        require(result.eventId == event.id)
        val tone = eventStoryTone(result.phase)
        panel.badge.text = eventStoryPhaseTitle(result.phase)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(
            tone.background,
            14,
            tone.background,
            1
        )
        panel.metric.text = getString(
            R.string.event_story_metric,
            result.currentChapterNumber,
            eventStoryChapterTitle(result.currentChapter)
        )
        panel.metric.setTextColor(tone.foreground)
        panel.time.text = eventStoryTime(result, now)
        panel.body.text = result
            .chapter(result.currentChapter)
            .summary
        panel.rail.setResult(result)
        result.chapters.forEach { chapter ->
            val chapterTone = eventStoryChapterTone(chapter.state)
            panel.chapterLabels.getValue(chapter.chapter).apply {
                text = getString(
                    R.string.event_story_chapter_state,
                    eventStoryChapterTitle(chapter.chapter),
                    eventStoryChapterStateTitle(chapter.state)
                )
                setTextColor(chapterTone.foreground)
                background = rounded(
                    chapterTone.background,
                    8,
                    chapterTone.foreground,
                    1
                )
            }
        }
        if (result.action == EventStoryAction.NONE) {
            panel.action.visibility = View.GONE
            panel.action.setOnClickListener(null)
        } else {
            panel.action.visibility = View.VISIBLE
            panel.action.text = if (interactionLocked) {
                "Пауза • маршрут только для чтения"
            } else {
                eventStoryActionTitle(
                    action = result.action,
                    factor = result.actionFactor
                )
            }
            panel.action.isEnabled = !interactionLocked
            panel.action.alpha = if (interactionLocked) 0.55f else 1f
            panel.action.background = rippleRounded(
                if (interactionLocked) {
                    AppColors.muted
                } else {
                    tone.foreground
                },
                8
            )
            panel.action.setOnClickListener {
                panel.onAction(
                    result.action,
                    result.actionFactor
                )
            }
        }
        panel.footer.text = getString(
            R.string.event_story_footer,
            result.completedCount,
            result.shortFingerprint
        )
    }

    private fun shareEventStoryPoster(
        event: SportEvent,
        story: EventStoryResult,
        generatedAt: Long
    ) {
        if (passportExportInProgress) {
            Toast.makeText(
                this,
                "Изображение уже создается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        passportExportInProgress = true
        val poster = EventStoryPosterFactory.create(
            event = event,
            story = story,
            selectedZone = state.selectedRegionalZone,
            generatedAt = generatedAt
        )
        Toast.makeText(
            this,
            "Создаем постер сюжета…",
            Toast.LENGTH_SHORT
        ).show()
        passportExecutor.execute {
            runCatching {
                val file = EventStoryPosterExporter(
                    applicationContext
                ).export(poster)
                AnalysisImageProvider.uriFor(
                    applicationContext,
                    file
                )
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Сюжет события: ${event.match}"
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            EventStoryPosterFactory.shareText(
                                poster
                            )
                        )
                        clipData = ClipData.newRawUri(
                            "Постер сюжета",
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться сюжетом"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать постер сюжета",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun shareStoryThreadPoster(event: SportEvent) {
        val generatedAt = System.currentTimeMillis()
        if (state.isPauseActive(generatedAt)) {
            Toast.makeText(
                this,
                "Во время паузы экспорт недоступен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (passportExportInProgress) {
            Toast.makeText(
                this,
                "Изображение уже создается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val story = eventStoryResult(
            event = event,
            now = generatedAt
        )
        val thread = state.storyThread(event.id).thread
        if (thread == null) {
            Toast.makeText(
                this,
                "Нить недоступна — обновите экран",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val result = StoryThreadEngine.evaluate(
            thread = thread,
            story = story
        )
        val beacon = storyBeaconResult(
            event = event,
            story = story,
            now = generatedAt
        )
        val poster = StoryThreadPosterFactory.create(
            event = event,
            result = result,
            nextMoment = StoryThreadPolicy.relevantMoment(
                chapter = thread.chapter,
                beacon = beacon
            ),
            selectedZone = state.selectedRegionalZone,
            generatedAt = generatedAt
        )
        passportExportInProgress = true
        Toast.makeText(
            this,
            "Создаем карточку нити…",
            Toast.LENGTH_SHORT
        ).show()
        passportExecutor.execute {
            runCatching {
                val file = StoryThreadPosterExporter(
                    applicationContext
                ).export(poster)
                AnalysisImageProvider.uriFor(
                    applicationContext,
                    file
                )
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Нить события: ${event.match}"
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            StoryThreadPosterFactory.shareText(
                                poster
                            )
                        )
                        clipData = ClipData.newRawUri(
                            "Карточка нити",
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться нитью"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать карточку нити",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun shareStoryReturnFrame() {
        val generatedAt = System.currentTimeMillis()
        if (state.isPauseActive(generatedAt)) {
            Toast.makeText(
                this,
                "До конца паузы кадр недоступен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (passportExportInProgress) {
            Toast.makeText(
                this,
                "Изображение уже создается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val read = state.storyReturnCapsule()
        val capsule = read.capsule
        if (
            read.integrity != StoryReturnCapsuleIntegrity.VALID ||
            capsule == null
        ) {
            Toast.makeText(
                this,
                "Капсула не прошла повторную проверку",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule,
            currentMap = storyThreadMapResult(generatedAt),
            now = generatedAt
        )
        if (result.state == StoryReturnCapsuleState.SEALED) {
            Toast.makeText(
                this,
                "Капсула еще запечатана",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val frame = runCatching {
            StoryReturnFrameFactory.create(
                result = result,
                selectedZone = state.selectedRegionalZone,
                generatedAt = generatedAt
            )
        }.getOrElse {
            Toast.makeText(
                this,
                "Итог изменился — экран обновлен",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        passportExportInProgress = true
        Toast.makeText(
            this,
            "Создаем кадр возвращения…",
            Toast.LENGTH_SHORT
        ).show()
        passportExecutor.execute {
            runCatching {
                val file = StoryReturnFrameExporter(
                    applicationContext
                ).export(frame)
                AnalysisImageProvider.uriFor(
                    applicationContext,
                    file
                )
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Кадр возвращения: ${frame.eventLabel}"
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            StoryReturnFrameFactory.shareText(frame)
                        )
                        clipData = ClipData.newRawUri(
                            "Кадр возвращения",
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться кадром возвращения"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать кадр возвращения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun shareFactExpress() {
        val generatedAt = System.currentTimeMillis()
        if (state.isPauseActive(generatedAt)) {
            Toast.makeText(
                this,
                "Во время паузы экспорт недоступен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (passportExportInProgress) {
            Toast.makeText(
                this,
                "Изображение уже создается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val result = factExpressResult(
            bookmarks = state.bookmarkedIds(),
            now = generatedAt
        )
        if (!result.isReady) {
            Toast.makeText(
                this,
                when (result.state) {
                    FactExpressState.EMPTY ->
                        "Сначала сохраните 2–4 события"
                    FactExpressState.NEED_MORE ->
                        "Добавьте еще одно событие"
                    FactExpressState.TOO_MANY ->
                        "Оставьте не больше четырех событий"
                    FactExpressState.READY ->
                        error("Ready result handled above")
                },
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            return
        }
        val poster = FactExpressPosterFactory.create(
            result = result,
            generatedAt = generatedAt
        )
        passportExportInProgress = true
        Toast.makeText(
            this,
            "Создаём маршрут фактов…",
            Toast.LENGTH_SHORT
        ).show()
        passportExecutor.execute {
            runCatching {
                val file = FactExpressPosterExporter(
                    applicationContext
                ).export(poster)
                AnalysisImageProvider.uriFor(
                    applicationContext,
                    file
                )
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Маршрут фактов: ${result.entries.size} события"
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            FactExpressPosterFactory.shareText(poster)
                        )
                        clipData = ClipData.newRawUri(
                            "Маршрут фактов",
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться маршрутом фактов"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать маршрут фактов",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun eventStoryPhaseTitle(
        phase: EventStoryPhase
    ): String {
        return when (phase) {
            EventStoryPhase.PREPARING -> "МАРШРУТ СОБИРАЕТСЯ"
            EventStoryPhase.READY -> "ГОТОВО К СТАРТУ"
            EventStoryPhase.IN_PROGRESS -> "ОКНО СОБЫТИЯ"
            EventStoryPhase.REVIEW_DUE -> "ОТКРЫТ РАЗБОР"
            EventStoryPhase.COMPLETE -> "ИСТОРИЯ ЗАКРЫТА"
            EventStoryPhase.INCOMPLETE ->
                "ХРОНОЛОГИЯ НЕПОЛНА"
        }
    }

    private fun eventStoryChapterTitle(
        chapter: EventStoryChapter
    ): String {
        return when (chapter) {
            EventStoryChapter.SOURCE -> "Источник"
            EventStoryChapter.FACTS -> "Факты"
            EventStoryChapter.PLAN -> "План"
            EventStoryChapter.DECISION -> "Решение"
            EventStoryChapter.START -> "Старт"
            EventStoryChapter.REVIEW -> "Разбор"
        }
    }

    private fun eventStoryChapterStateTitle(
        state: EventStoryChapterState
    ): String {
        return when (state) {
            EventStoryChapterState.COMPLETE -> "ГОТОВО"
            EventStoryChapterState.ACTIVE -> "АКТИВНО"
            EventStoryChapterState.ATTENTION -> "ВНИМАНИЕ"
            EventStoryChapterState.LOCKED -> "ЗАКРЫТО"
            EventStoryChapterState.MISSED -> "УПУЩЕНО"
            EventStoryChapterState.CONTEXT -> "КОНТЕКСТ"
        }
    }

    private fun eventStoryActionTitle(
        action: EventStoryAction,
        factor: SignalFactor?
    ): String {
        return when (action) {
            EventStoryAction.OPEN_SOURCE -> "Проверить источник"
            EventStoryAction.OPEN_FACTS -> factor?.let {
                "Проверить фактор: ${it.title}"
            } ?: "Открыть факты"
            EventStoryAction.OPEN_PLAN -> "Открыть план к старту"
            EventStoryAction.OPEN_DECISION ->
                "Зафиксировать решение"
            EventStoryAction.OPEN_REVIEW -> "Открыть разбор"
            EventStoryAction.NONE -> ""
        }
    }

    private fun eventStoryTime(
        result: EventStoryResult,
        now: Long
    ): String {
        val startAt = result.startAt
            ?: return "СТАРТ • НЕ ПОДТВЕРЖДЕН"
        val reviewAt = checkNotNull(result.reviewOpensAt)
        return when {
            now < startAt ->
                "СТАРТ • ${TimeBridgeEngine.formatInstant(
                    startAt = startAt,
                    selectedZone = state.selectedRegionalZone
                )}"
            now < reviewAt ->
                "РАЗБОР НЕ РАНЬШЕ • ${TimeBridgeEngine.formatInstant(
                    startAt = reviewAt,
                    selectedZone = state.selectedRegionalZone
                )}"
            else ->
                "ОКНО РАЗБОРА • СВЕРИТЬ ФАКТИЧЕСКОЕ ЗАВЕРШЕНИЕ"
        }
    }

    private fun eventStoryTone(phase: EventStoryPhase): Tone {
        return when (phase) {
            EventStoryPhase.PREPARING ->
                Tone(AppColors.signal, AppColors.signalSoft)
            EventStoryPhase.READY,
            EventStoryPhase.COMPLETE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            EventStoryPhase.IN_PROGRESS,
            EventStoryPhase.REVIEW_DUE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            EventStoryPhase.INCOMPLETE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun eventStoryChapterTone(
        state: EventStoryChapterState
    ): Tone {
        return when (state) {
            EventStoryChapterState.COMPLETE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.CONTEXT ->
                Tone(AppColors.signal, AppColors.signalSoft)
            EventStoryChapterState.ATTENTION ->
                Tone(AppColors.warning, AppColors.warningSoft)
            EventStoryChapterState.LOCKED ->
                Tone(AppColors.muted, AppColors.background)
            EventStoryChapterState.MISSED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun analysisEventHeader(event: SportEvent): LinearLayout {
        val now = System.currentTimeMillis()
        val compactChangeAction =
            effectiveFontScale() >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        val frame = imageFrame().apply {
            minimumHeight = dp(190)
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(event.imageRes)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = null
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = false)
                },
                frameMatch()
            )
            addView(
                label(
                    when (event.origin) {
                        SportEventOrigin.DEMO -> "ДЕМО • ДОСЬЕ"
                        SportEventOrigin.EVENT_PACKAGE -> "EVENT PACK • ДОСЬЕ"
                        SportEventOrigin.API_SPORTS -> "ОНЛАЙН • ДОСЬЕ"
                    },
                    Color.argb(220, 13, 30, 33),
                    Color.WHITE,
                    Color.argb(150, 255, 255, 255)
                ),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    leftMargin = dp(14)
                    topMargin = dp(14)
                }
            )
            addView(
                text(
                    if (compactChangeAction) "↔" else "Сменить матч ›",
                    fixedControlTextSize(12.5f),
                    Color.WHITE,
                    Typeface.BOLD
                ).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(48)
                    setPadding(dp(10), 0, dp(10), 0)
                    background = rippleRounded(
                        Color.argb(190, 13, 30, 33),
                        6,
                        Color.argb(150, 255, 255, 255),
                        1
                    )
                    applyAccessibleAction(dp(48))
                    contentDescription = "Сменить событие анализа"
                    setOnClickListener { selectTab(0) }
                },
                FrameLayout.LayoutParams(
                    if (compactChangeAction) {
                        dp(48)
                    } else {
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    },
                    dp(48),
                    Gravity.TOP or Gravity.END
                ).apply {
                    rightMargin = dp(14)
                    topMargin = dp(14)
                }
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(78), dp(14), dp(14))
                    addView(
                        text(
                            event.match,
                            23f,
                            Color.WHITE,
                            Typeface.BOLD
                        ).apply {
                            setTextSize(
                                TypedValue.COMPLEX_UNIT_PX,
                                23f *
                                    resources.displayMetrics.density *
                                    min(effectiveFontScale(), 1.25f)
                            )
                        }
                    )
                    addView(
                        text(
                            "${event.tournament} • ${event.region}",
                            12.5f,
                            Color.rgb(219, 229, 226)
                        ),
                        matchWrap(top = 5)
                    )
                    addView(
                        text(
                            TimeBridgeEngine.formatEventTime(
                                event = event,
                                selectedZone = state.selectedRegionalZone,
                                referenceMillis = now
                            ),
                            13.5f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 5)
                    )
                    event.providerStatus?.let { status ->
                        addView(
                            text(
                                "Статус: $status",
                                12f,
                                Color.rgb(142, 232, 207),
                                Typeface.BOLD
                            ),
                            matchWrap(top = 3)
                        )
                    }
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                )
            )
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(frame, matchWrap())
            feedTimelineExplanationBand(
                event = event,
                now = now,
                alwaysForVerification = true
            )?.let { explanation ->
                addView(explanation, matchWrap(top = 8))
            }
            event.syncedAt?.let { syncedAt ->
                val freshness = ScheduleFreshnessPolicy.evaluate(
                    syncedAt = syncedAt,
                    now = now
                )
                val tone = scheduleFreshnessTone(freshness.status)
                addView(
                    text(
                        scheduleFreshnessTitle(
                            result = freshness,
                            syncedAt = syncedAt
                        ),
                        12f,
                        tone.foreground,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(tone.background, 6)
                        setPadding(dp(11), dp(9), dp(11), dp(9))
                    },
                    matchWrap(top = 8)
                )
            }
        }
    }

    private fun scheduleFreshnessTone(
        status: ScheduleFreshnessStatus
    ): Tone {
        return when (status) {
            ScheduleFreshnessStatus.FRESH ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            ScheduleFreshnessStatus.VERIFY ->
                Tone(AppColors.warning, AppColors.warningSoft)
            ScheduleFreshnessStatus.STALE,
            ScheduleFreshnessStatus.INVALID ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun scheduleFreshnessTitle(
        result: ScheduleFreshnessResult,
        syncedAt: Long
    ): String {
        return when (result.status) {
            ScheduleFreshnessStatus.FRESH ->
                "Расписание обновлено ${formatPackageDate(syncedAt)}"
            ScheduleFreshnessStatus.VERIFY ->
                "Проверьте расписание • обновлено ${formatPackageDate(syncedAt)}"
            ScheduleFreshnessStatus.STALE ->
                "Расписание устарело • обновлено ${formatPackageDate(syncedAt)}"
            ScheduleFreshnessStatus.INVALID ->
                "Время обновления некорректно • сверьте расписание"
        }
    }

    private fun factorControl(
        factor: SignalFactor,
        initialValue: Int,
        initialEvidence: EvidenceLevel,
        effectiveEvidence: EvidenceLevel,
        sourceAuditState: SourceAuditState,
        initialCheckedAt: Long,
        receiptRead: FactReceiptReadResult,
        onOpenReceipt: () -> Unit,
        onChanged: (Int) -> Unit,
        onEvidenceChanged: (EvidenceLevel, Long) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val valueText = text(
                getString(R.string.number_value, initialValue),
                14f,
                AppColors.accent,
                Typeface.BOLD
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(factor.title, 16f, AppColors.ink, Typeface.BOLD),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    )
                    addView(valueText)
                }
            )
            addView(text(factor.hint, 12f, AppColors.muted), matchWrap(top = 2))
            addView(
                SeekBar(this@MainActivity).apply {
                    max = 100
                    progress = initialValue
                    progressTintList = ColorStateList.valueOf(AppColors.accent)
                    thumbTintList = ColorStateList.valueOf(AppColors.accent)
                    contentDescription =
                        "${factor.title}: ручная оценка $initialValue из 100"
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            valueText.text = getString(R.string.number_value, progress)
                            seekBar.contentDescription =
                                "${factor.title}: ручная оценка $progress из 100"
                            if (fromUser) onChanged(progress)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                    })
                },
                matchWrap(top = 2)
            )

            var evidenceLevel = initialEvidence
            var checkedAt = initialCheckedAt
            val evidenceButton = outlineButton("", AppColors.warning) {}
                .apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
            val refreshEvidenceButton: () -> Unit = {
                val freshness = FreshnessEngine.evaluateFactor(
                    factor = factor,
                    claimedLevel = evidenceLevel,
                    checkedAt = checkedAt,
                    now = System.currentTimeMillis()
                )
                val color = evidenceFreshnessColor(freshness)
                evidenceButton.text = evidenceButtonTitle(freshness)
                evidenceButton.setTextColor(color)
                evidenceButton.background = rippleRounded(
                    AppColors.surface,
                    8,
                    color,
                    1
                )
                evidenceButton.contentDescription =
                    "${factor.title}. ${evidenceButtonTitle(freshness)}"
            }
            evidenceButton.setOnClickListener {
                showEvidenceDialog(factor, evidenceLevel) { selected ->
                    val confirmedAt = System.currentTimeMillis()
                    evidenceLevel = selected
                    checkedAt = confirmedAt
                    refreshEvidenceButton()
                    onEvidenceChanged(selected, confirmedAt)
                }
            }
            refreshEvidenceButton()
            addView(evidenceButton, matchWrap(top = 6))
            if (receiptRead.integrity != FactReceiptIntegrity.EMPTY) {
                addView(
                    factReceiptSummaryBand(receiptRead),
                    matchWrap(top = 8)
                )
            }
            addView(
                outlineButton(
                    when (receiptRead.integrity) {
                        FactReceiptIntegrity.EMPTY ->
                            "Записать факт и источники"
                        FactReceiptIntegrity.VALID ->
                            "Обновить факт-квитанцию"
                        FactReceiptIntegrity.TAMPERED ->
                            "Пересобрать факт-квитанцию"
                    },
                    AppColors.signal,
                    onOpenReceipt
                ),
                matchWrap(top = 8)
            )
            if (initialEvidence != effectiveEvidence) {
                val tone = if (
                    effectiveEvidence ==
                    EvidenceLevel.UNCONFIRMED
                ) {
                    Tone(AppColors.danger, AppColors.dangerSoft)
                } else {
                    Tone(AppColors.warning, AppColors.warningSoft)
                }
                addView(
                    text(
                        "Антиэхо: ${initialEvidence.title} → ${
                            effectiveEvidence.title.lowercase(
                                Locale.getDefault()
                            )
                        } • ${
                            sourceAuditState.title.lowercase(
                                Locale.getDefault()
                            )
                        }",
                        12f,
                        tone.foreground,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(
                            tone.background,
                            8
                        )
                        setPadding(
                            dp(10),
                            dp(8),
                            dp(10),
                            dp(8)
                        )
                    },
                    matchWrap(top = 6)
                )
            }
        }
    }

    private fun sourceIntegrityPanel(
        result: SourceIntegrityResult,
        onAuditChanged: (
            SignalFactor,
            SourceAuditState
        ) -> Unit
    ): LinearLayout {
        val tone = sourceIntegrityTone(result.verdict)
        return card().apply {
            addView(
                text(
                    "Антиэхо источников",
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                )
            )
            addView(
                label(
                    sourceIntegrityBadge(result.verdict),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 7)
            )
            addView(
                text(
                    sourceIntegrityMetric(result),
                    18f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
            addView(
                text(
                    sourceIntegrityExplanation(result),
                    14f,
                    AppColors.ink
                ),
                matchWrap(top = 5)
            )
            addView(
                SourceIntegrityView(this@MainActivity).apply {
                    setIntegrity(result)
                },
                matchFixed(130, top = 8)
            )
            addView(
                text(
                    "Сверху заявлено • снизу учтено",
                    12f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 2, bottom = 6)
            )

            val stackRows =
                resources.configuration.fontScale >= 1.3f ||
                    resources.configuration.screenWidthDp < 380
            result.factors.forEachIndexed { index, factorResult ->
                if (index > 0) {
                    addView(divider(), matchFixed(1, top = 7))
                }
                val auditTone = sourceAuditTone(
                    factorResult.auditState
                )
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = if (stackRows) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackRows) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    val factorCopy = text(
                        sourceIntegrityFactorLabel(factorResult),
                        13f,
                        AppColors.ink,
                        Typeface.BOLD
                    )
                    addView(
                        factorCopy,
                        if (stackRows) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(10)
                            }
                        }
                    )
                    addView(
                        outlineButton(
                            factorResult.auditState.title,
                            auditTone.foreground
                        ) {
                            showSourceAuditDialog(
                                factor = factorResult.factor,
                                selected =
                                    factorResult.auditState
                            ) { selected ->
                                onAuditChanged(
                                    factorResult.factor,
                                    selected
                                )
                            }
                        }.apply {
                            minHeight = dp(48)
                            setPadding(
                                dp(11),
                                dp(8),
                                dp(11),
                                dp(8)
                            )
                            contentDescription =
                                "${factorResult.factor.title}. ${
                                    factorResult.auditState.title
                                }. Изменить аудит источников."
                        },
                        if (stackRows) {
                            matchWrap(top = 6)
                        } else {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                    )
                }
                addView(
                    row,
                    matchWrap(top = if (index == 0) 2 else 7)
                )
            }
            addView(
                text(
                    "Это ручной редакционный аудит. Подпись пакета подтверждает происхождение байтов, но не независимость спортивных фактов. Метка ${result.shortFingerprint}.",
                    12f,
                    AppColors.muted
                ),
                matchWrap(top = 11)
            )
        }
    }

    private fun verificationCommandPanel(
        onTaskSelected: (VerificationCommandTask) -> Unit
    ): VerificationCommandPanel {
        val badge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        )
        val metric = text(
            "",
            22f,
            AppColors.ink,
            Typeface.BOLD
        )
        val summary = text("", 13.5f, AppColors.ink)
        val chart = VerificationCommandView(this)
        val tasks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val footer = text("", 11.5f, AppColors.muted)
        val root = card().apply {
            addView(
                verificationCommandHeader(),
                matchFixed(imageHeaderHeight(baseDp = 116))
            )
            addView(badge, matchWrap(top = 12))
            addView(metric, matchWrap(top = 10))
            addView(summary, matchWrap(top = 4))
            addView(chart, matchFixed(126, top = 6))
            addView(tasks, matchWrap(top = 2))
            addView(footer, matchWrap(top = 10))
        }
        return VerificationCommandPanel(
            root = root,
            badge = badge,
            metric = metric,
            summary = summary,
            chart = chart,
            tasks = tasks,
            footer = footer,
            onTaskSelected = onTaskSelected
        )
    }

    private fun verificationCommandHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(
                        R.drawable.verification_command
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Спортивный центр проверки направляет факты к остановке, проверке или подтверждению"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ШТАБ ПРОВЕРКИ",
                            11f,
                            Color.rgb(191, 238, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Что проверять первым",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun renderVerificationCommand(
        panel: VerificationCommandPanel,
        result: VerificationCommandResult,
        now: Long
    ) {
        val tone = verificationCommandStatusTone(result.status)
        panel.badge.text = when (result.status) {
            VerificationCommandStatus.STOP ->
                "СТОП: РЕШЕНИЕ ЗАБЛОКИРОВАНО"
            VerificationCommandStatus.ATTENTION ->
                "НУЖНО ВМЕШАТЕЛЬСТВО"
            VerificationCommandStatus.ACTIVE ->
                "ОЧЕРЕДЬ ПРОВЕРКИ"
            VerificationCommandStatus.STABLE ->
                "КОНТРОЛЬ СТАБИЛЕН"
        }
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(
            tone.background,
            14
        )
        panel.metric.text = if (result.tasks.size == 1) {
            "1 действие"
        } else {
            "Первые ${result.visibleTasks.size} • всего ${result.tasks.size}"
        }
        panel.metric.setTextColor(tone.foreground)
        panel.summary.text = when (result.status) {
            VerificationCommandStatus.STOP ->
                "Сначала устраните стоп-причину. Остальные проверки не отменены, но не могут поднять решение выше ограничения."
            VerificationCommandStatus.ATTENTION ->
                "В очереди есть повреждённый или стареющий факт. Дедлайн важнее роста полноты."
            VerificationCommandStatus.ACTIVE ->
                "Стоп-причин нет. Очередь ведёт к самому полезному следующему факту через контрпроверку и рынки."
            VerificationCommandStatus.STABLE ->
                "Активных блокеров нет. Штаб показывает ближайший момент, когда подтверждение потребует внимания."
        }
        panel.chart.setTasks(result.visibleTasks)
        panel.tasks.removeAllViews()
        result.visibleTasks.forEachIndexed { index, task ->
            if (index > 0) {
                panel.tasks.addView(
                    divider(),
                    matchFixed(1, top = 5)
                )
            }
            panel.tasks.addView(
                verificationCommandTaskRow(
                    number = index + 1,
                    task = task,
                    now = now,
                    onClick = {
                        panel.onTaskSelected(task)
                    }
                ),
                matchWrap(top = 6)
            )
        }
        panel.footer.text = getString(
            R.string.verification_command_footer,
            result.shortFingerprint
        )
    }

    private fun verificationCommandTaskRow(
        number: Int,
        task: VerificationCommandTask,
        now: Long,
        onClick: () -> Unit
    ): LinearLayout {
        val tone = verificationCommandPriorityTone(
            task.priority
        )
        val stackHeading =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        val destination = task.factor?.let {
            "фактору ${it.title}"
        } ?: "стоп-контракту"
        val meta = buildList {
            add(task.modules.joinToString(" • ") { it.title })
            task.dueAt?.let { deadline ->
                add(
                    if (deadline <= now) {
                        "Срок наступил"
                    } else {
                        "До перехода ${
                            FreshnessFormatter.duration(
                                deadline - now
                            )
                        }"
                    }
                )
            }
            if (
                task.readinessImpact in 1 until Int.MAX_VALUE
            ) {
                add("Влияние ${task.readinessImpact}")
            }
        }.joinToString("  •  ")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(7), dp(3), dp(8))
            applyAccessibleAction(dp(48))
            background = rippleRounded(AppColors.surface, 6)
            setOnClickListener { onClick() }
            contentDescription =
                "Шаг $number. ${task.priority.title}. ${task.title}. ${task.reason} Перейти к $destination."
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackHeading) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackHeading) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        label(
                            "$number · ${task.priority.shortTitle}",
                            tone.background,
                            tone.foreground
                        ),
                        if (stackHeading) {
                            wrapWrap(bottom = 5)
                        } else {
                            wrapWrap(right = 9)
                        }
                    )
                    addView(
                        text(
                            task.title,
                            15f,
                            AppColors.ink,
                            Typeface.BOLD
                        ).apply {
                            maxLines = 3
                        },
                        if (stackHeading) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                    )
                }
            )
            addView(
                text(task.reason, 12.5f, AppColors.ink),
                matchWrap(top = 5)
            )
            addView(
                text(meta, 11f, AppColors.muted).apply {
                    maxLines = 3
                },
                matchWrap(top = 5)
            )
            addView(
                text(
                    "Открыть: ${task.factor?.shortTitle ?: "решение"} →",
                    12f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 6)
            )
        }
    }

    private fun verificationCommandStatusTone(
        status: VerificationCommandStatus
    ): Tone {
        return when (status) {
            VerificationCommandStatus.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            VerificationCommandStatus.ATTENTION ->
                Tone(AppColors.warning, AppColors.warningSoft)
            VerificationCommandStatus.ACTIVE ->
                Tone(AppColors.signal, AppColors.signalSoft)
            VerificationCommandStatus.STABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun verificationCommandPriorityTone(
        priority: VerificationCommandPriority
    ): Tone {
        return when (priority) {
            VerificationCommandPriority.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            VerificationCommandPriority.REPAIR,
            VerificationCommandPriority.REFRESH ->
                Tone(AppColors.warning, AppColors.warningSoft)
            VerificationCommandPriority.CHALLENGE,
            VerificationCommandPriority.UNBLOCK ->
                Tone(AppColors.signal, AppColors.signalSoft)
            VerificationCommandPriority.MAINTAIN ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun dataDuelOpponent(
        current: SportEvent
    ): SportEvent? {
        val candidates = catalogEvents.filter {
            it.id != current.id
        }
        return candidates.firstOrNull {
            it.id == state.dataDuelOpponentId
        } ?: candidates.firstOrNull()
    }

    private fun dataDuelInput(
        event: SportEvent,
        now: Long
    ): DataDuelInput {
        return DataDuelInput(
            eventId = event.id,
            assessment = state.assessment(event),
            claimedEvidence =
                state.claimedEvidence(event),
            sourceAudit = state.sourceAudit(event.id),
            timeline = state.evidenceTimeline(event.id, now),
            counterReview = state.counterReview(event.id)
        )
    }

    private fun dataDuelPanel(
        leftEvent: SportEvent,
        rightEvent: SportEvent,
        onChooseOpponent: () -> Unit,
        onSwap: () -> Unit
    ): DataDuelPanel {
        val badge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        )
        val score = text(
            "",
            19f,
            AppColors.ink,
            Typeface.BOLD
        )
        val summary = text("", 13.5f, AppColors.ink)
        val chart = DataDuelView(this)
        val leftLimit = text(
            "",
            12f,
            AppColors.ink,
            Typeface.BOLD
        )
        val rightLimit = text(
            "",
            12f,
            AppColors.ink,
            Typeface.BOLD
        )
        val footer = text("", 12f, AppColors.muted)
        val stackControls =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        val root = card().apply {
            addView(dataDuelHeader(), matchFixed(imageHeaderHeight()))
            addView(badge, matchWrap(top = 12))
            addView(
                dataDuelIdentityRow(
                    leftEvent = leftEvent,
                    rightEvent = rightEvent
                ),
                matchWrap(top = 11)
            )
            addView(score, matchWrap(top = 11))
            addView(summary, matchWrap(top = 4))
            addView(chart, matchFixed(238, top = 8))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackControls) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    addView(
                        leftLimit,
                        if (stackControls) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(5)
                            }
                        }
                    )
                    addView(
                        rightLimit,
                        if (stackControls) {
                            matchWrap(top = 6)
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                leftMargin = dp(5)
                            }
                        }
                    )
                },
                matchWrap(top = 5)
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackControls) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    addView(
                        outlineButton(
                            "Выбрать справа",
                            AppColors.signal,
                            onChooseOpponent
                        ).apply {
                            contentDescription =
                                "Выбрать другое событие справа"
                        },
                        if (stackControls) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(5)
                            }
                        }
                    )
                    addView(
                        outlineButton(
                            "Поменять стороны",
                            AppColors.accentDark,
                            onSwap
                        ).apply {
                            contentDescription =
                                "Открыть правое событие и оставить текущее слева"
                        },
                        if (stackControls) {
                            matchWrap(top = 7)
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                leftMargin = dp(5)
                            }
                        }
                    )
                },
                matchWrap(top = 12)
            )
            addView(footer, matchWrap(top = 11))
        }
        return DataDuelPanel(
            root = root,
            badge = badge,
            score = score,
            summary = summary,
            chart = chart,
            leftLimit = leftLimit,
            rightLimit = rightLimit,
            footer = footer,
            leftTitle = leftEvent.match,
            rightTitle = rightEvent.match
        )
    }

    private fun dataDuelHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.data_duel)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Два независимых аналитика сравнивают качество данных двух спортивных событий"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ДУЭЛЬ ДАННЫХ",
                            11f,
                            Color.rgb(184, 239, 227),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Сравнение качества, не исхода",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun dataDuelIdentityRow(
        leftEvent: SportEvent,
        rightEvent: SportEvent
    ): LinearLayout {
        return LinearLayout(this).apply {
            val stack =
                resources.configuration.fontScale >= 1.3f ||
                    resources.configuration.screenWidthDp < 380 ||
                    leftEvent.match.length > 48 ||
                    rightEvent.match.length > 48
            orientation = if (stack) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            gravity = if (stack) Gravity.START else Gravity.TOP
            addView(
                dataDuelIdentity(
                    event = leftEvent,
                    side = "СЛЕВА",
                    color = AppColors.accentDark
                ),
                if (stack) {
                    matchWrap()
                } else {
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        rightMargin = dp(10)
                    }
                }
            )
            addView(
                divider(),
                if (stack) {
                    matchFixed(1, top = 10, bottom = 10)
                } else {
                    LinearLayout.LayoutParams(
                        dp(1),
                        dp(82)
                    )
                }
            )
            addView(
                dataDuelIdentity(
                    event = rightEvent,
                    side = "СПРАВА",
                    color = AppColors.signal
                ),
                if (stack) {
                    matchWrap()
                } else {
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        leftMargin = dp(10)
                    }
                }
            )
        }
    }

    private fun dataDuelIdentity(
        event: SportEvent,
        side: String,
        color: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(82)
            addView(
                text(
                    side,
                    10f,
                    color,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    event.match,
                    13.5f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 2)
            )
            addView(
                text(
                    event.tournament,
                    11f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
        }
    }

    private fun renderDataDuel(
        panel: DataDuelPanel,
        result: DataDuelResult
    ) {
        val tone = dataDuelTone(result.balance)
        panel.badge.text = getString(
            R.string.data_duel_badge,
            result.leftWins,
            result.rightWins
        )
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(
            tone.background,
            14
        )
        panel.score.text = dataDuelScore(result)
        panel.score.setTextColor(tone.foreground)
        panel.summary.text = getString(
            R.string.data_duel_summary,
            result.ties
        )
        panel.chart.setResult(
            value = result,
            leftTitle = panel.leftTitle,
            rightTitle = panel.rightTitle
        )
        renderDataDuelLimit(
            view = panel.leftLimit,
            prefix = "Слева",
            decision = result.left.decisionCeiling
        )
        renderDataDuelLimit(
            view = panel.rightLimit,
            prefix = "Справа",
            decision = result.right.decisionCeiling
        )
        panel.footer.text = getString(
            R.string.data_duel_footer,
            result.shortFingerprint
        )
    }

    private fun renderDataDuelLimit(
        view: TextView,
        prefix: String,
        decision: SavedDecision
    ) {
        val tone = dataDuelDecisionTone(decision)
        view.text = getString(
            R.string.data_duel_limit,
            prefix,
            decisionTitle(decision)
        )
        view.setTextColor(tone.foreground)
        view.background = rounded(tone.background, 8)
        view.setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    private fun dataDuelScore(
        result: DataDuelResult
    ): String {
        return when (result.balance) {
            DataDuelSide.LEFT ->
                "Больше покрытых дорожек слева"
            DataDuelSide.RIGHT ->
                "Больше покрытых дорожек справа"
            DataDuelSide.TIE ->
                "Паритет качества данных"
        }
    }

    private fun dataDuelTone(
        side: DataDuelSide
    ): Tone {
        return when (side) {
            DataDuelSide.LEFT ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            DataDuelSide.RIGHT ->
                Tone(AppColors.signal, AppColors.signalSoft)
            DataDuelSide.TIE ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
    }

    private fun dataDuelDecisionTone(
        decision: SavedDecision
    ): Tone {
        return when (decision) {
            SavedDecision.SKIP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            SavedDecision.OBSERVE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            SavedDecision.DATA_READY ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun evidenceRelayPanel(
        onAction: (SignalFactor) -> Unit
    ): EvidenceRelayPanel {
        val badge = label(
            "",
            AppColors.warningSoft,
            AppColors.warning
        )
        val metric = text(
            "",
            25f,
            AppColors.ink,
            Typeface.BOLD
        )
        val start = text(
            "",
            12.5f,
            AppColors.signal,
            Typeface.BOLD
        )
        val body = text("", 13.5f, AppColors.ink)
        val chart = EvidenceRelayView(this)
        val legend = text(
            "0 — нет подтверждения • 1 — один источник • 2 — два независимых источника",
            10.5f,
            AppColors.muted,
            Typeface.BOLD
        )
        val plan = text(
            "",
            12.5f,
            AppColors.ink,
            Typeface.BOLD
        ).apply {
            background = rounded(AppColors.background, 8)
            setPadding(dp(11), dp(9), dp(11), dp(9))
        }
        val action = outlineButton(
            "",
            AppColors.warning
        ) {}.apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setOnClickListener {
                (tag as? SignalFactor)?.let(onAction)
            }
        }
        val footer = text("", 10.5f, AppColors.muted, Typeface.BOLD)
        val root = card().apply {
            addView(evidenceRelayHeader(), matchFixed(imageHeaderHeight()))
            addView(badge, matchWrap(top = 12))
            addView(metric, matchWrap(top = 10))
            addView(start, matchWrap(top = 4))
            addView(body, matchWrap(top = 8))
            addView(chart, matchFixed(214, top = 9))
            addView(legend, matchWrap(top = 4))
            addView(plan, matchWrap(top = 10))
            addView(action, matchWrap(top = 11))
            addView(footer, matchWrap(top = 10))
        }
        return EvidenceRelayPanel(
            root = root,
            badge = badge,
            metric = metric,
            start = start,
            body = body,
            chart = chart,
            legend = legend,
            plan = plan,
            action = action,
            footer = footer
        )
    }

    private fun evidenceRelayHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.evidence_relay)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Пять дорожек подтверждений сходятся к стартовым воротам события"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ЭСТАФЕТА ФАКТОВ",
                            11f,
                            Color.rgb(184, 239, 227),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Что доживет до старта",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun renderEvidenceRelay(
        panel: EvidenceRelayPanel,
        result: EvidenceRelayResult?,
        event: SportEvent,
        now: Long
    ) {
        if (result == null) {
            panel.badge.text = if (
                event.startAt != null && event.startAt <= now
            ) {
                "СТАРТ НАСТУПИЛ"
            } else {
                "НЕТ ТОЧНОГО СТАРТА"
            }
            panel.badge.setTextColor(AppColors.muted)
            panel.badge.background = rounded(
                AppColors.background,
                14
            )
            panel.metric.visibility = View.GONE
            panel.chart.visibility = View.GONE
            panel.legend.visibility = View.GONE
            panel.plan.visibility = View.GONE
            panel.action.visibility = View.GONE
            panel.start.text = if (
                event.startAt != null && event.startAt <= now
            ) {
                "Будущая проекция закрыта: указанное время события уже прошло."
            } else {
                "Для этого события время указано как «по расписанию»."
            }
            panel.body.text = getString(
                R.string.evidence_relay_no_start_body
            )
            panel.footer.text = getString(
                R.string.evidence_relay_no_start_footer
            )
            return
        }

        panel.metric.visibility = View.VISIBLE
        panel.chart.visibility = View.VISIBLE
        panel.legend.visibility = View.VISIBLE
        panel.plan.visibility = View.VISIBLE
        panel.footer.visibility = View.VISIBLE
        val tone = evidenceRelayTone(result.state)
        panel.badge.text = evidenceRelayStateTitle(result.state)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(tone.background, 14)
        val currentReadiness = result.currentEvidenceResult
            .effectiveSignal.readiness
        val startReadiness = result.startEvidenceResult
            .effectiveSignal.readiness
        panel.metric.text = getString(
            R.string.evidence_relay_metric,
            currentReadiness,
            startReadiness
        )
        panel.metric.setTextColor(tone.foreground)
        val sourceTitle = when (result.start.source) {
            EventStartSource.EVENT_PACK -> "EVENT PACK"
            EventStartSource.DEMO_SCHEDULE -> "ДЕМО-РАСПИСАНИЕ"
        }
        panel.start.text = getString(
            R.string.evidence_relay_start,
            sourceTitle,
            TimeBridgeEngine.formatInstant(
                startAt = result.start.startAt,
                selectedZone = state.selectedRegionalZone
            ),
            FreshnessFormatter.duration(
                result.start.startAt - now
            )
        )
        panel.body.text = evidenceRelaySummary(result)
        panel.chart.setResult(result)
        panel.plan.text = evidenceRelayPlan(result, now)
        result.priorityFactor?.let { factor ->
            val priority = result.factors.first {
                it.factor == factor
            }
            val safeAt = checkNotNull(priority.safeRecheckAt)
            panel.action.visibility = View.VISIBLE
            panel.action.tag = factor
            panel.action.text = if (safeAt <= now) {
                getString(
                    R.string.evidence_relay_action_now,
                    factor.title
                )
            } else {
                getString(
                    R.string.evidence_relay_action_future,
                    factor.title,
                    FreshnessFormatter.duration(safeAt - now)
                )
            }
            panel.action.setTextColor(tone.foreground)
            panel.action.background = rippleRounded(
                AppColors.surface,
                8,
                tone.foreground,
                1
            )
            panel.action.contentDescription =
                "${panel.action.text}. Перейти к фактору."
        } ?: run {
            panel.action.tag = null
            panel.action.visibility = View.GONE
        }
        panel.footer.text = getString(
            R.string.evidence_relay_footer,
            result.shortFingerprint
        )
    }

    private fun evidenceRelaySummary(
        result: EvidenceRelayResult
    ): String {
        val recheck = result.factors.filter {
            it.state == EvidenceRelayFactorState.RECHECK_REQUIRED
        }
        return when (result.state) {
            EvidenceRelayState.INTACT ->
                "Все ${result.survivingCount} подтвержденных факторов сохранят текущий уровень к старту."
            EvidenceRelayState.RECHECK_REQUIRED ->
                "До старта потеряют уровень: ${recheck.joinToString(", ") { it.factor.title.lowercase(Locale.getDefault()) }}. Повторная проверка восстанавливает срок, но не повышает оценку."
            EvidenceRelayState.READINESS_DROP -> {
                val current = verdictTitle(
                    result.currentEvidenceResult.effectiveSignal.verdict
                ).lowercase(Locale.getDefault())
                val atStart = verdictTitle(
                    result.startEvidenceResult.effectiveSignal.verdict
                ).lowercase(Locale.getDefault())
                "Без обновления статус изменится: $current → $atStart. Дорожек к перепроверке: ${result.recheckCount}."
            }
            EvidenceRelayState.NO_CONFIRMED_FACTS ->
                "Ни один фактор не подтвержден. Эстафета не строит ложные сроки из отсутствующих данных."
        }
    }

    private fun evidenceRelayPlan(
        result: EvidenceRelayResult,
        now: Long
    ): String {
        val rechecks = result.factors
            .filter {
                it.state ==
                    EvidenceRelayFactorState.RECHECK_REQUIRED
            }
            .sortedWith(
                compareBy<EvidenceRelayFactor> {
                    it.safeRecheckAt ?: Long.MAX_VALUE
                }.thenBy { it.factor.ordinal }
            )
        if (rechecks.isNotEmpty()) {
            val visible = rechecks.take(3).map { item ->
                val safeAt = checkNotNull(item.safeRecheckAt)
                val window = if (safeAt <= now) {
                    "перепроверить сейчас"
                } else {
                    "окно с " + TimeBridgeEngine.formatInstant(
                        startAt = safeAt,
                        selectedZone = state.selectedRegionalZone
                    )
                }
                "${item.factor.title} • $window"
            }.toMutableList()
            if (rechecks.size > visible.size) {
                visible +=
                    "Еще дорожек: ${rechecks.size - visible.size}"
            }
            return visible.joinToString("\n")
        }
        val unconfirmed = result.factors.filter {
            it.state == EvidenceRelayFactorState.UNCONFIRMED
        }
        return if (unconfirmed.isNotEmpty()) {
            "Без подтверждения: ${unconfirmed.joinToString(", ") { it.factor.title.lowercase(Locale.getDefault()) }}. Срок не рассчитывается."
        } else {
            "Повторная проверка до старта не требуется. Следите за фактическими изменениями источников."
        }
    }

    private fun evidenceRelayStateTitle(
        state: EvidenceRelayState
    ): String {
        return when (state) {
            EvidenceRelayState.INTACT -> "ДОЖИВАЕТ"
            EvidenceRelayState.RECHECK_REQUIRED ->
                "НУЖНА ПЕРЕПРОВЕРКА"
            EvidenceRelayState.READINESS_DROP ->
                "СТАТУС СНИЗИТСЯ"
            EvidenceRelayState.NO_CONFIRMED_FACTS ->
                "НЕТ ПОДТВЕРЖДЕНИЙ"
        }
    }

    private fun evidenceRelayTone(
        state: EvidenceRelayState
    ): Tone {
        return when (state) {
            EvidenceRelayState.INTACT ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            EvidenceRelayState.RECHECK_REQUIRED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            EvidenceRelayState.READINESS_DROP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            EvidenceRelayState.NO_CONFIRMED_FACTS ->
                Tone(AppColors.muted, AppColors.background)
        }
    }

    private fun preflightProtocolPanel(
        onExport: (PreflightProtocol) -> Unit
    ): PreflightProtocolPanel {
        val badge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        )
        val metric = text(
            "",
            25f,
            AppColors.ink,
            Typeface.BOLD
        )
        val start = text(
            "",
            12.5f,
            AppColors.signal,
            Typeface.BOLD
        )
        val body = text("", 13.5f, AppColors.ink)
        val chart = PreflightProtocolView(this)
        val plan = text(
            "",
            12.5f,
            AppColors.ink,
            Typeface.BOLD
        ).apply {
            background = rounded(AppColors.background, 8)
            setPadding(dp(11), dp(9), dp(11), dp(9))
        }
        val sync = text(
            "",
            11.5f,
            AppColors.signal,
            Typeface.BOLD
        ).apply {
            background = rounded(AppColors.signalSoft, 8)
            setPadding(dp(11), dp(9), dp(11), dp(9))
        }
        val action = outlineButton(
            "Передать план .ics",
            AppColors.signal
        ) {}.apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setOnClickListener {
                (tag as? PreflightProtocol)?.let(onExport)
            }
        }
        val footer = text("", 10.5f, AppColors.muted, Typeface.BOLD)
        val root = card().apply {
            addView(preflightProtocolHeader(), matchFixed(imageHeaderHeight()))
            addView(badge, matchWrap(top = 12))
            addView(metric, matchWrap(top = 10))
            addView(start, matchWrap(top = 4))
            addView(body, matchWrap(top = 8))
            addView(chart, matchFixed(214, top = 9))
            addView(plan, matchWrap(top = 10))
            addView(sync, matchWrap(top = 8))
            addView(action, matchWrap(top = 11))
            addView(footer, matchWrap(top = 10))
        }
        return PreflightProtocolPanel(
            root = root,
            badge = badge,
            metric = metric,
            start = start,
            body = body,
            chart = chart,
            plan = plan,
            sync = sync,
            action = action,
            footer = footer
        )
    }

    private fun preflightProtocolHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.preflight_protocol)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Пять контрольных кассет распределены по плану до старта события"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ПРЕДСТАРТОВЫЙ ПРОТОКОЛ",
                            11f,
                            Color.rgb(184, 239, 227),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Проверки становятся планом",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun renderPreflightProtocol(
        panel: PreflightProtocolPanel,
        protocol: PreflightProtocol?,
        event: SportEvent,
        now: Long
    ) {
        if (protocol == null) {
            panel.badge.text = "НЕТ ТОЧНОГО СТАРТА"
            panel.badge.setTextColor(AppColors.muted)
            panel.badge.background = rounded(
                AppColors.background,
                14
            )
            panel.metric.visibility = View.GONE
            panel.chart.visibility = View.GONE
            panel.plan.visibility = View.GONE
            panel.sync.visibility = View.GONE
            panel.action.visibility = View.GONE
            panel.start.text = preflightUnavailableStart(event, now)
            panel.body.text = preflightUnavailableBody()
            panel.footer.text = preflightUnavailableFooter()
            return
        }

        panel.metric.visibility = View.VISIBLE
        panel.chart.visibility = View.VISIBLE
        panel.plan.visibility = View.VISIBLE
        panel.sync.visibility = View.VISIBLE
        panel.action.visibility = View.VISIBLE
        panel.footer.visibility = View.VISIBLE
        val tone = preflightProtocolTone(protocol.state)
        panel.badge.text = preflightProtocolStateTitle(protocol.state)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(tone.background, 14)
        panel.metric.text = preflightProtocolMetric(protocol)
        panel.metric.setTextColor(tone.foreground)
        panel.start.text = preflightProtocolStart(protocol)
        panel.body.text = preflightProtocolSummary(protocol)
        panel.chart.setResult(protocol)
        panel.plan.text = preflightProtocolPlan(protocol)
        val syncResult = PreflightSyncEngine.evaluate(
            protocol = protocol,
            selectedZone = state.selectedRegionalZone,
            stored = state.preflightExportReceipt(event.id)
        )
        val syncTone = preflightSyncTone(syncResult.state)
        panel.sync.text = preflightSyncText(syncResult)
        panel.sync.setTextColor(syncTone.foreground)
        panel.sync.background = rounded(syncTone.background, 8)
        panel.action.tag = protocol
        panel.action.text = preflightSyncAction(syncResult.state)
        panel.action.setTextColor(syncTone.foreground)
        panel.action.background = rippleRounded(
            AppColors.surface,
            8,
            syncTone.foreground,
            1
        )
        panel.action.contentDescription =
            "Передать предстартовый протокол в формате iCalendar"
        panel.footer.text = preflightProtocolFooter(protocol)
    }

    private fun preflightProtocolStateTitle(
        state: PreflightProtocolState
    ): String {
        return when (state) {
            PreflightProtocolState.SEALED -> "СРОК ДЕРЖИТСЯ"
            PreflightProtocolState.PLANNED -> "ПЛАН СОБРАН"
            PreflightProtocolState.ACTION_NOW -> "ОКНО ОТКРЫТО"
            PreflightProtocolState.INCOMPLETE -> "ЕСТЬ ПРОБЕЛЫ"
        }
    }

    private fun preflightProtocolMetric(
        protocol: PreflightProtocol
    ): String {
        return if (protocol.actionCount == 0) {
            "5 факторов без повтора"
        } else {
            "Проверок ${protocol.actionCount} • слотов ${protocol.slots.size}"
        }
    }

    private fun preflightProtocolStart(
        protocol: PreflightProtocol
    ): String {
        val zone = state.selectedRegionalZone
        return "СТАРТ • " + TimeBridgeEngine.formatInstant(
            startAt = protocol.start.startAt,
            selectedZone = zone
        )
    }

    private fun preflightProtocolSummary(
        protocol: PreflightProtocol
    ): String {
        return when (protocol.state) {
            PreflightProtocolState.SEALED ->
                "Все пять подтверждений сохраняют уровень до старта. В .ics попадет только контрольный момент события."
            PreflightProtocolState.PLANNED -> {
                val next = checkNotNull(protocol.nextSlot)
                "Первое безопасное окно откроется через ${FreshnessFormatter.duration(next.scheduledAt - protocol.evaluatedAt)}. Импорт календаря не отмечает проверку выполненной."
            }
            PreflightProtocolState.ACTION_NOW -> {
                val factors = checkNotNull(protocol.nextSlot)
                    .factors.joinToString(", ") {
                        it.title.lowercase(Locale.getDefault())
                    }
                "Первый слот открыт сейчас: $factors. Остальные окна остаются привязаны к старту."
            }
            PreflightProtocolState.INCOMPLETE -> {
                val missing = protocol.checks.filter {
                    it.state == PreflightFactorState.MISSING
                }.joinToString(", ") {
                    it.factor.title.lowercase(Locale.getDefault())
                }
                "Нет подтверждения: $missing. Протокол ставит пробелы в первый слот, но не считает их проверенными."
            }
        }
    }

    private fun preflightProtocolPlan(
        protocol: PreflightProtocol
    ): String {
        val zone = state.selectedRegionalZone
        val lines = protocol.slots.map { slot ->
            val time = if (slot.immediate) {
                "Сейчас"
            } else {
                TimeBridgeEngine.formatInstant(
                    startAt = slot.scheduledAt,
                    selectedZone = zone
                )
            }
            val factors = slot.factors.joinToString(", ") {
                it.title
            }
            "$time • $factors"
        }.toMutableList()
        if (lines.isEmpty()) {
            lines += "Повторные проверки не требуются"
        }
        lines += "Старт • " + TimeBridgeEngine.formatInstant(
            startAt = protocol.start.startAt,
            selectedZone = zone
        )
        return lines.joinToString("\n")
    }

    private fun preflightProtocolFooter(
        protocol: PreflightProtocol
    ): String {
        return "UTC В ФАЙЛЕ • ЛОКАЛЬНОЕ ВРЕМЯ В ОПИСАНИИ • SHA-256 ${protocol.shortFingerprint}"
    }

    private fun preflightSyncText(
        result: PreflightSyncResult
    ): String {
        return when (result.state) {
            PreflightSyncState.NOT_EXPORTED ->
                "КОНТУР СИНХРОНИЗАЦИИ • ФАЙЛ ЕЩЁ НЕ ПЕРЕДАН\nПервая передача создаст ревизию 1."
            PreflightSyncState.CURRENT -> {
                val receipt = checkNotNull(result.receipt)
                "ПЛАН СОВПАДАЕТ • РЕВИЗИЯ ${receipt.sequence}\nЛокальная квитанция ${receipt.shortScheduleFingerprint}."
            }
            PreflightSyncState.STALE -> {
                val receipt = checkNotNull(result.receipt)
                val changes = result.drift.joinToString(" • ") {
                    preflightDriftTitle(it)
                }
                val nextRevision = if (
                    receipt.sequence < Int.MAX_VALUE
                ) {
                    (receipt.sequence + 1).toString()
                } else {
                    "недоступна"
                }
                "ОБНОВИТЬ .ICS • $changes\nСледующая ревизия: $nextRevision."
            }
            PreflightSyncState.WITHDRAWN -> {
                val receipt = checkNotNull(result.receipt)
                val nextRevision = if (
                    receipt.sequence < Int.MAX_VALUE
                ) {
                    (receipt.sequence + 1).toString()
                } else {
                    "недоступна"
                }
                "ПЛАН ОТОЗВАН • РЕВИЗИЯ ${receipt.sequence}\nВосстановление создаст ревизию $nextRevision."
            }
            PreflightSyncState.TAMPERED ->
                "КВИТАНЦИЯ ПОВРЕЖДЕНА • СОЗДАТЬ НОВУЮ\nЦепочка ревизий начнётся заново."
        }
    }

    private fun preflightDriftTitle(
        drift: PreflightDriftKind
    ): String {
        return when (drift) {
            PreflightDriftKind.START -> "СТАРТ"
            PreflightDriftKind.WINDOWS -> "ОКНА"
            PreflightDriftKind.ZONE -> "ГОРОД"
            PreflightDriftKind.LABEL -> "НАЗВАНИЕ"
            PreflightDriftKind.METADATA -> "МЕТАДАННЫЕ"
        }
    }

    private fun preflightSyncAction(
        syncState: PreflightSyncState
    ): String {
        return when (syncState) {
            PreflightSyncState.NOT_EXPORTED -> "Передать план .ics"
            PreflightSyncState.CURRENT -> "Передать ещё раз .ics"
            PreflightSyncState.WITHDRAWN ->
                "Восстановить план .ics"
            PreflightSyncState.STALE,
            PreflightSyncState.TAMPERED -> "Обновить план .ics"
        }
    }

    private fun preflightSyncTone(
        syncState: PreflightSyncState
    ): Tone {
        return when (syncState) {
            PreflightSyncState.NOT_EXPORTED ->
                Tone(AppColors.signal, AppColors.signalSoft)
            PreflightSyncState.CURRENT ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            PreflightSyncState.STALE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PreflightSyncState.WITHDRAWN ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PreflightSyncState.TAMPERED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun preflightUnavailableStart(
        event: SportEvent,
        now: Long
    ): String {
        return if (event.startAt != null && event.startAt <= now) {
            "Указанное время события уже прошло."
        } else {
            "Время события указано как «по расписанию»."
        }
    }

    private fun preflightUnavailableBody(): String {
        return "Без будущего точного старта приложение не создает календарные события и ложные дедлайны."
    }

    private fun preflightUnavailableFooter(): String {
        return "READ-ONLY • БЕЗ РАЗРЕШЕНИЯ КАЛЕНДАРЯ И ФОНОВОГО СЕРВИСА"
    }

    private fun preflightProtocolTone(
        state: PreflightProtocolState
    ): Tone {
        return when (state) {
            PreflightProtocolState.SEALED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            PreflightProtocolState.PLANNED ->
                Tone(AppColors.signal, AppColors.signalSoft)
            PreflightProtocolState.ACTION_NOW ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PreflightProtocolState.INCOMPLETE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun confirmPreflightWithdrawal(
        entry: RevisionRadarEntry
    ) {
        require(entry.action == RevisionRadarAction.WITHDRAW)
        val receipt = checkNotNull(entry.receipt)
        if (receipt.sequence == Int.MAX_VALUE) {
            Toast.makeText(
                this,
                "Достигнут предел цепочки ревизий",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val nextRevision = receipt.sequence + 1
        val eventCount = receipt.slots.size + 1
        AlertDialog.Builder(this)
            .setTitle("Отозвать календарный план?")
            .setMessage(
                "Будет создана ревизия $nextRevision с " +
                    "STATUS:CANCELLED для $eventCount событий. " +
                    "Спорт Пульс не удаляет записи скрыто: применение " +
                    "отзыва зависит от выбранного календаря."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Создать отзыв") { _, _ ->
                sharePreflightWithdrawal(receipt)
            }
            .show()
    }

    private fun confirmForgetRevisionReceipt(
        entry: RevisionRadarEntry
    ) {
        require(entry.action == RevisionRadarAction.FORGET)
        AlertDialog.Builder(this)
            .setTitle("Удалить локальную квитанцию?")
            .setMessage(
                "Радар забудет эту запись на устройстве. События во " +
                    "внешнем календаре не изменятся и не будут удалены."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить локально") { _, _ ->
                state.clearPreflightExportReceipt(entry.eventId)
                rerenderContentPreservingScroll()
            }
            .show()
    }

    private fun sharePreflightWithdrawal(
        previousReceipt: PreflightExportReceipt
    ) {
        val withdrawal = runCatching {
            PreflightExportReceiptFactory.withdraw(
                previous = previousReceipt,
                exportedAt = System.currentTimeMillis()
            )
        }.getOrElse {
            Toast.makeText(
                this,
                "Не удалось создать ревизию отзыва",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val uri = runCatching {
            val file = PreflightCalendarExporter(
                applicationContext
            ).exportWithdrawal(withdrawal)
            PreflightCalendarProvider.uriFor(
                applicationContext,
                file
            )
        }.getOrElse {
            Toast.makeText(
                this,
                "Не удалось создать календарный отзыв",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Отзыв календарного плана: ${withdrawal.eventLabel}"
            )
            putExtra(
                Intent.EXTRA_TEXT,
                preflightWithdrawalShareText(withdrawal)
            )
            clipData = ClipData.newRawUri(
                "Отзыв предстартового протокола",
                uri
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Передать отзыв .ics"
                )
            )
            state.savePreflightExportReceipt(withdrawal)
            rerenderContentPreservingScroll()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Нет приложения для передачи календаря",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun preflightWithdrawalShareText(
        withdrawal: PreflightExportReceipt
    ): String {
        require(withdrawal.withdrawn)
        return buildString {
            appendLine("Спорт Пульс • Отзыв календарного плана")
            appendLine(withdrawal.eventLabel)
            appendLine(
                "Ревизия ${withdrawal.sequence} • " +
                    "отмен ${withdrawal.slots.size + 1}"
            )
            appendLine(
                "План SHA-256 ${withdrawal.scheduleFingerprint}"
            )
            append(
                "UID сохранены. Запуск передачи не подтверждает, " +
                    "что внешний календарь применил отмену."
            )
        }
    }

    private fun sharePreflightProtocol(
        event: SportEvent,
        protocol: PreflightProtocol
    ) {
        val selectedZone = state.selectedRegionalZone
        val stored = state.preflightExportReceipt(event.id)
        val receipt = runCatching {
            PreflightSyncEngine.receiptForExport(
                protocol = protocol,
                selectedZone = selectedZone,
                stored = stored,
                exportedAt = System.currentTimeMillis()
            )
        }.getOrElse {
            Toast.makeText(
                this,
                "Не удалось создать ревизию плана",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val uri = runCatching {
            val file = PreflightCalendarExporter(
                applicationContext
            ).export(
                protocol = protocol,
                selectedZone = selectedZone,
                receipt = receipt,
                previousReceipt = stored.receipt
            )
            PreflightCalendarProvider.uriFor(
                applicationContext,
                file
            )
        }.getOrElse {
            Toast.makeText(
                this,
                "Не удалось создать предстартовый протокол",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Предстартовый протокол: ${event.match}"
            )
            putExtra(
                Intent.EXTRA_TEXT,
                preflightProtocolShareText(
                    event = event,
                    protocol = protocol,
                    receipt = receipt,
                    selectedZone = selectedZone
                )
            )
            clipData = ClipData.newRawUri(
                "Предстартовый протокол",
                uri
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Передать план .ics"
                )
            )
            state.savePreflightExportReceipt(receipt)
            rerenderContentPreservingScroll()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Нет приложения для передачи календаря",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun preflightProtocolShareText(
        event: SportEvent,
        protocol: PreflightProtocol,
        receipt: PreflightExportReceipt,
        selectedZone: RegionalZone
    ): String {
        return buildString {
            appendLine("Спорт Пульс • Предстартовый протокол")
            appendLine(event.match)
            appendLine(
                "Проверок ${protocol.actionCount} • " +
                    "слотов ${protocol.slots.size}"
            )
            appendLine("Ревизия ${receipt.sequence}")
            appendLine(
                "Старт • " + TimeBridgeEngine.formatInstant(
                    startAt = protocol.start.startAt,
                    selectedZone = selectedZone
                )
            )
            appendLine(
                "План SHA-256 ${receipt.scheduleFingerprint}"
            )
            append(
                "План свежести данных, не прогноз исхода. " +
                    "Проверки не выполняются автоматически."
            )
        }
    }

    private fun chronoLensPanel(
        onOffsetSelected: (Int) -> Unit,
        onNow: () -> Unit,
        onNext: () -> Unit
    ): ChronoLensPanel {
        val badge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        )
        val metric = text(
            "",
            25f,
            AppColors.ink,
            Typeface.BOLD
        )
        val body = text("", 13.5f, AppColors.ink)
        val chart = ChronoLensView(this)
        val selectedTime = text(
            "",
            12f,
            AppColors.signal,
            Typeface.BOLD
        )
        val horizon = text(
            "",
            10.5f,
            AppColors.muted,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.END
        }
        val markets = text(
            "",
            12f,
            AppColors.ink,
            Typeface.BOLD
        )
        val limit = text(
            "",
            12f,
            AppColors.ink,
            Typeface.BOLD
        )
        val footer = text("", 12f, AppColors.muted)
        val seekBar = SeekBar(this).apply {
            max = 1
            progress = 0
            progressTintList = ColorStateList.valueOf(
                AppColors.signal
            )
            thumbTintList = ColorStateList.valueOf(
                AppColors.signal
            )
            contentDescription =
                "Выбран текущий момент"
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (!fromUser) return
                        selectedTime.text = if (progress == 0) {
                            getString(
                                R.string.chrono_lens_selected_now
                            )
                        } else {
                            getString(
                                R.string
                                    .chrono_lens_selected_preview,
                                FreshnessFormatter.duration(
                                    progress.toLong() *
                                        ChronoLensPolicy
                                            .MINUTE_MILLIS
                                )
                            )
                        }
                        seekBar.contentDescription =
                            if (progress == 0) {
                                "Выбран текущий момент"
                            } else {
                                "Выбрано через ${
                                    FreshnessFormatter.duration(
                                        progress.toLong() *
                                            ChronoLensPolicy
                                                .MINUTE_MILLIS
                                    )
                                }"
                            }
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar
                    ) = Unit

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar
                    ) {
                        onOffsetSelected(seekBar.progress)
                    }
                }
            )
        }
        val nowButton = outlineButton(
            "Сейчас",
            AppColors.accentDark,
            onNow
        ).apply {
            contentDescription =
                "Вернуть Хронолинзу к текущему моменту"
        }
        val nextButton = outlineButton(
            "Следующая граница",
            AppColors.signal,
            onNext
        )
        val stackControls =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        val root = card().apply {
            addView(chronoLensHeader(), matchFixed(imageHeaderHeight()))
            addView(badge, matchWrap(top = 12))
            addView(metric, matchWrap(top = 10))
            addView(body, matchWrap(top = 4))
            addView(chart, matchFixed(220, top = 8))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(selectedTime)
                    addView(horizon, matchWrap(top = 2))
                },
                matchWrap(top = 6)
            )
            addView(seekBar, matchWrap(top = 2))
            addView(markets, matchWrap(top = 7))
            addView(limit, matchWrap(top = 7))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackControls) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    addView(
                        nowButton,
                        if (stackControls) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                0.38f
                            ).apply {
                                rightMargin = dp(5)
                            }
                        }
                    )
                    addView(
                        nextButton,
                        if (stackControls) {
                            matchWrap(top = 7)
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                0.62f
                            ).apply {
                                leftMargin = dp(5)
                            }
                        }
                    )
                },
                matchWrap(top = 12)
            )
            addView(footer, matchWrap(top = 11))
        }
        return ChronoLensPanel(
            root = root,
            badge = badge,
            metric = metric,
            body = body,
            chart = chart,
            selectedTime = selectedTime,
            horizon = horizon,
            seekBar = seekBar,
            markets = markets,
            limit = limit,
            nextButton = nextButton,
            footer = footer
        )
    }

    private fun chronoLensHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.chrono_lens)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Аналитик перемещает временную шкалу пяти потоков спортивных данных"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "ХРОНОЛИНЗА",
                            11f,
                            Color.rgb(184, 239, 227),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Будущее данных, не результата",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun renderChronoLens(
        panel: ChronoLensPanel,
        result: ChronoLensResult
    ) {
        val tone = chronoLensTone(result.state)
        panel.badge.text = getString(
            R.string.chrono_lens_badge,
            chronoLensStateTitle(result.state)
        )
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(
            tone.background,
            14
        )
        panel.metric.text = getString(
            R.string.chrono_lens_metric,
            result.baseline.readiness,
            result.selected.readiness
        )
        panel.metric.setTextColor(tone.foreground)
        panel.body.text = chronoLensSummary(result)
        panel.chart.setResult(result)

        val selectedOffset =
            result.selectedAt - result.now
        panel.selectedTime.text = if (selectedOffset == 0L) {
            getString(R.string.chrono_lens_selected_now)
        } else {
            getString(
                R.string.chrono_lens_selected_future,
                FreshnessFormatter.duration(selectedOffset),
                formatDateTime(result.selectedAt)
            )
        }
        val horizonOffset =
            result.horizonAt - result.now
        panel.horizon.text = getString(
            R.string.chrono_lens_horizon,
            FreshnessFormatter.duration(horizonOffset)
        )
        val horizonMinutes = (
            (horizonOffset +
                ChronoLensPolicy.MINUTE_MILLIS - 1L) /
                ChronoLensPolicy.MINUTE_MILLIS
            ).toInt().coerceAtLeast(1)
        panel.seekBar.max = horizonMinutes
        panel.seekBar.progress = if (
            result.selectedAt == result.horizonAt
        ) {
            horizonMinutes
        } else {
            (
                selectedOffset /
                    ChronoLensPolicy.MINUTE_MILLIS
                ).toInt().coerceIn(0, horizonMinutes)
        }
        panel.seekBar.contentDescription =
            if (selectedOffset == 0L) {
                "Хронолинза: выбран текущий момент; " +
                    "горизонт ${
                        FreshnessFormatter.duration(
                            horizonOffset
                        )
                    }"
            } else {
                "Хронолинза: выбран момент через ${
                    FreshnessFormatter.duration(selectedOffset)
                } из ${
                    FreshnessFormatter.duration(horizonOffset)
                }"
            }

        panel.markets.text = getString(
            R.string.chrono_lens_markets,
            result.baseline.marketLens.coveredCount,
            result.selected.marketLens.coveredCount,
            result.baseline.marketLens.closedCount,
            result.selected.marketLens.closedCount
        )
        panel.markets.setTextColor(tone.foreground)
        panel.markets.background = rounded(
            tone.background,
            8
        )
        panel.markets.setPadding(
            dp(10),
            dp(8),
            dp(10),
            dp(8)
        )
        panel.limit.text = getString(
            R.string.chrono_lens_limit,
            decisionTitle(
                result.baseline.counterView.decisionCeiling
            ),
            decisionTitle(
                result.selected.counterView.decisionCeiling
            ),
            chronoLensGuardTitle(
                result.selected.decisionGuard
            )
        )
        panel.limit.setTextColor(AppColors.ink)
        panel.limit.background = rounded(
            AppColors.background,
            8
        )
        panel.limit.setPadding(
            dp(10),
            dp(8),
            dp(10),
            dp(8)
        )
        result.nextCheckpoint?.let { checkpoint ->
            panel.nextButton.visibility = View.VISIBLE
            panel.nextButton.text = getString(
                R.string.chrono_lens_next,
                FreshnessFormatter.duration(
                    checkpoint.at - result.now
                )
            )
            panel.nextButton.contentDescription =
                "${panel.nextButton.text}. " +
                    chronoLensCheckpointDescription(
                        checkpoint
                    )
        } ?: run {
            panel.nextButton.visibility = View.GONE
        }
        panel.footer.text = getString(
            R.string.chrono_lens_footer,
            result.shortFingerprint
        )
    }

    private fun chronoLensSummary(
        result: ChronoLensResult
    ): String {
        if (result.changedFactors.isNotEmpty()) {
            return getString(
                R.string.chrono_lens_summary_changed,
                result.changedFactors.joinToString(", ") {
                    it.shortTitle.lowercase(
                        Locale.forLanguageTag("ru-RU")
                    )
                },
                verdictTitle(result.baseline.verdict)
                    .lowercase(Locale.forLanguageTag("ru-RU")),
                verdictTitle(result.selected.verdict)
                    .lowercase(Locale.forLanguageTag("ru-RU"))
            )
        }
        val expiring =
            result.selected.freshness.expiringFactors
        return if (expiring.isEmpty()) {
            getString(
                R.string.chrono_lens_summary_stable
            )
        } else {
            getString(
                R.string.chrono_lens_summary_expiring,
                expiring.joinToString(", ") {
                    it.shortTitle.lowercase(
                        Locale.forLanguageTag("ru-RU")
                    )
                }
            )
        }
    }

    private fun chronoLensCheckpointDescription(
        checkpoint: ChronoLensCheckpoint
    ): String {
        return checkpoint.changes.joinToString(". ") { change ->
            when (change.kind) {
                ChronoLensChangeKind.EXPIRING ->
                    "${change.factor.title}: входит в окно истечения"
                ChronoLensChangeKind.LEVEL_DROP ->
                    "${change.factor.title}: ${
                        change.fromLevel.title
                    } станет ${change.toLevel.title}"
            }
        }
    }

    private fun chronoLensGuardTitle(
        guard: DecisionGuardResult?
    ): String {
        return when (guard?.status) {
            null -> "стоп-контракт: нет снимка"
            DecisionGuardStatus.SEALED_SKIP ->
                "стоп-контракт: вывод закрыт"
            DecisionGuardStatus.ARMED ->
                "стоп-контракт: не сработает"
            DecisionGuardStatus.TRIGGERED ->
                "стоп-контракт: сработает"
        }
    }

    private fun chronoLensStateTitle(
        state: ChronoLensState
    ): String {
        return when (state) {
            ChronoLensState.STABLE -> "СТАБИЛЬНО"
            ChronoLensState.NARROWING -> "СУЖЕНИЕ"
            ChronoLensState.DOWNGRADE -> "СНИЖЕНИЕ"
            ChronoLensState.STOP -> "СТОП-ГРАНИЦА"
        }
    }

    private fun chronoLensTone(
        state: ChronoLensState
    ): Tone {
        return when (state) {
            ChronoLensState.STABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            ChronoLensState.NARROWING ->
                Tone(AppColors.signal, AppColors.signalSoft)
            ChronoLensState.DOWNGRADE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            ChronoLensState.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun showDataDuelOpponentDialog(
        current: SportEvent
    ) {
        val candidates = catalogEvents.filter {
            it.id != current.id
        }
        if (candidates.isEmpty()) {
            Toast.makeText(
                this,
                "Для Дуэли данных нужны минимум два события.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val selectedIndex = candidates.indexOfFirst {
            it.id == state.dataDuelOpponentId
        }
        AlertDialog.Builder(this)
            .setTitle("Дуэль данных: событие справа")
            .setSingleChoiceItems(
                candidates.map(SportEvent::match).toTypedArray(),
                selectedIndex
            ) { dialog, which ->
                state.dataDuelOpponentId =
                    candidates[which].id
                dialog.dismiss()
                rerenderContentPreservingScroll()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun dataDuelUnavailablePanel(
        current: SportEvent
    ): LinearLayout {
        return card().apply {
            addView(dataDuelHeader(), matchFixed(imageHeaderHeight()))
            addView(
                label(
                    "НУЖНО 2 СОБЫТИЯ",
                    AppColors.warningSoft,
                    AppColors.warning
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    "В активном каталоге есть только «${
                        current.match
                    }». Добавьте еще одно событие в Event Pack, " +
                        "чтобы сравнить качество данных.",
                    14f,
                    AppColors.ink
                ),
                matchWrap(top = 9)
            )
        }
    }

    private fun counterViewPanel(
        result: CounterViewResult,
        onReviewChanged: (
            SignalFactor,
            CounterReviewState
        ) -> Unit
    ): CounterViewPanel {
        val badge = label(
            "",
            AppColors.warningSoft,
            AppColors.warning
        )
        val metric = text(
            "",
            18f,
            AppColors.ink,
            Typeface.BOLD
        )
        val body = text("", 14f, AppColors.ink)
        val balanceView = CounterViewBalanceView(this)
        val footer = text("", 12f, AppColors.muted)
        val factorLabels =
            linkedMapOf<SignalFactor, TextView>()
        val root = card().apply {
            addView(
                counterViewHeader(),
                matchFixed(imageHeaderHeight())
            )
            addView(badge, matchWrap(top = 12))
            addView(metric, matchWrap(top = 10))
            addView(body, matchWrap(top = 5))
            addView(
                balanceView,
                matchFixed(178, top = 8)
            )
            addView(
                text(
                    "Слева сила подтвержденной версии • справа результат контрпроверки",
                    12f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 2, bottom = 5)
            )

            val stackRows =
                resources.configuration.fontScale >= 1.3f ||
                    resources.configuration.screenWidthDp < 380
            result.factors.forEachIndexed { index, factorResult ->
                if (index > 0) {
                    addView(
                        divider(),
                        matchFixed(1, top = 7)
                    )
                }
                val reviewTone = counterReviewTone(
                    factorResult.reviewState
                )
                val factorCopy = text(
                    "",
                    13f,
                    AppColors.ink,
                    Typeface.BOLD
                )
                factorLabels[factorResult.factor] = factorCopy
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = if (stackRows) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackRows) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        factorCopy,
                        if (stackRows) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(10)
                            }
                        }
                    )
                    addView(
                        outlineButton(
                            factorResult.reviewState.title,
                            reviewTone.foreground
                        ) {
                            showCounterReviewDialog(
                                factor = factorResult.factor,
                                selected =
                                    factorResult.reviewState
                            ) { selected ->
                                onReviewChanged(
                                    factorResult.factor,
                                    selected
                                )
                            }
                        }.apply {
                            minHeight = dp(48)
                            setPadding(
                                dp(11),
                                dp(8),
                                dp(11),
                                dp(8)
                            )
                            contentDescription =
                                "${factorResult.factor.title}. ${
                                    factorResult.reviewState.title
                                }. Изменить Контрракурс."
                        },
                        if (stackRows) {
                            matchWrap(top = 6)
                        } else {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                    )
                }
                addView(
                    row,
                    matchWrap(
                        top = if (index == 0) 2 else 7
                    )
                )
            }
            addView(footer, matchWrap(top = 11))
        }
        return CounterViewPanel(
            root = root,
            badge = badge,
            metric = metric,
            body = body,
            balanceView = balanceView,
            factorLabels = factorLabels,
            footer = footer
        ).also {
            renderCounterView(it, result)
        }
    }

    private fun counterViewHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.counter_view)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Два аналитика независимо проверяют разные версии одного спортивного события"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "КОНТРРАКУРС",
                            11f,
                            Color.rgb(184, 239, 227),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Проверка против своей версии",
                            19f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun renderCounterView(
        panel: CounterViewPanel,
        result: CounterViewResult
    ) {
        val tone = counterViewTone(result.verdict)
        panel.badge.text = counterViewBadge(result.verdict)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(
            tone.background,
            14
        )
        panel.metric.text = counterViewMetric(result)
        panel.metric.setTextColor(tone.foreground)
        panel.body.text = counterViewExplanation(result)
        panel.balanceView.setResult(result)
        result.factors.forEach { factor ->
            panel.factorLabels[factor.factor]?.text =
                counterViewFactorLabel(factor)
        }
        panel.footer.text = getString(
            R.string.counter_view_footer,
            result.shortFingerprint
        )
    }

    private fun showCounterReviewDialog(
        factor: SignalFactor,
        selected: CounterReviewState,
        onSelected: (CounterReviewState) -> Unit
    ) {
        val states = CounterReviewState.values()
        val items = states.map(CounterReviewState::selectionTitle)
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Контрракурс: ${factor.title}")
            .setSingleChoiceItems(
                items,
                selected.ordinal
            ) { dialog, which ->
                onSelected(states[which])
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSourceAuditDialog(
        factor: SignalFactor,
        selected: SourceAuditState,
        onSelected: (SourceAuditState) -> Unit
    ) {
        val states = SourceAuditState.values()
        val items = states.map { state ->
            "${state.title}\n${state.explanation}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Антиэхо: ${factor.title}")
            .setSingleChoiceItems(
                items,
                selected.ordinal
            ) { dialog, which ->
                onSelected(states[which])
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEvidenceDialog(
        factor: SignalFactor,
        selected: EvidenceLevel,
        onSelected: (EvidenceLevel) -> Unit
    ) {
        val levels = EvidenceLevel.values()
        val items = levels.map { level ->
            if (level == EvidenceLevel.UNCONFIRMED) {
                "${level.title} • предел ${level.scoreCap}"
            } else {
                "${level.title} • предел ${level.scoreCap} • ${FreshnessFormatter.duration(FreshnessPolicy.validForMillis(factor))}"
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Проверка сейчас: ${factor.title}")
            .setSingleChoiceItems(items, selected.ordinal) { dialog, which ->
                onSelected(levels[which])
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showFactReceiptDialog(
        event: SportEvent,
        factor: SignalFactor,
        returnToRegister: Boolean = false
    ) {
        lateinit var dialog: AlertDialog
        val read = state.factReceipt(event.id, factor)
        val existing = read.receipt
        val statementField = factReceiptEditText(
            value = existing?.statement.orEmpty(),
            hint = "Например: в последних трёх матчах использовалась одна схема",
            maxLength = FactReceiptPolicy.MAX_STATEMENT_LENGTH,
            multiline = true
        )
        val primarySourceField = factReceiptEditText(
            value = existing?.primarySource.orEmpty(),
            hint = "Название или ссылка на первичную публикацию",
            maxLength = FactReceiptPolicy.MAX_SOURCE_LENGTH,
            multiline = false
        )
        val secondarySourceField = factReceiptEditText(
            value = existing?.secondarySource.orEmpty(),
            hint = "Название или ссылка; можно оставить пустым",
            maxLength = FactReceiptPolicy.MAX_SOURCE_LENGTH,
            multiline = false
        )
        val selectedAudit = existing?.sourceAuditState
            ?: SourceAuditState.UNAUDITED
        val auditButtons = SourceAuditState.values().associateWith { audit ->
            factReceiptRadioButton(
                title = "${audit.title} • ${audit.explanation}",
                checked = audit == selectedAudit
            )
        }
        val auditGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            auditButtons.values.forEach(::addView)
        }
        val selectedCoverage = existing?.coverage
            ?: FactReceiptCoverage.CORE
        val coverageButtons = FactReceiptCoverage.values().associateWith {
            coverage ->
            factReceiptRadioButton(
                title = "${coverage.title} • ${coverage.score} из 100\n${coverage.explanation}",
                checked = coverage == selectedCoverage
            )
        }
        val coverageGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            coverageButtons.values.forEach(::addView)
        }

        val composerBadge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        ).apply {
            textSize = fixedControlTextSize(11f)
        }
        val composerHeadline = text(
            "",
            17f,
            AppColors.ink,
            Typeface.BOLD
        ).apply {
            textSize = fixedControlTextSize(17f)
        }
        val composerBody = text("", 12.5f, AppColors.ink).apply {
            textSize = fixedControlTextSize(12.5f)
        }
        val composerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            addView(composerBadge)
            addView(composerHeadline, matchWrap(top = 7))
            addView(composerBody, matchWrap(top = 4))
        }

        val includeSecondSource = CheckBox(this).apply {
            text = "Добавить второй источник"
            textSize = fixedControlTextSize(14f)
            setTextColor(AppColors.ink)
            typeface = AppTypography.body(this@MainActivity)
            buttonTintList = checkBoxColors()
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            isChecked = existing?.secondarySource != null
            contentDescription =
                "Добавить второй источник для независимой сверки"
        }
        val secondSourceDisclosure = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                AppColors.signalSoft,
                8,
                AppColors.signal,
                1
            )
            setPadding(dp(12), dp(5), dp(12), dp(10))
            addView(includeSecondSource, matchWrap())
            addView(
                text(
                    "Необязательно. Кворум появится только после проверки связи двух первичных цепочек.",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 1)
            )
        }
        val secondarySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                factReceiptField(
                    title = "Второй источник",
                    field = secondarySourceField
                )
            )
            addView(
                text(
                    "Как связаны источники?",
                    14f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 14)
            )
            addView(
                text(
                    "Разные ссылки могут вести к одной публикации. Выберите фактическое происхождение.",
                    12f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
            addView(auditGroup, matchWrap(top = 5))
            visibility = if (includeSecondSource.isChecked) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        val refineCoverage = CheckBox(this).apply {
            text = "Уточнить полноту проверки"
            textSize = fixedControlTextSize(14f)
            setTextColor(AppColors.ink)
            typeface = AppTypography.body(this@MainActivity)
            buttonTintList = checkBoxColors()
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            isChecked = existing?.coverage != null &&
                existing.coverage != FactReceiptCoverage.CORE
            contentDescription =
                "Показать варианты полноты проверки"
        }
        val coverageDisclosure = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(AppColors.background, 8)
            setPadding(dp(12), dp(5), dp(12), dp(10))
            addView(refineCoverage, matchWrap())
            addView(
                text(
                    "По умолчанию сохраняется базовый факт. Откройте только если проверены детали или контраргумент.",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 1)
            )
        }
        val coverageSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(
                    "Полнота проверки",
                    14f,
                    AppColors.ink,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    "Это объём собранных данных, а не сила команды и не вероятность исхода.",
                    12f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
            addView(coverageGroup, matchWrap(top = 5))
            visibility = if (refineCoverage.isChecked) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(20))
            addView(
                label(
                    if (returnToRegister) {
                        "ФАКТ-МАРШРУТ • ФАКТОР ${factor.ordinal + 1} ИЗ 5"
                    } else {
                        "ЛОКАЛЬНО • БЕЗ СЕТИ"
                    },
                    if (returnToRegister) {
                        AppColors.signalSoft
                    } else {
                        AppColors.accentSoft
                    },
                    if (returnToRegister) {
                        AppColors.signal
                    } else {
                        AppColors.accentDark
                    }
                )
            )
            addView(
                text(
                    "Факт-квитанция: ${factor.title}",
                    21f,
                    AppColors.ink,
                    Typeface.BOLD
                ).apply {
                    textSize = fixedControlTextSize(21f)
                },
                matchWrap(top = 11)
            )
            addView(
                composerPanel,
                matchWrap(top = 10)
            )
            addView(
                text(
                    if (returnToRegister) {
                        "Один тезис и его происхождение. После сохранения вернёмся в обновлённый реестр."
                    } else {
                        "Один тезис и его происхождение. Сохранение обновит только этот фактор."
                    },
                    13f,
                    AppColors.muted
                ).apply {
                    textSize = fixedControlTextSize(13f)
                },
                matchWrap(top = 5)
            )
            addView(
                factReceiptField(
                    title = "Что удалось подтвердить",
                    field = statementField
                ),
                matchWrap(top = 14)
            )
            addView(
                factReceiptField(
                    title = "Первичный источник",
                    field = primarySourceField
                ),
                matchWrap(top = 12)
            )
            addView(
                secondSourceDisclosure,
                matchWrap(top = 14)
            )
            addView(
                secondarySection,
                matchWrap(top = 12)
            )
            addView(
                coverageDisclosure,
                matchWrap(top = 14)
            )
            addView(
                coverageSection,
                matchWrap(top = 12)
            )
            addView(
                text(
                    "Ссылки не открываются автоматически. Приложение проверяет структуру записи, а не истинность публикации.",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 14)
            )
            if (read.integrity != FactReceiptIntegrity.EMPTY) {
                addView(
                    outlineButton("Удалить", AppColors.danger) {
                        confirmFactReceiptDelete(
                            event = event,
                            factor = factor,
                            editorDialog = dialog,
                            returnToRegister = returnToRegister
                        )
                    }.apply {
                        textSize = fixedControlTextSize(13f)
                        contentDescription =
                            "Удалить факт-квитанцию: ${factor.title}"
                    },
                    matchWrap(top = 14)
                )
            }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.fact_receipt_v360)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Первичный источник и независимая сверка собираются в запечатанную факт-квитанцию"
                },
                matchFixed(
                    if (effectiveFontScale() >= 1.8f) 76 else 112
                )
            )
            addView(form)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(
                panel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val saveButton = commandButton(
            "Сохранить",
            AppColors.accent
        ) {}.apply {
            textSize = fixedControlTextSize(14f)
            contentDescription = "Сохранить факт-квитанцию"
        }
        val cancelButton = outlineButton(
            if (returnToRegister) "К реестру" else "Отмена",
            AppColors.muted
        ) {}.apply {
            textSize = fixedControlTextSize(13f)
            contentDescription = if (returnToRegister) {
                "Вернуться в реестр фактов без сохранения"
            } else {
                "Закрыть факт-квитанцию без сохранения"
            }
        }
        val actions = LinearLayout(this).apply {
            val stackActions =
                effectiveFontScale() >= 1.3f ||
                    resources.configuration.screenWidthDp < 380
            orientation = if (stackActions) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            setPadding(dp(16), dp(10), dp(16), dp(14))
            if (stackActions) {
                addView(saveButton, matchWrap())
                addView(cancelButton, matchWrap(top = 8))
            } else {
                addView(
                    cancelButton,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        rightMargin = dp(8)
                    }
                )
                addView(
                    saveButton,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.surface)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(factReceiptDialogHeightDp())
            )
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(divider(), matchFixed(1))
            addView(actions, matchWrap())
        }
        dialog = AlertDialog.Builder(this)
            .setView(root)
            .create()
        lateinit var refreshComposer: () -> Unit
        fun selectedAudit(): SourceAuditState {
            return auditButtons.entries.firstOrNull {
                it.value.isChecked
            }?.key ?: SourceAuditState.UNAUDITED
        }
        refreshComposer = {
            val result = FactReceiptComposer.evaluate(
                statement = statementField.text.toString(),
                primarySource = primarySourceField.text.toString(),
                includeSecondSource = includeSecondSource.isChecked,
                secondarySource = secondarySourceField.text.toString(),
                selectedAudit = selectedAudit()
            )
            val tone = factReceiptComposerTone(result.status)
            composerPanel.background = rounded(
                tone.background,
                8,
                tone.foreground,
                1
            )
            composerBadge.text = result.status.badge
            composerBadge.setTextColor(tone.foreground)
            composerBadge.background = rounded(
                tone.background,
                14,
                tone.foreground,
                1
            )
            composerHeadline.text = result.status.headline
            composerHeadline.setTextColor(tone.foreground)
            composerBody.text = result.status.body
            saveButton.isEnabled = result.canSave
            saveButton.alpha = if (result.canSave) 1f else 0.48f
        }
        val composerWatcher = object : TextWatcher {
            override fun beforeTextChanged(
                value: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                value: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(value: Editable?) {
                refreshComposer()
            }
        }
        statementField.addTextChangedListener(composerWatcher)
        primarySourceField.addTextChangedListener(composerWatcher)
        secondarySourceField.addTextChangedListener(composerWatcher)
        includeSecondSource.setOnCheckedChangeListener { _, checked ->
            secondarySection.visibility = if (checked) {
                View.VISIBLE
            } else {
                View.GONE
            }
            refreshComposer()
        }
        refineCoverage.setOnCheckedChangeListener { _, checked ->
            coverageSection.visibility = if (checked) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        auditGroup.setOnCheckedChangeListener { _, _ ->
            refreshComposer()
        }
        saveButton.setOnClickListener {
            val statement = statementField.text.toString().trim()
            val primarySource = primarySourceField.text.toString().trim()
            val secondarySource = if (includeSecondSource.isChecked) {
                secondarySourceField.text.toString().trim()
            } else {
                ""
            }
            val composer = FactReceiptComposer.evaluate(
                statement = statement,
                primarySource = primarySource,
                includeSecondSource = includeSecondSource.isChecked,
                secondarySource = secondarySource,
                selectedAudit = selectedAudit()
            )
            var valid = true
            if (
                statement.length < FactReceiptPolicy.MIN_STATEMENT_LENGTH
            ) {
                statementField.error =
                    "Опишите тезис минимум в ${FactReceiptPolicy.MIN_STATEMENT_LENGTH} символах"
                valid = false
            }
            if (
                primarySource.length < FactReceiptPolicy.MIN_SOURCE_LENGTH
            ) {
                primarySourceField.error =
                    "Укажите название или ссылку на источник"
                valid = false
            }
            if (
                includeSecondSource.isChecked &&
                secondarySource.length < FactReceiptPolicy.MIN_SOURCE_LENGTH
            ) {
                secondarySourceField.error =
                    "Укажите источник полностью или отключите второй источник"
                valid = false
            }
            if (!valid) return@setOnClickListener

            val coverage = if (refineCoverage.isChecked) {
                coverageButtons.entries.first { it.value.isChecked }.key
            } else {
                FactReceiptCoverage.CORE
            }
            val receipt = FactReceiptFactory.create(
                eventId = event.id,
                factor = factor,
                statement = statement,
                primarySource = primarySource,
                secondarySource = secondarySource,
                sourceAuditState = composer.effectiveAudit,
                coverage = coverage,
                checkedAt = System.currentTimeMillis()
            )
            state.saveFactReceipt(receipt)
            dialog.dismiss()
            Toast.makeText(
                this,
                "Квитанция сохранена • ${factReceiptSourceSummary(receipt)}",
                Toast.LENGTH_SHORT
            ).show()
            rerenderContentPreservingScroll()
            if (returnToRegister) {
                showFactRegisterDialog(event)
            }
        }
        cancelButton.setOnClickListener {
            dialog.dismiss()
            if (returnToRegister) {
                showFactRegisterDialog(event)
            }
        }
        refreshComposer()
        if (returnToRegister) {
            dialog.setOnCancelListener {
                showFactRegisterDialog(event)
            }
        }
        dialog.show()
        dialog.window?.setLayout(
            dp(factReceiptDialogWidthDp()),
            dp(factReceiptDialogHeightDp())
        )
    }

    private fun factReceiptDialogWidthDp(): Int {
        return min(
            560,
            resources.configuration.screenWidthDp - 24
        ).coerceAtLeast(280)
    }

    private fun factReceiptDialogHeightDp(): Int {
        val preferred = if (effectiveFontScale() >= 1.8f) 720 else 760
        return min(
            preferred,
            resources.configuration.screenHeightDp - 104
        ).coerceAtLeast(420)
    }

    private fun showFactRegisterDialog(event: SportEvent) {
        val register = FactRegisterEngine.create(
            eventId = event.id,
            reads = SignalFactor.values().associateWith { factor ->
                state.factReceipt(event.id, factor)
            },
            now = System.currentTimeMillis()
        )
        val sourceMap = CrossSourceMapEngine.create(register)
        val timeSlice = FactTimeSliceEngine.create(register)
        val tone = factRegisterTone(register.status)
        lateinit var dialog: AlertDialog
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(20))
            addView(
                label(
                    factRegisterStatusTitle(register.status),
                    tone.background,
                    tone.foreground
                )
            )
            addView(
                text(
                    "Реестр фактов",
                    22f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 11)
            )
            addView(
                text(
                    event.match,
                    14f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 4)
            )
            addView(
                text(
                    "${register.validCount} из 5 квитанций • " +
                        "независимо сверено: ${register.quorumCount}",
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 12)
            )
            addView(
                horizontalProgress().apply {
                    progress = register.validCount * 20
                    progressTintList =
                        ColorStateList.valueOf(tone.foreground)
                },
                matchFixed(7, top = 7)
            )
            addView(
                text(
                    factRegisterExplanation(register),
                    12.5f,
                    AppColors.muted
                ),
                matchWrap(top = 8)
            )
            if (register.validCount > 0) {
                addView(
                    factRegisterFreshnessPanel(register),
                    matchWrap(top = 10)
                )
            }
            register.nextFactor?.let { factor ->
                addView(
                    text(
                        "ФАКТ-МАРШРУТ • СЛЕДУЮЩИЙ ШАГ\n${factRegisterNextAction(register)}",
                        12.5f,
                        tone.foreground,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(tone.background, 8)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        contentDescription =
                            "Следующий фактор реестра: ${factor.title}"
                    },
                    matchWrap(top = 11)
                )
                addView(
                    commandButton(
                        "Продолжить маршрут: ${factor.title}",
                        tone.foreground
                    ) {
                        dialog.dismiss()
                        showFactReceiptDialog(
                            event = event,
                            factor = factor,
                            returnToRegister = true
                        )
                    }.apply {
                        contentDescription =
                            "Продолжить факт-маршрут: ${factor.title}"
                    },
                    matchWrap(top = 8)
                )
            }
            addView(
                text(
                    "РЕЕСТР ${register.shortFingerprint} • SHA-256",
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 9)
            )
            addView(
                factTimeSlicePanel(
                    value = timeSlice,
                    allowAction = register.status ==
                        FactRegisterStatus.READY
                ) { factor ->
                    dialog.dismiss()
                    showFactReceiptDialog(
                        event = event,
                        factor = factor,
                        returnToRegister = true
                    )
                },
                matchWrap(top = 11)
            )
            addView(
                crossSourceMapPanel(sourceMap) { factor ->
                    dialog.dismiss()
                    showFactReceiptDialog(
                        event = event,
                        factor = factor,
                        returnToRegister = true
                    )
                },
                matchWrap(top = 11)
            )
            register.entries.forEach { entry ->
                addView(
                    factRegisterEntryPanel(entry) {
                        dialog.dismiss()
                        showFactReceiptDialog(
                            event = event,
                            factor = entry.factor,
                            returnToRegister = true
                        )
                    },
                    matchWrap(top = 11)
                )
            }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.cross_source_map)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Пять доказательных каналов и общая цепочка происхождения"
                },
                matchFixed(132)
            )
            addView(content)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                panel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("Закрыть", null)
            .create()
        dialog.show()
    }

    private fun factRegisterFreshnessPanel(
        register: FactRegister
    ): LinearLayout {
        val unconfirmedCount = register.entries.count {
            it.freshness?.status == FreshnessStatus.UNCONFIRMED
        }
        val tone = when {
            register.expiredCount > 0 || unconfirmedCount > 0 ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            register.degradedCount > 0 || register.expiringCount > 0 ->
                Tone(AppColors.warning, AppColors.warningSoft)
            else -> Tone(AppColors.accentDark, AppColors.accentSoft)
        }
        val nearest = register.entries
            .mapNotNull { it.freshness?.remainingMillis }
            .minOrNull()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tone.background, 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                text(
                    "СРОК ФАКТА",
                    10.5f,
                    tone.foreground,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    when {
                        register.expiredCount > 0 ->
                            "Истёк срок: ${register.expiredCount} • нужна новая проверка"
                        unconfirmedCount > 0 ->
                            "Без активного срока: $unconfirmedCount • нет подтверждения"
                        register.degradedCount > 0 ->
                            "Кворум ослаб: ${register.degradedCount} • текущий уровень уже ниже"
                        register.expiringCount > 0 ->
                            "Обновить скоро: ${register.expiringCount} • ближайший переход через ${FreshnessFormatter.duration(nearest ?: 0L)}"
                        nearest != null ->
                            "Все сохранённые уровни действуют • ближайший переход через ${FreshnessFormatter.duration(nearest)}"
                        else ->
                            "Активных временных переходов нет"
                    },
                    12.5f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 4)
            )
            addView(
                text(
                    "Форма 72 ч • состав 6 ч • нагрузка 24 ч • контекст 48 ч • источники 12 ч",
                    11f,
                    AppColors.muted
                ),
                matchWrap(top = 4)
            )
        }
    }

    private fun factTimeSlicePanel(
        value: FactTimeSlice,
        allowAction: Boolean,
        onRefreshOldest: (SignalFactor) -> Unit
    ): LinearLayout {
        val tone = factTimeSliceTone(value.status)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tone.background, 8)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            addView(
                text(
                    "ЕДИНЫЙ СРЕЗ • ${factTimeSliceStatusTitle(value.status)}",
                    10.5f,
                    tone.foreground,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    when (value.activeCount) {
                        0 -> "0 активных фактов • нужно минимум 2"
                        1 -> "1 активный факт • нужно минимум 2"
                        else ->
                            "${value.activeCount} активных • разброс ${FreshnessFormatter.duration(requireNotNull(value.spreadMillis))}"
                    },
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    factTimeSliceExplanation(value),
                    12.5f,
                    AppColors.ink
                ),
                matchWrap(top = 4)
            )
            addView(
                FactTimeSliceView(this@MainActivity).apply {
                    setResult(value)
                },
                matchFixed(132, top = 7)
            )
            addView(
                text(
                    "1 Форма • 2 Состав • 3 Нагрузка\n4 Контекст • 5 Источники",
                    10.5f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
            if (value.referenceFactor != null) {
                addView(
                    text(
                        "Правило: единое окно = 25% срока самого быстрого активного факта «${value.referenceFactor.title}».",
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 6)
                )
            }
            if (allowAction) {
                value.suggestedFactor?.let { factor ->
                    addView(
                        outlineButton(
                            "Обновить старейший факт: ${factor.title}",
                            tone.foreground
                        ) {
                            onRefreshOldest(factor)
                        }.apply {
                            contentDescription =
                                "Синхронизировать единый срез: ${factor.title}"
                        },
                        matchWrap(top = 10)
                    )
                }
            }
            addView(
                text(
                    "СРЕЗ ${value.shortFingerprint} • READ-ONLY",
                    10f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 8)
            )
        }
    }

    private fun factTimeSliceStatusTitle(
        status: FactTimeSliceStatus
    ): String {
        return when (status) {
            FactTimeSliceStatus.INSUFFICIENT -> "НУЖЕН 2-Й ФАКТ"
            FactTimeSliceStatus.ALIGNED -> "ОДИН МОМЕНТ"
            FactTimeSliceStatus.DRIFTING -> "СДВИГ ВРЕМЕНИ"
            FactTimeSliceStatus.SPLIT -> "РАЗНЫЕ МОМЕНТЫ"
        }
    }

    private fun factTimeSliceExplanation(
        value: FactTimeSlice
    ): String {
        return when (value.status) {
            FactTimeSliceStatus.INSUFFICIENT ->
                "После второй действующей квитанции шкала покажет, относятся ли факты примерно к одному моменту."
            FactTimeSliceStatus.ALIGNED ->
                "Проверки укладываются в единое окно ${FreshnessFormatter.duration(requireNotNull(value.syncWindowMillis))}. Это согласованность времени, а не прогноз исхода."
            FactTimeSliceStatus.DRIFTING ->
                "Разброс вышел за единое окно ${FreshnessFormatter.duration(requireNotNull(value.syncWindowMillis))}. Старейший фактор — «${requireNotNull(value.oldestFactor).title}»."
            FactTimeSliceStatus.SPLIT ->
                "Активные тезисы проверены в заметно разные моменты. Для цельного среза сначала обновите «${requireNotNull(value.oldestFactor).title}»."
        }
    }

    private fun factTimeSliceTone(
        status: FactTimeSliceStatus
    ): Tone {
        return when (status) {
            FactTimeSliceStatus.INSUFFICIENT ->
                Tone(AppColors.signal, AppColors.signalSoft)
            FactTimeSliceStatus.ALIGNED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            FactTimeSliceStatus.DRIFTING ->
                Tone(AppColors.warning, AppColors.warningSoft)
            FactTimeSliceStatus.SPLIT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun crossSourceMapPanel(
        sourceMap: CrossSourceMap,
        onDiversify: (SignalFactor) -> Unit
    ): LinearLayout {
        val tone = crossSourceTone(sourceMap.status)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(tone.background, 8)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            addView(
                text(
                    "КРОСС-ЭХО • ${crossSourceStatusTitle(sourceMap.status)}",
                    10.5f,
                    tone.foreground,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    if (sourceMap.status == CrossSourceStatus.EMPTY) {
                        "Карта происхождений ещё пуста"
                    } else {
                        "${sourceMap.sourceMentionCount} обозначений • " +
                            "${sourceMap.uniqueOriginCount} происхождений"
                    },
                    15f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    crossSourceExplanation(sourceMap),
                    12.5f,
                    AppColors.ink
                ),
                matchWrap(top = 4)
            )
            sourceMap.reusedOrigins.take(3).forEach { origin ->
                addView(
                    crossSourceOriginRow(origin, tone.foreground),
                    matchWrap(top = 8)
                )
            }
            if (sourceMap.reusedOriginCount > 3) {
                addView(
                    text(
                        "Ещё общих происхождений: ${sourceMap.reusedOriginCount - 3}",
                        11.5f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 6)
                )
            }
            sourceMap.diversificationFactor?.let { factor ->
                addView(
                    outlineButton(
                        "Добавить другое происхождение: ${factor.title}",
                        tone.foreground
                    ) {
                        onDiversify(factor)
                    }.apply {
                        contentDescription =
                            "Развести перекрёстное эхо фактора ${factor.title}"
                    },
                    matchWrap(top = 10)
                )
            }
            addView(
                text(
                    "КАРТА ${sourceMap.shortFingerprint} • READ-ONLY",
                    10f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 8)
            )
        }
    }

    private fun crossSourceOriginRow(
        origin: CrossSourceOrigin,
        color: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(AppColors.surface, 8, color, 1)
            addView(
                text(
                    origin.label,
                    12.5f,
                    AppColors.ink,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    origin.factors.joinToString(" • ") {
                        it.title
                    },
                    11.5f,
                    color,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
        }
    }

    private fun crossSourceStatusTitle(
        status: CrossSourceStatus
    ): String {
        return when (status) {
            CrossSourceStatus.EMPTY -> "НЕТ ДАННЫХ"
            CrossSourceStatus.BUILDING -> "НУЖЕН 2-Й ФАКТОР"
            CrossSourceStatus.DISTRIBUTED -> "ПОВТОРОВ НЕТ"
            CrossSourceStatus.REUSED -> "ОБЩАЯ ЗАВИСИМОСТЬ"
        }
    }

    private fun crossSourceExplanation(
        sourceMap: CrossSourceMap
    ): String {
        return when (sourceMap.status) {
            CrossSourceStatus.EMPTY ->
                "После первой квитанции здесь появятся домены и названия источников из всех пяти факторов."
            CrossSourceStatus.BUILDING ->
                "Происхождения записаны только для одного фактора. Межфакторное сравнение начнётся после второй квитанции."
            CrossSourceStatus.DISTRIBUTED ->
                "Одинаковые домены или нормализованные названия в разных факторах не найдены."
            CrossSourceStatus.REUSED -> {
                val dominant = checkNotNull(sourceMap.dominantOrigin)
                "«${dominant.label}» участвует в ${dominant.factorCount} факторах. " +
                    "Это общая зависимость, а не автоматическое опровержение фактов."
            }
        }
    }

    private fun crossSourceTone(status: CrossSourceStatus): Tone {
        return when (status) {
            CrossSourceStatus.EMPTY,
            CrossSourceStatus.BUILDING ->
                Tone(AppColors.signal, AppColors.signalSoft)
            CrossSourceStatus.DISTRIBUTED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CrossSourceStatus.REUSED ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
    }

    private fun factRegisterEntryPanel(
        entry: FactRegisterEntry,
        onAction: () -> Unit
    ): LinearLayout {
        val tone = factRegisterEntryTone(entry)
        val receipt = entry.receipt
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(AppColors.surface, 8, AppColors.line, 1)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            addView(
                label(
                    entry.state.title.uppercase(Locale.getDefault()),
                    tone.background,
                    tone.foreground
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            )
            entry.freshness?.let { freshness ->
                val freshnessTone = factFreshnessTone(freshness.status)
                addView(
                    label(
                        factFreshnessStatusTitle(freshness.status),
                        freshnessTone.background,
                        freshnessTone.foreground
                    ).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    },
                    matchWrap(top = 6)
                )
            }
            addView(
                text(
                    entry.factor.title,
                    17f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 8)
            )
            addView(
                text(
                    when {
                        receipt != null -> receipt.statement
                        entry.state == FactRegisterEntryState.TAMPERED ->
                            "Пломба не совпала. Запись не участвует в доказательствах."
                        else ->
                            "Проверяемый тезис и его источник ещё не записаны."
                    },
                    13f,
                    AppColors.ink,
                    if (receipt == null) Typeface.NORMAL else Typeface.BOLD
                ),
                matchWrap(top = 4)
            )
            if (receipt != null) {
                addView(
                    text(
                        "${receipt.coverage.title} • ${receipt.coverage.score} из 100 • " +
                            factReceiptSourceSummary(receipt),
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 5)
                )
                entry.freshness?.let { freshness ->
                    addView(
                        text(
                            factFreshnessExplanation(freshness),
                            11.5f,
                            factFreshnessTone(freshness.status).foreground,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 5)
                    )
                }
                addView(
                    text(
                        buildString {
                            append("Источник 1: ")
                            append(receipt.primarySource)
                            receipt.secondarySource?.let { source ->
                                append("\nИсточник 2: ")
                                append(source)
                            }
                        },
                        12f,
                        AppColors.muted
                    ),
                    matchWrap(top = 6)
                )
                addView(
                    text(
                        "КВИТАНЦИЯ ${receipt.shortFingerprint} • СЕЙЧАС ${(entry.freshness?.effectiveLevel ?: receipt.effectiveEvidence).title.uppercase(Locale.getDefault())}",
                        10.5f,
                        tone.foreground,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 6)
                )
            }
            addView(
                outlineButton(
                    when (entry.integrity) {
                        FactReceiptIntegrity.EMPTY -> "Записать"
                        FactReceiptIntegrity.VALID -> "Обновить"
                        FactReceiptIntegrity.TAMPERED -> "Пересобрать"
                    },
                    tone.foreground,
                    onAction
                ).apply {
                    contentDescription =
                        "${text} факт-квитанцию: ${entry.factor.title}"
                },
                matchWrap(top = 10)
            )
        }
    }

    private fun factRegisterStatusTitle(
        status: FactRegisterStatus
    ): String {
        return when (status) {
            FactRegisterStatus.EMPTY -> "РЕЕСТР ПУСТ"
            FactRegisterStatus.PARTIAL -> "СБОР ФАКТОВ"
            FactRegisterStatus.EXPIRING -> "ОБНОВИТЬ СКОРО"
            FactRegisterStatus.READY -> "ПЯТЬ ФАКТОРОВ СВЕРЕНЫ"
            FactRegisterStatus.ATTENTION -> "НУЖНА ПЕРЕПРОВЕРКА"
        }
    }

    private fun factRegisterExplanation(
        register: FactRegister
    ): String {
        return when (register.status) {
            FactRegisterStatus.EMPTY ->
                "Начните с главного пробела ниже: один тезис, затем его происхождение."
            FactRegisterStatus.PARTIAL ->
                "Сохранённые тезисы собраны в одном месте. Кворум учитывается только пока два независимых источника остаются свежими."
            FactRegisterStatus.EXPIRING ->
                "Все пять кворумов действуют сейчас, но минимум один скоро ослабнет по времени."
            FactRegisterStatus.READY ->
                "У всех пяти факторов есть свежие независимые кворумы. Это полнота происхождения, а не прогноз исхода."
            FactRegisterStatus.ATTENTION ->
                "Есть истёкший факт, конфликт источников или повреждённая пломба. Такой фактор проверяется первым."
        }
    }

    private fun factRegisterNextAction(
        register: FactRegister
    ): String {
        val factor = checkNotNull(register.nextFactor)
        val entry = register.entries.first { it.factor == factor }
        return when {
            entry.state == FactRegisterEntryState.TAMPERED ->
                "Пересоберите квитанцию «${factor.title}»."
            entry.state == FactRegisterEntryState.CONFLICT ->
                "Разберите расхождение по фактору «${factor.title}»."
            entry.freshness?.status == FreshnessStatus.EXPIRED ->
                "Повторно проверьте истёкший факт «${factor.title}»."
            entry.state == FactRegisterEntryState.EMPTY ->
                "Запишите первый тезис по фактору «${factor.title}»."
            entry.freshness?.status == FreshnessStatus.DEGRADED ->
                "Обновите фактор «${factor.title}»: кворум ослаб по времени."
            entry.state == FactRegisterEntryState.SHARED_LINEAGE ->
                "Найдите независимую цепочку для фактора «${factor.title}»."
            entry.state == FactRegisterEntryState.UNAUDITED ->
                "Проверьте связь источников фактора «${factor.title}»."
            entry.state == FactRegisterEntryState.SINGLE_SOURCE ->
                "Сверьте фактор «${factor.title}» со вторым источником."
            entry.freshness?.status == FreshnessStatus.EXPIRING ->
                "Обновите фактор «${factor.title}» до снижения уровня."
            else ->
                "Обновите квитанцию фактора «${factor.title}»."
        }
    }

    private fun factRegisterTone(status: FactRegisterStatus): Tone {
        return when (status) {
            FactRegisterStatus.EMPTY ->
                Tone(AppColors.signal, AppColors.signalSoft)
            FactRegisterStatus.PARTIAL ->
                Tone(AppColors.warning, AppColors.warningSoft)
            FactRegisterStatus.EXPIRING ->
                Tone(AppColors.warning, AppColors.warningSoft)
            FactRegisterStatus.READY ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            FactRegisterStatus.ATTENTION ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun factRegisterEntryTone(
        entry: FactRegisterEntry
    ): Tone {
        return when {
            entry.state == FactRegisterEntryState.TAMPERED ||
                entry.state == FactRegisterEntryState.CONFLICT ||
                entry.freshness?.status == FreshnessStatus.EXPIRED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            entry.freshness?.status == FreshnessStatus.DEGRADED ||
                entry.freshness?.status == FreshnessStatus.EXPIRING ->
                Tone(AppColors.warning, AppColors.warningSoft)
            else -> factRegisterSourceTone(entry.state)
        }
    }

    private fun factRegisterSourceTone(
        state: FactRegisterEntryState
    ): Tone {
        return when (state) {
            FactRegisterEntryState.TAMPERED,
            FactRegisterEntryState.CONFLICT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            FactRegisterEntryState.EMPTY ->
                Tone(AppColors.signal, AppColors.signalSoft)
            FactRegisterEntryState.INDEPENDENT ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            FactRegisterEntryState.SHARED_LINEAGE,
            FactRegisterEntryState.UNAUDITED,
            FactRegisterEntryState.SINGLE_SOURCE ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
    }

    private fun factFreshnessStatusTitle(
        status: FreshnessStatus
    ): String {
        return when (status) {
            FreshnessStatus.FRESH -> "СРОК ДЕЙСТВУЕТ"
            FreshnessStatus.EXPIRING -> "ОБНОВИТЬ СКОРО"
            FreshnessStatus.DEGRADED -> "КВОРУМ ОСЛАБ"
            FreshnessStatus.EXPIRED -> "СРОК ИСТЁК"
            FreshnessStatus.UNCONFIRMED -> "СРОК НЕ ЗАПУЩЕН"
        }
    }

    private fun factFreshnessExplanation(
        freshness: FactorFreshness
    ): String {
        return when (freshness.status) {
            FreshnessStatus.FRESH ->
                "Срок факта: ещё ${FreshnessFormatter.duration(freshness.remainingMillis ?: 0L)}"
            FreshnessStatus.EXPIRING ->
                "Обновить в течение ${FreshnessFormatter.duration(freshness.remainingMillis ?: 0L)}"
            FreshnessStatus.DEGRADED ->
                "По времени сейчас: ${freshness.effectiveLevel.title.lowercase(Locale.getDefault())} • следующий переход через ${FreshnessFormatter.duration(freshness.remainingMillis ?: 0L)}"
            FreshnessStatus.EXPIRED ->
                "Срок подтверждения истёк • нужна новая проверка"
            FreshnessStatus.UNCONFIRMED ->
                "Активный срок не запускается без подтверждения"
        }
    }

    private fun factFreshnessTone(
        status: FreshnessStatus
    ): Tone {
        return when (status) {
            FreshnessStatus.FRESH ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            FreshnessStatus.EXPIRING,
            FreshnessStatus.DEGRADED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            FreshnessStatus.EXPIRED,
            FreshnessStatus.UNCONFIRMED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun confirmFactReceiptDelete(
        event: SportEvent,
        factor: SignalFactor,
        editorDialog: AlertDialog,
        returnToRegister: Boolean
    ) {
        AlertDialog.Builder(this)
            .setTitle("Удалить факт-квитанцию?")
            .setMessage(
                "Фактор «${factor.title}» вернётся к исходной оценке и уровню подтверждения этого события."
            )
            .setPositiveButton("Удалить") { _, _ ->
                state.clearFactReceipt(event, factor)
                editorDialog.dismiss()
                Toast.makeText(
                    this,
                    "Факт-квитанция удалена",
                    Toast.LENGTH_SHORT
                ).show()
                rerenderContentPreservingScroll()
                if (returnToRegister) {
                    showFactRegisterDialog(event)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun factReceiptEditText(
        value: String,
        hint: String,
        maxLength: Int,
        multiline: Boolean
    ): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
            textSize = 13.5f
            setTextColor(AppColors.ink)
            setHintTextColor(AppColors.muted)
            background = rounded(
                AppColors.surface,
                8,
                AppColors.line,
                1
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = if (multiline) 2 else 1
            maxLines = Int.MAX_VALUE
            setHorizontallyScrolling(false)
        }
    }

    private fun factReceiptField(
        title: String,
        field: EditText
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(
                    title,
                    13.5f,
                    AppColors.ink,
                    Typeface.BOLD
                )
            )
            addView(field, matchWrap(top = 5))
        }
    }

    private fun factReceiptRadioButton(
        title: String,
        checked: Boolean
    ): RadioButton {
        return RadioButton(this).apply {
            id = View.generateViewId()
            text = title
            textSize = 13f
            setTextColor(AppColors.ink)
            buttonTintList = checkBoxColors()
            isChecked = checked
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun factReceiptComposerTone(
        status: FactReceiptComposerStatus
    ): Tone {
        return when (status) {
            FactReceiptComposerStatus.STATEMENT_REQUIRED,
            FactReceiptComposerStatus.PRIMARY_SOURCE_REQUIRED,
            FactReceiptComposerStatus.SECONDARY_SOURCE_REQUIRED ->
                Tone(AppColors.signal, AppColors.signalSoft)
            FactReceiptComposerStatus.SINGLE_SOURCE_READY,
            FactReceiptComposerStatus.SOURCE_RELATION_REQUIRED,
            FactReceiptComposerStatus.SHARED_LINEAGE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            FactReceiptComposerStatus.INDEPENDENT_QUORUM ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            FactReceiptComposerStatus.CONFLICT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun confidenceShadowPanel(
        onAction: () -> Unit
    ): ConfidenceShadowPanel {
        val badge = label(
            "",
            AppColors.accentSoft,
            AppColors.accentDark
        ).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val metric = text("", 23f, AppColors.ink, Typeface.BOLD)
        val body = text("", 14f, AppColors.ink)
        val action = outlineButton("", AppColors.warning, onAction).apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        val legend = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                confidenceShadowLegendItem(
                    title = "Пунктир: оценка",
                    color = AppColors.warning
                ),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            addView(
                confidenceShadowLegendItem(
                    title = "Контур: подтверждено",
                    color = AppColors.accent
                ),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
        val root = card().apply {
            addView(
                text("Тень уверенности", 20f, AppColors.ink, Typeface.BOLD)
            )
            addView(
                text(
                    "Два контура на карте выше отделяют оценку от доказанной части.",
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 4)
            )
            addView(legend, matchWrap(top = 10))
            addView(badge, matchWrap(top = 10))
            addView(metric, matchWrap(top = 11))
            addView(body, matchWrap(top = 5))
            addView(action, matchWrap(top = 12))
        }
        return ConfidenceShadowPanel(
            root = root,
            badge = badge,
            metric = metric,
            body = body,
            action = action
        )
    }

    private fun confidenceShadowLegendItem(
        title: String,
        color: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                View(this@MainActivity).apply {
                    background = rounded(color, 2)
                },
                LinearLayout.LayoutParams(dp(20), dp(4)).apply {
                    rightMargin = dp(7)
                }
            )
            addView(text(title, 11f, AppColors.muted, Typeface.BOLD))
        }
    }

    private fun renderConfidenceShadow(
        panel: ConfidenceShadowPanel,
        result: ConfidenceShadowResult,
        actionFactor: SignalFactor?
    ) {
        val tone = confidenceShadowTone(result.status)
        panel.badge.text = confidenceShadowBadge(result.status)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(tone.background, 14)
        panel.metric.text = confidenceShadowMetric(result)
        panel.metric.setTextColor(tone.foreground)
        panel.body.text = confidenceShadowExplanation(result)

        if (actionFactor == null) {
            panel.action.visibility = View.GONE
        } else {
            panel.action.visibility = View.VISIBLE
            panel.action.text = getString(
                R.string.confidence_shadow_action,
                actionFactor.title
            )
            panel.action.setTextColor(tone.foreground)
            panel.action.background = rippleRounded(
                AppColors.surface,
                8,
                tone.foreground,
                1
            )
            panel.action.contentDescription =
                "${panel.action.text}. Перейти к фактору."
        }
    }

    private fun confidenceShadowBadge(
        status: ConfidenceShadowStatus
    ): String {
        return when (status) {
            ConfidenceShadowStatus.CLEAR ->
                "ТЕНИ НЕТ"
            ConfidenceShadowStatus.CONTAINED ->
                "РАЗРЫВ ЕСТЬ • СТАТУС СОХРАНЕН"
            ConfidenceShadowStatus.VERDICT_SHIFT ->
                "ТЕНЬ МЕНЯЕТ СТАТУС"
        }
    }

    private fun confidenceShadowMetric(
        result: ConfidenceShadowResult
    ): String {
        val claimed = result.claimedSignal.readiness
        val supported = result.supportedSignal.readiness
        return if (result.status == ConfidenceShadowStatus.CLEAR) {
            "$supported/100 • контуры совпадают"
        } else if (result.readinessGap == 0) {
            "$claimed → $supported • Δ 0"
        } else {
            "$claimed → $supported • Δ -${result.readinessGap}"
        }
    }

    private fun confidenceShadowExplanation(
        result: ConfidenceShadowResult
    ): String {
        val critical = result.criticalFactor
            ?: return "Каждый балл карты укладывается в действующие пределы источников."
        val factor = critical.factor.title
        val claimedVerdict = verdictTitle(
            result.claimedSignal.verdict
        ).lowercase(Locale.getDefault())
        val supportedVerdict = verdictTitle(
            result.supportedSignal.verdict
        ).lowercase(Locale.getDefault())
        return when (result.status) {
            ConfidenceShadowStatus.CLEAR ->
                "Каждый балл карты укладывается в действующие пределы источников."
            ConfidenceShadowStatus.CONTAINED ->
                "Самая заметная тень: «$factor» ${critical.claimedScore} → ${critical.supportedScore}. Часть оценки сверх лимита источников не меняет статус «$supportedVerdict»."
            ConfidenceShadowStatus.VERDICT_SHIFT ->
                "Без неподтвержденной части статус меняется: «$claimedVerdict» → «$supportedVerdict». Главный разрыв: «$factor» ${critical.claimedScore} → ${critical.supportedScore}."
        }
    }

    private fun confidenceShadowTone(
        status: ConfidenceShadowStatus
    ): Tone {
        return when (status) {
            ConfidenceShadowStatus.CLEAR ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            ConfidenceShadowStatus.CONTAINED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            ConfidenceShadowStatus.VERDICT_SHIFT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun decisionCorridorPanel(
        onAction: () -> Unit
    ): DecisionCorridorPanel {
        val badge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        ).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val metric = text(
            "",
            22f,
            AppColors.ink,
            Typeface.BOLD
        )
        val chart = DecisionCorridorView(this)
        val body = text("", 14f, AppColors.ink)
        val rule = text(
            "Пробный сценарий не меняет и не сохраняет карту. Это граница данных, а не прогноз результата.",
            12f,
            AppColors.muted
        )
        val action = outlineButton("", AppColors.signal, onAction).apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        val root = card().apply {
            addView(
                text("Коридор решения", 20f, AppColors.ink, Typeface.BOLD)
            )
            addView(
                text(
                    "Минимальное изменение одного фактора до соседнего статуса.",
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 4)
            )
            addView(badge, matchWrap(top = 9))
            addView(metric, matchWrap(top = 11))
            addView(chart, matchFixed(148, top = 2))
            addView(body, matchWrap(top = 3))
            addView(rule, matchWrap(top = 8))
            addView(action, matchWrap(top = 12))
        }
        return DecisionCorridorPanel(
            root = root,
            badge = badge,
            metric = metric,
            chart = chart,
            body = body,
            rule = rule,
            action = action
        )
    }

    private fun renderDecisionCorridor(
        panel: DecisionCorridorPanel,
        corridor: DecisionCorridor,
        actionFactor: SignalFactor?
    ) {
        val tone = decisionCorridorTone(corridor)
        panel.badge.text = decisionCorridorBadge(corridor)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(tone.background, 14)
        panel.metric.text = decisionCorridorMetric(corridor)
        panel.metric.setTextColor(tone.foreground)
        panel.chart.setCorridor(corridor)
        panel.body.text = decisionCorridorExplanation(corridor)

        if (actionFactor == null) {
            panel.action.visibility = View.GONE
        } else {
            panel.action.visibility = View.VISIBLE
            panel.action.text = getString(
                R.string.confidence_shadow_action,
                actionFactor.title
            )
            panel.action.setTextColor(tone.foreground)
            panel.action.background = rippleRounded(
                AppColors.surface,
                8,
                tone.foreground,
                1
            )
            panel.action.contentDescription =
                "${panel.action.text}. Перейти к фактору."
        }
    }

    private fun decisionCorridorBadge(
        corridor: DecisionCorridor
    ): String {
        val boundaries = listOfNotNull(
            corridor.lowerBoundary,
            corridor.upperBoundary
        ).size
        return when (boundaries) {
            2 -> "ДВЕ ГРАНИЦЫ СТАТУСА"
            1 -> "БЛИЖАЙШАЯ ГРАНИЦА"
            else -> "ОДНОГО ФАКТА НЕДОСТАТОЧНО"
        }
    }

    private fun decisionCorridorMetric(
        corridor: DecisionCorridor
    ): String {
        val boundary = corridor.nearestBoundary
            ?: return "${corridor.baseline.effectiveSignal.readiness}/100 • граница вне одного фактора"
        val delta = boundary.claimedAfter - boundary.claimedBefore
        return "${boundary.factor.title}: ${boundary.claimedBefore} → ${boundary.claimedAfter} • Δ ${signedValue(delta)}"
    }

    private fun decisionCorridorExplanation(
        corridor: DecisionCorridor
    ): String {
        val parts = mutableListOf<String>()
        corridor.lowerTarget?.let { target ->
            val boundary = corridor.lowerBoundary
            if (boundary == null) {
                parts += "Вниз: один фактор не переводит карту в статус «${verdictTitle(target).lowercase(Locale.getDefault())}»."
            } else {
                parts += decisionBoundaryExplanation(
                    prefix = "Вниз",
                    boundary = boundary
                )
            }
        }
        corridor.upperTarget?.let { target ->
            val boundary = corridor.upperBoundary
            if (boundary == null) {
                parts += "Вверх: одного фактора недостаточно для статуса «${verdictTitle(target).lowercase(Locale.getDefault())}» в текущих пределах источников."
            } else {
                parts += decisionBoundaryExplanation(
                    prefix = "Вверх",
                    boundary = boundary
                )
            }
        }
        return parts.joinToString(" ").ifBlank {
            "Текущий статус не имеет соседней границы."
        }
    }

    private fun decisionBoundaryExplanation(
        prefix: String,
        boundary: DecisionBoundary
    ): String {
        val verdict = verdictTitle(
            boundary.result.effectiveSignal.verdict
        ).lowercase(Locale.getDefault())
        return "$prefix: «${boundary.factor.title}» ${boundary.claimedBefore} → ${boundary.claimedAfter} впервые меняет статус на «$verdict»."
    }

    private fun decisionCorridorTone(
        corridor: DecisionCorridor
    ): Tone {
        return when (corridor.nearestBoundary?.direction) {
            CorridorDirection.DOWN ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            CorridorDirection.UP ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            null ->
                Tone(AppColors.signal, AppColors.signalSoft)
        }
    }

    private fun signalStressPanel(
        onAction: () -> Unit
    ): SignalStressPanel {
        val badge = label(
            "",
            AppColors.accentSoft,
            AppColors.accentDark
        ).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val metric = text("", 23f, AppColors.ink, Typeface.BOLD)
        val timeline = SignalStressTimelineView(this)
        val body = text("", 14f, AppColors.ink)
        val deadline = text("", 13f, AppColors.signal, Typeface.BOLD)
        val action = outlineButton("", AppColors.accent, onAction).apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        val root = card().apply {
            addView(
                text("Стресс-тест сигнала", 20f, AppColors.ink, Typeface.BOLD)
            )
            addView(badge, matchWrap(top = 7))
            addView(metric, matchWrap(top = 11))
            addView(timeline, matchFixed(148, top = 3))
            addView(body, matchWrap(top = 4))
            addView(deadline, matchWrap(top = 9))
            addView(action, matchWrap(top = 12))
        }
        return SignalStressPanel(
            root = root,
            badge = badge,
            metric = metric,
            timeline = timeline,
            body = body,
            deadline = deadline,
            action = action
        )
    }

    private fun renderSignalStress(
        panel: SignalStressPanel,
        result: SignalStressResult,
        now: Long,
        actionFactor: SignalFactor?
    ) {
        val tone = signalStressTone(result.status)
        panel.badge.text = signalStressBadge(result.status)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(tone.background, 14)
        panel.metric.text = signalStressMetric(result)
        panel.metric.setTextColor(tone.foreground)
        panel.timeline.setResult(result, now)
        panel.body.text = signalStressExplanation(result)

        val deadlineTone = when {
            result.firstVerdictChange != null ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            result.status == SignalStressStatus.NO_BUFFER ->
                Tone(AppColors.warning, AppColors.warningSoft)
            else ->
                Tone(AppColors.signal, AppColors.signalSoft)
        }
        panel.deadline.text = signalStressDeadline(result, now)
        panel.deadline.setTextColor(deadlineTone.foreground)
        panel.deadline.background = rounded(deadlineTone.background, 8)
        panel.deadline.setPadding(dp(12), dp(10), dp(12), dp(10))

        if (actionFactor == null) {
            panel.action.visibility = View.GONE
        } else {
            panel.action.visibility = View.VISIBLE
            panel.action.text = signalStressAction(
                result,
                actionFactor
            )
            panel.action.setTextColor(tone.foreground)
            panel.action.background = rippleRounded(
                AppColors.surface,
                8,
                tone.foreground,
                1
            )
            panel.action.contentDescription =
                "${panel.action.text}. Перейти к фактору."
        }
    }

    private fun signalStressActionFactor(
        result: SignalStressResult
    ): SignalFactor {
        return when (result.status) {
            SignalStressStatus.FRAGILE ->
                result.criticalShock?.factor
            SignalStressStatus.ROBUST ->
                result.firstVerdictChange
                    ?.changedFactors
                    ?.firstOrNull()
                    ?: result.criticalShock?.factor
            SignalStressStatus.NO_BUFFER ->
                result.baselineResult.effectiveSignal.weakestFactor
        } ?: result.baselineResult.effectiveSignal.weakestFactor
    }

    private fun signalStressBadge(
        status: SignalStressStatus
    ): String {
        return when (status) {
            SignalStressStatus.ROBUST ->
                "ВЫДЕРЖИВАЕТ 1 СБОЙ"
            SignalStressStatus.FRAGILE ->
                "ХРУПКО • 1 СБОЙ МЕНЯЕТ СТАТУС"
            SignalStressStatus.NO_BUFFER ->
                "НЕТ ЗАПАСА ПОДТВЕРЖДЕНИЙ"
        }
    }

    private fun signalStressMetric(
        result: SignalStressResult
    ): String {
        val baseline = result.baselineResult.effectiveSignal.readiness
        val shock = result.criticalShock
            ?: return "$baseline • запас не сформирован"
        val stressed = shock.result.effectiveSignal.readiness
        val delta = if (shock.readinessDrop > 0) {
            "-${shock.readinessDrop}"
        } else {
            "0"
        }
        return "$baseline → $stressed • Δ $delta"
    }

    private fun signalStressExplanation(
        result: SignalStressResult
    ): String {
        val shock = result.criticalShock
            ?: return "Все факторы уже без действующего подтверждения. Снять еще один уровень невозможно."
        val factor = shock.factor.title
        val levelChange = "${shock.fromLevel.title} → ${shock.toLevel.title}"
        val currentVerdict = verdictTitle(
            result.baselineResult.effectiveSignal.verdict
        ).lowercase(Locale.getDefault())
        val stressedVerdict = verdictTitle(
            shock.result.effectiveSignal.verdict
        ).lowercase(Locale.getDefault())
        return if (shock.verdictChanged) {
            "«$factor»: $levelChange. Потеря одного уровня меняет статус «$currentVerdict» → «$stressedVerdict»."
        } else if (shock.readinessDrop == 0) {
            "Критический фактор «$factor»: $levelChange. Полнота и статус «$currentVerdict» не изменятся."
        } else {
            "Критический фактор «$factor»: $levelChange. Полнота снизится на ${shock.readinessDrop}, статус «$currentVerdict» сохранится."
        }
    }

    private fun signalStressDeadline(
        result: SignalStressResult,
        now: Long
    ): String {
        result.firstVerdictChange?.let { point ->
            val before = verdictTitle(
                result.baselineResult.effectiveSignal.verdict
            ).lowercase(Locale.getDefault())
            val after = verdictTitle(
                point.result.effectiveSignal.verdict
            ).lowercase(Locale.getDefault())
            val factors = point.changedFactors.joinToString(", ") {
                it.title
            }
            return "Без обновлений через ${FreshnessFormatter.duration(point.at - now)}: «$before» → «$after». Точка риска: $factors."
        }
        result.nextTransition?.let { point ->
            val factors = point.changedFactors.joinToString(", ") {
                it.title
            }
            return "Ближайшее устаревание через ${FreshnessFormatter.duration(point.at - now)}: $factors. Статус на горизонте не меняется."
        }
        return "Активных сроков нет. Время само по себе статус не улучшит."
    }

    private fun signalStressAction(
        result: SignalStressResult,
        factor: SignalFactor
    ): String {
        return when {
            result.status == SignalStressStatus.FRAGILE ->
                "Укрепить «${factor.title}»"
            result.status == SignalStressStatus.NO_BUFFER ->
                "Подтвердить «${factor.title}»"
            result.firstVerdictChange != null ->
                "Удержать статус: проверить «${factor.title}»"
            else ->
                "Укрепить «${factor.title}»"
        }
    }

    private fun signalStressTone(
        status: SignalStressStatus
    ): Tone {
        return when (status) {
            SignalStressStatus.ROBUST ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            SignalStressStatus.FRAGILE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            SignalStressStatus.NO_BUFFER ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
    }

    private fun decisionTracePanel(): DecisionTracePanel {
        val badge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        )
        val metric = text(
            "",
            22f,
            AppColors.ink,
            Typeface.BOLD
        )
        val status = text(
            "",
            13f,
            AppColors.signal,
            Typeface.BOLD
        )
        val summary = text("", 13f, AppColors.muted)
        val changes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val root = card().apply {
            visibility = View.GONE
            addView(
                text("След решения", 20f, AppColors.ink, Typeface.BOLD)
            )
            addView(badge, matchWrap(top = 7))
            addView(metric, matchWrap(top = 11))
            addView(status, matchWrap(top = 5))
            addView(summary, matchWrap(top = 8))
            addView(changes, matchWrap(top = 8))
        }
        return DecisionTracePanel(
            root = root,
            badge = badge,
            metric = metric,
            status = status,
            summary = summary,
            changes = changes
        )
    }

    private fun renderDecisionTrace(
        panel: DecisionTracePanel,
        trace: DecisionTraceResult
    ) {
        panel.root.visibility = View.VISIBLE
        panel.badge.text = getString(
            R.string.decision_trace_badge,
            trace.snapshot.shortFingerprint
        )
        panel.badge.contentDescription = getString(
            R.string.decision_trace_badge_description,
            trace.snapshot.shortFingerprint
        )

        val baseline = trace.baselineEvidenceResult.effectiveSignal
        val current = trace.currentEvidenceResult.effectiveSignal
        val delta = trace.readinessDelta
        val deltaColor = when {
            delta > 0 -> AppColors.accent
            delta < 0 -> AppColors.danger
            else -> AppColors.signal
        }
        panel.metric.text = getString(
            R.string.decision_trace_metric,
            baseline.readiness,
            current.readiness,
            signedValue(delta)
        )
        panel.metric.setTextColor(deltaColor)

        val beforeVerdict = verdictTitle(baseline.verdict)
            .lowercase(Locale.getDefault())
        val currentVerdict = verdictTitle(current.verdict)
            .lowercase(Locale.getDefault())
        panel.status.text = if (trace.verdictChanged) {
            "Статус: $beforeVerdict → $currentVerdict"
        } else {
            "Статус: $currentVerdict • исходный вывод сохранен"
        }
        panel.status.setTextColor(deltaColor)

        panel.summary.text = if (trace.changedFactors.isEmpty()) {
            "После фиксации карта не менялась. Снимок ${formatDateTime(trace.snapshot.savedAt)} остается исходной точкой."
        } else {
            "После ${formatDateTime(trace.snapshot.savedAt)} изменено факторов: ${trace.changedFactors.size}. Исходная карта не пересчитана задним числом."
        }

        panel.changes.removeAllViews()
        if (trace.changedFactors.isEmpty()) {
            panel.changes.addView(
                text(
                    "Новых фактов, подтверждений и потерь свежести нет.",
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 2)
            )
            return
        }

        trace.changedFactors.forEachIndexed { index, deltaItem ->
            val itemColor = when {
                deltaItem.valueDelta > 0 -> AppColors.accent
                deltaItem.valueDelta < 0 -> AppColors.danger
                else -> AppColors.signal
            }
            panel.changes.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            addView(
                                text(
                                    deltaItem.factor.title,
                                    15f,
                                    AppColors.ink,
                                    Typeface.BOLD
                                ),
                                LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    1f
                                )
                            )
                            addView(
                                text(
                                    "${deltaItem.beforeValue} → ${deltaItem.currentValue}",
                                    15f,
                                    itemColor,
                                    Typeface.BOLD
                                )
                            )
                        }
                    )
                    addView(
                        text(
                            decisionTraceCause(deltaItem),
                            12f,
                            AppColors.muted
                        ),
                        matchWrap(top = 3)
                    )
                },
                matchWrap(top = if (index == 0) 2 else 8)
            )
            if (index < trace.changedFactors.lastIndex) {
                panel.changes.addView(
                    divider(),
                    matchFixed(1, top = 8)
                )
            }
        }
    }

    private fun decisionGuardPanel(
        onAction: () -> Unit,
        onShare: () -> Unit
    ): DecisionGuardPanel {
        val badge = label(
            "",
            AppColors.accentSoft,
            AppColors.accentDark
        ).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val seal = text(
            "",
            12f,
            AppColors.muted,
            Typeface.BOLD
        )
        val metric = text(
            "",
            22f,
            AppColors.ink,
            Typeface.BOLD
        )
        val chart = DecisionGuardView(this)
        val body = text("", 14f, AppColors.ink)
        val deadline = text(
            "",
            13f,
            AppColors.signal,
            Typeface.BOLD
        )
        val action = outlineButton(
            "",
            AppColors.accent,
            onAction
        ).apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        val share = commandButton(
            "Создать стоп-контракт PNG",
            AppColors.signal,
            onShare
        ).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val root = card().apply {
            visibility = View.GONE
            addView(
                text(
                    "Стоп-контракт",
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                )
            )
            addView(badge, matchWrap(top = 7))
            addView(seal, matchWrap(top = 8))
            addView(metric, matchWrap(top = 8))
            addView(chart, matchFixed(126, top = 2))
            addView(body, matchWrap(top = 3))
            addView(deadline, matchWrap(top = 9))
            addView(action, matchWrap(top = 12))
            addView(share, matchWrap(top = 8))
        }
        return DecisionGuardPanel(
            root = root,
            badge = badge,
            seal = seal,
            metric = metric,
            chart = chart,
            body = body,
            deadline = deadline,
            action = action,
            share = share
        )
    }

    private fun renderDecisionGuard(
        panel: DecisionGuardPanel,
        result: DecisionGuardResult,
        now: Long,
        actionFactor: SignalFactor?
    ) {
        val tone = decisionGuardTone(result.status)
        panel.root.visibility = View.VISIBLE
        panel.root.background = rounded(
            AppColors.surface,
            8,
            tone.foreground,
            1
        )
        panel.badge.text = decisionGuardBadge(result.status)
        panel.badge.setTextColor(tone.foreground)
        panel.badge.background = rounded(
            tone.background,
            14
        )
        val snapshotMark = result.plan
            .snapshotFingerprint
            .take(8)
            .uppercase()
        panel.seal.text = result.breach?.let { breach ->
            getString(
                R.string.decision_guard_seal_breached,
                result.plan.shortSeal,
                snapshotMark,
                breach.shortFingerprint
            )
        } ?: getString(
            R.string.decision_guard_seal,
            result.plan.shortSeal,
            snapshotMark
        )
        panel.seal.setTextColor(tone.foreground)
        panel.metric.text = decisionGuardMetric(result)
        panel.metric.setTextColor(tone.foreground)
        panel.chart.setResult(result)
        panel.body.text = decisionGuardExplanation(result)
        panel.deadline.text = decisionGuardDeadline(
            result,
            now
        )
        panel.deadline.setTextColor(tone.foreground)
        panel.deadline.background = rounded(
            tone.background,
            8
        )
        panel.deadline.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(10)
        )

        if (
            result.status == DecisionGuardStatus.SEALED_SKIP ||
            actionFactor == null
        ) {
            panel.action.visibility = View.GONE
        } else {
            panel.action.visibility = View.VISIBLE
            panel.action.text = if (result.isTriggered) {
                "Перепроверить «${actionFactor.title}»"
            } else {
                "Проверить пломбу «${actionFactor.title}»"
            }
            panel.action.setTextColor(tone.foreground)
            panel.action.background = rippleRounded(
                AppColors.surface,
                8,
                tone.foreground,
                1
            )
            panel.action.contentDescription =
                "${panel.action.text}. Перейти к фактору."
        }
        panel.share.contentDescription =
            "Создать PNG стоп-контракта и поделиться"
    }

    private fun decisionGuardBadge(
        status: DecisionGuardStatus
    ): String {
        return when (status) {
            DecisionGuardStatus.SEALED_SKIP ->
                "ПРОПУСК ЗАПЕЧАТАН"
            DecisionGuardStatus.ARMED ->
                "УСЛОВИЕ ДЕЙСТВУЕТ"
            DecisionGuardStatus.TRIGGERED ->
                "СТОП-КОНТРАКТ СРАБОТАЛ"
        }
    }

    private fun decisionGuardMetric(
        result: DecisionGuardResult
    ): String {
        if (
            result.status ==
            DecisionGuardStatus.SEALED_SKIP
        ) {
            return "Пропустить • вывод закрыт"
        }
        if (result.isTriggered) {
            val baseline =
                result.baselineResult.effectiveSignal.readiness
            val current =
                result.currentResult.effectiveSignal.readiness
            return "$baseline → $current • решение остановлено"
        }
        val condition = requireNotNull(
            result.plan.condition
        )
        return condition.scoreFloor?.let {
            "${condition.factor.title}: " +
                "${condition.baselineValue} • стоп ≤ $it"
        } ?: (
            "${condition.factor.title}: " +
                "контроль подтверждения"
            )
    }

    private fun decisionGuardExplanation(
        result: DecisionGuardResult
    ): String {
        if (
            result.status ==
            DecisionGuardStatus.SEALED_SKIP
        ) {
            return "Вывод «пропустить» зафиксирован ${
                formatDateTime(result.plan.armedAt)
            }. Изменения карты не повышают его задним числом."
        }
        if (!result.isTriggered) {
            val required = verdictTitle(
                result.plan.requiredVerdict
            ).lowercase(Locale.getDefault())
            val condition = requireNotNull(
                result.plan.condition
            )
            val evidenceRule =
                condition.requiredEvidence?.let {
                    " и подтверждение не слабее «${it.title}»"
                }.orEmpty()
            return "Пломба удерживает минимум «$required»: " +
                "«${condition.factor.title}» не пересек " +
                "стоп-линию$evidenceRule."
        }
        if (result.isRecoveredAfterBreach) {
            val breachedAt = requireNotNull(
                result.breach
            ).triggeredAt
            return "Пломба была нарушена ${
                formatDateTime(breachedAt)
            }. Текущие условия восстановлены, но для нового " +
                "вывода нужен новый снимок."
        }
        return result.effectiveCauses
            .joinToString("\n") { cause ->
                decisionGuardCause(result, cause)
            }
    }

    private fun decisionGuardCause(
        result: DecisionGuardResult,
        cause: DecisionGuardCause
    ): String {
        return when (cause) {
            DecisionGuardCause.DECISION_ABOVE_SIGNAL -> {
                val required = verdictTitle(
                    result.plan.requiredVerdict
                ).lowercase(Locale.getDefault())
                val baseline = verdictTitle(
                    result.baselineResult
                        .effectiveSignal
                        .verdict
                ).lowercase(Locale.getDefault())
                "Зафиксировано «$required», но подтвержденные " +
                    "данные давали «$baseline»."
            }
            DecisionGuardCause.SIGNAL_BELOW_CONTRACT -> {
                val required = verdictTitle(
                    result.plan.requiredVerdict
                ).lowercase(Locale.getDefault())
                val observedVerdict =
                    result.breach?.verdict
                        ?: result.currentResult
                            .effectiveSignal
                            .verdict
                val current = verdictTitle(
                    observedVerdict
                ).lowercase(Locale.getDefault())
                "Статус снизился: минимум «$required», " +
                    "сейчас «$current»."
            }
            DecisionGuardCause.FACTOR_FLOOR -> {
                val condition = requireNotNull(
                    result.plan.condition
                )
                "«${condition.factor.title}»: " +
                    "${result.breach?.factorValue
                        ?: result.currentFactorValue} ≤ " +
                    "${condition.scoreFloor}. Стоп-линия пересечена."
            }
            DecisionGuardCause.EVIDENCE_LOSS -> {
                val condition = requireNotNull(
                    result.plan.condition
                )
                "«${condition.factor.title}»: " +
                    "${condition.requiredEvidence?.title} → " +
                    "${result.breach?.evidence?.title
                        ?: result.currentEvidence?.title}. " +
                    "Подтверждение слабее пломбы."
            }
            DecisionGuardCause.COUNTERVIEW_LIMIT ->
                "Контрракурс больше не допускает сохраненный " +
                    "вывод. Проверьте альтернативную версию " +
                    "и создайте новый снимок."
        }
    }

    private fun decisionGuardDeadline(
        result: DecisionGuardResult,
        now: Long
    ): String {
        if (
            result.status ==
            DecisionGuardStatus.SEALED_SKIP
        ) {
            return "Пломба не снимается изменением шкал. " +
                "Для другого вывода нужен новый снимок."
        }
        if (result.isTriggered) {
            val breachTime = result.breach?.triggeredAt
                ?.let { " ${formatDateTime(it)}" }
                .orEmpty()
            return "Пломба ${result.plan.shortSeal} " +
                "нарушена$breachTime. " +
                "Старый вывод не использовать."
        }
        val condition = requireNotNull(
            result.plan.condition
        )
        val transitionAt = result.currentFreshness
            .factor(condition.factor)
            .nextTransitionAt
        return if (transitionAt == null) {
            "Таймер не участвует: пломба действует до " +
                "нового снимка решения."
        } else {
            "Через ${
                FreshnessFormatter.duration(
                    transitionAt - now
                )
            } подтверждение «${condition.factor.title}» " +
                "может потерять уровень."
        }
    }

    private fun latchDecisionGuard(
        live: DecisionGuardResult,
        now: Long
    ): DecisionGuardResult {
        val eventId = live.plan.eventId
        val stored = state.decisionGuardBreach(eventId)
        if (
            live.status ==
            DecisionGuardStatus.SEALED_SKIP
        ) {
            if (stored != null) {
                state.clearDecisionGuardBreach(eventId)
            }
            return live
        }
        if (stored != null) {
            val latched = runCatching {
                live.withBreach(stored)
            }.getOrNull()
            if (latched != null) return latched
            state.clearDecisionGuardBreach(eventId)
        }
        if (live.causes.isEmpty()) return live

        val breach = DecisionGuardBreachFactory.create(
            result = live,
            triggeredAt = maxOf(
                now,
                live.plan.armedAt
            )
        )
        state.saveDecisionGuardBreach(breach)
        return live.withBreach(breach)
    }

    private fun decisionGuardTone(
        status: DecisionGuardStatus
    ): Tone {
        return when (status) {
            DecisionGuardStatus.SEALED_SKIP ->
                Tone(AppColors.signal, AppColors.signalSoft)
            DecisionGuardStatus.ARMED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            DecisionGuardStatus.TRIGGERED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun shareDecisionGuardPassport(
        event: SportEvent,
        guard: DecisionGuardResult
    ) {
        if (passportExportInProgress) {
            Toast.makeText(
                this,
                "Изображение уже создается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        passportExportInProgress = true
        val passport =
            DecisionGuardPassportFactory.create(
                event = event,
                guard = guard
            )
        Toast.makeText(
            this,
            "Создаем стоп-контракт…",
            Toast.LENGTH_SHORT
        ).show()
        passportExecutor.execute {
            runCatching {
                val file =
                    DecisionGuardPassportExporter(
                        applicationContext
                    ).export(passport)
                AnalysisImageProvider.uriFor(
                    applicationContext,
                    file
                )
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Стоп-контракт: ${event.match}"
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            DecisionGuardPassportFactory
                                .shareText(passport)
                        )
                        clipData = ClipData.newRawUri(
                            "Стоп-контракт",
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться стоп-контрактом"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать стоп-контракт",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun decisionTraceCause(
        delta: DecisionFactorDelta
    ): String {
        val causes = delta.causes.joinToString(" • ") { cause ->
            when (cause) {
                DecisionChangeCause.FACTS -> "новые факты"
                DecisionChangeCause.CONFIRMATION ->
                    "обновлено подтверждение"
                DecisionChangeCause.FRESHNESS -> "истек срок"
                DecisionChangeCause.COUNTERVIEW ->
                    "обновлен Контрракурс"
            }
        }
        return if (delta.beforeEvidence != delta.currentEvidence) {
            "$causes • ${delta.beforeEvidence.title} → ${delta.currentEvidence.title}"
        } else {
            causes
        }
    }

    private fun decisionJournal(
        event: SportEvent,
        assessment: () -> SignalAssessment,
        evidence: () -> EvidenceAssessment,
        timeline: () -> EvidenceTimeline,
        counterReview: () -> CounterReviewAssessment,
        counterView: () -> CounterViewResult,
        onDecisionSaved: () -> Unit
    ): LinearLayout {
        val panel = card()
        panel.addView(
            imageFrame().apply {
                addView(
                    ImageView(this@MainActivity).apply {
                        setImageResource(
                            R.drawable.decision_receipt_v370
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription =
                            "Три варианта решения и отдельный механизм фиксации квитанции"
                    },
                    frameMatch()
                )
            },
            matchFixed(
                if (effectiveFontScale() >= 1.8f) 120 else 172
            )
        )
        panel.addView(
            text(
                "Журнал решения",
                20f,
                AppColors.ink,
                Typeface.BOLD
            ),
            matchWrap(top = 13)
        )
        panel.addView(
            text(
                "Сначала выберите итог и прочитайте, что именно будет записано. Отдельная команда создаст предстартовый снимок.",
                13f,
                AppColors.muted
            ),
            matchWrap(top = 5)
        )

        val composerBadge = label(
            "",
            AppColors.signalSoft,
            AppColors.signal
        ).apply {
            textSize = fixedControlTextSize(11f)
        }
        val composerHeadline = text(
            "",
            17f,
            AppColors.ink,
            Typeface.BOLD
        )
        val composerBody = text("", 12.5f, AppColors.ink)
        val composerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            addView(composerBadge)
            addView(composerHeadline, matchWrap(top = 7))
            addView(composerBody, matchWrap(top = 4))
        }
        panel.addView(composerPanel, matchWrap(top = 13))

        var selectedDecision = pendingDecisionReceiptChoice.takeIf {
            pendingDecisionReceiptEventId == event.id
        }
        val choiceButtons = linkedMapOf<SavedDecision, RadioButton>()
        val choiceGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        listOf(
            Triple(
                SavedDecision.SKIP,
                "Пропустить",
                "Данных недостаточно или сработало стоп-условие."
            ),
            Triple(
                SavedDecision.OBSERVE,
                "Наблюдать",
                "Версия открыта, но сильный вывод пока не защищён."
            ),
            Triple(
                SavedDecision.DATA_READY,
                "Факты сверены",
                "Все проверки допускают итог; это не прогноз матча."
            )
        ).forEachIndexed { index, (decision, title, body) ->
            val button = decisionReceiptChoice(
                decision = decision,
                title = title,
                body = body
            )
            choiceButtons[decision] = button
            choiceGroup.addView(
                button,
                matchWrap(top = if (index == 0) 0 else 7)
            )
        }
        selectedDecision?.let { choice ->
            choiceButtons[choice]?.isChecked = true
        }
        panel.addView(choiceGroup, matchWrap(top = 12))

        val actionButton = commandButton(
            "Выберите итог",
            AppColors.accent
        ) {}.apply {
            textSize = fixedControlTextSize(14f)
            contentDescription = "Выберите итог перед фиксацией"
        }
        panel.addView(actionButton, matchWrap(top = 12))
        val savedText = text("", 12f, AppColors.muted)
        panel.addView(savedText, matchWrap(top = 10))

        lateinit var refresh: () -> Unit
        fun compose(
            now: Long,
            budget: AttentionBudgetResult =
                currentAttentionBudget(now)
        ): DecisionReceiptComposerResult {
            val snapshot = state.decisionSnapshot(event.id)
            return DecisionReceiptComposer.evaluate(
                selectedDecision = selectedDecision,
                reviewFinalized = state.postEventReview(event.id)
                    ?.isFinalized == true,
                decisionWindowOpen =
                    EventStoryTiming.decisionWindowOpen(
                        event = event,
                        snapshot = snapshot,
                        now = now
                    ),
                ledgerIntegrity = state.decisionLedger().integrity,
                decisionCeiling = counterView().decisionCeiling,
                attentionBudgetStatus = budget.status,
                distanceClearanceValid =
                    state.distanceClearance(now) != null
            )
        }

        refresh = {
            val now = System.currentTimeMillis()
            val saved = state.savedDecision(event.id)
            val snapshot = state.decisionSnapshot(event.id)
            val reviewFinalized = state
                .postEventReview(event.id)
                ?.isFinalized == true
            val decisionWindowOpen =
                EventStoryTiming.decisionWindowOpen(
                    event = event,
                    snapshot = snapshot,
                    now = now
                )
            val currentCounterView = counterView()
            val ledgerIntegrity = state.decisionLedger().integrity
            val choicesEnabled =
                !reviewFinalized &&
                    decisionWindowOpen &&
                    ledgerIntegrity != DecisionLedgerIntegrity.TAMPERED
            choiceButtons.forEach { (decision, button) ->
                val selected = decision == selectedDecision
                val color = decisionColor(decision)
                button.setTextColor(color)
                button.background = rippleRounded(
                    if (selected) {
                        decisionReceiptChoiceBackground(decision)
                    } else {
                        AppColors.surface
                    },
                    8,
                    color,
                    if (selected) 2 else 1
                )
                button.isEnabled = choicesEnabled
                button.alpha = if (choicesEnabled) 1f else 0.5f
                val limit = if (
                    decision.ordinal >
                    currentCounterView.decisionCeiling.ordinal
                ) {
                    ". Выше текущего предела Контрракурса"
                } else {
                    ""
                }
                button.contentDescription =
                    "Выбрать итог: ${decisionTitle(decision)}$limit"
            }
            val composer = compose(now)
            val tone = decisionReceiptTone(composer.status)
            composerPanel.background = rounded(
                tone.background,
                8,
                tone.foreground,
                1
            )
            composerBadge.text = composer.badge
            composerBadge.setTextColor(tone.foreground)
            composerBadge.background = rounded(
                tone.background,
                14,
                tone.foreground,
                1
            )
            composerHeadline.text = composer.headline
            composerHeadline.setTextColor(tone.foreground)
            composerBody.text = composer.body
            actionButton.text = composer.actionTitle
            actionButton.isEnabled = composer.canAct
            actionButton.alpha = if (composer.canAct) 1f else 0.48f
            actionButton.contentDescription = composer.actionTitle
            val savedAt = state.savedDecisionTime(event.id)
            val savedAboveCounterView =
                saved != null &&
                    !currentCounterView.allows(saved)
            savedText.setTextColor(
                if (savedAboveCounterView) {
                    AppColors.danger
                } else {
                    AppColors.muted
                }
            )
            savedText.text = if (saved == null || savedAt == 0L) {
                if (decisionWindowOpen) {
                    "Решение еще не зафиксировано."
                } else {
                    "Предстартовое решение упущено: старт уже наступил. Создать снимок задним числом нельзя."
                }
            } else {
                buildString {
                    append("Сохранено: ")
                    append(decisionTitle(saved))
                    append(" • ")
                    append(formatDateTime(savedAt))
                    snapshot?.let {
                        append(" • метка ")
                        append(it.shortFingerprint)
                        it.distanceClearanceFingerprint?.let {
                                clearanceFingerprint ->
                            append(
                                "\nКонтур дистанции • допуск "
                            )
                            append(
                                clearanceFingerprint.take(8)
                                    .uppercase()
                            )
                        }
                        it.attentionBudgetFingerprint?.let {
                                budgetFingerprint ->
                            append(
                                "\nБюджет внимания • метка "
                            )
                            append(
                                budgetFingerprint.take(8)
                                    .uppercase()
                            )
                        }
                    }
                    if (reviewFinalized) {
                        append("\nЗакрыто ретроспективой «После свистка».")
                    }
                    if (savedAboveCounterView) {
                        append(
                            "\nКонтрракурс допускает максимум «${
                                decisionTitle(
                                    currentCounterView
                                        .decisionCeiling
                                )
                            }». Нужен новый снимок."
                        )
                    }
                }
            }
        }
        choiceGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedDecision = choiceButtons.entries
                .firstOrNull { it.value.id == checkedId }
                ?.key
            pendingDecisionReceiptEventId =
                event.id.takeIf { selectedDecision != null }
            pendingDecisionReceiptChoice = selectedDecision
            refresh()
        }
        actionButton.setOnClickListener action@{
            val decision = selectedDecision
            val savedAt = System.currentTimeMillis()
            val budgetChecked = decision?.let {
                AttentionBudgetPolicy.requiresBudget(it)
            } == true
            val attentionBudget = if (budgetChecked) {
                flushAttentionTracking(now = savedAt)
            } else {
                currentAttentionBudget(now = savedAt)
            }
            val composer = compose(
                now = savedAt,
                budget = attentionBudget
            )
            when (composer.action) {
                DecisionReceiptAction.NONE -> refresh()
                DecisionReceiptAction.SHOW_LEDGER ->
                    showDecisionLedgerTampered()
                DecisionReceiptAction.SHOW_ATTENTION -> {
                    startAttentionTrackingIfNeeded()
                    showAttentionBudgetExceeded(attentionBudget)
                }
                DecisionReceiptAction.OPEN_DISTANCE -> {
                    if (budgetChecked) {
                        startAttentionTrackingIfNeeded()
                    }
                    showDecisionDistanceRequired()
                }
                DecisionReceiptAction.COMMIT -> {
                    val committedDecision = decision
                        ?: return@action
                    val distanceClearance =
                        state.distanceClearance(savedAt)
                    val snapshot = state.saveDecision(
                        eventId = event.id,
                        eventLabel = event.match,
                        decision = committedDecision,
                        assessment = assessment(),
                        evidence = evidence(),
                        timeline = timeline(),
                        counterReview = counterReview(),
                        distanceClearance = distanceClearance,
                        savedAt = savedAt
                    )
                    if (budgetChecked) {
                        startAttentionTrackingIfNeeded()
                    }
                    selectedDecision = null
                    pendingDecisionReceiptEventId = null
                    pendingDecisionReceiptChoice = null
                    choiceGroup.clearCheck()
                    refresh()
                    onDecisionSaved()
                    Toast.makeText(
                        this,
                        "Снимок ${snapshot.shortFingerprint} сохранен",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        refresh()
        return panel
    }

    private fun decisionReceiptChoice(
        decision: SavedDecision,
        title: String,
        body: String
    ): RadioButton {
        val value = SpannableString("$title\n$body").apply {
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                title.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return RadioButton(this).apply {
            id = View.generateViewId()
            text = value
            textSize = 13f
            setTextColor(decisionColor(decision))
            typeface = AppTypography.body(this@MainActivity)
            buttonTintList = ColorStateList.valueOf(
                decisionColor(decision)
            )
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(64)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rippleRounded(
                AppColors.surface,
                8,
                decisionColor(decision),
                1
            )
        }
    }

    private fun decisionReceiptChoiceBackground(
        decision: SavedDecision
    ): Int {
        return when (decision) {
            SavedDecision.SKIP -> AppColors.dangerSoft
            SavedDecision.OBSERVE -> AppColors.warningSoft
            SavedDecision.DATA_READY -> AppColors.accentSoft
        }
    }

    private fun decisionReceiptTone(
        status: DecisionReceiptStatus
    ): Tone {
        return when (status) {
            DecisionReceiptStatus.CHOICE_REQUIRED,
            DecisionReceiptStatus.DISTANCE_REQUIRED ->
                Tone(AppColors.signal, AppColors.signalSoft)
            DecisionReceiptStatus.READY_SKIP,
            DecisionReceiptStatus.LEDGER_TAMPERED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            DecisionReceiptStatus.READY_OBSERVE,
            DecisionReceiptStatus.COUNTERVIEW_LIMIT,
            DecisionReceiptStatus.ATTENTION_EXHAUSTED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            DecisionReceiptStatus.READY_DATA ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            DecisionReceiptStatus.WINDOW_CLOSED,
            DecisionReceiptStatus.REVIEW_FINALIZED ->
                Tone(AppColors.muted, AppColors.background)
        }
    }

    private fun showAttentionBudgetExceeded(
        budget: AttentionBudgetResult
    ) {
        AlertDialog.Builder(this)
            .setTitle("Бюджет внимания исчерпан")
            .setMessage(
                "Активный анализ: ${
                    attentionBudgetDuration(budget.usedMillis)
                } из ${budget.limitMinutes} мин. " +
                    "«Пропустить» и «Наблюдать» " +
                    "остаются доступны."
            )
            .setNegativeButton("Остаться", null)
            .setPositiveButton("Открыть 18+") { _, _ ->
                selectTab(4)
            }
            .show()
    }

    private fun showDecisionLedgerTampered() {
        AlertDialog.Builder(this)
            .setTitle("Цепочка журнала нарушена")
            .setMessage(
                "Новая запись не будет добавлена поверх поврежденной истории. Откройте 18+, проверьте статус и явно начните новую цепочку."
            )
            .setNegativeButton("Остаться", null)
            .setPositiveButton("Открыть 18+") { _, _ ->
                selectTab(4)
            }
            .show()
    }

    private fun attentionBudgetDuration(
        millis: Long,
        roundUp: Boolean = false
    ): String {
        require(millis >= 0L)
        if (millis == 0L) return "0 мин"
        val minute = 60L * 1000L
        if (!roundUp && millis < minute) return "< 1 мин"
        val minutes = if (roundUp) {
            (millis + minute - 1L) / minute
        } else {
            millis / minute
        }
        return "$minutes мин"
    }

    private fun showDecisionDistanceRequired() {
        AlertDialog.Builder(this)
            .setTitle("Нужен Контур дистанции")
            .setMessage(
                "Вывод «Факты сверены» требует свежей " +
                    "самопроверки. «Пропустить» и «Наблюдать» " +
                    "остаются доступны."
            )
            .setNegativeButton("Остаться", null)
            .setPositiveButton("Пройти проверку") { _, _ ->
                decisionDistanceDraft =
                    DecisionDistanceAssessment.unanswered()
                returnToPulseAfterDistance = true
                selectTab(4)
            }
            .show()
    }

    private fun postEventReviewPanel(
        event: SportEvent,
        onNeedDecision: () -> Unit = {},
        onReviewFinalized: () -> Unit
    ): LinearLayout {
        val panel = card()
        lateinit var refresh: () -> Unit
        refresh = refreshBlock@{
            panel.removeAllViews()
            val now = System.currentTimeMillis()
            val snapshot = state.decisionSnapshot(event.id)
            val storyWindow = EventStoryTiming.window(
                event = event,
                snapshot = snapshot,
                now = now
            )
            val storedReview = state.postEventReview(event.id)
            val review = if (
                snapshot != null &&
                storedReview != null &&
                storedReview.decisionFingerprint ==
                snapshot.fingerprint
            ) {
                storedReview
            } else {
                if (storedReview != null) {
                    state.clearPostEventReview(event.id)
                }
                null
            }
            val result = if (
                snapshot != null &&
                review != null
            ) {
                PostEventReviewEngine.evaluate(
                    snapshot = snapshot,
                    review = review
                )
            } else {
                null
            }
            val snapshotMissed = snapshot == null &&
                storyWindow != null &&
                now >= storyWindow.startAt
            val snapshotAfterStart = snapshot != null &&
                storyWindow != null &&
                snapshot.savedAt >= storyWindow.startAt
            val earlyReview = review != null &&
                storyWindow != null &&
                review.updatedAt < storyWindow.reviewOpensAt
            val reviewWindowLocked = storyWindow != null &&
                now < storyWindow.reviewOpensAt
            val tone = when {
                snapshotMissed || snapshotAfterStart || earlyReview ->
                    Tone(AppColors.danger, AppColors.dangerSoft)
                storyWindow == null || reviewWindowLocked ->
                    Tone(AppColors.warning, AppColors.warningSoft)
                result != null && review?.isFinalized == true ->
                    postEventReviewTone(result)
                else ->
                    Tone(AppColors.signal, AppColors.signalSoft)
            }
            val badgeTitle = when {
                storyWindow == null -> "СТАРТ НЕИЗВЕСТЕН"
                snapshotMissed -> "СНИМОК УПУЩЕН"
                snapshotAfterStart -> "ПОЗДНИЙ СНИМОК"
                earlyReview -> "РАННЯЯ ЗАПИСЬ"
                snapshot == null -> "НУЖЕН СНИМОК"
                reviewWindowLocked -> "РАЗБОР ЗАКРЫТ"
                review?.isFinalized == true &&
                    result != null ->
                    postEventReviewBadge(result.status)
                else ->
                    "АУДИТ • ${review?.answeredCount ?: 0}/5"
            }

            val stackHeader =
                resources.configuration.fontScale >= 1.3f ||
                    resources.configuration.screenWidthDp < 380
            panel.addView(
                LinearLayout(this).apply {
                    orientation = if (stackHeader) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackHeader) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        text(
                            "После свистка",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stackHeader) {
                            wrapWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                    )
                    addView(
                        label(
                            badgeTitle,
                            tone.background,
                            tone.foreground
                        ),
                        if (stackHeader) {
                            wrapWrap().apply {
                                topMargin = dp(8)
                            }
                        } else {
                            wrapWrap()
                        }
                    )
                }
            )
            panel.addView(
                text(
                    "Проверьте, выдержали ли исходные факты реальность. Счет матча, коэффициенты и финансовый результат не используются.",
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 6)
            )

            if (storyWindow == null) {
                panel.addView(
                    postEventGateNotice(
                        "Точный старт не подтвержден. Без него приложение не открывает ретроспективу и не имитирует знание момента завершения.",
                        tone
                    ),
                    matchWrap(top = 12)
                )
                return@refreshBlock
            }

            if (snapshot == null) {
                if (snapshotMissed) {
                    panel.addView(
                        postEventGateNotice(
                            "Предстартовый снимок не был создан. После старта восстановить исходное решение задним числом нельзя.",
                            tone
                        ),
                        matchWrap(top = 12)
                    )
                    return@refreshBlock
                }
                panel.addView(
                    text(
                        "Сначала зафиксируйте вывод в журнале. Ретроспектива будет связана с неизменяемой меткой этого решения.",
                        13f,
                        AppColors.signal,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(
                            AppColors.signalSoft,
                            8
                        )
                        setPadding(
                            dp(12),
                            dp(10),
                            dp(12),
                            dp(10)
                        )
                    },
                    matchWrap(top = 12)
                )
                if (!state.isPauseActive()) {
                    panel.addView(
                        outlineButton(
                            "Перейти к журналу решения",
                            AppColors.signal,
                            onNeedDecision
                        ),
                        matchWrap(top = 11)
                    )
                }
                return@refreshBlock
            }

            if (snapshotAfterStart) {
                panel.addView(
                    postEventGateNotice(
                        "Связанный снимок создан после указанного старта и не считается предстартовым. Разбор по нему заблокирован.",
                        tone
                    ),
                    matchWrap(top = 12)
                )
                return@refreshBlock
            }

            if (earlyReview) {
                panel.addView(
                    postEventGateNotice(
                        "Эта локальная запись появилась раньше минимального окна разбора. Ее нельзя выдавать за послесобытийную ретроспективу.",
                        tone
                    ),
                    matchWrap(top = 12)
                )
                if (!state.isPauseActive(now)) {
                    panel.addView(
                        outlineButton(
                            "Удалить раннюю запись",
                            AppColors.danger
                        ) {
                            state.clearPostEventReview(event.id)
                            onReviewFinalized()
                        },
                        matchWrap(top = 11)
                    )
                }
                return@refreshBlock
            }

            if (reviewWindowLocked) {
                panel.addView(
                    postEventGateNotice(
                        "Минимальное окно события еще идет. Разбор откроется не раньше ${
                            TimeBridgeEngine.formatInstant(
                                startAt = storyWindow.reviewOpensAt,
                                selectedZone = state.selectedRegionalZone
                            )
                        }; фактическое завершение нужно сверить отдельно.",
                        tone
                    ),
                    matchWrap(top = 12)
                )
                return@refreshBlock
            }

            if (review?.isFinalized == true && result != null) {
                renderPostEventReviewResult(
                    panel = panel,
                    snapshot = snapshot,
                    result = result
                )
                return@refreshBlock
            }

            panel.addView(
                text(
                    "Что произошло с предпосылками, которые были доступны до события?",
                    14f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 13)
            )
            SignalFactor.values().forEachIndexed {
                    index,
                    factor ->
                if (index > 0) {
                    panel.addView(
                        divider(),
                        matchFixed(
                            1,
                            top = 12,
                            bottom = 10
                        )
                    )
                }
                panel.addView(
                    postEventFactorInput(
                        factor = factor,
                        snapshot = snapshot,
                        selected = review
                            ?.outcome(factor)
                            ?: PostEventOutcome.UNREVIEWED
                    ) { outcome ->
                        val now = System.currentTimeMillis()
                        var activeReview = state
                            .postEventReview(event.id)
                            ?.takeIf {
                                !it.isFinalized &&
                                    it.decisionFingerprint ==
                                    snapshot.fingerprint
                            }
                            ?: PostEventReviewFactory.start(
                                snapshot = snapshot,
                                now = maxOf(
                                    now,
                                    snapshot.savedAt
                                )
                            )
                        activeReview =
                            PostEventReviewFactory.setOutcome(
                                review = activeReview,
                                snapshot = snapshot,
                                factor = factor,
                                outcome = outcome,
                                now = maxOf(
                                    now,
                                    activeReview.updatedAt
                                )
                            )
                        state.savePostEventReview(activeReview)
                        refresh()
                    }
                )
            }

            val answered = review?.answeredCount ?: 0
            val remaining = (
                SignalFactor.values().size - answered
            ).coerceAtLeast(0)
            panel.addView(
                text(
                    if (remaining == 0) {
                        "Все факторы отмечены. После фиксации ответы и связанный снимок будут закрыты."
                    } else {
                        "Осталось отметить факторов: $remaining. «Нет данных» является полноценным честным ответом."
                    },
                    12f,
                    AppColors.muted
                ),
                matchWrap(top = 13)
            )
            val finalizeButton = commandButton(
                if (remaining == 0) {
                    "Зафиксировать ретроспективу"
                } else {
                    "Ответьте еще: $remaining"
                },
                AppColors.accent
            ) {
                val activeReview = state
                    .postEventReview(event.id)
                    ?.takeIf {
                        !it.isFinalized &&
                            it.decisionFingerprint ==
                            snapshot.fingerprint
                    }
                    ?: return@commandButton
                val finalized =
                    PostEventReviewFactory.finalize(
                        review = activeReview,
                        snapshot = snapshot,
                        now = maxOf(
                            System.currentTimeMillis(),
                            activeReview.updatedAt
                        )
                    )
                state.savePostEventReview(finalized)
                Toast.makeText(
                    this,
                    "Ретроспектива ${finalized.shortFingerprint} зафиксирована",
                    Toast.LENGTH_LONG
                ).show()
                onReviewFinalized()
            }.apply {
                isEnabled = remaining == 0
                alpha = if (isEnabled) 1f else 0.48f
                setPadding(dp(12), dp(9), dp(12), dp(9))
            }
            panel.addView(finalizeButton, matchWrap(top = 11))
        }
        refresh()
        return panel
    }

    private fun postEventGateNotice(
        message: String,
        tone: Tone
    ): TextView {
        return text(
            message,
            13f,
            tone.foreground,
            Typeface.BOLD
        ).apply {
            background = rounded(tone.background, 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
    }

    private fun postEventFactorInput(
        factor: SignalFactor,
        snapshot: DecisionSnapshot,
        selected: PostEventOutcome,
        onSelected: (PostEventOutcome) -> Unit
    ): LinearLayout {
        val baselineFreshness = FreshnessEngine.evaluate(
            evidence = snapshot.evidence,
            timeline = snapshot.timeline,
            now = snapshot.savedAt
        )
        val baselineEvidence = baselineFreshness
            .effectiveEvidence
            .level(factor)
        val baselineValue = EvidenceEngine.evaluate(
            assessment = snapshot.assessment,
            evidence = baselineFreshness.effectiveEvidence
        ).effectiveAssessment.value(factor)
        val choices = listOf(
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.PARTIAL,
            PostEventOutcome.DISPROVED,
            PostEventOutcome.UNKNOWN
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(
                            factor.title,
                            14f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        text(
                            "$baselineValue • ${baselineEvidence.title}",
                            11f,
                            evidenceColor(baselineEvidence),
                            Typeface.BOLD
                        )
                    )
                }
            )
            choices.chunked(2).forEachIndexed {
                    rowIndex,
                    rowChoices ->
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        weightSum = 2f
                        rowChoices.forEachIndexed {
                                choiceIndex,
                                outcome ->
                            val tone =
                                postEventOutcomeTone(outcome)
                            val isSelected = outcome == selected
                            val button = text(
                                outcome.shortTitle,
                                12f,
                                if (isSelected) {
                                    Color.WHITE
                                } else {
                                    tone.foreground
                                },
                                Typeface.BOLD
                            ).apply {
                                gravity = Gravity.CENTER
                                minHeight = dp(48)
                                setPadding(
                                    dp(8),
                                    dp(7),
                                    dp(8),
                                    dp(7)
                                )
                                background = rippleRounded(
                                    if (isSelected) {
                                        tone.foreground
                                    } else {
                                        AppColors.surface
                                    },
                                    8,
                                    tone.foreground,
                                    1
                                )
                                applyAccessibleAction(dp(48))
                                this.isSelected = isSelected
                                contentDescription =
                                    "${factor.title}: ${outcome.title}"
                                setOnClickListener {
                                    onSelected(outcome)
                                }
                            }
                            addView(
                                button,
                                LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    1f
                                ).apply {
                                    if (choiceIndex == 0) {
                                        rightMargin = dp(6)
                                    }
                                }
                            )
                        }
                    },
                    matchWrap(top = if (rowIndex == 0) 8 else 6)
                )
            }
        }
    }

    private fun renderPostEventReviewResult(
        panel: LinearLayout,
        snapshot: DecisionSnapshot,
        result: PostEventReviewResult
    ) {
        val tone = postEventReviewTone(result)
        val visibleScore = if (
            result.status ==
            PostEventReviewStatus.NOT_ENOUGH_DATA
        ) {
            "—"
        } else {
            result.reliabilityScore?.toString() ?: "—"
        }
        panel.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.BOTTOM
                        addView(
                            text(
                                visibleScore,
                                42f,
                                tone.foreground,
                                Typeface.BOLD
                            )
                        )
                        addView(
                            text(
                                if (visibleScore == "—") {
                                    "оценка отложена"
                                } else {
                                    "из 100"
                                },
                                12f,
                                AppColors.muted,
                                Typeface.BOLD
                            ),
                            wrapWrap(bottom = 8)
                        )
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
                addView(
                    text(
                        "Проверено\n${result.verifiedCount}/5",
                        12f,
                        AppColors.muted,
                        Typeface.BOLD
                    ).apply {
                        gravity = Gravity.END
                    }
                )
            },
            matchWrap(top = 13)
        )
        if (visibleScore != "—") {
            panel.addView(
                horizontalProgress().apply {
                    progress = result.reliabilityScore ?: 0
                    progressTintList =
                        ColorStateList.valueOf(tone.foreground)
                },
                matchFixed(7, top = 6)
            )
        }
        panel.addView(
            text(
                postEventReviewSummary(result),
                13f,
                tone.foreground,
                Typeface.BOLD
            ).apply {
                background = rounded(tone.background, 8)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            },
            matchWrap(top = 11)
        )
        result.factorResults.forEach { factorResult ->
            panel.addView(
                divider(),
                matchFixed(1, top = 12, bottom = 10)
            )
            val outcomeTone = postEventOutcomeTone(
                factorResult.outcome
            )
            panel.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(
                                text(
                                    factorResult.factor.title,
                                    14f,
                                    AppColors.ink,
                                    Typeface.BOLD
                                )
                            )
                            addView(
                                text(
                                    "На снимке ${factorResult.baselineValue} • ${factorResult.baselineEvidence.title}",
                                    11f,
                                    AppColors.muted
                                ),
                                matchWrap(top = 2)
                            )
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        label(
                            factorResult.outcome.shortTitle.uppercase(
                                Locale.getDefault()
                            ),
                            outcomeTone.background,
                            outcomeTone.foreground
                        )
                    )
                }
            )
        }
        panel.addView(
            text(
                postEventReviewLesson(result),
                13f,
                AppColors.ink,
                Typeface.BOLD
            ).apply {
                background = rounded(
                    AppColors.background,
                    8,
                    AppColors.line,
                    1
                )
                setPadding(dp(12), dp(10), dp(12), dp(10))
            },
            matchWrap(top = 13)
        )
        panel.addView(
            text(
                "Снимок ${snapshot.shortFingerprint} → ретроспектива ${result.review.shortFingerprint} • ${
                    formatDateTime(
                        requireNotNull(
                            result.review.finalizedAt
                        )
                    )
                }",
                11f,
                AppColors.muted,
                Typeface.BOLD
            ),
            matchWrap(top = 9)
        )
    }

    private fun postEventReviewTone(
        result: PostEventReviewResult
    ): Tone {
        return when (result.status) {
            PostEventReviewStatus.RELIABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            PostEventReviewStatus.MIXED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PostEventReviewStatus.FRAGILE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            PostEventReviewStatus.NOT_ENOUGH_DATA ->
                Tone(AppColors.signal, AppColors.signalSoft)
        }
    }

    private fun postEventOutcomeTone(
        outcome: PostEventOutcome
    ): Tone {
        return when (outcome) {
            PostEventOutcome.CONFIRMED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            PostEventOutcome.PARTIAL ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PostEventOutcome.DISPROVED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            PostEventOutcome.UNKNOWN,
            PostEventOutcome.UNREVIEWED ->
                Tone(AppColors.muted, AppColors.background)
        }
    }

    private fun postEventReviewBadge(
        status: PostEventReviewStatus
    ): String {
        return when (status) {
            PostEventReviewStatus.RELIABLE -> "УСТОЙЧИВО"
            PostEventReviewStatus.MIXED -> "СМЕШАННО"
            PostEventReviewStatus.FRAGILE -> "ХРУПКО"
            PostEventReviewStatus.NOT_ENOUGH_DATA ->
                "МАЛО ДАННЫХ"
        }
    }

    private fun postEventReviewSummary(
        result: PostEventReviewResult
    ): String {
        return when (result.status) {
            PostEventReviewStatus.RELIABLE ->
                "Исходные факты выдержали проверку. Это качество данных, а не доказательство правильного прогноза."
            PostEventReviewStatus.MIXED ->
                "Картина подтвердилась не полностью. Следующее решение требует точечной перепроверки слабых факторов."
            PostEventReviewStatus.FRAGILE -> {
                if (result.criticalMisses.isNotEmpty()) {
                    "Обнаружена ложная уверенность: минимум один факт с кворумом источников оказался неверным."
                } else {
                    "Исходная картина не выдержала проверку. Не переносите прежнюю уверенность на следующее событие."
                }
            }
            PostEventReviewStatus.NOT_ENOUGH_DATA ->
                "Проверяемых факторов ${result.verifiedCount}/5. Для честной оценки нужно минимум три."
        }
    }

    private fun postEventReviewLesson(
        result: PostEventReviewResult
    ): String {
        val factor = result.focusFactor
        return when {
            result.criticalMisses.isNotEmpty() &&
                factor != null ->
                "Главный урок: кворум по фактору «${factor.title}» не выдержал реальность. Проверьте независимость и первичность источников."
            result.status ==
                PostEventReviewStatus.NOT_ENOUGH_DATA &&
                factor != null ->
                "Главный урок: соберите постфактум данные по фактору «${factor.title}», не подглядывая на итоговый счет."
            factor != null ->
                "Главный урок: в следующем разборе начните с фактора «${factor.title}»."
            else ->
                "Главный урок: процесс оказался устойчивым, но один удачный разбор еще не формирует закономерность."
        }
    }

    private fun passportPanel(
        event: SportEvent,
        assessment: () -> SignalAssessment,
        evidence: () -> EvidenceAssessment,
        timeline: () -> EvidenceTimeline,
        sourceIntegrity: () -> SourceIntegrityResult,
        counterReview: () -> CounterReviewAssessment
    ): LinearLayout {
        val hasPostEventReview = state
            .postEventReview(event.id)
            ?.isFinalized == true
        return card().apply {
            addView(
                label(
                    if (hasPostEventReview) {
                        "ПОСЛЕ СВИСТКА"
                    } else {
                        "НОВЫЙ ФОРМАТ"
                    },
                    if (hasPostEventReview) {
                        AppColors.accentSoft
                    } else {
                        AppColors.signalSoft
                    },
                    if (hasPostEventReview) {
                        AppColors.accentDark
                    } else {
                        AppColors.signal
                    }
                )
            )
            addView(
                text(
                    if (hasPostEventReview) {
                        "PNG-разбор процесса"
                    } else {
                        "Паспорт события"
                    },
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            addView(
                text(
                    if (hasPostEventReview) {
                        "1080 × 1350 • 5 факторов • без оглядки на счет"
                    } else {
                        "1080 × 1350 • Антиэхо • Контрракурс • стресс • след"
                    },
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )
            val shareButton = commandButton(
                if (hasPostEventReview) {
                    "Создать PNG-разбор и поделиться"
                } else {
                    "Создать PNG и поделиться"
                },
                AppColors.signal
            ) {
                sharePassport(
                    event,
                    assessment(),
                    evidence(),
                    timeline(),
                    sourceIntegrity(),
                    counterReview()
                )
            }.apply {
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            addView(shareButton, matchWrap(top = 13))
        }
    }

    private fun sharePassport(
        event: SportEvent,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        sourceIntegrity: SourceIntegrityResult,
        counterReview: CounterReviewAssessment
    ) {
        if (passportExportInProgress) {
            Toast.makeText(this, "Паспорт уже создается", Toast.LENGTH_SHORT).show()
            return
        }

        passportExportInProgress = true
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = state.savedDecision(event.id),
            evidence = evidence,
            timeline = timeline,
            sourceIntegrity = sourceIntegrity,
            counterReview = counterReview,
            decisionSnapshot = state.decisionSnapshot(event.id),
            postEventReview = state.postEventReview(event.id)
        )
        Toast.makeText(this, "Создаем паспорт события…", Toast.LENGTH_SHORT).show()

        passportExecutor.execute {
            runCatching {
                val file = AnalysisPassportExporter(applicationContext).export(snapshot)
                AnalysisImageProvider.uriFor(applicationContext, file)
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Паспорт события: ${snapshot.event.match}"
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            AnalysisPassportFactory.shareText(snapshot)
                        )
                        clipData = ClipData.newRawUri("Паспорт события", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться паспортом"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать паспорт",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun pauseLockedCard(): LinearLayout {
        return card().apply {
            background = rounded(AppColors.dangerSoft, 8, AppColors.danger, 1)
            addView(label("РЕЖИМ ТИШИНЫ", AppColors.danger, Color.WHITE))
            addView(
                text("Анализ поставлен на паузу", 22f, AppColors.danger, Typeface.BOLD),
                matchWrap(top = 12)
            )
            addView(
                text(
                    "Шкалы и журнал решений недоступны до ${formatDateTime(state.pauseUntil())}. Сохраненные события останутся на месте.",
                    14f,
                    AppColors.ink
                ),
                matchWrap(top = 7)
            )
            addView(
                outlineButton("Открыть правила 18+", AppColors.danger) { selectTab(4) },
                matchWrap(top = 14)
            )
        }
    }

    private fun renderMarkets() {
        val event = catalogEvent(state.selectedEventId)
        val now = System.currentTimeMillis()
        val assessment = state.assessment(event)
        val evidence = state.evidence(event)
        val timeline = state.evidenceTimeline(
            event.id,
            now
        )
        val lens = MarketLensEngine.evaluate(
            sport = event.sport,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        if (
            lens.item(activeMarketLensKind)
                ?.status ==
            MarketLensStatus.NOT_APPLICABLE
        ) {
            activeMarketLensKind = lens.items
                .firstOrNull {
                    it.status !=
                        MarketLensStatus.NOT_APPLICABLE
                }
                ?.guide
                ?.kind
                ?: activeMarketLensKind
            state.selectedMarketKind =
                activeMarketLensKind
        }
        content.addView(
            sectionTitle(
                "Чек-листы рынков",
                "Что проверить для каждого типа рынка. Без прогноза, расчёта выгоды и совета сделать ставку."
            )
        )
        content.addView(
            marketLensPanel(
                event = event,
                lens = lens,
                now = now
            ),
            matchWrap(top = 12)
        )
        content.addView(
            sectionTitle(
                "Механика рынков",
                "Для каждого шаблона сохранены проверка и отдельный стоп-сигнал."
            ),
            matchWrap(top = 22)
        )
        lens.items.forEachIndexed { index, item ->
            content.addView(
                marketGuideCard(
                    index = index,
                    item = item,
                    event = event
                ),
                matchWrap(top = 12)
            )
        }
    }

    private fun marketLensPanel(
        event: SportEvent,
        lens: MarketLensResult,
        now: Long
    ): LinearLayout {
        val panel = card()
        panel.addView(
            label(
                "БЕЗ КОЭФФИЦИЕНТОВ",
                AppColors.signalSoft,
                AppColors.signal
            )
        )
        panel.addView(
            text(
                event.match,
                21f,
                AppColors.ink,
                Typeface.BOLD
            ),
            matchWrap(top = 10)
        )
        panel.addView(
            text(
                "${event.sport} • ${event.tournament}",
                13f,
                AppColors.muted,
                Typeface.BOLD
            ),
            matchWrap(top = 4)
        )
        panel.addView(
            text(
                marketLensSummary(lens),
                13f,
                AppColors.signal,
                Typeface.BOLD
            ).apply {
                background = rounded(
                    AppColors.signalSoft,
                    8
                )
                setPadding(
                    dp(12),
                    dp(10),
                    dp(12),
                    dp(10)
                )
            },
            matchWrap(top = 11)
        )

        val chart = MarketLensView(this)
        panel.addView(
            chart,
            matchFixed(300, top = 8)
        )
        val selectors =
            linkedMapOf<MarketKind, TextView>()
        val selectorRow = AdaptiveWrapLayout(this).apply {
            tag = AdaptiveGroupTags.MARKET_TEMPLATES
            lineSpacingPx = dp(6)
            lens.items.forEachIndexed { index, item ->
                val selector = text(
                    item.guide.kind.shortTitle,
                    12f,
                    AppColors.ink,
                    Typeface.BOLD
                ).apply {
                    gravity = Gravity.CENTER
                    minWidth = dp(58)
                    minHeight = dp(48)
                    setPadding(
                        dp(10),
                        dp(7),
                        dp(10),
                        dp(7)
                    )
                    applyAccessibleAction(dp(48))
                    contentDescription =
                        "Выбрать ${item.guide.title}"
                }
                selectors[item.guide.kind] = selector
                addView(
                    selector,
                    wrapWrap(
                        right = if (
                            index ==
                            lens.items.lastIndex
                        ) {
                            0
                        } else {
                            6
                        }
                    )
                )
            }
        }
        panel.addView(
            selectorRow,
            matchWrap(top = 5)
        )
        panel.addView(
            divider(),
            matchFixed(1, top = 12, bottom = 13)
        )

        val detailTitle = text(
            "",
            20f,
            AppColors.ink,
            Typeface.BOLD
        )
        val detailBadge = label(
            "",
            AppColors.warningSoft,
            AppColors.warning
        )
        panel.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(detailTitle, matchWrap())
                addView(detailBadge, matchWrap(top = 7))
            }
        )
        val metric = text(
            "",
            17f,
            AppColors.ink,
            Typeface.BOLD
        )
        val explanation = text(
            "",
            14f,
            AppColors.ink
        )
        val stopSignal = text(
            "",
            13f,
            AppColors.danger,
            Typeface.BOLD
        ).apply {
            background = rounded(
                AppColors.dangerSoft,
                8
            )
            setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
            )
        }
        var selectedItem = lens.item(
            activeMarketLensKind
        ) ?: lens.items.first()
        val action = outlineButton(
            "",
            AppColors.signal
        ) {
            selectedItem.nextCheck?.factor?.let(
                ::openPulseFactor
            )
        }.apply {
            setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(9)
            )
        }
        panel.addView(metric, matchWrap(top = 9))
        panel.addView(explanation, matchWrap(top = 7))
        panel.addView(stopSignal, matchWrap(top = 10))
        panel.addView(action, matchWrap(top = 12))
        panel.addView(
            text(
                "Заполненный чек-лист не показывает вероятность, ожидаемую выгоду или преимущество над линией букмекера.",
                12f,
                AppColors.muted
            ),
            matchWrap(top = 10)
        )

        lateinit var refresh: () -> Unit
        refresh = {
            selectedItem = lens.item(
                activeMarketLensKind
            ) ?: lens.items.first()
            chart.setLens(
                lens,
                selectedItem.guide.kind
            )
            selectors.forEach { (kind, view) ->
                val item = requireNotNull(
                    lens.item(kind)
                )
                val selected =
                    kind == selectedItem.guide.kind
                val tone = marketLensTone(item.status)
                view.setTextColor(
                    if (selected) {
                        Color.WHITE
                    } else {
                        tone.foreground
                    }
                )
                view.background = rippleRounded(
                    if (selected) {
                        tone.foreground
                    } else {
                        AppColors.surface
                    },
                    8,
                    tone.foreground,
                    1
                )
                view.isSelected = selected
                view.contentDescription = if (selected) {
                    "${item.guide.title}, выбрано"
                } else {
                    "Выбрать ${item.guide.title}"
                }
            }
            val tone = marketLensTone(
                selectedItem.status
            )
            detailTitle.text = selectedItem.guide.title
            detailBadge.text = marketLensStatusTitle(
                selectedItem.status
            )
            detailBadge.setTextColor(tone.foreground)
            detailBadge.background = rounded(
                tone.background,
                14
            )
            metric.text = marketLensMetric(
                selectedItem,
                event
            )
            metric.setTextColor(tone.foreground)
            explanation.text = marketLensExplanation(
                selectedItem,
                event,
                now
            )
            if (
                selectedItem.status ==
                MarketLensStatus.NOT_APPLICABLE
            ) {
                stopSignal.visibility = View.GONE
            } else {
                stopSignal.visibility = View.VISIBLE
                stopSignal.text = getString(
                    R.string.market_lens_stop_signal,
                    selectedItem.guide.stopSignal
                )
            }
            val nextCheck = selectedItem.nextCheck
            if (nextCheck == null) {
                action.visibility = View.GONE
            } else {
                action.visibility = View.VISIBLE
                action.text = marketLensAction(
                    selectedItem,
                    nextCheck.factor
                )
                action.setTextColor(tone.foreground)
                action.background = rippleRounded(
                    AppColors.surface,
                    8,
                    tone.foreground,
                    1
                )
                action.contentDescription =
                    "${action.text}. Открыть фактор в анализе."
            }
        }
        chart.setOnMarketSelectedListener { kind ->
            activeMarketLensKind = kind
            state.selectedMarketKind = kind
            refresh()
        }
        selectors.forEach { (kind, view) ->
            view.setOnClickListener {
                activeMarketLensKind = kind
                state.selectedMarketKind = kind
                refresh()
            }
        }
        refresh()
        return panel
    }

    private fun marketLeveragePanel(
        leverage: MarketLeverageResult,
        now: Long
    ): LinearLayout {
        val tone = if (
            leverage.mode ==
            MarketLeverageMode.MAINTAIN
        ) {
            Tone(
                AppColors.accentDark,
                AppColors.accentSoft
            )
        } else {
            Tone(
                AppColors.signal,
                AppColors.signalSoft
            )
        }
        val title = if (
            leverage.mode ==
            MarketLeverageMode.MAINTAIN
        ) {
            "Удержать: ${leverage.factor.title}"
        } else {
            "Сначала: ${leverage.factor.title}"
        }
        val metric = if (
            leverage.mode ==
            MarketLeverageMode.MAINTAIN
        ) {
            "${leverage.affectedMarketCount} " +
                marketWord(
                    leverage.affectedMarketCount
                ) +
                " зависят от свежести"
        } else {
            "Охват ${leverage.affectedMarketCount}/" +
                "${leverage.baseline.applicableCount} • " +
                "+${leverage.conditionGain} " +
                conditionWord(leverage.conditionGain) +
                " • переходов ${leverage.statusTransitionCount}"
        }
        val chart = MarketLeverageView(this).apply {
            setLeverage(leverage)
        }
        val actionTitle = when {
            leverage.mode ==
                MarketLeverageMode.MAINTAIN ->
                "Обновить «${leverage.factor.title}» в анализе"
            leverage.requiresNewData ->
                "Собрать факты по «${leverage.factor.title}»"
            else ->
                "Подтвердить «${leverage.factor.title}» в анализе"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                tone.background,
                8,
                tone.foreground,
                1
            )
            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )
            addView(
                label(
                    "ФАКТОР-РЫЧАГ",
                    tone.foreground,
                    Color.WHITE
                )
            )
            addView(
                text(
                    title,
                    19f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 9)
            )
            addView(
                text(
                    metric,
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 3)
            )
            addView(
                chart,
                matchFixed(96, top = 7)
            )
            addView(
                text(
                    marketLeverageExplanation(
                        leverage,
                        now
                    ),
                    13f,
                    AppColors.ink
                ),
                matchWrap(top = 6)
            )
            addView(
                outlineButton(
                    actionTitle,
                    tone.foreground
                ) {
                    openPulseFactor(
                        leverage.factor
                    )
                }.apply {
                    contentDescription =
                        "$actionTitle. Открыть фактор в анализе."
                },
                matchWrap(top = 10)
            )
            addView(
                text(
                    "Приоритет проверки не является приоритетом рынка и не оценивает исход.",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 8)
            )
        }
    }

    private fun marketLeverageExplanation(
        leverage: MarketLeverageResult,
        now: Long
    ): String {
        if (
            leverage.mode ==
            MarketLeverageMode.MAINTAIN
        ) {
            val remaining = (
                requireNotNull(
                    leverage.nextTransitionAt
                ) - now
                ).coerceAtLeast(0L)
            return "Все применимые чек-листы заполнены. " +
                "Ближайшая общая точка обновления — " +
                "«${leverage.factor.title}» через " +
                "${FreshnessFormatter.duration(remaining)}. " +
                "Это обслуживание свежести, а не новый сигнал."
        }

        val premise = when {
            leverage.requiresNewData &&
                leverage.requiresFreshQuorum ->
                "Если по фактору «${leverage.factor.title}» " +
                    "появится проверяемый факт и свежая независимая сверка"
            leverage.requiresNewData ->
                "Если по фактору «${leverage.factor.title}» " +
                    "появится проверяемый источник"
            else ->
                "Если «${leverage.factor.title}» " +
                    "получит свежую независимую сверку"
        }
        val impact = if (
            leverage.statusTransitionCount > 0
        ) {
            "$premise, контрсценарий даст " +
                "${leverage.statusTransitionCount} " +
                statusTransitionWord(
                    leverage.statusTransitionCount
                ) +
                ": из стопа ${leverage.reopenedCount}, " +
                "до полного покрытия ${leverage.coveredCount}."
        } else {
            val conditionVerb = if (
                leverage.conditionGain == 1
            ) {
                "закроется"
            } else {
                "закроются"
            }
            "$premise, статус пока не изменится, " +
                "но $conditionVerb ${leverage.conditionGain} " +
                conditionWord(
                    leverage.conditionGain
                ) +
                " в ${leverage.affectedMarketCount} " +
                marketWord(
                    leverage.affectedMarketCount
                ) +
                "."
        }
        return "$impact Сохраненная карта не меняется."
    }

    private fun marketWord(
        count: Int
    ): String {
        return russianPlural(
            count,
            "шаблон",
            "шаблона",
            "шаблонов"
        )
    }

    private fun conditionWord(
        count: Int
    ): String {
        return russianPlural(
            count,
            "условие",
            "условия",
            "условий"
        )
    }

    private fun statusTransitionWord(
        count: Int
    ): String {
        return russianPlural(
            count,
            "переход статуса",
            "перехода статуса",
            "переходов статуса"
        )
    }

    private fun russianPlural(
        count: Int,
        one: String,
        few: String,
        many: String
    ): String {
        val lastTwo = count % 100
        val last = count % 10
        return when {
            lastTwo in 11..14 -> many
            last == 1 -> one
            last in 2..4 -> few
            else -> many
        }
    }

    private fun marketGuideCard(
        index: Int,
        item: MarketLensItem,
        event: SportEvent
    ): LinearLayout {
        val tone = marketLensTone(item.status)
        return card().apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(
                            (index + 1).toString(),
                            fixedControlTextSize(15f),
                            Color.WHITE,
                            Typeface.BOLD
                        ).apply {
                            gravity = Gravity.CENTER
                            background = rounded(
                                AppColors.signal,
                                18
                            )
                        },
                        LinearLayout.LayoutParams(
                            dp(36),
                            dp(36)
                        ).apply {
                            rightMargin = dp(11)
                        }
                    )
                    addView(
                        text(
                            item.guide.title,
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                }
            )
            addView(
                label(
                    marketLensStatusTitle(item.status),
                    tone.background,
                    tone.foreground
                ),
                matchWrap(top = 9)
            )
            addView(
                text(
                    marketLensMetric(item, event),
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 7)
            )
            addView(
                text(
                    marketLensFactorSummary(item),
                    12f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    item.guide.summary,
                    14f,
                    AppColors.muted
                ),
                matchWrap(top = 10)
            )
            addView(
                divider(),
                matchFixed(
                    1,
                    top = 12,
                    bottom = 11
                )
            )
            addView(
                text(
                    "ПРОВЕРИТЬ",
                    11f,
                    AppColors.accent,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    item.guide.check,
                    14f,
                    AppColors.ink
                ),
                matchWrap(top = 3)
            )
            addView(
                text(
                    "СТОП-СИГНАЛ",
                    11f,
                    AppColors.danger,
                    Typeface.BOLD
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    item.guide.stopSignal,
                    14f,
                    AppColors.ink
                ),
                matchWrap(top = 3)
            )
        }
    }

    private fun marketLensSummary(
        lens: MarketLensResult
    ): String {
        val notApplicable =
            lens.items.size - lens.applicableCount
        return "Готово ${lens.coveredCount} • " +
            "нужна сверка ${lens.checkCount} • " +
            "есть пробелы ${lens.closedCount} • " +
            "не применяется $notApplicable"
    }

    private fun marketLensStatusTitle(
        status: MarketLensStatus
    ): String {
        return when (status) {
            MarketLensStatus.NOT_APPLICABLE ->
                "НЕ ДЛЯ ЭТОГО СПОРТА"
            MarketLensStatus.CLOSED ->
                "ЕСТЬ КРИТИЧЕСКИЙ ПРОБЕЛ"
            MarketLensStatus.CHECK ->
                "НУЖНА СВЕРКА"
            MarketLensStatus.COVERED ->
                "ЧЕК-ЛИСТ ЗАПОЛНЕН"
        }
    }

    private fun marketLensMetric(
        item: MarketLensItem,
        event: SportEvent
    ): String {
        return if (
            item.status ==
            MarketLensStatus.NOT_APPLICABLE
        ) {
            "Шаблон отключен для «${event.sport}»"
        } else {
            "Выполнено ${item.metConditions} из ${item.conditionCount} проверок"
        }
    }

    private fun marketLensExplanation(
        item: MarketLensItem,
        event: SportEvent,
        now: Long
    ): String {
        if (
            item.status ==
            MarketLensStatus.NOT_APPLICABLE
        ) {
            return "Для вида спорта «${event.sport}» этот " +
                "шаблон по умолчанию не применяется. " +
                "Правила турнира имеют приоритет."
        }
        if (item.status == MarketLensStatus.CLOSED) {
            val blockers = item.blockingFactors.joinToString(
                ", "
            ) { factor ->
                factor.title
            }
            return "Нет подтверждений по критическим факторам: $blockers. " +
                "Сначала проверьте их источники."
        }
        if (item.status == MarketLensStatus.COVERED) {
            return "Обязательные факторы подтверждены, критические " +
                "сверены по свежим независимым источникам. Это завершение " +
                "чек-листа, не рекомендация ставки."
        }
        val next = item.nextCheck
            ?: return "Необходима дополнительная проверка данных."
        val coverage = item.factor(next.factor)
        return when (next.reason) {
            MarketNextCheckReason.BLOCKER ->
                "По критическому фактору «${next.factor.title}» " +
                    "нет подтверждённого источника."
            MarketNextCheckReason.QUORUM ->
                "Для критического фактора " +
                    "«${next.factor.title}» нужен свежий " +
                    "два независимых источника, сейчас «${coverage.evidence.title}»."
            MarketNextCheckReason.EVIDENCE_GAP ->
                "По фактору «${next.factor.title}» не хватает " +
                    "проверяемого источника для завершения чек-листа."
            MarketNextCheckReason.FRESHNESS ->
                "Кворум «${next.factor.title}» скоро " +
                    "потеряет уровень: осталось ${
                        FreshnessFormatter.duration(
                            (
                                coverage.freshness
                                    .nextTransitionAt
                                    ?: now
                                ) - now
                        )
                    }."
            MarketNextCheckReason.MAINTENANCE ->
                "Условия покрыты; ближайшее обновление " +
                    "нужно фактору «${next.factor.title}»."
        }
    }

    private fun marketLensAction(
        item: MarketLensItem,
        factor: SignalFactor
    ): String {
        return when (item.status) {
            MarketLensStatus.CLOSED ->
                "Открыть блокер «${factor.title}»"
            MarketLensStatus.CHECK ->
                "Проверить «${factor.title}» в анализе"
            MarketLensStatus.COVERED ->
                "Удержать свежесть «${factor.title}»"
            MarketLensStatus.NOT_APPLICABLE ->
                "Открыть анализ"
        }
    }

    private fun marketLensFactorSummary(
        item: MarketLensItem
    ): String {
        val critical = item.definition.criticalFactors
            .sortedBy(SignalFactor::ordinal)
            .joinToString(", ") { it.title }
        val additional = item.definition.requiredFactors
            .filter {
                it !in item.definition.criticalFactors
            }
            .joinToString(", ") { it.title }
        return buildString {
            append("Критические: ")
            append(critical)
            if (additional.isNotBlank()) {
                append(" • дополнительно: ")
                append(additional)
            }
        }
    }

    private fun marketLensTone(
        status: MarketLensStatus
    ): Tone {
        return when (status) {
            MarketLensStatus.NOT_APPLICABLE ->
                Tone(AppColors.muted, AppColors.background)
            MarketLensStatus.CLOSED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            MarketLensStatus.CHECK ->
                Tone(AppColors.warning, AppColors.warningSoft)
            MarketLensStatus.COVERED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun guideNavigatorMission(
        event: SportEvent?
    ): GuideNavigatorMission {
        val now = System.currentTimeMillis()
        val draft = event?.let {
            state.decisionDeskDraft(it.id)
        }
        val recordedFactCount = event?.let { selected ->
            SignalFactor.values().count { factor ->
                state.factReceipt(selected.id, factor).integrity ==
                    FactReceiptIntegrity.VALID
            }
        } ?: 0
        val nextFactor = event?.let { selected ->
            PlainAnalyticsEngine.evaluate(
                assessment = state.assessment(selected),
                evidence = state.evidence(selected),
                timeline = state.evidenceTimeline(
                    eventId = selected.id,
                    now = now
                ),
                now = now
            ).actionFactor
        } ?: SignalFactor.SOURCES
        val snapshot = event?.let {
            state.decisionSnapshot(it.id)
        }
        val reviewFinalized = event?.let {
            state.postEventReview(it.id)?.isFinalized == true
        } == true
        return GuideNavigatorEngine.evaluate(
            GuideNavigatorInput(
                hasSelectedEvent = event != null,
                missingPlanFields = draft?.missingFields
                    ?: DecisionDeskField.values().toList(),
                recordedFactCount = recordedFactCount,
                hasDecision = snapshot != null,
                reviewFinalized = reviewFinalized,
                nextFactor = nextFactor
            )
        )
    }

    private fun guideNavigatorPanel(
        event: SportEvent?,
        mission: GuideNavigatorMission
    ): LinearLayout {
        val tone = when (mission.stage) {
            GuideNavigatorStage.EVENT,
            GuideNavigatorStage.PLAN ->
                Tone(AppColors.signal, AppColors.signalSoft)
            GuideNavigatorStage.FACT,
            GuideNavigatorStage.REVIEW ->
                Tone(AppColors.warning, AppColors.warningSoft)
            GuideNavigatorStage.DECISION,
            GuideNavigatorStage.COMPLETE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
        val stackProgressHeader =
            resources.configuration.screenWidthDp < 380 ||
                effectiveFontScale() >= 1.3f
        return card(padding = 12).apply {
            addView(
                imageFrame().apply {
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(
                                R.drawable.guide_navigator_v3150
                            )
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            contentDescription =
                                "Навигатор проверки: пять этапов от выбора события до постсобытийного разбора; текущий этап — ${mission.title}"
                        },
                        frameMatch()
                    )
                    addView(
                        View(this@MainActivity).apply {
                            background = gradientScrim(compact = true)
                        },
                        frameMatch()
                    )
                    addView(
                        label(
                            mission.badge,
                            tone.foreground,
                            Color.WHITE
                        ),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.START
                        ).apply {
                            leftMargin = dp(12)
                            topMargin = dp(12)
                        }
                    )
                    addView(
                        text(
                            mission.title,
                            19f,
                            Color.WHITE,
                            Typeface.BOLD
                        ).apply {
                            maxLines = 2
                            setTextSize(
                                TypedValue.COMPLEX_UNIT_PX,
                                19f *
                                    resources.displayMetrics.density *
                                    min(effectiveFontScale(), 1.35f)
                            )
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM
                        ).apply {
                            leftMargin = dp(12)
                            rightMargin = dp(12)
                            bottomMargin = dp(11)
                        }
                    )
                },
                matchFixed(
                    if (effectiveFontScale() >= 1.8f) 182 else 150
                )
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stackProgressHeader) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stackProgressHeader) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        text(
                            "ВАШ МАРШРУТ",
                            10.5f,
                            AppColors.muted,
                            Typeface.BOLD
                        ),
                        if (stackProgressHeader) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                    )
                    val progressValue = text(
                        mission.progressDescription,
                        11.5f,
                        tone.foreground,
                        Typeface.BOLD
                    )
                    if (stackProgressHeader) {
                        addView(progressValue, matchWrap(top = 2))
                    } else {
                        addView(progressValue)
                    }
                },
                matchWrap(top = 13)
            )
            addView(
                horizontalProgress().apply {
                    max = GuideNavigatorMission.TOTAL_STEPS
                    progress = mission.completedSteps
                    progressTintList =
                        ColorStateList.valueOf(tone.foreground)
                    contentDescription = mission.progressDescription
                },
                matchFixed(7, top = 6)
            )
            addView(
                text(
                    if (event == null) {
                        "СОБЫТИЕ НЕ ВЫБРАНО"
                    } else {
                        "СОБЫТИЕ • ${event.match}"
                    },
                    11.5f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 13)
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(tone.background, 7)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    addView(
                        text(
                            "ПОЧЕМУ СЕЙЧАС",
                            10.5f,
                            tone.foreground,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            mission.explanation,
                            13.5f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 4)
                    )
                },
                matchWrap(top = 8)
            )
            addView(
                commandButton(
                    mission.actionTitle,
                    tone.foreground
                ) {
                    openGuideNavigatorMission(mission)
                },
                matchWrap(top = 12)
            )
            addView(
                text(
                    "Шаг определяется только локальными записями выбранного события. Он не является прогнозом или советом сделать ставку.",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 9)
            )
        }
    }

    private fun openGuideNavigatorMission(
        mission: GuideNavigatorMission
    ) {
        when (mission.stage) {
            GuideNavigatorStage.EVENT -> selectTab(0)
            GuideNavigatorStage.PLAN -> {
                activeDecisionDeskSection =
                    DecisionDeskSection.DECISION
                decisionDeskWorkspaceExpanded = true
                pendingDecisionDeskField = mission.planField
                selectTab(1, scrollToContent = false)
            }
            GuideNavigatorStage.FACT ->
                mission.factor?.let(::openPulseFactor)
            GuideNavigatorStage.DECISION,
            GuideNavigatorStage.REVIEW -> {
                activeDecisionDeskSection =
                    DecisionDeskSection.DECISION
                decisionDeskWorkspaceExpanded = false
                pendingDecisionDeskField = null
                activePulseWorkspaceMode = PulseWorkspaceMode.LAB
                activePulseLabSection = PulseLabSection.DECISION
                state.selectedPulseWorkspaceMode =
                    PulseWorkspaceMode.LAB
                pendingPulseFactor = null
                pendingPulseStoryAction = if (
                    mission.stage == GuideNavigatorStage.REVIEW
                ) {
                    EventStoryAction.OPEN_REVIEW
                } else {
                    EventStoryAction.OPEN_DECISION
                }
                selectTab(1, scrollToContent = false)
            }
            GuideNavigatorStage.COMPLETE -> {
                activeDecisionDeskSection =
                    DecisionDeskSection.PROFILE
                decisionDeskWorkspaceExpanded = false
                pendingDecisionDeskField = null
                selectTab(1, scrollToContent = false)
                decisionDeskSectionAnchor?.let {
                    scrollToAppView(it, topOffsetDp = 10)
                }
            }
        }
    }

    private fun guideReferencePanel(): LinearLayout {
        val stack =
            resources.configuration.screenWidthDp < 380 ||
                effectiveFontScale() >= 1.3f
        return card().apply {
            addView(
                text(
                    "Справка по запросу",
                    20f,
                    AppColors.ink,
                    Typeface.BOLD
                )
            )
            addView(
                text(
                    "Короткий маршрут, подробное обучение и словарь открываются отдельно, не прерывая текущий шаг.",
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 5)
            )
            addView(
                commandButton(
                    "Показать обучение по шагам",
                    AppColors.signal
                ) {
                    showProductTour()
                },
                matchWrap(top = 12)
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = if (stack) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    addView(
                        outlineButton(
                            "Быстрый старт",
                            AppColors.signal
                        ) {
                            showGuideReferenceDialog { close ->
                                quickStartGuidePanel {
                                    close()
                                    showProductTour()
                                }
                            }
                        },
                        if (stack) {
                            matchWrap()
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { marginEnd = dp(4) }
                        }
                    )
                    addView(
                        outlineButton(
                            "Словарь терминов",
                            AppColors.signal
                        ) {
                            showGuideReferenceDialog {
                                analyticsDictionaryPanel()
                            }
                        },
                        if (stack) {
                            matchWrap(top = 8)
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { marginStart = dp(4) }
                        }
                    )
                },
                matchWrap(top = 8)
            )
        }
    }

    private fun showGuideReferenceDialog(
        contentFactory: (() -> Unit) -> View
    ) {
        lateinit var dialog: AlertDialog
        val closeDialog = { dialog.dismiss() }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
            addView(contentFactory(closeDialog), matchWrap())
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
            addView(
                outlineButton("Закрыть", AppColors.muted) {
                    dialog.dismiss()
                },
                matchWrap()
            )
        }
        val height = min(
            if (effectiveFontScale() >= 1.8f) 720 else 620,
            resources.configuration.screenHeightDp - 72
        ).coerceAtLeast(360)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(height)
            )
            addView(
                ScrollView(this@MainActivity).apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = true
                    addView(body, matchWrap())
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(actions, matchWrap())
        }
        dialog = AlertDialog.Builder(this)
            .setView(shell)
            .create()
        dialog.show()
    }

    private fun quickStartGuidePanel(
        onShowTour: () -> Unit
    ): LinearLayout {
        return card().apply {
            addView(text("Быстрый старт", 20f, AppColors.ink, Typeface.BOLD))
            addView(
                guideStepRow(
                    number = "1",
                    title = "Выберите событие",
                    body = "Откройте матч в «Матчах». Вся дальнейшая проверка будет привязана только к нему."
                ),
                matchWrap(top = 12)
            )
            addView(
                guideStepRow(
                    number = "2",
                    title = "Прочитайте табло",
                    body = "В «Штабе» сначала прочитайте статус, причину и одно следующее действие."
                ),
                matchWrap(top = 10)
            )
            addView(
                guideStepRow(
                    number = "3",
                    title = "Откройте рабочую форму",
                    body = "Ответьте на три вопроса: идея матча, альтернативный сценарий и наблюдаемая стоп-линия."
                ),
                matchWrap(top = 10)
            )
            addView(
                guideStepRow(
                    number = "4",
                    title = "Проверьте один пробел",
                    body = "Запишите минимум один проверяемый факт и его первичный источник."
                ),
                matchWrap(top = 10)
            )
            addView(
                guideStepRow(
                    number = "5",
                    title = "Проверьте и зафиксируйте",
                    body = "Выберите итог и отдельно сохраните снимок до матча. После события сравните те же пять факторов."
                ),
                matchWrap(top = 10)
            )
            addView(
                commandButton(
                    "Показать обучение по шагам",
                    AppColors.signal
                ) {
                    onShowTour()
                },
                matchWrap(top = 14)
            )
        }
    }

    private fun guideStepRow(
        number: String,
        title: String,
        body: String
    ): LinearLayout {
        val stack =
            resources.configuration.screenWidthDp < 380 ||
                effectiveFontScale() >= 1.3f
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val numberView = text(
                number,
                fixedControlTextSize(16f),
                Color.WHITE,
                Typeface.BOLD
            ).apply {
                gravity = Gravity.CENTER
                background = rounded(AppColors.accent, 18)
            }
            if (stack) {
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            numberView,
                            LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                                rightMargin = dp(11)
                            }
                        )
                        addView(
                            text(title, 16f, AppColors.ink, Typeface.BOLD),
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        )
                    },
                    matchWrap()
                )
                addView(
                    text(body, 13.5f, AppColors.muted),
                    matchWrap(top = 7)
                )
            } else {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                addView(
                    numberView,
                    LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                        rightMargin = dp(11)
                    }
                )
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            text(
                                title,
                                16f,
                                AppColors.ink,
                                Typeface.BOLD
                            )
                        )
                        addView(
                            text(body, 13.5f, AppColors.muted),
                            matchWrap(top = 4)
                        )
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }
        }
    }

    private fun analyticsDictionaryPanel(): LinearLayout {
        return card().apply {
            addView(text("Словарь экрана", 20f, AppColors.ink, Typeface.BOLD))
            addView(
                dictionaryRow(
                    "Штаб решения",
                    "Сначала показывает компактное табло: статус, заполненность замысла, причину и одно действие. Полная форма раскрывается только для редактирования; «История» и «Профиль» оценивают процесс без коэффициентов, сумм и доходности."
                ),
                matchWrap(top = 11)
            )
            addView(
                dictionaryRow(
                    "Глубина разбора",
                    "«Коротко» оставляет итог, главный пробел и один следующий шаг. «Подробно» открывает навигатор маршрута, фактов и решения. Переключение не меняет оценки, источники или журнал."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Развилка матча",
                    "Две равноправные версии и заранее заданная стоп-линия. После заполнения приложение показывает один различающий фактор, но не выбирает удобный сценарий и не прогнозирует исход."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Карта данных",
                    "Пять строк формы, состава, нагрузки, контекста и источников. Статус показывает отсутствие факта, один источник, независимую сверку или истёкший срок. Выделение означает только следующий шаг проверки, а не прогноз."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Фокус / Инструменты",
                    "«Фокус» оставляет один следующий шаг и компактные события. «Инструменты» открывают весь исследовательский набор ленты."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Поиск событий",
                    "Локально ищет по команде, турниру, региону и виду спорта. Кириллица и латиница сопоставляются детерминированно. Метка «Совпадение» объясняет, по какому полю прошёл каждый токен; порядок каталога не меняется и скрытого рейтинга нет."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Ручная шкала 0–100",
                    "Ваша оценка содержания пяти заметок. Статус проверки зависит от источников и свежести; число не является вероятностью победы."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Прогноз",
                    "Приложение не вычисляет вероятность исхода, ожидаемую выгоду или размер ставки. Для прогноза нужны обученная модель, проверка на прошлых матчах и отдельная оценка калибровки; этих расчётов здесь нет."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Протокол проверки",
                    "Три действия для выбранного фактора: найти первичный факт, независимо его сверить и заранее определить стоп-правило."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Факт-квитанция",
                    "Один локальный тезис и его происхождение. Форма ведёт по двум обязательным шагам, а второй источник, связь и полноту показывает только по запросу. Квитанция сама обновляет уровень выбранного фактора и получает SHA-256-метку."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Реестр фактов",
                    "Read-only сводка пяти квитанций. Считает только действующие кворумы и ставит первым повреждение, конфликт, истёкший срок или пробел. Общая SHA-256-метка меняется вместе с записью или этапом её свежести."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Квитанция решения",
                    "Двухэтапная запись в журнал: выбор только показывает точное последствие и ничего не сохраняет. Отдельная команда повторно проверяет окно решения, Контрракурс, бюджет внимания, Контур дистанции и целостность журнала, затем создаёт предстартовый снимок."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Срок факта",
                    "Период до снижения текущего уровня: форма 72 ч, состав 6 ч, нагрузка 24 ч, контекст 48 ч, источники 12 ч. Кворум сначала ослабевает до одного источника, затем истекает."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Единый срез",
                    "Проверяет не возраст каждого факта, а разброс времени между действующими квитанциями. Единое окно равно 25% срока самого быстрого активного фактора; при большом сдвиге приложение называет старейший."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Кросс-эхо",
                    "Сравнение происхождений между факторами. Повтор одного домена или нормализованного названия показывает общую зависимость, но сам по себе не опровергает факт и не снижает оценку."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Факт-маршрут",
                    "Замкнутый сценарий реестра: открывает вычисленный следующий фактор и возвращает к пересчитанным пяти строкам после сохранения, удаления или команды «К реестру»."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Подтверждение",
                    "Нет источника, один источник или два независимых источника."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Свежесть",
                    "Сколько времени прошло с проверки. Составы устаревают быстрее формы."
                ),
                matchWrap(top = 9)
            )
            addView(
                dictionaryRow(
                    "Стоп-сигнал",
                    "Критического факта нет или он устарел. Вывод лучше отложить."
                ),
                matchWrap(top = 9)
            )
        }
    }

    private fun dictionaryRow(
        title: String,
        body: String
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(AppColors.background, 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(text(title, 14f, AppColors.ink, Typeface.BOLD))
            addView(text(body, 13f, AppColors.muted), matchWrap(top = 3))
        }
    }

    private fun showAgeGate() {
        lateinit var dialog: AlertDialog
        val imageHeight = when {
            effectiveFontScale() >= 1.8f -> 72
            effectiveFontScale() >= 1.3f -> 84
            else -> 96
        }
        val contentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(12))
            addView(
                label(
                    "ТОЛЬКО 18+",
                    AppColors.dangerSoft,
                    AppColors.danger
                )
            )
            addView(
                text(
                    "Нет 18 лет — выберите «Выйти».",
                    13.5f,
                    AppColors.danger,
                    Typeface.BOLD
                ).apply {
                    background = rounded(AppColors.dangerSoft, 8)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                },
                matchWrap(top = 10)
            )
            addView(
                imageFrame().apply {
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.age_gate_v3100)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            contentDescription =
                                "Порог 18+: закрытый доступ к инструментам проверки спортивных данных"
                        },
                        frameMatch()
                    )
                },
                matchFixed(imageHeight, top = 10)
            )
            addView(
                text(
                    "Подтвердите возраст",
                    25f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                matchWrap(top = 12)
            )
            addView(
                text(
                    "Спорт Пульс — информационное приложение о спортивных событиях и проверке данных. Оно не принимает ставки, не показывает коэффициенты и не обещает выигрыш.",
                    15f,
                    AppColors.ink
                ),
                matchWrap(top = 9)
            )
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), dp(18))
            addView(
                commandButton(
                    "Мне есть 18 лет",
                    AppColors.accent
                ) {
                    state.hasConfirmedAge = true
                    appShell.visibility = View.VISIBLE
                    dialog.dismiss()
                    refreshApiFootball(force = false)
                    if (!state.hasSeenProductTour) {
                        showProductTour()
                    }
                },
                matchWrap(top = 12)
            )
            addView(
                outlineButton("Выйти", AppColors.muted) {
                    dialog.dismiss()
                    finishAndRemoveTask()
                },
                matchWrap(top = 8)
            )
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                min(
                    560,
                    resources.configuration.screenHeightDp - 72
                ).coerceAtLeast(360).let(::dp)
            )
            addView(
                ScrollView(this@MainActivity).apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = true
                    addView(contentPanel, matchWrap())
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(actions, matchWrap())
        }
        dialog = AlertDialog.Builder(this)
            .setView(panel)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun showProductTour(stepIndex: Int = 0) {
        val steps = listOf(
            ProductTourStep(
                stage = "СОБЫТИЕ",
                title = "Выберите матч",
                action = "Откройте «Матчи» и выберите событие по времени и турниру.",
                result = "Первая незакрытая проверка.",
                guardrail = "Расписание — отправная точка. Перед решением сверьте официальный источник.",
                nextAction = "Далее: табло"
            ),
            ProductTourStep(
                stage = "ТАБЛО",
                title = "Сначала прочитайте статус",
                action = "В «Штабе» найдите причину статуса и одну синюю команду.",
                result = "Короткий итог или полный аудит.",
                guardrail = "Статус описывает готовность проверки, а не вероятность исхода.",
                nextAction = "Далее: сценарий"
            ),
            ProductTourStep(
                stage = "СЦЕНАРИЙ",
                title = "Разведите варианты A и B",
                action = "Откройте рабочую форму, запишите два сценария и наблюдаемую стоп-линию.",
                result = "Условие, при котором решение останавливается.",
                guardrail = "Незаполненная развилка получает «Стоп»; приложение не выбирает удобный сценарий.",
                nextAction = "Далее: факт"
            ),
            ProductTourStep(
                stage = "ИСТОЧНИК",
                title = "Закройте один пробел",
                action = "Запишите один проверяемый факт и его первичный источник. Второй добавляйте только для независимой сверки.",
                result = "Квитанция происхождения факта.",
                guardrail = "Общее происхождение не создаёт кворум; расхождение остаётся стоп-сигналом.",
                nextAction = "Далее: решение"
            ),
            ProductTourStep(
                stage = "РЕШЕНИЕ",
                title = "Сначала выберите, потом запишите",
                action = "Выберите итог, прочитайте будущую запись и только затем подтвердите фиксацию.",
                result = "Решение, связанное с проверенными фактами.",
                guardrail = "Это контроль качества анализа, а не расчёт вероятности, выгоды или размера ставки.",
                nextAction = "Начать с Матчей"
            )
        )
        val index = stepIndex.coerceIn(steps.indices)
        val step = steps[index]
        val imageHeight = when {
            effectiveFontScale() >= 1.8f -> 72
            effectiveFontScale() >= 1.3f -> 84
            else -> 104
        }

        val contentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(12))
            addView(
                label(
                    "ШАГ ${index + 1} ИЗ ${steps.size}",
                    AppColors.signalSoft,
                    AppColors.signal
                )
            )
            addView(
                productTourProgress(index, steps.size),
                matchFixed(6, top = 10)
            )
            addView(
                imageFrame().apply {
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.product_tour_route_v3110)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            contentDescription =
                                "Маршрут проверки: событие, табло, сценарий, источник и решение"
                        },
                        frameMatch()
                    )
                },
                matchFixed(imageHeight, top = 12)
            )
            addView(
                text(step.stage, 11f, AppColors.signal, Typeface.BOLD),
                matchWrap(top = 14)
            )
            addView(
                text(step.title, 22f, AppColors.ink, Typeface.BOLD),
                matchWrap(top = 4)
            )
            addView(
                text("СЕЙЧАС", 10.5f, AppColors.accentDark, Typeface.BOLD),
                matchWrap(top = 12)
            )
            addView(
                text(step.action, 15f, AppColors.ink),
                matchWrap(top = 4)
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(AppColors.accentSoft, 8)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    addView(
                        text(
                            "РЕЗУЛЬТАТ ШАГА",
                            10.5f,
                            AppColors.accentDark,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            step.result,
                            13.5f,
                            AppColors.accentDark,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 3)
                    )
                },
                matchWrap(top = 12)
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(AppColors.dangerSoft, 8)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    addView(
                        text(
                            "ГРАНИЦА",
                            10.5f,
                            AppColors.danger,
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(step.guardrail, 12.5f, AppColors.ink),
                        matchWrap(top = 3)
                    )
                },
                matchWrap(top = 8)
            )
        }
        lateinit var dialog: AlertDialog
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), dp(18))
        }
        actions.addView(
            commandButton(
                step.nextAction,
                AppColors.accent
            ) {
                dialog.dismiss()
                if (index == steps.lastIndex) {
                    state.hasSeenProductTour = true
                    selectTab(0)
                } else {
                    showProductTour(index + 1)
                }
            },
            matchWrap(top = 16)
        )
        if (index > 0) {
            actions.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        outlineButton("Назад", AppColors.muted) {
                            dialog.dismiss()
                            showProductTour(index - 1)
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginEnd = dp(4)
                        }
                    )
                    addView(
                        outlineButton("Закрыть", AppColors.muted) {
                            state.hasSeenProductTour = true
                            dialog.dismiss()
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginStart = dp(4)
                        }
                    )
                },
                matchWrap(top = 8)
            )
        } else {
            actions.addView(
                outlineButton("Закрыть", AppColors.muted) {
                    state.hasSeenProductTour = true
                    dialog.dismiss()
                },
                matchWrap(top = 8)
            )
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(tourDialogHeightDp())
            )
            addView(
                ScrollView(this@MainActivity).apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = true
                    addView(contentPanel, matchWrap())
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(actions, matchWrap())
        }
        dialog = AlertDialog.Builder(this)
            .setView(panel)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun productTourProgress(
        activeIndex: Int,
        total: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription =
                "Маршрут обучения: шаг ${activeIndex + 1} из $total"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            repeat(total) { position ->
                addView(
                    View(this@MainActivity).apply {
                        background = rounded(
                            when {
                                position < activeIndex -> AppColors.accentDark
                                position == activeIndex -> AppColors.signal
                                else -> AppColors.line
                            },
                            3
                        )
                        importantForAccessibility =
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f
                    ).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                )
            }
        }
    }

    private fun tourDialogHeightDp(): Int {
        val preferred = when {
            resources.configuration.fontScale >= 1.8f -> 720
            resources.configuration.fontScale >= 1.3f -> 660
            else -> 560
        }
        return min(
            preferred,
            resources.configuration.screenHeightDp - 72
        ).coerceAtLeast(320)
    }

    private fun renderGuide() {
        val selectedEvent = catalogEvents.firstOrNull {
            it.id == state.selectedEventId
        }
        val mission = guideNavigatorMission(selectedEvent)
        content.addView(
            sectionTitle(
                "Навигатор проверки",
                "Один следующий шаг по реальному состоянию выбранного события."
            )
        )
        content.addView(
            guideNavigatorPanel(
                event = selectedEvent,
                mission = mission
            ),
            matchWrap(top = 12)
        )
        content.addView(
            guideReferencePanel(),
            matchWrap(top = 12)
        )

        content.addView(
            sectionTitle(
                "Чек-лист события",
                "Отмечайте только то, что действительно проверили."
            ),
            matchWrap(top = 20)
        )

        val completed = state.completedGuideSteps().toMutableSet()
        val progressText = text("", 14f, AppColors.ink, Typeface.BOLD)
        val progressBar = horizontalProgress().apply {
            max = DemoCatalog.guideSteps.size
            progressTintList = ColorStateList.valueOf(AppColors.accent)
        }
        val reset = outlineButton("Сбросить", AppColors.muted) {
            state.clearGuide()
            completed.clear()
        }
        val checkBoxes = mutableListOf<CheckBox>()
        val completion = text("", 14f, AppColors.accentDark, Typeface.BOLD)
        lateinit var refreshProgress: () -> Unit

        val progressPanel = card()
        val stackProgressHeader =
            resources.configuration.fontScale >= 1.3f ||
                resources.configuration.screenWidthDp < 380
        progressPanel.addView(
            LinearLayout(this).apply {
                orientation = if (stackProgressHeader) {
                    LinearLayout.VERTICAL
                } else {
                    LinearLayout.HORIZONTAL
                }
                gravity = if (stackProgressHeader) {
                    Gravity.START
                } else {
                    Gravity.CENTER_VERTICAL
                }
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text("Прогресс проверки", 19f, AppColors.ink, Typeface.BOLD))
                        addView(progressText, matchWrap(top = 2))
                    },
                    if (stackProgressHeader) {
                        matchWrap()
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    }
                )
                addView(
                    reset,
                    if (stackProgressHeader) {
                        matchWrap(top = 10)
                    } else {
                        LinearLayout.LayoutParams(
                            dp(104),
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                )
            }
        )
        progressPanel.addView(progressBar, matchFixed(7, top = 13))
        progressPanel.addView(completion, matchWrap(top = 12))
        progressPanel.addView(
            text(
                "ПУНКТЫ ПРОВЕРКИ • ${DemoCatalog.guideSteps.size}",
                11f,
                AppColors.muted,
                Typeface.BOLD
            ),
            matchWrap(top = 16)
        )

        DemoCatalog.guideSteps.forEachIndexed { index, step ->
            val checkbox = CheckBox(this).apply {
                text = step
                textSize = 14.5f
                setTextColor(AppColors.ink)
                buttonTintList = checkBoxColors()
                gravity = Gravity.TOP
                minHeight = dp(52)
                isChecked = index in completed
                setPadding(0, dp(3), 0, dp(3))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) completed.add(index) else completed.remove(index)
                    state.setGuideStepCompleted(index, checked)
                    refreshProgress()
                }
            }
            checkBoxes.add(checkbox)
            if (index > 0) {
                progressPanel.addView(
                    divider(),
                    matchFixed(1, top = 7, bottom = 7)
                )
            }
            progressPanel.addView(
                checkbox,
                matchWrap(top = if (index == 0) 8 else 0)
            )
        }
        content.addView(progressPanel, matchWrap(top = 12))

        reset.setOnClickListener {
            state.clearGuide()
            completed.clear()
            checkBoxes.forEach { it.isChecked = false }
            refreshProgress()
        }

        refreshProgress = {
            progressText.text = getString(
                R.string.guide_progress,
                completed.size,
                DemoCatalog.guideSteps.size
            )
            progressBar.progress = completed.size
            val done = completed.size == DemoCatalog.guideSteps.size
            completion.text = if (done) {
                "Проверка собрана. Теперь отделите факты от собственных предположений."
            } else {
                "Незаполненный пункт считается неизвестностью, а не нейтральным фактором."
            }
            completion.setTextColor(if (done) AppColors.accentDark else AppColors.warning)
            completion.background = rounded(
                if (done) AppColors.accentSoft else AppColors.warningSoft,
                8
            )
            completion.setPadding(dp(11), dp(10), dp(11), dp(10))
        }
        refreshProgress()

        val calibrationMemory = CalibrationMemoryEngine.evaluate(
            state.calibrationRecords()
        )
        content.addView(
            sectionTitle(
                "После события",
                "Разбор превращает отдельное решение в навык проверки."
            ),
            matchWrap(top = 20)
        )
        content.addView(
            calibrationMemoryPanel(calibrationMemory),
            matchWrap(top = 12)
        )
    }

    private fun calibrationMemoryPanel(
        memory: CalibrationMemory
    ): LinearLayout {
        val tone = calibrationMemoryTone(memory.status)
        return card().apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        text(
                            "Память процесса",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        label(
                            calibrationMemoryBadge(memory),
                            tone.background,
                            tone.foreground
                        )
                    )
                }
            )
            addView(
                text(
                    "Карта накапливает только завершенные ретроспективы «После свистка» и ищет повторяющиеся ошибки процесса.",
                    13f,
                    AppColors.muted
                ),
                matchWrap(top = 6)
            )

            if (memory.reviewCount == 0) {
                addView(
                    text(
                        "Завершите первый постсобытийный разбор. Счет, коэффициенты и финансовый результат в профиль не входят.",
                        13f,
                        tone.foreground,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(tone.background, 8)
                        setPadding(
                            dp(12),
                            dp(10),
                            dp(12),
                            dp(10)
                        )
                    },
                    matchWrap(top = 12)
                )
                addView(
                    outlineButton(
                        "Открыть «Анализ»",
                        AppColors.signal
                    ) {
                        selectTab(1)
                    },
                    matchWrap(top = 11)
                )
                return@apply
            }

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.BOTTOM
                            addView(
                                text(
                                    memory.overallScore
                                        ?.toString()
                                        ?: "—",
                                    42f,
                                    tone.foreground,
                                    Typeface.BOLD
                                )
                            )
                            addView(
                                text(
                                    if (
                                        memory.overallScore == null
                                    ) {
                                        "нет оценки"
                                    } else {
                                        "из 100"
                                    },
                                    12f,
                                    AppColors.muted,
                                    Typeface.BOLD
                                ),
                                wrapWrap(bottom = 8)
                            )
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        text(
                            "${memory.reviewCount} ${
                                calibrationReviewWord(
                                    memory.reviewCount
                                )
                            }\nпокрытие ${memory.coveragePercent}%",
                            12f,
                            AppColors.muted,
                            Typeface.BOLD
                        ).apply {
                            gravity = Gravity.END
                        }
                    )
                },
                matchWrap(top = 12)
            )
            memory.overallScore?.let { score ->
                addView(
                    horizontalProgress().apply {
                        progress = score
                        progressTintList =
                            ColorStateList.valueOf(
                                tone.foreground
                            )
                    },
                    matchFixed(7, top = 6)
                )
            }
            addView(
                text(
                    calibrationMemorySummary(memory),
                    13f,
                    tone.foreground,
                    Typeface.BOLD
                ).apply {
                    background = rounded(tone.background, 8)
                    setPadding(
                        dp(12),
                        dp(10),
                        dp(12),
                        dp(10)
                    )
                },
                matchWrap(top = 11)
            )
            addView(
                text(
                    "КАРТА СЛЕПЫХ ЗОН • БАЛЛ · НАБЛЮДЕНИЯ",
                    11f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 14)
            )
            addView(
                CalibrationMemoryView(this@MainActivity).apply {
                    setMemory(memory)
                },
                matchWrap(top = 5)
            )
            memory.focusProfile?.let { focus ->
                addView(
                    text(
                        calibrationMemoryFocus(memory, focus),
                        13f,
                        AppColors.ink,
                        Typeface.BOLD
                    ).apply {
                        background = rounded(
                            AppColors.background,
                            8,
                            if (
                                memory.status ==
                                CalibrationMemoryStatus.BLIND_SPOT
                            ) {
                                AppColors.danger
                            } else {
                                AppColors.line
                            },
                            1
                        )
                        setPadding(
                            dp(12),
                            dp(10),
                            dp(12),
                            dp(10)
                        )
                    },
                    matchWrap(top = 10)
                )
            }
            addView(
                text(
                    calibrationTrendText(memory.trend),
                    12f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 9)
            )
            addView(
                text(
                    "Цепочка ${memory.shortFingerprint} • ${
                        memory.verifiedFactorCount
                    } проверяемых факторов",
                    11f,
                    AppColors.muted,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                commandButton(
                    "Создать PNG-профиль",
                    AppColors.signal
                ) {
                    shareCalibrationMemory(memory)
                },
                matchWrap(top = 11)
            )
            addView(
                outlineButton(
                    "Добавить разбор в «Анализе»",
                    AppColors.signal
                ) {
                    selectTab(1)
                },
                matchWrap(top = 11)
            )
        }
    }

    private fun shareCalibrationMemory(
        memory: CalibrationMemory
    ) {
        if (passportExportInProgress) {
            Toast.makeText(
                this,
                "Изображение уже создается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        passportExportInProgress = true
        val passport = CalibrationMemoryPassportFactory.create(
            memory
        )
        Toast.makeText(
            this,
            "Создаем карту слепых зон…",
            Toast.LENGTH_SHORT
        ).show()
        passportExecutor.execute {
            runCatching {
                val file = CalibrationMemoryPassportExporter(
                    applicationContext
                ).export(passport)
                AnalysisImageProvider.uriFor(
                    applicationContext,
                    file
                )
            }.onSuccess { uri ->
                runOnUiThread {
                    passportExportInProgress = false
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Память процесса: карта слепых зон"
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            CalibrationMemoryPassportFactory
                                .shareText(passport)
                        )
                        clipData = ClipData.newRawUri(
                            "Карта слепых зон",
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    try {
                        startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Поделиться картой"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Нет приложения для отправки изображения",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure {
                runOnUiThread {
                    passportExportInProgress = false
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            "Не удалось создать карту",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun calibrationMemoryTone(
        status: CalibrationMemoryStatus
    ): Tone {
        return when (status) {
            CalibrationMemoryStatus.LEARNING ->
                Tone(AppColors.signal, AppColors.signalSoft)
            CalibrationMemoryStatus.STABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CalibrationMemoryStatus.UNEVEN ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CalibrationMemoryStatus.BLIND_SPOT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun calibrationMemoryBadge(
        memory: CalibrationMemory
    ): String {
        return when (memory.status) {
            CalibrationMemoryStatus.LEARNING ->
                "БАЗА • ${memory.reviewCount}/3"
            CalibrationMemoryStatus.STABLE -> "УСТОЙЧИВО"
            CalibrationMemoryStatus.UNEVEN -> "НЕРОВНО"
            CalibrationMemoryStatus.BLIND_SPOT ->
                "СЛЕПАЯ ЗОНА"
        }
    }

    private fun calibrationMemorySummary(
        memory: CalibrationMemory
    ): String {
        return when (memory.status) {
            CalibrationMemoryStatus.LEARNING -> {
                val remaining = (
                    3 - memory.reviewCount
                ).coerceAtLeast(0)
                if (remaining > 0) {
                    "Профиль предварительный. До устойчивой выборки: $remaining ${
                        calibrationReviewWord(remaining)
                    }."
                } else {
                    "Разборов достаточно, но проверяемых факторов пока меньше девяти."
                }
            }
            CalibrationMemoryStatus.STABLE ->
                "Исходные данные устойчивы на серии разборов. Это качество процесса, а не успешность прогнозов."
            CalibrationMemoryStatus.UNEVEN ->
                "Среднее приемлемо, но факторы подтверждаются неравномерно. Не переносите общий балл на слабые зоны."
            CalibrationMemoryStatus.BLIND_SPOT -> {
                if (memory.criticalMissCount > 0) {
                    "Обнаружены критические ошибки: ${memory.criticalMissCount}. Кворум источников не всегда выдерживал проверку."
                } else {
                    "Один фактор повторно получает меньше 45/100 и скрывается за общим средним."
                }
            }
        }
    }

    private fun calibrationMemoryFocus(
        memory: CalibrationMemory,
        focus: CalibrationFactorProfile
    ): String {
        val score = focus.score?.toString() ?: "—"
        val critical = if (focus.criticalMissCount > 0) {
            " • критических ошибок ${focus.criticalMissCount}"
        } else {
            ""
        }
        val prefix = if (
            memory.status ==
            CalibrationMemoryStatus.BLIND_SPOT
        ) {
            "Слепая зона"
        } else {
            "Фокус следующей проверки"
        }
        return "$prefix: «${focus.factor.title}» • $score/100 • ${
            focus.verifiedCount
        } наблюдений$critical"
    }

    private fun calibrationTrendText(
        trend: CalibrationTrend
    ): String {
        return when (trend.status) {
            CalibrationTrendStatus.INSUFFICIENT ->
                "Тренд появится после четырех разборов с проверяемыми факторами."
            CalibrationTrendStatus.IMPROVING ->
                "Тренд последних двух пар: ${trend.previousScore}→${trend.recentScore} (${signedValue(requireNotNull(trend.delta))})."
            CalibrationTrendStatus.STABLE ->
                "Тренд последних двух пар стабилен: ${trend.previousScore}→${trend.recentScore} (${signedValue(requireNotNull(trend.delta))})."
            CalibrationTrendStatus.DECLINING ->
                "Тренд последних двух пар снижается: ${trend.previousScore}→${trend.recentScore} (${signedValue(requireNotNull(trend.delta))})."
        }
    }

    private fun calibrationReviewWord(count: Int): String {
        val normalized = count % 100
        return when {
            normalized in 11..14 -> "разборов"
            count % 10 == 1 -> "разбор"
            count % 10 in 2..4 -> "разбора"
            else -> "разборов"
        }
    }

    private fun renderResponsible() {
        content.addView(
            sectionTitle(
                "Ответственный режим 18+",
                "Управляйте не ставкой, а дистанцией до решения."
            )
        )
        content.addView(
            decisionLedgerPanel(),
            matchWrap(top = 12)
        )
        content.addView(
            attentionBudgetPanel(),
            matchWrap(top = 12)
        )
        content.addView(
            decisionDistancePanel(),
            matchWrap(top = 12)
        )

        val pause = card()
        if (state.isPauseActive()) {
            pause.background = rounded(AppColors.dangerSoft, 8, AppColors.danger, 1)
            pause.addView(label("ПАУЗА АКТИВНА", AppColors.danger, Color.WHITE))
            pause.addView(
                text("Режим тишины", 23f, AppColors.danger, Typeface.BOLD),
                matchWrap(top = 12)
            )
            pause.addView(
                text(
                    "Новые решения и изменение карты сигнала закрыты до ${formatDateTime(state.pauseUntil())}.",
                    14f,
                    AppColors.ink
                ),
                matchWrap(top = 7)
            )
        } else {
            pause.addView(label("УНИКАЛЬНАЯ ФУНКЦИЯ", AppColors.signalSoft, AppColors.signal))
            pause.addView(
                text("Режим тишины на 24 часа", 23f, AppColors.ink, Typeface.BOLD),
                matchWrap(top = 12)
            )
            pause.addView(
                text(
                    "Одно действие временно закрывает шкалы и журнал решений. События и справочник останутся доступны.",
                    14f,
                    AppColors.muted
                ),
                matchWrap(top = 7)
            )
            pause.addView(
                commandButton("Включить паузу", AppColors.danger) {
                    confirmPause()
                },
                matchWrap(top = 14)
            )
        }
        content.addView(pause, matchWrap(top = 12))

        val limits = card()
        limits.addView(text("Границы вне приложения", 21f, AppColors.ink, Typeface.BOLD))
        limits.addView(limitRow("Средства", "только сумма, потеря которой не влияет на жизнь"))
        limits.addView(limitRow("После проигрыша", "24 часа без новых решений"))
        content.addView(limits, matchWrap(top = 12))

        val rules = listOf(
            "Не используйте заемные деньги и кредитные средства.",
            "Не пытайтесь отыграться сразу после проигрыша.",
            "Не принимайте решения в состоянии стресса, усталости или опьянения.",
            "Если контроль теряется, остановитесь и обратитесь за профессиональной помощью.",
            "Любая ставка связана с риском полной потери средств."
        )
        content.addView(
            text("БАЗОВЫЕ ПРАВИЛА", 12f, AppColors.muted, Typeface.BOLD),
            matchWrap(top = 18)
        )
        rules.forEach { rule ->
            content.addView(responsibleRule(rule), matchWrap(top = 9))
        }
    }

    private fun attentionBudgetPanel(): LinearLayout {
        val panel = card()
        lateinit var refresh: () -> Unit
        refresh = refreshBlock@{
            panel.removeAllViews()
            val now = System.currentTimeMillis()
            val result = currentAttentionBudget(now)
            val tone = attentionBudgetTone(result.status)
            val badgeTitle = when (result.status) {
                AttentionBudgetStatus.OPEN ->
                    "ОТКРЫТ • ${
                        attentionBudgetDuration(
                            result.remainingMillis,
                            roundUp = true
                        )
                    }"
                AttentionBudgetStatus.WARNING ->
                    "ГРАНИЦА • ${
                        attentionBudgetDuration(
                            result.remainingMillis,
                            roundUp = true
                        )
                    }"
                AttentionBudgetStatus.EXHAUSTED ->
                    "СТОП • ЛИМИТ"
            }

            panel.addView(
                attentionBudgetHeader(),
                matchFixed(
                    132 + compactLargeTextExtraDp(4)
                )
            )
            panel.addView(
                LinearLayout(this).apply {
                    val stack =
                        resources.configuration.fontScale >= 1.3f ||
                            resources.configuration.screenWidthDp < 380
                    orientation = if (stack) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stack) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        text(
                            "Бюджет внимания",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stack) {
                            matchWrap(bottom = 7)
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(10)
                            }
                        }
                    )
                    addView(
                        label(
                            badgeTitle,
                            tone.background,
                            tone.foreground
                        )
                    )
                },
                matchWrap(top = 12)
            )
            panel.addView(
                text(
                    "Использовано ${
                        attentionBudgetDuration(result.usedMillis)
                    } • лимит ${result.limitMinutes} мин",
                    17f,
                    tone.foreground,
                    Typeface.BOLD
                ),
                matchWrap(top = 10)
            )
            panel.addView(
                AttentionBudgetView(this).apply {
                    setResult(result)
                },
                matchFixed(112, top = 5)
            )
            panel.addView(
                text(
                    when (result.status) {
                        AttentionBudgetStatus.OPEN ->
                            "Граница не близко. Счётчик идёт только в активных «Анализе» и «Чек-листах»."
                        AttentionBudgetStatus.WARNING ->
                            "Использовано не меньше 75% времени. Осталось ${
                                attentionBudgetDuration(
                                    result.remainingMillis,
                                    roundUp = true
                                )
                            }."
                        AttentionBudgetStatus.EXHAUSTED ->
                            "Вывод «Факты сверены» закрыт до следующего московского дня. Безопасные выводы остаются доступны."
                    },
                    13f,
                    if (
                        result.status ==
                        AttentionBudgetStatus.EXHAUSTED
                    ) {
                        AppColors.danger
                    } else {
                        AppColors.muted
                    },
                    if (
                        result.status ==
                        AttentionBudgetStatus.EXHAUSTED
                    ) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                ),
                matchWrap(top = 5)
            )

            val limitValue = text(
                "Лимит: ${result.limitMinutes} минут",
                13f,
                AppColors.ink,
                Typeface.BOLD
            )
            panel.addView(limitValue, matchWrap(top = 13))
            panel.addView(
                SeekBar(this).apply {
                    max = (
                        AttentionBudgetPolicy.MAX_LIMIT_MINUTES -
                            AttentionBudgetPolicy.MIN_LIMIT_MINUTES
                        ) / AttentionBudgetPolicy.LIMIT_STEP_MINUTES
                    progress = (
                        result.limitMinutes -
                            AttentionBudgetPolicy.MIN_LIMIT_MINUTES
                        ) / AttentionBudgetPolicy.LIMIT_STEP_MINUTES
                    progressTintList = ColorStateList.valueOf(
                        tone.foreground
                    )
                    thumbTintList = ColorStateList.valueOf(
                        tone.foreground
                    )
                    contentDescription =
                        "Дневной лимит: ${result.limitMinutes} минут"
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar,
                                progress: Int,
                                fromUser: Boolean
                            ) {
                                val proposed =
                                    AttentionBudgetPolicy
                                        .MIN_LIMIT_MINUTES +
                                        progress *
                                        AttentionBudgetPolicy
                                            .LIMIT_STEP_MINUTES
                                limitValue.text = getString(
                                    R.string.attention_budget_limit,
                                    proposed
                                )
                                seekBar.contentDescription =
                                    "Дневной лимит: $proposed минут"
                            }

                            override fun onStartTrackingTouch(
                                seekBar: SeekBar
                            ) = Unit

                            override fun onStopTrackingTouch(
                                seekBar: SeekBar
                            ) {
                                val proposed =
                                    AttentionBudgetPolicy
                                        .MIN_LIMIT_MINUTES +
                                        seekBar.progress *
                                        AttentionBudgetPolicy
                                            .LIMIT_STEP_MINUTES
                                val changed = state
                                    .updateAttentionLimitMinutes(
                                        proposedMinutes = proposed,
                                        now = System.currentTimeMillis()
                                    )
                                if (!changed) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "После начала анализа лимит можно только уменьшить.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                refresh()
                            }
                        }
                    )
                },
                matchWrap(top = 2)
            )
            panel.addView(
                text(
                    if (result.usedMillis == 0L) {
                        "До начала анализа можно выбрать любой лимит."
                    } else {
                        "Повышение лимита закрыто до завтра; уменьшение остается доступным."
                    },
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
            if (
                result.status ==
                AttentionBudgetStatus.EXHAUSTED &&
                !state.isPauseActive(now)
            ) {
                panel.addView(
                    commandButton(
                        "Включить паузу на 24 часа",
                        AppColors.danger
                    ) {
                        confirmPause()
                    },
                    matchWrap(top = 12)
                )
            }
            panel.addView(
                text(
                    "Матчи, Гид и 18+ не учитываются. Нет фонового сервиса • SHA-256 ${result.shortFingerprint}.",
                    11.5f,
                    AppColors.muted
                ),
                matchWrap(top = 10)
            )
        }
        refresh()
        return panel
    }

    private fun attentionBudgetHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.attention_budget)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Физическая шкала из восьми сегментов времени, янтарная граница и красный стоп"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "БЮДЖЕТ ВНИМАНИЯ",
                            11f,
                            Color.rgb(191, 238, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Время, которое видно",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun attentionBudgetTone(
        status: AttentionBudgetStatus
    ): Tone {
        return when (status) {
            AttentionBudgetStatus.OPEN ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            AttentionBudgetStatus.WARNING ->
                Tone(AppColors.warning, AppColors.warningSoft)
            AttentionBudgetStatus.EXHAUSTED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun decisionLedgerPanel(): LinearLayout {
        val panel = card()
        lateinit var refresh: () -> Unit
        refresh = refreshBlock@{
            panel.removeAllViews()
            val read = state.decisionLedger()
            val ledger = read.ledger
            val tone = when (read.integrity) {
                DecisionLedgerIntegrity.EMPTY ->
                    Tone(AppColors.signal, AppColors.signalSoft)
                DecisionLedgerIntegrity.INTACT ->
                    Tone(AppColors.accentDark, AppColors.accentSoft)
                DecisionLedgerIntegrity.TAMPERED ->
                    Tone(AppColors.danger, AppColors.dangerSoft)
            }
            val badgeTitle = when (read.integrity) {
                DecisionLedgerIntegrity.EMPTY ->
                    "ГОТОВ К ПЕРВОЙ ЗАПИСИ"
                DecisionLedgerIntegrity.INTACT ->
                    "ЦЕПЬ ЦЕЛА • ${ledger?.totalRecordCount ?: 0L}"
                DecisionLedgerIntegrity.TAMPERED ->
                    "ЦЕПЬ НАРУШЕНА"
            }

            panel.addView(
                decisionLedgerHeader(),
                matchFixed(
                    132 + compactLargeTextExtraDp(4)
                )
            )
            panel.addView(
                LinearLayout(this).apply {
                    val stack =
                        resources.configuration.fontScale >= 1.3f ||
                            resources.configuration.screenWidthDp < 380
                    orientation = if (stack) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stack) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        text(
                            "Бортовой журнал",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stack) {
                            matchWrap(bottom = 7)
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(10)
                            }
                        }
                    )
                    addView(
                        label(
                            badgeTitle,
                            tone.background,
                            tone.foreground
                        )
                    )
                },
                matchWrap(top = 12)
            )
            panel.addView(
                text(
                    when (read.integrity) {
                        DecisionLedgerIntegrity.EMPTY ->
                            "Следующий сохраненный вывод станет первым звеном локальной истории. Старые снимки не будут добавлены задним числом."
                        DecisionLedgerIntegrity.INTACT ->
                            "Порядок, снимок и предыдущее звено связаны SHA-256. Удаление или перестановка внутри сохраненного окна нарушит проверку."
                        DecisionLedgerIntegrity.TAMPERED ->
                            "Проверка сохраненной цепочки не пройдена. Новые записи заблокированы, чтобы не скрывать повреждение новой историей."
                    },
                    13f,
                    tone.foreground,
                    if (
                        read.integrity ==
                        DecisionLedgerIntegrity.TAMPERED
                    ) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                ),
                matchWrap(top = 9)
            )
            panel.addView(
                DecisionLedgerView(this).apply {
                    setResult(read)
                },
                matchFixed(112, top = 4)
            )

            if (ledger != null && ledger.records.isNotEmpty()) {
                panel.addView(
                    text(
                        "Всего ${ledger.totalRecordCount} • " +
                            "в окне ${ledger.records.size} • " +
                            if (ledger.anchorSequence > 0L) {
                                "якорь #${ledger.anchorSequence}"
                            } else {
                                "якорь GENESIS"
                            },
                        12f,
                        AppColors.muted,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 4)
                )
                val visibleRecords = if (
                    decisionLedgerExpanded
                ) {
                    ledger.records.asReversed()
                } else {
                    ledger.records.takeLast(
                        COLLAPSED_LEDGER_RECORDS
                    ).asReversed()
                }
                visibleRecords.forEachIndexed { index, record ->
                    if (index > 0) {
                        panel.addView(
                            divider(),
                            matchFixed(1, top = 7)
                        )
                    }
                    panel.addView(
                        decisionLedgerRecordRow(record),
                        matchWrap(top = 7)
                    )
                }
                if (
                    ledger.records.size >
                    COLLAPSED_LEDGER_RECORDS
                ) {
                    panel.addView(
                        outlineButton(
                            if (decisionLedgerExpanded) {
                                "Показать последние 3"
                            } else {
                                "Показать все • ${ledger.records.size}"
                            },
                            AppColors.signal
                        ) {
                            decisionLedgerExpanded =
                                !decisionLedgerExpanded
                            refresh()
                        },
                        matchWrap(top = 11)
                    )
                }
                panel.addView(
                    commandButton(
                        "Поделиться квитанцией",
                        AppColors.signal
                    ) {
                        shareDecisionLedger(ledger)
                    },
                    matchWrap(top = 11)
                )
                panel.addView(
                    outlineButton(
                        "Очистить локальный журнал",
                        AppColors.muted
                    ) {
                        confirmDecisionLedgerReset(refresh)
                    },
                    matchWrap(top = 8)
                )
                panel.addView(
                    text(
                        "Окно до ${DecisionLedgerFactory.MAX_RECORDS} записей • SHA-256 ${ledger.shortFingerprint}. Это локальная контрольная сумма, не электронная подпись.",
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 10)
                )
            } else if (
                read.integrity ==
                DecisionLedgerIntegrity.TAMPERED
            ) {
                panel.addView(
                    commandButton(
                        "Начать новую цепочку",
                        AppColors.danger
                    ) {
                        confirmDecisionLedgerReset(refresh)
                    },
                    matchWrap(top = 8)
                )
                panel.addView(
                    text(
                        "Сброс удалит поврежденную локальную строку журнала. Существующие снимки событий останутся, но не будут выданы за звенья новой цепочки.",
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 10)
                )
            } else {
                panel.addView(
                    text(
                        "Хранятся только метаданные вывода и контрольные метки. Суммы, коэффициенты и результаты матчей в журнал не входят.",
                        11.5f,
                        AppColors.muted
                    ),
                    matchWrap(top = 5)
                )
            }
        }
        refresh()
        return panel
    }

    private fun decisionLedgerHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.decision_ledger)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Физический регистратор с непрерывной цепью пломб, зеленым индикатором и отдельным поврежденным звеном"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "БОРТОВОЙ ЖУРНАЛ",
                            11f,
                            Color.rgb(191, 238, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Решение оставляет след",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun decisionLedgerRecordRow(
        record: DecisionLedgerRecord
    ): LinearLayout {
        val color = decisionColor(record.decision)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(5))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        label(
                            "#${record.sequence}",
                            color,
                            Color.WHITE
                        ),
                        wrapWrap(right = 8)
                    )
                    addView(
                        text(
                            record.eventLabel,
                            14f,
                            AppColors.ink,
                            Typeface.BOLD
                        ).apply {
                            maxLines = 3
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                }
            )
            addView(
                text(
                    "${decisionTitle(record.decision)} • " +
                        formatDateTime(record.savedAt),
                    12f,
                    color,
                    Typeface.BOLD
                ),
                matchWrap(top = 5)
            )
            addView(
                text(
                    "Снимок ${record.snapshotFingerprint.take(8).uppercase()} • звено ${record.shortFingerprint}",
                    11f,
                    AppColors.muted
                ),
                matchWrap(top = 3)
            )
        }
    }

    private fun shareDecisionLedger(
        ledger: DecisionLedger
    ) {
        val receipt = buildString {
            appendLine("Спорт Пульс • Бортовой журнал")
            appendLine("Цепочка: цела")
            appendLine(
                "Всего ${ledger.totalRecordCount} • " +
                    "в окне ${ledger.records.size}"
            )
            appendLine(
                "Якорь #${ledger.anchorSequence} • " +
                    ledger.anchorFingerprint.uppercase()
            )
            ledger.records.forEach { record ->
                appendLine()
                appendLine(
                    "#${record.sequence} • " +
                        record.eventLabel
                )
                appendLine(
                    "${decisionTitle(record.decision)} • " +
                        formatDateTime(record.savedAt)
                )
                appendLine(
                    "Снимок ${record.snapshotFingerprint.uppercase()}"
                )
                appendLine(
                    "Звено ${record.fingerprint.uppercase()}"
                )
            }
            appendLine()
            appendLine("Цепочка SHA-256 ${ledger.fingerprint.uppercase()}")
            append(
                "Локальная контрольная сумма, не электронная подпись."
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Бортовой журнал Спорт Пульс"
            )
            putExtra(Intent.EXTRA_TEXT, receipt)
        }
        try {
            startActivity(
                Intent.createChooser(
                    intent,
                    "Поделиться квитанцией"
                )
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Нет приложения для отправки квитанции",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmDecisionLedgerReset(
        onReset: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("Начать новую цепочку?")
            .setMessage(
                "Локальный Бортовой журнал будет удален без возможности восстановления. Снимки событий останутся отдельно и не попадут в новую цепочку задним числом."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить цепочку") { _, _ ->
                state.resetDecisionLedger()
                decisionLedgerExpanded = false
                onReset()
            }
            .show()
    }

    private fun decisionDistancePanel(): LinearLayout {
        val panel = card()
        lateinit var refresh: () -> Unit
        refresh = refreshBlock@{
            panel.removeAllViews()
            val now = System.currentTimeMillis()
            val paused = state.isPauseActive(now)
            val clearance = state.distanceClearance(now)
            val result = if (clearance != null) {
                DecisionDistanceEngine.evaluate(
                    assessment = DecisionDistanceAssessment.clear(),
                    checkedAt = clearance.checkedAt
                )
            } else {
                DecisionDistanceEngine.evaluate(
                    assessment = decisionDistanceDraft,
                    checkedAt = now
                )
            }
            val status = when {
                paused -> DecisionDistanceStatus.STOP
                clearance != null -> DecisionDistanceStatus.CLEAR
                else -> result.status
            }
            val tone = decisionDistanceTone(status)
            val badgeTitle = when {
                paused -> "ПАУЗА АКТИВНА"
                clearance != null -> "ДОПУСК • 1 РЕШЕНИЕ"
                status == DecisionDistanceStatus.STOP ->
                    "СТОП • ${result.riskFactors.size}"
                status == DecisionDistanceStatus.CLEAR ->
                    "ГОТОВО К ПОДТВЕРЖДЕНИЮ"
                else -> "ОТВЕТЫ • ${result.answeredCount}/4"
            }

            panel.addView(
                decisionDistanceHeader(),
                matchFixed(
                    if (resources.configuration.fontScale >= 1.8f) {
                        136
                    } else {
                        132
                    }
                )
            )
            panel.addView(
                LinearLayout(this).apply {
                    val stack =
                        resources.configuration.fontScale >= 1.3f ||
                            resources.configuration.screenWidthDp < 380
                    orientation = if (stack) {
                        LinearLayout.VERTICAL
                    } else {
                        LinearLayout.HORIZONTAL
                    }
                    gravity = if (stack) {
                        Gravity.START
                    } else {
                        Gravity.CENTER_VERTICAL
                    }
                    addView(
                        text(
                            "Контур дистанции",
                            20f,
                            AppColors.ink,
                            Typeface.BOLD
                        ),
                        if (stack) {
                            matchWrap(bottom = 7)
                        } else {
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply {
                                rightMargin = dp(10)
                            }
                        }
                    )
                    addView(
                        label(
                            badgeTitle,
                            tone.background,
                            tone.foreground
                        )
                    )
                },
                matchWrap(top = 12)
            )
            panel.addView(
                text(
                    when {
                        paused ->
                            "Допуск закрыт до окончания режима тишины."
                        clearance != null ->
                            "Четыре ответа пройдены. Допуск одноразовый: он исчезнет после одного вывода «Факты сверены»."
                        status == DecisionDistanceStatus.STOP ->
                            result.riskFactors.joinToString(" ") {
                                it.stopReason
                            }
                        status == DecisionDistanceStatus.CLEAR ->
                            "В ответах нет стоп-причин. Подтвердите одноразовый допуск."
                        else ->
                            "Ответьте на четыре вопроса перед самым сильным выводом."
                    },
                    13f,
                    if (status == DecisionDistanceStatus.STOP) {
                        AppColors.danger
                    } else {
                        AppColors.muted
                    },
                    if (status == DecisionDistanceStatus.STOP) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                ),
                matchWrap(top = 7)
            )
            panel.addView(
                DecisionDistanceView(this).apply {
                    setResult(result)
                },
                matchFixed(118, top = 8)
            )

            if (paused) {
                panel.addView(
                    text(
                        "До ${formatDateTime(state.pauseUntil())} нельзя подтвердить новый допуск.",
                        12f,
                        AppColors.danger,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 7)
                )
                return@refreshBlock
            }

            if (clearance != null) {
                panel.addView(
                    text(
                        "Действует до ${formatDateTime(clearance.expiresAt)} • метка ${clearance.shortFingerprint}",
                        12f,
                        AppColors.accentDark,
                        Typeface.BOLD
                    ),
                    matchWrap(top = 7)
                )
                panel.addView(
                    commandButton(
                        if (returnToPulseAfterDistance) {
                            "Вернуться к выводу"
                        } else {
                            "Открыть анализ"
                        },
                        AppColors.accent
                    ) {
                        returnToPulseAfterDistance = false
                        selectTab(1)
                    },
                    matchWrap(top = 12)
                )
                panel.addView(
                    outlineButton(
                        "Отозвать допуск",
                        AppColors.muted
                    ) {
                        state.clearDistanceClearance()
                        decisionDistanceDraft =
                            DecisionDistanceAssessment.unanswered()
                        returnToPulseAfterDistance = false
                        refresh()
                    },
                    matchWrap(top = 8)
                )
                panel.addView(
                    decisionDistancePrivacyNote(),
                    matchWrap(top = 10)
                )
                return@refreshBlock
            }

            DecisionDistanceFactor.values().forEachIndexed {
                    index,
                    factor ->
                if (index > 0) {
                    panel.addView(
                        divider(),
                        matchFixed(1, top = 10, bottom = 8)
                    )
                }
                panel.addView(
                    decisionDistanceFactorRow(
                        factor = factor,
                        selected = decisionDistanceDraft.answer(
                            factor
                        )
                    ) { answer ->
                        decisionDistanceDraft =
                            decisionDistanceDraft.withAnswer(
                                factor,
                                answer
                            )
                        refresh()
                    }
                )
            }

            when (status) {
                DecisionDistanceStatus.CLEAR -> {
                    panel.addView(
                        commandButton(
                            "Подтвердить допуск на 30 минут",
                            AppColors.accent
                        ) {
                            val checked =
                                DecisionDistanceEngine.evaluate(
                                    assessment =
                                        decisionDistanceDraft,
                                    checkedAt =
                                        System.currentTimeMillis()
                                )
                            if (
                                checked.status ==
                                DecisionDistanceStatus.CLEAR
                            ) {
                                state.saveDistanceClearance(
                                    DecisionDistanceEngine
                                        .clearanceFor(checked)
                                )
                                decisionDistanceDraft =
                                    DecisionDistanceAssessment
                                        .unanswered()
                                refresh()
                            }
                        },
                        matchWrap(top = 13)
                    )
                }
                DecisionDistanceStatus.STOP -> {
                    panel.addView(
                        commandButton(
                            "Включить паузу на 24 часа",
                            AppColors.danger
                        ) {
                            confirmPause()
                        },
                        matchWrap(top = 13)
                    )
                }
                DecisionDistanceStatus.INCOMPLETE -> Unit
            }
            if (result.answeredCount > 0) {
                panel.addView(
                    outlineButton(
                        "Сбросить ответы",
                        AppColors.muted
                    ) {
                        decisionDistanceDraft =
                            DecisionDistanceAssessment.unanswered()
                        refresh()
                    },
                    matchWrap(top = 8)
                )
            }
            panel.addView(
                decisionDistancePrivacyNote(),
                matchWrap(top = 10)
            )
        }
        refresh()
        return panel
    }

    private fun decisionDistanceHeader(): FrameLayout {
        return imageFrame().apply {
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(
                        R.drawable.decision_distance
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        "Четыре физических шлюза, сигнал допуска и красный рычаг паузы"
                },
                frameMatch()
            )
            addView(
                View(this@MainActivity).apply {
                    background = gradientScrim(compact = true)
                },
                frameMatch()
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        text(
                            "КОНТУР ДИСТАНЦИИ",
                            11f,
                            Color.rgb(191, 238, 228),
                            Typeface.BOLD
                        )
                    )
                    addView(
                        text(
                            "Четыре шлюза до решения",
                            18f,
                            Color.WHITE,
                            Typeface.BOLD
                        ),
                        matchWrap(top = 2)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = dp(13)
                    rightMargin = dp(13)
                    bottomMargin = dp(11)
                }
            )
        }
    }

    private fun decisionDistanceFactorRow(
        factor: DecisionDistanceFactor,
        selected: DecisionDistanceAnswer,
        onSelected: (DecisionDistanceAnswer) -> Unit
    ): LinearLayout {
        val stack = resources.configuration.fontScale >= 1.3f ||
            resources.configuration.screenWidthDp < 380
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                decisionDistanceAnswerButton(
                    title = "Нет",
                    factor = factor,
                    answer = DecisionDistanceAnswer.NO,
                    selected =
                        selected == DecisionDistanceAnswer.NO,
                    onClick = {
                        onSelected(DecisionDistanceAnswer.NO)
                    }
                ),
                LinearLayout.LayoutParams(dp(62), dp(48)).apply {
                    rightMargin = dp(6)
                }
            )
            addView(
                decisionDistanceAnswerButton(
                    title = "Да",
                    factor = factor,
                    answer = DecisionDistanceAnswer.YES,
                    selected =
                        selected == DecisionDistanceAnswer.YES,
                    onClick = {
                        onSelected(DecisionDistanceAnswer.YES)
                    }
                ),
                LinearLayout.LayoutParams(dp(62), dp(48))
            )
        }
        return LinearLayout(this).apply {
            orientation = if (stack) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            gravity = if (stack) {
                Gravity.START
            } else {
                Gravity.CENTER_VERTICAL
            }
            addView(
                text(
                    factor.question,
                    13.5f,
                    AppColors.ink,
                    Typeface.BOLD
                ),
                if (stack) {
                    matchWrap(bottom = 7)
                } else {
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        rightMargin = dp(10)
                    }
                }
            )
            addView(choices)
        }
    }

    private fun decisionDistanceAnswerButton(
        title: String,
        factor: DecisionDistanceFactor,
        answer: DecisionDistanceAnswer,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView {
        val color = if (
            answer == DecisionDistanceAnswer.YES
        ) {
            AppColors.danger
        } else {
            AppColors.accent
        }
        return text(
            title,
            fixedControlTextSize(13f),
            if (selected) Color.WHITE else color,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            background = rippleRounded(
                if (selected) color else AppColors.surface,
                8,
                if (selected) color else AppColors.line,
                1
            )
            applyAccessibleAction(dp(48))
            isSelected = selected
            contentDescription = buildString {
                append(factor.question)
                append(" Ответ ")
                append(title.lowercase())
                if (selected) append(". Выбрано.")
            }
            setOnClickListener { onClick() }
        }
    }

    private fun decisionDistancePrivacyNote(): TextView {
        return text(
            "Без балла риска. Черновик ответов не сохраняется; на устройстве остается только срок и метка успешного допуска.",
            11.5f,
            AppColors.muted
        )
    }

    private fun decisionDistanceTone(
        status: DecisionDistanceStatus
    ): Tone {
        return when (status) {
            DecisionDistanceStatus.INCOMPLETE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            DecisionDistanceStatus.STOP ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            DecisionDistanceStatus.CLEAR ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun confirmPause() {
        AlertDialog.Builder(this)
            .setTitle("Включить паузу на 24 часа?")
            .setMessage(
                "До окончания паузы нельзя будет менять карту сигнала и фиксировать решения. Справочник останется доступен."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Включить") { _, _ ->
                state.activatePause()
                decisionDistanceDraft =
                    DecisionDistanceAssessment.unanswered()
                returnToPulseAfterDistance = false
                refreshSourceBadge()
                renderContent()
                Toast.makeText(this, "Режим тишины включен", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.windowInsetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarInsets(view: View) {
        view.setOnApplyWindowInsetsListener { target, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                left = bars.left
                top = bars.top
                right = bars.right
                bottom = bars.bottom
            } else {
                left = insets.systemWindowInsetLeft
                top = insets.systemWindowInsetTop
                right = insets.systemWindowInsetRight
                bottom = insets.systemWindowInsetBottom
            }
            if (
                target.paddingLeft != left ||
                target.paddingTop != top ||
                target.paddingRight != right ||
                target.paddingBottom != bottom
            ) {
                target.setPadding(left, top, right, bottom)
            }
            insets
        }
        view.requestApplyInsets()
    }

    private fun limitRow(title: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(11), 0, 0)
            addView(text(title.uppercase(Locale.getDefault()), 11f, AppColors.muted, Typeface.BOLD))
            addView(text(value, 15f, AppColors.ink, Typeface.BOLD), matchWrap(top = 2))
        }
    }

    private fun responsibleRule(rule: String): LinearLayout {
        return card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(
                text("!", 15f, Color.WHITE, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    minWidth = dp(28)
                    minHeight = dp(28)
                    setPadding(dp(4), 0, dp(4), 0)
                    background = rounded(AppColors.danger, 14)
                },
                wrapWrap(right = 10)
            )
            addView(
                text(rule, 14f, AppColors.ink),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun sectionTitle(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 26f, AppColors.ink, Typeface.BOLD))
            addView(text(subtitle, 14.5f, AppColors.muted), matchWrap(top = 5))
        }
    }

    private fun legalFooter(): TextView {
        return text(
            "18+  •  Информационный продукт. Не принимает ставки, не хранит платежные данные и не гарантирует результат.",
            12f,
            AppColors.muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(16), dp(10), dp(8))
        }
    }

    private fun card(padding: Int = 16): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(AppColors.surface, 8, AppColors.line, 1)
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        }
    }

    private fun imageFrame(): FrameLayout {
        return FrameLayout(this).apply {
            background = rounded(AppColors.ink, 8)
            clipToOutline = true
        }
    }

    private fun divider(): View {
        return View(this).apply { setBackgroundColor(AppColors.line) }
    }

    private fun commandButton(
        title: String,
        color: Int,
        onClick: () -> Unit
    ): TextView {
        return text(title, 14f, Color.WHITE, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            maxLines = 3
            setSingleLine(false)
            setPadding(dp(14), 0, dp(14), 0)
            background = rippleRounded(color, 8)
            applyAccessibleAction(dp(48))
            setOnClickListener { onClick() }
        }
    }

    private fun outlineButton(
        title: String,
        color: Int,
        onClick: () -> Unit
    ): TextView {
        return text(title, 13f, color, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            maxLines = 3
            setSingleLine(false)
            setPadding(dp(12), 0, dp(12), 0)
            background = rippleRounded(AppColors.surface, 8, color, 1)
            applyAccessibleAction(dp(48))
            setOnClickListener { onClick() }
        }
    }

    private fun label(
        value: String,
        backgroundColor: Int,
        textColor: Int,
        strokeColor: Int = backgroundColor
    ): TextView {
        return text(value, 11f, textColor, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            minHeight = dp(28)
            maxLines = 3
            setSingleLine(false)
            maxWidth = resources.displayMetrics.widthPixels - dp(72)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(backgroundColor, 14, strokeColor, 1)
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        style: Int = Typeface.NORMAL
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = AppTypography.forText(this@MainActivity, style)
            includeFontPadding = true
            setLineSpacing(0f, 1.08f)
            setHorizontallyScrolling(false)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                breakStrategy = android.graphics.text.LineBreaker
                    .BREAK_STRATEGY_HIGH_QUALITY
            }
            hyphenationFrequency =
                android.text.Layout.HYPHENATION_FREQUENCY_NORMAL
        }
    }

    private fun horizontalProgress(): ProgressBar {
        return ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressBackgroundTintList = ColorStateList.valueOf(AppColors.line)
            progressTintList = ColorStateList.valueOf(AppColors.accent)
        }
    }

    private fun checkBoxColors(): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(AppColors.accent, AppColors.muted)
        )
    }

    private fun gradientScrim(compact: Boolean = false): GradientDrawable {
        val top = if (compact) Color.argb(8, 10, 15, 18) else AppColors.scrimClear
        val bottom = if (compact) Color.argb(225, 10, 15, 18) else AppColors.scrim
        return GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(bottom, Color.argb(90, 10, 15, 18), top)
        )
    }

    private fun rounded(
        color: Int,
        radiusDp: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), strokeColor)
        }
    }

    private fun rippleRounded(
        color: Int,
        radiusDp: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Int = 0
    ): RippleDrawable {
        val content = rounded(color, radiusDp, strokeColor, strokeWidthDp)
        val mask = rounded(Color.WHITE, radiusDp)
        val rippleColor = if (Color.luminance(color) >= 0.5f) {
            Color.argb(28, 20, 24, 27)
        } else {
            Color.argb(38, 255, 255, 255)
        }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            content,
            mask
        )
    }

    private fun verdictTitle(verdict: SignalVerdict): String {
        return when (verdict) {
            SignalVerdict.SKIP -> "ПРОПУСТИТЬ"
            SignalVerdict.OBSERVE -> "НАБЛЮДАТЬ"
            SignalVerdict.READY -> "ФАКТЫ СВЕРЕНЫ"
        }
    }

    private fun verdictExplanation(result: SignalResult): String {
        val weak = result.weakestFactor.title.lowercase(Locale.getDefault())
        return when (result.verdict) {
            SignalVerdict.SKIP ->
                "Слишком много белых пятен. Слабое место: $weak. Пропуск события сейчас является нормальным решением."
            SignalVerdict.OBSERVE ->
                "Сигнал еще неустойчив. Сначала подтвердите фактор «$weak», затем пересоберите карту."
            SignalVerdict.READY ->
                "Основные данные собраны. Можно сравнивать рынки, но карта не предсказывает исход и не устраняет риск."
        }
    }

    private fun verificationRouteBadge(
        route: VerificationRoute
    ): String {
        return when (route.status) {
            VerificationRouteStatus.REACHABLE -> {
                val count = route.steps.size
                val checkWord = when {
                    count == 1 -> "ПРОВЕРКА"
                    count in 2..4 -> "ПРОВЕРКИ"
                    else -> "ПРОВЕРОК"
                }
                "МИНИМУМ • $count $checkWord"
            }
            VerificationRouteStatus.FACTS_LIMIT ->
                "ЧЕСТНЫЙ ПРЕДЕЛ"
            VerificationRouteStatus.READY_MAINTAIN ->
                "СТАТУС ДОСТИГНУТ"
        }
    }

    private fun verificationRouteMetric(
        route: VerificationRoute
    ): String {
        val current = route.baselineResult.effectiveSignal.readiness
        return when (route.status) {
            VerificationRouteStatus.REACHABLE ->
                "$current → ${route.projectedResult.effectiveSignal.readiness} • цель ${route.targetReadiness}"
            VerificationRouteStatus.FACTS_LIMIT ->
                "$current → предел ${route.allQuorumResult.effectiveSignal.readiness} • цель ${route.targetReadiness}"
            VerificationRouteStatus.READY_MAINTAIN ->
                "$current/100 • без искусственного повышения"
        }
    }

    private fun verificationRouteExplanation(
        route: VerificationRoute
    ): String {
        return when (route.status) {
            VerificationRouteStatus.REACHABLE -> {
                val factors = route.steps.joinToString(", ") {
                    "«${it.factor.title}»"
                }
                val target = route.targetVerdict
                    ?.let(::verdictTitle)
                    ?.lowercase(Locale.getDefault())
                    .orEmpty()
                "Минимальный маршрут до статуса «$target»: подтвердить кворумом $factors. Текущие оценки не меняются."
            }
            VerificationRouteStatus.FACTS_LIMIT -> {
                val ceiling =
                    route.allQuorumResult.effectiveSignal.readiness
                "Даже кворум 5/5 даст максимум $ceiling/100. До следующего статуса не хватает ${route.remainingGap} пунктов полноты: нужны новые факты, а не дополнительные источники."
            }
            VerificationRouteStatus.READY_MAINTAIN ->
                "Карта уже готова к сравнению рынков. Поддерживайте свежесть и не повышайте оценки ради более уверенного вида."
        }
    }

    private fun verificationRouteAction(
        route: VerificationRoute,
        factor: SignalFactor
    ): String {
        return when (route.status) {
            VerificationRouteStatus.REACHABLE ->
                "Начать с «${factor.title}»"
            VerificationRouteStatus.FACTS_LIMIT -> {
                val gain = route.bestCheck?.readinessGain ?: 0
                "Проверить «${factor.title}»: максимум +$gain"
            }
            VerificationRouteStatus.READY_MAINTAIN ->
                "Обновить первым: «${factor.title}»"
        }
    }

    private fun verificationRouteTone(
        route: VerificationRoute
    ): Tone {
        return when (route.status) {
            VerificationRouteStatus.REACHABLE ->
                Tone(AppColors.signal, AppColors.signalSoft)
            VerificationRouteStatus.FACTS_LIMIT ->
                Tone(AppColors.warning, AppColors.warningSoft)
            VerificationRouteStatus.READY_MAINTAIN ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun verdictTone(verdict: SignalVerdict): Tone {
        return when (verdict) {
            SignalVerdict.SKIP -> Tone(AppColors.danger, AppColors.dangerSoft)
            SignalVerdict.OBSERVE -> Tone(AppColors.warning, AppColors.warningSoft)
            SignalVerdict.READY -> Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun evidenceSummary(result: EvidenceResult): String {
        if (result.quorumCount == SignalFactor.values().size) {
            return "Кворум фактов: 5/5 • все шкалы подтверждены"
        }
        if (result.cappedFactors.isEmpty()) {
            return "Кворум фактов: ${result.quorumCount}/5 • предел не достигнут"
        }
        val factors = result.cappedFactors.joinToString(", ") {
            it.shortTitle.lowercase(Locale.getDefault())
        }
        val impact = if (result.readinessLoss > 0) {
            "полнота -${result.readinessLoss}"
        } else {
            "полнота без снижения"
        }
        return "Кворум фактов: ${result.quorumCount}/5 • ограничено: $factors • $impact"
    }

    private fun sourceIntegrityTone(
        verdict: SourceIntegrityVerdict
    ): Tone {
        return when (verdict) {
            SourceIntegrityVerdict.NO_QUORUM ->
                Tone(AppColors.signal, AppColors.signalSoft)
            SourceIntegrityVerdict.OPEN,
            SourceIntegrityVerdict.ECHO ->
                Tone(AppColors.warning, AppColors.warningSoft)
            SourceIntegrityVerdict.AUDITED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            SourceIntegrityVerdict.CONFLICT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun sourceAuditTone(
        state: SourceAuditState
    ): Tone {
        return when (state) {
            SourceAuditState.UNAUDITED,
            SourceAuditState.SHARED_LINEAGE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            SourceAuditState.INDEPENDENT ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            SourceAuditState.CONFLICT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun sourceIntegrityBadge(
        verdict: SourceIntegrityVerdict
    ): String {
        return when (verdict) {
            SourceIntegrityVerdict.NO_QUORUM ->
                "АНТИЭХО • БЕЗ КВОРУМА"
            SourceIntegrityVerdict.OPEN ->
                "АНТИЭХО • НУЖНА ПРОВЕРКА"
            SourceIntegrityVerdict.AUDITED ->
                "АНТИЭХО • НЕЗАВИСИМЫ"
            SourceIntegrityVerdict.ECHO ->
                "АНТИЭХО • ОДНА ЦЕПОЧКА"
            SourceIntegrityVerdict.CONFLICT ->
                "АНТИЭХО • РАСХОЖДЕНИЕ"
        }
    }

    private fun sourceIntegrityMetric(
        result: SourceIntegrityResult
    ): String {
        return if (result.claimedQuorumCount == 0) {
            "Заявлено 0 кворумов"
        } else {
            "Принято ${result.acceptedQuorumCount} из ${
                result.claimedQuorumCount
            } кворумов"
        }
    }

    private fun sourceIntegrityExplanation(
        result: SourceIntegrityResult
    ): String {
        return when (result.verdict) {
            SourceIntegrityVerdict.NO_QUORUM ->
                "Ни один фактор не заявлен как «2+». Антиэхо не повышает уровень подтверждения автоматически."
            SourceIntegrityVerdict.OPEN ->
                "Без проверки независимости заявленные «2+» учитываются как один источник. Открытых проверок: ${result.unauditedQuorumCount}."
            SourceIntegrityVerdict.AUDITED ->
                "Все заявленные кворумы связаны с разными первичными цепочками и допускаются в расчёт."
            SourceIntegrityVerdict.ECHO ->
                "Кворумов из одной информационной цепочки: ${result.echoQuorumCount}. Повторы учтены как один источник."
            SourceIntegrityVerdict.CONFLICT ->
                "Расхождений: ${result.conflictCount}. Противоречивые факторы учтены как неподтверждённые."
        }
    }

    private fun sourceIntegrityFactorLabel(
        result: SourceIntegrityFactor
    ): String {
        return "${result.factor.title} • заявлено: ${
            result.claimedLevel.title.lowercase(
                Locale.getDefault()
            )
        } → учтено: ${
            result.effectiveLevel.title.lowercase(
                Locale.getDefault()
            )
        }"
    }

    private fun counterViewTone(
        verdict: CounterViewVerdict
    ): Tone {
        return when (verdict) {
            CounterViewVerdict.OPEN,
            CounterViewVerdict.MIXED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CounterViewVerdict.BALANCED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CounterViewVerdict.REFUTED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun counterReviewTone(
        state: CounterReviewState
    ): Tone {
        return when (state) {
            CounterReviewState.UNCHECKED,
            CounterReviewState.MIXED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CounterReviewState.CLEAR ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CounterReviewState.REFUTED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun counterViewBadge(
        verdict: CounterViewVerdict
    ): String {
        return when (verdict) {
            CounterViewVerdict.OPEN ->
                "КОНТРРАКУРС • ОТКРЫТ"
            CounterViewVerdict.BALANCED ->
                "КОНТРРАКУРС • ПРОВЕРЕН"
            CounterViewVerdict.MIXED ->
                "КОНТРРАКУРС • ЕСТЬ СПОР"
            CounterViewVerdict.REFUTED ->
                "КОНТРРАКУРС • КОНТРФАКТ"
        }
    }

    private fun counterViewMetric(
        result: CounterViewResult
    ): String {
        return "Проверено ${result.reviewedCount}/5 • предел: ${
            decisionTitle(result.decisionCeiling)
        }"
    }

    private fun counterViewExplanation(
        result: CounterViewResult
    ): String {
        val next = result.nextFactor?.let { factor ->
            " Следующая проверка: «${factor.title}»."
        }.orEmpty()
        return when (result.verdict) {
            CounterViewVerdict.OPEN -> {
                if (result.reviewedCount < 3) {
                    "Альтернативная версия проверена меньше чем по " +
                        "трем факторам: допустим только пропуск.$next"
                } else {
                    "Часть альтернативной версии еще не проверена: " +
                        "вывод не выше наблюдения.$next"
                }
            }
            CounterViewVerdict.BALANCED ->
                "Все пять факторов проверены против исходной " +
                    "версии. Контрракурс не повышает сигнал и " +
                    "оставляет решение за фактическими данными."
            CounterViewVerdict.MIXED ->
                "Спорных факторов: ${result.mixedCount}. Пока " +
                    "существуют две обоснованные трактовки, вывод " +
                    "не выше наблюдения."
            CounterViewVerdict.REFUTED ->
                "Контрфактов: ${result.refutedCount}. Исходная " +
                    "версия не выдержала проверку, допустим только " +
                    "пропуск."
        }
    }

    private fun counterViewFactorLabel(
        result: CounterViewFactor
    ): String {
        return "${result.factor.title} • поддержано ${
            result.supportedValue
        }/100 • влияние ${result.readinessImpact}"
    }

    private fun evidenceTone(evidence: EvidenceAssessment): Tone {
        return when {
            evidence.levels.all { it == EvidenceLevel.QUORUM } ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            evidence.levels.any { it == EvidenceLevel.UNCONFIRMED } ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            else ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
    }

    private fun freshnessSummary(
        result: FreshnessResult,
        now: Long
    ): String {
        val degraded = (result.degradedFactors + result.expiredFactors)
            .distinct()
        if (degraded.isNotEmpty()) {
            val factors = degraded.joinToString(", ") {
                it.shortTitle.lowercase(Locale.getDefault())
            }
            return "Срок сигнала: уровень снижен • обновите: $factors"
        }
        val transitionAt = result.nextTransitionAt
            ?: return "Срок сигнала: нет подтвержденных факторов"
        val factor = result.nextTransitionFactor
            ?.shortTitle
            ?.lowercase(Locale.getDefault())
            .orEmpty()
        val duration = FreshnessFormatter.duration(transitionAt - now)
        return if (result.expiringFactors.isNotEmpty()) {
            "Срок сигнала: скоро истечет $factor • через $duration"
        } else {
            "Срок сигнала: $duration • первым устареет $factor"
        }
    }

    private fun freshnessTone(result: FreshnessResult): Tone {
        return when {
            result.expiredFactors.isNotEmpty() ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            result.degradedFactors.isNotEmpty() ||
                result.expiringFactors.isNotEmpty() ->
                Tone(AppColors.warning, AppColors.warningSoft)
            else ->
                Tone(AppColors.signal, AppColors.signalSoft)
        }
    }

    private fun evidenceColor(level: EvidenceLevel): Int {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> AppColors.danger
            EvidenceLevel.SINGLE_SOURCE -> AppColors.warning
            EvidenceLevel.QUORUM -> AppColors.accent
        }
    }

    private fun evidenceFreshnessColor(freshness: FactorFreshness): Int {
        return when (freshness.status) {
            FreshnessStatus.EXPIRED -> AppColors.danger
            FreshnessStatus.DEGRADED,
            FreshnessStatus.EXPIRING -> AppColors.warning
            FreshnessStatus.FRESH,
            FreshnessStatus.UNCONFIRMED ->
                evidenceColor(freshness.effectiveLevel)
        }
    }

    private fun evidenceButtonTitle(freshness: FactorFreshness): String {
        return when (freshness.status) {
            FreshnessStatus.UNCONFIRMED ->
                "Не подтверждено • предел 25"
            FreshnessStatus.FRESH ->
                "${freshness.claimedLevel.title} • предел ${freshness.effectiveLevel.scoreCap} • еще ${FreshnessFormatter.duration(freshness.remainingMillis ?: 0L)}"
            FreshnessStatus.EXPIRING ->
                "Скоро истечет: ${freshness.claimedLevel.title} • ${FreshnessFormatter.duration(freshness.remainingMillis ?: 0L)}"
            FreshnessStatus.DEGRADED ->
                "Устарело: ${freshness.claimedLevel.title} → ${freshness.effectiveLevel.title} • предел ${freshness.effectiveLevel.scoreCap}"
            FreshnessStatus.EXPIRED ->
                "Истекло: требуется новая проверка • предел 25"
        }
    }

    private fun decisionColor(decision: SavedDecision): Int {
        return when (decision) {
            SavedDecision.SKIP -> AppColors.danger
            SavedDecision.OBSERVE -> AppColors.warning
            SavedDecision.DATA_READY -> AppColors.accent
        }
    }

    private fun decisionTitle(decision: SavedDecision): String {
        return when (decision) {
            SavedDecision.SKIP -> "пропустить"
            SavedDecision.OBSERVE -> "наблюдать"
            SavedDecision.DATA_READY -> "факты сверены"
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        val formatter = SimpleDateFormat(
            "d MMMM, HH:mm",
            Locale.forLanguageTag("ru-RU")
        )
        return formatter.format(Date(timestamp))
    }

    private fun signedValue(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
    }

    private fun scrollToPulseFactor(target: View) {
        target.post {
            val targetRect = Rect()
            target.getDrawingRect(targetRect)
            mainScroll.offsetDescendantRectToMyCoords(
                target,
                targetRect
            )
            mainScroll.smoothScrollTo(
                0,
                (targetRect.top - dp(88)).coerceAtLeast(0)
            )
        }
    }

    private fun openPulseFactor(factor: SignalFactor) {
        decisionDeskWorkspaceExpanded = false
        pendingDecisionDeskField = null
        activePulseWorkspaceMode = PulseWorkspaceMode.LAB
        activePulseLabSection = PulseLabSection.FACTS
        state.selectedPulseWorkspaceMode = PulseWorkspaceMode.LAB
        pendingPulseStoryAction = null
        pendingPulseFactor = factor
        selectTab(1, scrollToContent = false)
    }

    private fun openPulseStory(eventId: String) {
        state.selectedEventId = eventId
        activePulseWorkspaceMode = PulseWorkspaceMode.STORY
        activePulseLabSection = PulseLabSection.ROUTE
        activeDecisionDeskSection = DecisionDeskSection.DECISION
        decisionDeskWorkspaceExpanded = false
        pendingDecisionDeskField = null
        state.selectedPulseWorkspaceMode = PulseWorkspaceMode.STORY
        pulseStoryControlsExpanded = false
        pendingPulseFactor = null
        pendingPulseStoryAction = null
        selectTab(1, scrollToContent = false)
        analysisEventAnchor?.let { target ->
            scrollToAppView(target, topOffsetDp = 10)
        }
    }

    private fun scrollToAppView(
        target: View,
        topOffsetDp: Int
    ) {
        target.post {
            val targetRect = Rect()
            target.getDrawingRect(targetRect)
            mainScroll.offsetDescendantRectToMyCoords(
                target,
                targetRect
            )
            mainScroll.smoothScrollTo(
                0,
                (targetRect.top - dp(topOffsetDp)).coerceAtLeast(0)
            )
        }
    }

    private fun rerenderContentPreservingScroll() {
        val scrollY = mainScroll.scrollY
        renderContent()
        mainScroll.post {
            mainScroll.scrollTo(
                0,
                scrollY.coerceAtMost(
                    (mainScroll.getChildAt(0)?.height ?: scrollY) -
                        mainScroll.height
                ).coerceAtLeast(0)
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun effectiveFontScale(): Float {
        val metrics = resources.displayMetrics
        val metricsScale = if (metrics.density > 0f) {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                1f,
                metrics
            ) / metrics.density
        } else {
            1f
        }
        return maxOf(
            1f,
            resources.configuration.fontScale,
            metricsScale
        )
    }

    private fun heroHeightDp(): Int {
        val configuration = resources.configuration
        return when {
            configuration.fontScale >= 1.8f &&
                configuration.screenWidthDp < 360 -> 280
            configuration.fontScale >= 1.8f -> 248
            configuration.fontScale >= 1.3f &&
                configuration.screenWidthDp < 360 -> 220
            configuration.fontScale >= 1.3f -> 196
            configuration.screenHeightDp < 840 -> 96
            else -> 132
        }
    }

    private fun searchControlHeightDp(): Int {
        return if (resources.configuration.fontScale >= 1.8f) {
            58
        } else {
            50
        }
    }

    private fun findFirstEditText(root: View): EditText? {
        if (root is EditText) return root
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index ->
            findFirstEditText(root.getChildAt(index))?.let {
                return it
            }
        }
        return null
    }

    private fun matchWrap(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }

    private fun matchFixed(
        height: Int,
        top: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(height)
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }

    private fun imageHeaderHeight(baseDp: Int = 112): Int {
        val extra = (
            (resources.configuration.fontScale - 1f)
                .coerceAtLeast(0f) * 30f
            ).toInt()
        return baseDp + extra + compactLargeTextExtraDp(64)
    }

    private fun compactLargeTextExtraDp(extraDp: Int): Int {
        val configuration = resources.configuration
        return if (
            configuration.fontScale >= 1.8f &&
            configuration.screenWidthDp < 360
        ) {
            extraDp
        } else {
            0
        }
    }

    private fun fixedControlTextSize(baseSp: Float): Float {
        val fontScale = resources.configuration.fontScale
            .coerceAtLeast(1f)
        return baseSp * min(fontScale, 1.25f) / fontScale
    }

    private fun wrapWrap(right: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            rightMargin = dp(right)
            bottomMargin = dp(bottom)
        }
    }

    private fun frameMatch(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private data class Tone(
        val foreground: Int,
        val background: Int
    )

    private data class EventStoryPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val time: TextView,
        val body: TextView,
        val rail: EventStoryView,
        val chapterLabels: Map<EventStoryChapter, TextView>,
        val action: TextView,
        val share: TextView,
        val footer: TextView,
        val onAction: (EventStoryAction, SignalFactor?) -> Unit
    )

    private data class VerificationCommandPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val summary: TextView,
        val chart: VerificationCommandView,
        val tasks: LinearLayout,
        val footer: TextView,
        val onTaskSelected: (VerificationCommandTask) -> Unit
    )

    private data class DataDuelPanel(
        val root: LinearLayout,
        val badge: TextView,
        val score: TextView,
        val summary: TextView,
        val chart: DataDuelView,
        val leftLimit: TextView,
        val rightLimit: TextView,
        val footer: TextView,
        val leftTitle: String,
        val rightTitle: String
    )

    private data class ChronoLensPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val body: TextView,
        val chart: ChronoLensView,
        val selectedTime: TextView,
        val horizon: TextView,
        val seekBar: SeekBar,
        val markets: TextView,
        val limit: TextView,
        val nextButton: TextView,
        val footer: TextView
    )

    private data class EvidenceRelayPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val start: TextView,
        val body: TextView,
        val chart: EvidenceRelayView,
        val legend: TextView,
        val plan: TextView,
        val action: TextView,
        val footer: TextView
    )

    private data class PreflightProtocolPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val start: TextView,
        val body: TextView,
        val chart: PreflightProtocolView,
        val plan: TextView,
        val sync: TextView,
        val action: TextView,
        val footer: TextView
    )

    private data class CounterViewPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val body: TextView,
        val balanceView: CounterViewBalanceView,
        val factorLabels: Map<SignalFactor, TextView>,
        val footer: TextView
    )

    private data class ConfidenceShadowPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val body: TextView,
        val action: TextView
    )

    private data class DecisionCorridorPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val chart: DecisionCorridorView,
        val body: TextView,
        val rule: TextView,
        val action: TextView
    )

    private data class SignalStressPanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val timeline: SignalStressTimelineView,
        val body: TextView,
        val deadline: TextView,
        val action: TextView
    )

    private data class DecisionTracePanel(
        val root: LinearLayout,
        val badge: TextView,
        val metric: TextView,
        val status: TextView,
        val summary: TextView,
        val changes: LinearLayout
    )

    private data class DecisionGuardPanel(
        val root: LinearLayout,
        val badge: TextView,
        val seal: TextView,
        val metric: TextView,
        val chart: DecisionGuardView,
        val body: TextView,
        val deadline: TextView,
        val action: TextView,
        val share: TextView
    )

    private companion object {
        const val STATE_ACTIVE_TAB = "active_tab"
        const val STATE_FILTER = "sport_filter"
        const val STATE_EVENT_SEARCH_QUERY =
            "event_search_query"
        const val STATE_FEED_TIME_FILTER =
            "feed_time_filter"
        const val STATE_FEED_WORKSPACE_MODE =
            "feed_workspace_mode"
        const val STATE_SAVED_ONLY = "saved_only"
        const val STATE_FOCUS_EVENT_LIMIT =
            "focus_event_limit"
        const val STATE_API_UPDATE_PULSE_EXPANDED =
            "api_update_pulse_expanded"
        const val STATE_SOURCE_READINESS_DETAILS_EXPANDED =
            "source_readiness_details_expanded"
        const val STATE_MARKET_LENS_KIND =
            "market_lens_kind"
        const val STATE_PULSE_WORKSPACE_MODE =
            "pulse_workspace_mode"
        const val STATE_PULSE_LAB_SECTION =
            "pulse_lab_section"
        const val STATE_DECISION_DESK_SECTION =
            "decision_desk_section"
        const val STATE_DECISION_DESK_WORKSPACE_EXPANDED =
            "decision_desk_workspace_expanded"
        const val STATE_UPDATE_RADAR_EXPANDED =
            "update_radar_expanded"
        const val STATE_DECISION_LEDGER_EXPANDED =
            "decision_ledger_expanded"
        const val STATE_PULSE_STORY_CONTROLS_EXPANDED =
            "pulse_story_controls_expanded"
        const val STATE_PLAIN_ANALYTICS_PROTOCOL_EXPANDED =
            "plain_analytics_protocol_expanded"
        const val STATE_PLAIN_ANALYTICS_PROTOCOL_EVENT_ID =
            "plain_analytics_protocol_event_id"
        const val REQUEST_EVENT_PACKAGE = 410
        const val COLLAPSED_PACKAGE_CHANGES = 4
        const val COLLAPSED_API_CHANGES = 4
        const val COLLAPSED_LEDGER_RECORDS = 3
        const val FOCUS_EVENT_PAGE_SIZE =
            MatchCenterPolicy.DEFAULT_VISIBLE_COUNT
    }
}
