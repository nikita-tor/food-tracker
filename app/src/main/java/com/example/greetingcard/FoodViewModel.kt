package com.example.greetingcard

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.greetingcard.data.AppData
import com.example.greetingcard.data.Backup
import com.example.greetingcard.data.CalorieMode
import com.example.greetingcard.data.Day
import com.example.greetingcard.data.FoodEntry
import com.example.greetingcard.data.Goal
import com.example.greetingcard.data.Guidelines
import com.example.greetingcard.data.History
import com.example.greetingcard.data.HistoryPeriod
import com.example.greetingcard.data.Limit
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.LookupResult
import com.example.greetingcard.data.Meal
import com.example.greetingcard.data.MenuItem
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.Nutrients
import com.example.greetingcard.data.OpenFoodFacts
import com.example.greetingcard.data.Portion
import com.example.greetingcard.data.Profile
import com.example.greetingcard.data.Restaurants
import com.example.greetingcard.data.SavedFood
import com.example.greetingcard.data.SearchHit
import com.example.greetingcard.data.SearchResult
import com.example.greetingcard.data.Settings
import com.example.greetingcard.data.Store
import com.example.greetingcard.data.WholeFood
import com.example.greetingcard.data.WholeFoods
import com.example.greetingcard.data.newId
import com.example.greetingcard.data.total
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale

class FoodViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val saveLock = Mutex()
    private var pendingSave: Job? = null

    var data by mutableStateOf(AppData())
        private set
    var loaded by mutableStateOf(false)
        private set
    var date: LocalDate by mutableStateOf(LocalDate.now())
        private set
    var lookingUpBarcode by mutableStateOf(false)
        private set

    /** The bundled common-foods list, loaded once at startup. */
    var wholeFoods by mutableStateOf<List<WholeFood>>(emptyList())
        private set

    /** The bundled restaurant menus, loaded once at startup. */
    var menuItems by mutableStateOf<List<MenuItem>>(emptyList())
        private set

    /** The latest online search for packaged foods, started from Add food. */
    var onlineSearch by mutableStateOf<OnlineSearch>(OnlineSearch.Idle)
        private set
    private var searchJob: Job? = null

    sealed interface OnlineSearch {
        /** What was searched for, or null before any search. */
        val query: String?

        data object Idle : OnlineSearch {
            override val query = null
        }

        data class Loading(override val query: String) : OnlineSearch
        data class Done(override val query: String, val hits: List<SearchHit>) : OnlineSearch
        data class Failed(override val query: String, val reason: String) : OnlineSearch
    }

    /** One-off messages for the snackbar, with an optional undo action. */
    var message by mutableStateOf<Message?>(null)
        private set

    class Message(val text: String, val undo: (() -> Unit)? = null) {
        // Unique per message, so showing the same text twice still triggers the snackbar.
        val id = nextId++

        private companion object {
            var nextId = 0L
        }
    }

    val backStack = mutableStateListOf<Screen>(Screen.Today)
    val screen: Screen get() = backStack.last()

    init {
        viewModelScope.launch {
            data = store.load()
            loaded = true
        }
        viewModelScope.launch {
            wholeFoods = withContext(Dispatchers.IO) {
                WholeFoods.parse(app.assets.open(WholeFoods.ASSET).bufferedReader().use { it.readText() })
            }
        }
        viewModelScope.launch {
            menuItems = withContext(Dispatchers.IO) {
                Restaurants.parse(app.assets.open(Restaurants.ASSET).bufferedReader().use { it.readText() })
            }
        }
    }

    // --- Navigation ---

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun switchTab(tab: Screen) {
        backStack.clear()
        backStack.add(tab)
    }

    /** Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Opens Add food for [meal], or for the meal that fits the time of day. Starts with an empty search. */
    fun openAddFood(meal: Meal = Meal.forHour(LocalTime.now().hour)) {
        addFoodQuery = ""
        navigate(Screen.AddFood(meal))
    }

    /** Switches the meal Add food logs to, keeping what was typed in the search. */
    fun switchAddFoodMeal(meal: Meal) {
        if (screen is Screen.AddFood) backStack[backStack.lastIndex] = Screen.AddFood(meal)
    }

    /** The Add food search text. Kept here so it's still there after adding a food from the form. */
    var addFoodQuery by mutableStateOf("")

    /** Returns to the tab the user started from, closing any add-food screens on the way. */
    private fun backToTab() {
        while (screen is Screen.FoodForm || screen is Screen.AddFood) {
            if (!back()) break
        }
    }

    // --- Derived data ---

    /** True until the user has entered their name on the welcome screen. */
    val needsName: Boolean get() = data.settings.name.isBlank()
    val name: String get() = data.settings.name
    val calorieMode: CalorieMode get() = data.settings.calorieMode
    val showCalories: Boolean get() = calorieMode == CalorieMode.SHOW
    val trackedNutrients: List<Nutrient> get() = data.settings.trackedNutrients

    /** Goals the user tracks, for everything except the Goals tab. */
    val goals: List<Goal> get() = allGoals.filter { it.nutrient in trackedNutrients }

    /** Every goal, including ones turned off, for the Goals tab. */
    val allGoals: List<Goal> get() = Guidelines.goals(data.settings)

    val trackedLimits: List<LimitNutrient> get() = data.settings.trackedLimits

    /** Limits the user turned on. Empty by default. */
    val limits: List<Limit> get() = allLimits.filter { it.nutrient in trackedLimits }

    /** Every limit, including ones turned off, for the Goals tab. */
    val allLimits: List<Limit> get() = Guidelines.limits(data.settings)

    /** How many of [day]'s foods have no data for [limit], so its total may be too low. */
    fun unknownCount(day: LocalDate, limit: LimitNutrient): Int = entriesFor(day).count { it.perServing[limit] == null }

    fun entriesFor(day: LocalDate): List<FoodEntry> = data.entries.filter { it.date == day.toString() }

    fun totalsFor(day: LocalDate): Nutrients = dailyTotals[day.toString()] ?: Nutrients.ZERO

    // Today and History read per-day totals many times per frame. Entries only change when food is logged
    // (not on every note keystroke), so the totals are worked out again only when the list itself changes.
    private var totalsCache: Pair<List<FoodEntry>, Map<String, Nutrients>>? = null

    /** What was eaten each day, keyed by ISO date. */
    private val dailyTotals: Map<String, Nutrients>
        get() {
            val entries = data.entries
            totalsCache?.let { (cachedFor, totals) -> if (cachedFor === entries) return totals }
            return History.totalsByDate(entries).also { totalsCache = entries to it }
        }

    /** Saved foods, most-used first. */
    val savedFoods: List<SavedFood>
        get() = data.saved.sortedWith(
            compareByDescending<SavedFood> { it.useCount }.thenByDescending { it.lastUsed },
        )

    // --- Day navigation ---

    fun previousDay() {
        date = date.minusDays(1)
    }

    fun nextDay() {
        date = date.plusDays(1)
    }

    fun goToToday() {
        date = today
    }

    /** The current calendar day, refreshed when the app comes back to the front (see [onResume]). */
    var today: LocalDate by mutableStateOf(LocalDate.now())
        private set

    /**
     * Called whenever the app comes to the front. If the date changed while it was in the background,
     * anything that was showing "today" moves to the new today, so food isn't logged to yesterday by mistake.
     * A day the user picked on purpose stays put.
     */
    fun onResume() {
        val now = LocalDate.now()
        if (now == today) return
        if (date == today) date = now
        if (historyAnchor == today) historyAnchor = now
        today = now
    }

    // --- History ---

    // The History tab shows the week or month containing this day. Kept here so it survives switching tabs.
    private var historyAnchor: LocalDate by mutableStateOf(LocalDate.now())

    var historyPeriod by mutableStateOf(HistoryPeriod.MONTH)
        private set

    /** From the phone's locale: Sunday in the US, Monday in most of Europe. */
    val firstDayOfWeek: DayOfWeek get() = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    val historyRange: ClosedRange<LocalDate> get() = History.range(historyPeriod, historyAnchor, firstDayOfWeek)

    /** False on the current week or month: there's nothing to see in the future. */
    val canGoForwardInHistory: Boolean get() = historyRange.endInclusive < today

    val historyDays: List<Day> get() = historyRange.let { History.days(it.start, it.endInclusive, dailyTotals) }

    fun selectHistoryPeriod(period: HistoryPeriod) {
        historyPeriod = period
    }

    fun previousHistoryPeriod() = moveHistory(-1)

    fun nextHistoryPeriod() = moveHistory(1)

    private fun moveHistory(by: Long) {
        historyAnchor = when (historyPeriod) {
            HistoryPeriod.WEEK -> historyAnchor.plusWeeks(by)
            HistoryPeriod.MONTH -> historyAnchor.plusMonths(by)
        }
    }

    /** Days in a row, up to today, on which every one of [goals] was met. */
    fun streak(goals: List<Goal>): Int = History.streak(goals, dailyTotals, today)

    /** Days in a row, up to today, spent within [limit]. */
    fun limitStreak(limit: Limit): Int = History.limitStreak(limit, dailyTotals, today)

    /** Opens [day] on the Today tab, from a tap on the calendar. */
    fun openDay(day: LocalDate) {
        date = day
        switchTab(Screen.Today)
    }

    // --- Logging food ---

    fun quickAdd(food: SavedFood, meal: Meal) {
        val entry = FoodEntry(
            date = date.toString(),
            meal = meal,
            name = food.name,
            servingLabel = food.servingLabel,
            perServing = applyCalorieMode(food.perServing),
        )
        update { d ->
            d.copy(
                entries = d.entries + entry,
                saved = d.saved.replace(food.id) {
                    it.copy(useCount = it.useCount + 1, lastUsed = System.currentTimeMillis())
                },
            )
        }
        message = Message("Added ${food.name} to ${meal.label.lowercase()}") { removeEntry(entry.id) }
    }

    /** Saves the result of the food form: logs it (if [meal] is set) and/or saves it for quick add. */
    fun submitFood(
        meal: Meal?,
        draft: FoodDraft,
        name: String,
        servingLabel: String,
        servings: Double,
        perServing: Nutrients,
        saveForLater: Boolean,
    ) {
        val nutrients = applyCalorieMode(perServing)
        update { d ->
            // Logging the same food from search again updates its saved copy instead of adding a duplicate.
            val existing = d.saved.firstOrNull {
                !it.isMeal && it.name.equals(name, ignoreCase = true) &&
                    it.servingLabel.equals(servingLabel, ignoreCase = true)
            }
            val used = if (meal != null) 1 else 0
            val saved = when {
                draft.editingSavedId != null -> d.saved.replace(draft.editingSavedId) {
                    it.copy(name = name, servingLabel = servingLabel, perServing = nutrients)
                }

                saveForLater && existing != null -> d.saved.replace(existing.id) {
                    it.copy(
                        perServing = nutrients,
                        barcode = draft.barcode ?: it.barcode,
                        useCount = it.useCount + used,
                        lastUsed = System.currentTimeMillis(),
                    )
                }

                saveForLater -> d.saved + SavedFood(
                    name = name,
                    servingLabel = servingLabel,
                    perServing = nutrients,
                    barcode = draft.barcode,
                    useCount = used,
                    lastUsed = System.currentTimeMillis(),
                )

                else -> d.saved
            }
            val entries = if (meal == null) {
                d.entries
            } else {
                d.entries + FoodEntry(
                    date = date.toString(),
                    meal = meal,
                    name = name,
                    servingLabel = servingLabel,
                    servings = servings,
                    perServing = nutrients,
                )
            }
            d.copy(entries = entries, saved = saved)
        }
        // Back to Add food when the form came from there, so the next item of the meal is one search away.
        val fromAddFood = backStack.getOrNull(backStack.lastIndex - 1) is Screen.AddFood
        if (meal != null && fromAddFood) back() else backToTab()
        message = Message(
            when {
                meal != null -> "Added $name to ${meal.label.lowercase()}"
                draft.editingSavedId != null -> "Updated $name"
                else -> "Saved $name"
            },
        )
    }

    /** Changes how much of an entry was eaten, and which meal it belongs to. */
    fun updateEntry(entryId: String, servings: Double, meal: Meal) = update { d ->
        d.copy(entries = d.entries.map { if (it.id == entryId) it.copy(servings = servings, meal = meal) else it })
    }

    fun deleteEntry(entryId: String) {
        val entry = data.entries.firstOrNull { it.id == entryId } ?: return
        removeEntry(entryId)
        message = Message("Removed ${entry.name}") { update { d -> d.copy(entries = d.entries + entry) } }
    }

    private fun removeEntry(entryId: String) = update { d ->
        d.copy(entries = d.entries.filterNot { it.id == entryId })
    }

    /** What was logged for [meal] the day before the one being viewed. */
    fun yesterdaysEntries(meal: Meal): List<FoodEntry> = entriesFor(date.minusDays(1)).filter { it.meal == meal }

    /** Logs the same foods as the day before for [meal], for people who eat the same breakfast daily. */
    fun repeatYesterday(meal: Meal) {
        val copies = yesterdaysEntries(meal).map { it.copy(id = newId(), date = date.toString()) }
        if (copies.isEmpty()) return
        val ids = copies.map { it.id }.toSet()
        update { d -> d.copy(entries = d.entries + copies) }
        val what = copies.singleOrNull()?.name ?: "${copies.size} foods"
        message = Message("Added $what to ${meal.label.lowercase()}") {
            update { d -> d.copy(entries = d.entries.filterNot { it.id in ids }) }
        }
    }

    /** Saves everything logged for [meal] on the current day as one quick-add item. */
    fun saveMealAsFavorite(meal: Meal, name: String) {
        val items = entriesFor(date).filter { it.meal == meal }
        if (items.isEmpty()) return
        val food = SavedFood(
            name = name,
            servingLabel = items.joinToString(", ") { it.name },
            perServing = items.map { it.total }.total(),
            isMeal = true,
            lastUsed = System.currentTimeMillis(),
        )
        update { d -> d.copy(saved = d.saved + food) }
        message = Message("Saved \"$name\" for quick add")
    }

    fun deleteSaved(id: String) {
        val food = data.saved.firstOrNull { it.id == id } ?: return
        update { d -> d.copy(saved = d.saved.filterNot { it.id == id }) }
        message = Message("Deleted ${food.name}") { update { d -> d.copy(saved = d.saved + food) } }
    }

    // --- Daily notes ---

    /** When a note last reached the file, so Today can briefly say "Saved". */
    var noteSaved by mutableStateOf<NoteSaved?>(null)
        private set

    /** [atMillis] is from [SystemClock.elapsedRealtime]. */
    class NoteSaved(val day: LocalDate, val atMillis: Long)

    // A note edit not yet confirmed as written: which day, and a count to tell whether more typing came in since.
    private var unsavedNoteDay: LocalDate? = null
    private var noteEdits = 0

    fun noteFor(day: LocalDate): String = data.notes[day.toString()].orEmpty()

    /** Called on every keystroke, so the file is written once typing pauses instead of each time. */
    fun setNote(day: LocalDate, text: String) {
        val key = day.toString()
        update(saveDelayMs = NOTE_SAVE_DELAY_MS) { d ->
            d.copy(notes = if (text.isBlank()) d.notes - key else d.notes + (key to text))
        }
        unsavedNoteDay = day
        noteEdits++
    }

    // --- Food search ---

    /** Opens the food form for one [portion] of a common food, ready to log or adjust. */
    fun pickWholeFood(food: WholeFood, portion: Portion, meal: Meal) {
        val draft = FoodDraft(
            name = food.name,
            servingLabel = WholeFoods.servingLabel(portion),
            perServing = WholeFoods.nutrientsFor(food, portion),
            note = WholeFoods.SOURCE_NOTE,
        )
        navigate(Screen.FoodForm(meal, draft))
    }

    /** Opens the food form for a restaurant menu item, ready to log or adjust. */
    fun pickMenuItem(item: MenuItem, meal: Meal) {
        val draft = FoodDraft(
            name = Restaurants.displayName(item),
            servingLabel = item.serving,
            perServing = item.nutrients,
            note = Restaurants.sourceNote(item),
        )
        navigate(Screen.FoodForm(meal, draft))
    }

    /** Searches Open Food Facts for packaged foods. Only on request: the service doesn't allow search-as-you-type. */
    fun searchOnline(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        onlineSearch = OnlineSearch.Loading(q)
        searchJob = viewModelScope.launch {
            onlineSearch = when (val result = OpenFoodFacts.search(q)) {
                is SearchResult.Found -> OnlineSearch.Done(q, result.hits)
                is SearchResult.Failed -> OnlineSearch.Failed(q, result.reason)
            }
        }
    }

    // --- Barcodes ---

    fun onBarcode(code: String, meal: Meal) {
        val known = data.saved.firstOrNull { it.barcode == code }
        if (known != null) {
            // Stays on Add food, like tapping a saved food there.
            quickAdd(known, meal)
            return
        }
        lookingUpBarcode = true
        viewModelScope.launch {
            val result = OpenFoodFacts.lookup(code)
            lookingUpBarcode = false
            // The user went back while the lookup ran: don't open the form on top of another screen.
            // If they switched meals meanwhile, the food goes to the one now picked.
            val logTo = (screen as? Screen.AddFood)?.meal ?: return@launch
            when (result) {
                is LookupResult.Found -> {
                    val product = result.product
                    val draft = FoodDraft(
                        name = product.name,
                        servingLabel = product.servingLabel,
                        perServing = product.perServing,
                        barcode = code,
                    )
                    navigate(Screen.FoodForm(logTo, draft))
                }

                LookupResult.NotFound -> {
                    message = Message("No product found for $code. Enter it manually below.")
                    navigate(Screen.FoodForm(logTo, FoodDraft(barcode = code)))
                }

                is LookupResult.Failed -> message = Message("Barcode lookup failed: ${result.reason}")
            }
        }
    }

    fun showMessage(text: String) {
        message = Message(text)
    }

    // --- Backups ---

    /** A backup the user picked to restore, waiting for them to confirm. */
    var pendingRestore by mutableStateOf<Backup.Result.Ok?>(null)
        private set

    /** Writes a backup of everything to [uri], a file the user created with Android's file picker. */
    fun saveBackup(uri: Uri) {
        // The backup records its own date, so after restoring it the Goals tab doesn't say "No backup yet".
        val backedUp = data.copy(settings = data.settings.copy(lastBackup = today.toString()))
        val text = Backup.encode(backedUp, LocalDateTime.now().withNano(0).toString(), BuildConfig.VERSION_NAME)
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("the file couldn't be opened")
                    stream.use { it.write(text.toByteArray()) }
                }
            }
            message = if (written.isSuccess) {
                updateSettings { it.copy(lastBackup = today.toString()) }
                Message("Backup saved")
            } else {
                Message("Couldn't save the backup: ${written.exceptionOrNull()?.message}")
            }
        }
    }

    /** Reads the backup at [uri] and, if it's one of ours, asks the user to confirm restoring it. */
    fun openBackup(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.readBytes().decodeToString()
                    }
                }.getOrNull()
            }
            when (val result = text?.let(Backup::decode) ?: Backup.Result.Invalid("Couldn't open that file.")) {
                is Backup.Result.Ok -> pendingRestore = result
                is Backup.Result.Invalid -> message = Message(result.reason)
            }
        }
    }

    /** Replaces everything with the confirmed backup. Undo puts back what was there before. */
    fun confirmRestore() {
        val backup = pendingRestore ?: return
        pendingRestore = null
        val previous = data
        update { backup.data }
        goToToday()
        message = Message("Backup restored") { update { previous } }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    // --- Settings ---

    fun setName(name: String) = updateSettings { it.copy(name = name.trim()) }

    fun setCalorieMode(mode: CalorieMode) = updateSettings { it.copy(calorieMode = mode) }

    fun setProfile(profile: Profile) = updateSettings { it.copy(profile = profile) }

    fun setCustomGoal(nutrient: Nutrient, value: Double?) = updateSettings {
        it.copy(customGoals = if (value == null) it.customGoals - nutrient else it.customGoals + (nutrient to value))
    }

    /** Turns a goal on or off. The last tracked goal can't be turned off, or Today would have nothing to show. */
    fun setTracked(nutrient: Nutrient, tracked: Boolean) = updateSettings {
        when {
            tracked -> it.copy(hiddenNutrients = it.hiddenNutrients - nutrient)
            it.trackedNutrients == listOf(nutrient) -> it
            else -> it.copy(hiddenNutrients = it.hiddenNutrients + nutrient)
        }
    }

    fun setLimitEnabled(limit: LimitNutrient, enabled: Boolean) = updateSettings {
        it.copy(enabledLimits = if (enabled) it.enabledLimits + limit else it.enabledLimits - limit)
    }

    fun setCustomLimit(limit: LimitNutrient, value: Double?) = updateSettings {
        it.copy(customLimits = if (value == null) it.customLimits - limit else it.customLimits + (limit to value))
    }

    private fun updateSettings(change: (Settings) -> Settings) = update { d -> d.copy(settings = change(d.settings)) }

    // --- Internals ---

    private fun applyCalorieMode(n: Nutrients) = if (calorieMode == CalorieMode.OFF) n.copy(calories = null) else n

    private fun update(saveDelayMs: Long = 0, change: (AppData) -> AppData) {
        data = change(data)
        // A newer save always writes the latest data, so any save still waiting can be dropped.
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(saveDelayMs)
            saveNow()
        }
    }

    /** Writes a delayed save right away. Called when the app goes to the background. */
    fun flushPendingSave() {
        if (pendingSave?.isActive == true) {
            pendingSave?.cancel()
            viewModelScope.launch { saveNow() }
        }
    }

    // Saves run one at a time, and each writes whatever the data is when its turn comes.
    private suspend fun saveNow() {
        val edits = noteEdits
        saveLock.withLock { store.save(data) }
        // Only say "Saved" if no typing came in while this save ran; otherwise the save after it will.
        val day = unsavedNoteDay
        if (day != null && edits == noteEdits) {
            unsavedNoteDay = null
            noteSaved = NoteSaved(day, SystemClock.elapsedRealtime())
        }
    }

    private companion object {
        const val NOTE_SAVE_DELAY_MS = 600L
    }
}

/** Replaces the saved food with [id], leaving the rest of the list untouched. */
private fun List<SavedFood>.replace(id: String, change: (SavedFood) -> SavedFood) =
    map { if (it.id == id) change(it) else it }
