package com.example.blacksmithproject

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blacksmithproject.GameSession.Op
import com.example.blacksmithproject.GameSession.Result
import com.example.blacksmithproject.GameSession.Status
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.SaveStore
import com.example.blacksmithproject.data.Settings
import com.example.blacksmithproject.data.SettingsStore
import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.ui.ForgeLearningUi
import com.example.blacksmithproject.ui.ShopUi
import com.example.blacksmithproject.ui.forgeLearning
import com.example.blacksmithproject.ui.untriedPairing
import com.example.blacksmithproject.ui.detail.StockAction
import com.example.blacksmithproject.ui.detail.toCommand
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.shopday.ShopDayUiModel
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.legacy.RunEndResult
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.shopday.ShopDayScript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The four places the player goes (plan 1.2); the bar shows these and nothing else. */
enum class Dest { SHOP, FORGE, TOWN, RECORDS }

/** The three segments of Records; GAZETTE is the one labelled "News". */
enum class RecordsPage { GAZETTE, JOURNAL, LEGACY }

/** The detail sheet that is open over the workshop: only who or what it shows; its content is read from the save on every render. */
sealed interface Sheet {
    data class Hero(val id: HeroId) : Sheet
    data class Item(val id: WeaponId) : Sheet
}

data class ForgeDraft(
    val mode: ForgeMode = ForgeMode.QUICK,
    val familyId: WeaponFamilyId? = null,
    val coreId: MaterialId? = null,
    val augmentId: MaterialId? = null,
    val catalystId: MaterialId? = null,
    val risk: Risk = Risk.BALANCED,
    val technique: Technique? = null,
    /** The request this draft was started from ("Forge this"); the forge shows it while that request is open. */
    val commissionId: CommissionId? = null,
)

/** What is on screen. Game state in it is always the session's snapshot (what is saved); [op] is the session's status. */
sealed interface UiState {
    val op: Status get() = Status.Idle

    data object Loading : UiState
    /** The save could not be loaded. [working] while Retry or Start over is reading the store again. */
    data class LoadFailed(val failure: SaveFailure, val working: Boolean = false) : UiState
    data class Title(val legacy: LegacyProfile, override val op: Status = Status.Idle) : UiState
    data class Playing(
        val state: GameState,
        /** The Shop destination's content, built from [state] off the main thread. */
        val shop: ShopUi,
        /** The next siege as the engine weighs it today (Town), computed once per [state] off the main thread; null when no faction presses. */
        val forecast: Battle.SiegeOutlook? = null,
        val dest: Dest = Dest.SHOP,
        val records: RecordsPage = RecordsPage.GAZETTE,
        val draft: ForgeDraft = ForgeDraft(),
        val revealWeaponId: WeaponId? = null,
        val lastError: String? = null,
        override val op: Status = Status.Idle,
        /** Day on which the player chose "Decide later" for the blessing offer; UI-only, the offer itself stays in core state. */
        val blessingOfferDismissedDay: Int? = null,
        val sheet: Sheet? = null,
        /** Where a blade just went (the shelf or storage), said once over the workshop; UI-only. */
        val notice: String? = null,
        /** Raised by "Forge this" and "Use this recipe": the Forge then opens the step to choose next and scrolls to it. UI-only. */
        val forgeReveal: Int = 0,
        /** What the forge whose result is open added to the journal; null on a result reopened after a restart. UI-only. */
        val learning: ForgeLearningUi? = null,
    ) : UiState {
        val busy: Boolean get() = op is Status.Working
    }
    /**
     * The last resolved day while the player has not watched it to its end (the day cursor is not at DONE). [state] is
     * the saved game after that day; [script] is derived from it and its stored record, never from RNG. Nothing here
     * changes the save except the blessing choice. [resumed]: the game was opened onto this day (the Resume prompt).
     * [model] is the script laid out for the screen, built with it off the main thread.
     */
    data class ShopDay(
        val state: GameState,
        val script: ShopDayScript,
        val model: ShopDayUiModel,
        val position: ShopDayPosition,
        val speed: ShopDaySpeed = ShopDaySpeed.TAP,
        val sheet: Sheet? = null,
        val gazetteOpen: Boolean = false,
        val resumed: Boolean = false,
        override val op: Status = Status.Idle,
        val lastError: String? = null,
    ) : UiState {
        val busy: Boolean get() = op is Status.Working
        /** False where system back opens the main menu: the Resume prompt and the day's first card. */
        val backIsConsumed: Boolean get() = !resumed && (gazetteOpen || sheet != null || !position.isFirst)
    }
    /** The ended run stays stored, so this screen is rebuilt from it and the legacy row after any restart. */
    data class RunEnded(
        val run: GameState,
        val runEnd: RunEndResult,
        val legacy: LegacyProfile,
        val claimed: Boolean,
        val lastError: String? = null,
        override val op: Status = Status.Idle,
    ) : UiState {
        val busy: Boolean get() = op is Status.Working
    }
}

/**
 * UI is an observer (GDD 13.2): every operation goes through [GameSession], which saves an accepted result before
 * publishing it. This class keeps only what the screen adds (destination, forge draft, open reveal, messages) and
 * never computes gameplay outcomes. The destination, draft and reveal are also kept in [saved], so they come back
 * after the system has killed the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    val engine: GameEngine,
    private val session: GameSession,
    val settings: Settings,
    private val saved: SavedStateHandle,
    private val compute: CoroutineDispatcher = Dispatchers.Default,   // builds the shop day (script and screen model), the Shop's content and the siege forecast off the main thread
    private val buildScript: (DayResolution, GameState, ContentCatalog, BalanceConfig) -> ShopDayScript = ShopDay::script,
) : ViewModel() {
    private data class Local(
        val dest: Dest = Dest.SHOP,
        val records: RecordsPage = RecordsPage.GAZETTE,
        val draft: ForgeDraft = ForgeDraft(),
        val revealWeaponId: WeaponId? = null,
        val blessingOfferDismissedDay: Int? = null,
        val sheet: Sheet? = null,
        val lastError: String? = null,
        val notice: String? = null,
        val forgeReveal: Int = 0,
        val learning: ForgeLearningUi? = null,
        val loadFailure: SaveFailure? = null,
        /** True until the first load (and the hand-over of the old report key) has finished, and again during Retry. */
        val loading: Boolean = true,
        /** The shop day on screen: the card reached in [dayId] (the day's command ID); another day starts from its cursor. */
        val dayId: String? = null,
        val dayAt: Int = 0,
        val gazetteOpen: Boolean = false,
        val resumed: Boolean = false,
        val speed: ShopDaySpeed = ShopDaySpeed.TAP,
    )

    private val local = MutableStateFlow(restored())
    /** closeRun of the ended run, computed once per run instead of on every emission. */
    private var closed: Pair<GameState, RunEndResult>? = null

    private class Day(val script: ShopDayScript, val model: ShopDayUiModel)
    private class Plan(val shop: ShopUi, val forecast: Battle.SiegeOutlook?)

    /** The script of the unwatched day and its screen model, built once per saved state: moving through the day never builds them again. */
    private var script: Pair<GameState, Day>? = null

    /** A run whose last day could not be turned into a script: that day counts as watched (the Gazette still has it). */
    private var unshowable: GameState? = null

    /** The Shop destination's content and the siege forecast, built once per saved state. */
    private var shop: Pair<GameState, Plan>? = null

    /**
     * While a script is being built nothing is emitted, so the screen stays on what it showed (planning with its
     * controls locked, or the loading spinner) until the day can be shown whole.
     */
    val ui: StateFlow<UiState> = combine(session.snapshot, session.status, local, ::Triple)
        .mapLatest { (snap, op, l) -> val script = scriptFor(snap); render(snap, op, l, script, shopFor(snap)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    init {
        viewModelScope.launch { open { session.load() } }
        viewModelScope.launch { settings.shopDaySpeed.collect { speed -> local.update { it.copy(speed = speed) } } }
    }

    /** The last resolved day while it has not been watched to its end. */
    private fun unwatched(snap: GameSession.Snapshot?): DayResolution? =
        snap?.run?.takeIf { it !== unshowable }?.lastResolution?.takeIf { GameSession.pending(snap.run, snap.legacy, snap.cursor) != DayCursor.Stage.DONE }

    /**
     * A script that cannot be built must not end the [ui] flow (the game would then crash at every launch): the day is
     * treated as watched, its cursor moves to DONE so planning opens, and the Gazette still has the day.
     */
    private suspend fun scriptFor(snap: GameSession.Snapshot?): Day? {
        val last = unwatched(snap) ?: return null
        val run = snap?.run ?: return null
        script?.takeIf { it.first === run }?.let { return it.second }
        return try {
            withContext(compute) { buildScript(last, run, engine.content, engine.config).let { Day(it, it.toUi(run, engine.content, engine.config)) } }.also { script = run to it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Remembered before the cursor moves: the write re-enters this function through the session's status.
            unshowable = run
            viewModelScope.launch { session.run(Op.MoveCursor(DayCursor(last.commandId.value, DayCursor.Stage.DONE))) }
            null
        }
    }

    /** True where the workshop is on screen: a run that goes on and whose last day has been watched. */
    private fun plans(snap: GameSession.Snapshot?): Boolean = snap?.run?.isEnded == false && unwatched(snap) == null

    private suspend fun shopFor(snap: GameSession.Snapshot?): Plan? {
        val run = snap?.run?.takeIf { plans(snap) } ?: return null
        shop?.takeIf { it.first === run }?.let { return it.second }
        return withContext(compute) { planFor(run) }.also { shop = run to it }
    }

    private fun planFor(run: GameState) = engine.siegeForecast(run).let { Plan(engine.shopUi(run, it), it) }

    private fun render(snap: GameSession.Snapshot?, op: Status, l: Local, day: Day?, plan: Plan?): UiState {
        if (snap == null || l.loading) return l.loadFailure?.let { UiState.LoadFailed(it, working = l.loading) } ?: UiState.Loading
        val run = snap.run ?: return UiState.Title(snap.legacy, op)
        val last = unwatched(snap)
        // An unwatched day always arrives with its script: scriptFor builds it before this is called, and now() checks.
        if (last != null && day != null) {
            val id = last.commandId.value
            val beats = ShopDayPosition.beats(day.script)
            val at = if (l.dayId == id) l.dayAt.coerceIn(beats.indices) else ShopDayPosition.index(beats, snap.cursor?.takeIf { it.commandId == id })
            return UiState.ShopDay(run, day.script, day.model, ShopDayPosition(beats, at), l.speed, l.sheet, l.gazetteOpen, l.resumed, op, l.lastError)
        }
        if (run.isEnded && last == null) {
            val end = closed?.takeIf { it.first === run }?.second ?: engine.closeRun(run).also { closed = run to it }
            return UiState.RunEnded(run, end, snap.legacy, claimed = run.runId.value in snap.legacy.claimedRunIds, lastError = l.lastError, op = op)
        }
        // Planning always arrives with the Shop's content: shopFor builds it before this is called, and now() checks.
        val planned = plan ?: planFor(run)
        return UiState.Playing(run, planned.shop, planned.forecast, l.dest, l.records, l.draft, l.revealWeaponId, l.lastError, op, l.blessingOfferDismissedDay, l.sheet, l.notice, l.forgeReveal, l.learning)
    }

    /** The screen as it stands this instant ([ui] may be one dispatch behind, or waiting for a script). */
    private fun now(): UiState {
        val snap = session.snapshot.value
        val built = script?.takeIf { it.first === snap?.run }?.second
        if (unwatched(snap) != null && built == null) return ui.value
        val stocked = shop?.takeIf { it.first === snap?.run }?.second
        if (plans(snap) && stocked == null) return ui.value
        return render(snap, session.status.value, local.value, built, stocked)
    }

    /** A load, a retried load or a start over: the screen leaves Loading / LoadFailed only when it has finished. */
    private suspend fun open(read: suspend () -> Result) {
        local.update { it.copy(loading = true) }
        val result = read()
        if (result is Result.Done) handOverDismissedReport()
        // Opened onto a day that was resolved and saved but not watched to its end: the Resume prompt.
        local.update { it.copy(loading = false, loadFailure = (result as? Result.Failed)?.failure, resumed = unwatched(session.snapshot.value) != null) }
    }

    /**
     * 0.6.0 kept "the player closed this report" in settings. Read once: when it names the stored last day and no
     * cursor row exists yet, the cursor is written at DONE, so a save from 0.6.0 opens exactly where it was left.
     */
    private suspend fun handOverDismissedReport() {
        val snap = session.snapshot.value ?: return
        val last = snap.run?.lastResolution ?: return
        if (snap.cursor == null && settings.dismissedReport() == last.commandId.value) session.run(Op.MoveCursor(DayCursor(last.commandId.value, DayCursor.Stage.DONE)))
    }

    private fun edit(transform: (Local) -> Local) {
        val l = local.updateAndGet(transform)
        saved[KEY_DEST] = l.dest.name
        saved[KEY_RECORDS] = l.records.name
        saved[KEY_REVEAL] = l.revealWeaponId?.value
        saved[KEY_BLESSING_DAY] = l.blessingOfferDismissedDay
        saved[KEY_SHEET] = when (val sheet = l.sheet) { is Sheet.Hero -> "hero:${sheet.id.value}"; is Sheet.Item -> "item:${sheet.id.value}"; null -> null }
        saved[KEY_DRAFT] = with(l.draft) { arrayListOf(mode.name, familyId?.value, coreId?.value, augmentId?.value, catalystId?.value, risk.name, technique?.name, commissionId?.value) }
    }

    private fun restored(): Local {
        val d = saved.get<ArrayList<String?>>(KEY_DRAFT)?.takeIf { it.size >= 7 }
        return Local(
            dest = saved.get<String>(KEY_DEST)?.let { name -> Dest.entries.firstOrNull { it.name == name } } ?: Dest.SHOP,
            records = saved.get<String>(KEY_RECORDS)?.let { name -> RecordsPage.entries.firstOrNull { it.name == name } } ?: RecordsPage.GAZETTE,
            draft = if (d == null) ForgeDraft() else ForgeDraft(
                mode = ForgeMode.entries.firstOrNull { it.name == d[0] } ?: ForgeMode.QUICK,
                familyId = d[1]?.let(::WeaponFamilyId), coreId = d[2]?.let(::MaterialId), augmentId = d[3]?.let(::MaterialId), catalystId = d[4]?.let(::MaterialId),
                risk = Risk.entries.firstOrNull { it.name == d[5] } ?: Risk.BALANCED,
                technique = Technique.entries.firstOrNull { it.name == d[6] },
                commissionId = d.getOrNull(7)?.let(::CommissionId),
            ),
            revealWeaponId = saved.get<String>(KEY_REVEAL)?.let(::WeaponId),
            blessingOfferDismissedDay = saved.get<Int>(KEY_BLESSING_DAY),
            sheet = saved.get<String>(KEY_SHEET)?.let { s ->
                val id = s.substringAfter(':')
                when (s.substringBefore(':')) { "hero" -> Sheet.Hero(HeroId(id)); "item" -> Sheet.Item(WeaponId(id)); else -> null }
            },
        )
    }

    /** One op at a time from the screen: a tap while another op is working is dropped, as the disabled buttons already say. */
    private fun launch(op: Op, onDone: () -> Unit = {}) {
        if (session.status.value is Status.Working) return
        viewModelScope.launch { val result = session.run(op); show(result); if (result is Result.Done) onDone() }
    }

    private fun show(result: Result) {
        when (result) {
            is Result.Done -> edit { it.copy(lastError = null, revealWeaponId = result.accepted?.forgedWeaponId ?: it.revealWeaponId) }
            is Result.Rejected -> edit { it.copy(lastError = describe(result.error)) }
            is Result.EngineFault -> edit { it.copy(lastError = "The forge cannot do that right now.") }
            // Stale and DayNotWatched: the screen already shows why. Failed: the session holds it and SaveFailureDialog shows it.
            Result.Stale, Result.DayNotWatched, is Result.Failed -> Unit
        }
    }

    fun newRun() = beginEra(null)

    /** Debug launch extra only (MainActivity checks the debuggable flag): a fixed-seed run, and only when no run is saved. */
    fun startSeededRun(seed: Long) {
        viewModelScope.launch { if (ui.first { it !is UiState.Loading } is UiState.Title) beginEra(seed) }
    }

    /** From the title (no run) or from the run-end screen (the session refuses it until the legacy is claimed). */
    private fun beginEra(seed: Long?) {
        val snap = session.snapshot.value ?: return
        launch(Op.BeginEra(seed ?: (System.nanoTime() xor snap.legacy.eras.size.toLong()), snap.run?.runId)) { edit { Local(loading = false, speed = it.speed) } }
    }

    /** Main menu, "Abandon run": the run is discarded unclaimed and the menu offers a new game. */
    fun abandonRun() {
        val run = session.snapshot.value?.run ?: return
        launch(Op.Abandon(run.runId)) { edit { Local(loading = false, speed = it.speed) } }
    }

    /** A bar tap. Records opens on the segment it was left on. */
    fun selectDest(dest: Dest) = edit { it.copy(dest = dest) }
    /** A segment of Records, from its own row or from a link elsewhere (yesterday's news on the Shop). */
    fun selectRecords(page: RecordsPage) = edit { it.copy(dest = Dest.RECORDS, records = page) }
    fun updateDraft(transform: (ForgeDraft) -> ForgeDraft) = edit { it.copy(draft = transform(it.draft)) }
    /**
     * "Forge this" on a request: the forge opens with the family asked for and, for an element, an augment of it (one
     * in stock if there is one). The quality asked for cannot be chosen; the forge shows the request beside the draft.
     */
    fun forgeFor(id: CommissionId) {
        val run = session.snapshot.value?.run ?: return
        val asked = run.commissions[id] ?: return
        val augments = engine.content.materials(MaterialCategory.AUGMENT).filter { asked.element != null && it.element == asked.element }
        val augment = augments.firstOrNull { (run.materials[it.id] ?: 0) > 0 } ?: augments.firstOrNull()
        edit { it.copy(dest = Dest.FORGE, forgeReveal = it.forgeReveal + 1, draft = it.draft.copy(familyId = asked.familyId, augmentId = augment?.id ?: it.draft.augmentId, commissionId = id)) }
    }
    /** "Forge this" on a standing want (the Shop's lead, "Who is buying", the Forge): the forge opens with the family the hero left without. */
    fun forgeFamily(id: WeaponFamilyId) = edit { it.copy(dest = Dest.FORGE, forgeReveal = it.forgeReveal + 1, draft = it.draft.copy(familyId = id, commissionId = null)) }
    /** "Use this recipe" on a found signature in the journal: the forge opens on the whole recipe (`SignatureCatalog.recipe`). Nothing is forged. */
    fun useRecipe(recipe: Command.Forge) = edit {
        it.copy(dest = Dest.FORGE, forgeReveal = it.forgeReveal + 1, draft = ForgeDraft(recipe.mode, recipe.familyId, recipe.coreId, recipe.augmentId, recipe.catalystId, recipe.risk, recipe.technique))
    }
    fun dismissReveal() = edit { it.copy(revealWeaponId = null, learning = null) }

    /** "Use" on a notebook row: its two ingredients go onto the workbench and the rest of the draft stays. Nothing is forged. */
    fun usePairing(key: String) {
        val ids = key.substringAfter(':').split("|").takeIf { it.size == 2 } ?: return
        edit {
            val draft = when {
                key.startsWith("ca:") -> it.draft.copy(coreId = MaterialId(ids[0]), augmentId = MaterialId(ids[1]))
                key.startsWith("af:") -> it.draft.copy(augmentId = MaterialId(ids[0]), familyId = WeaponFamilyId(ids[1]))
                else -> return@edit it
            }
            it.copy(dest = Dest.FORGE, forgeReveal = it.forgeReveal + 1, draft = draft, notice = "Pairing selected. Forge when ready.")
        }
    }

    /** "Try an untried pairing": a metal and an augment in stock that were never forged together go onto the workbench. Nothing is spent. */
    fun tryUntried() {
        val run = session.snapshot.value?.run ?: return
        val pair = engine.untriedPairing(run)
        edit {
            if (pair == null) it.copy(notice = "Every metal pairing you have the stock for has been tried.")
            else it.copy(dest = Dest.FORGE, forgeReveal = it.forgeReveal + 1, draft = it.draft.copy(coreId = pair.first, augmentId = pair.second), notice = "Untried ingredients selected. No materials spent.")
        }
    }

    /**
     * "List at N" on the forge result. The card closes only once the listing is saved, and the workshop then says where
     * the blade is; a refusal leaves the card open under the engine's reason.
     */
    fun listForged(id: WeaponId, price: Int) {
        val run = session.snapshot.value?.run ?: return
        launch(Op.Dispatch(Command.ToggleShelf(id, true, price), run.runId)) {
            val now = session.snapshot.value?.run ?: return@launch
            val name = now.weapons[id]?.name ?: return@launch
            edit { it.copy(revealWeaponId = null, learning = null, notice = "$name is on the shelf at $price gold. Shelf ${now.listedWeapons().size} of ${engine.shelfSlots(now)}. Your ingredients stay selected.") }
        }
    }

    /** "Store" on the forge result, and closing it any other way: the blade stays in storage, and the workshop says so. */
    fun storeForged() {
        val name = local.value.revealWeaponId?.let { session.snapshot.value?.run?.weapons?.get(it) }?.takeIf { it.isInStorage }?.name
        edit { it.copy(revealWeaponId = null, learning = null, notice = name?.let { n -> "$n is in storage, not for sale. Your ingredients stay selected." } ?: it.notice) }
    }

    /** The notice has been shown; a newer one is left alone. */
    fun dismissNotice(shown: String) = local.update { if (it.notice == shown) it.copy(notice = null) else it }
    /** Opening or closing a sheet also puts away the last notice: it was about what was on screen before. */
    fun openSheet(sheet: Sheet) = edit { it.copy(sheet = sheet, notice = null) }
    fun closeSheet() = edit { it.copy(sheet = null, notice = null) }

    /**
     * A stock change from a blade's sheet or a Storage row. Once it is saved the screen says what happened ([notice]).
     * A blade that was listed, melted down or given away has left the place it was opened from, so its sheet closes
     * (back to Storage when it was opened from there); after a new price, an unlisting or a hone the sheet stays on the blade.
     */
    fun stock(id: WeaponId, action: StockAction) {
        val before = session.snapshot.value?.run ?: return
        val was = before.weapons[id]
        launch(Op.Dispatch(action.toCommand(id), before.runId)) {
            val now = session.snapshot.value?.run ?: return@launch
            val blade = now.weapons[id] ?: was ?: return@launch
            val name = blade.name
            val said = when (action) {
                is StockAction.ListAt -> "$name is on the shelf at ${action.price} gold. Shelf ${now.listedWeapons().size} of ${engine.shelfSlots(now)}."
                is StockAction.SetPrice -> "$name now asks ${action.price} gold."
                StockAction.Unlist -> "$name is back in storage, not for sale."
                // Only what the hone changed: a first hone raises quality, any hone restores condition.
                StockAction.Hone -> "$name was honed" + listOfNotNull(
                    "quality ${was?.quality} to ${blade.quality}".takeIf { was?.quality != blade.quality },
                    "condition ${was?.condition} to ${blade.condition}".takeIf { was?.condition != blade.condition },
                ).joinToString(", ").let { if (it.isEmpty()) "." else ": $it." }
                StockAction.Salvage -> "$name was melted down. 1 ${engine.content.material(blade.coreId).name} is back in your stock."
                StockAction.Donate -> "$name went to the town watch. Armory ${before.town.armory} to ${now.town.armory}."
            }
            val leaves = action is StockAction.ListAt || action == StockAction.Salvage || action == StockAction.Donate
            edit { it.copy(notice = said, sheet = if (leaves && it.sheet == Sheet.Item(id)) null else it.sheet) }
        }
    }
    fun dismissError() = edit { it.copy(lastError = null) }
    /** "Decide later" (and Back) on the blessing offer: the player stays where they were; the Shop's lead and End Day's note still say it waits. */
    fun dismissBlessingOffer() = edit { it.copy(blessingOfferDismissedDay = session.snapshot.value?.run?.day) }
    fun reopenBlessingOffer() = edit { it.copy(blessingOfferDismissedDay = null) }

    // The shop day (plan 6.5). These move the saved position and what is open over it; only chooseBlessing issues a command.

    private fun shopDay(): UiState.ShopDay? = now() as? UiState.ShopDay

    /**
     * Shows card [target]. The cursor row only ever moves forward. A move into the ending (Forge fallen, Blessing,
     * Tomorrow) is awaited before the card shows, because the session unlocks the blessing on it; any other move is
     * written behind the screen, where a lost write only repeats a visit after a kill.
     */
    private fun moveTo(target: Int) {
        val s = shopDay() ?: return
        val id = s.state.lastResolution?.commandId?.value ?: return
        val to = s.position.copy(at = target.coerceIn(s.position.beats.indices))
        val cursor = to.cursor(id)
        val stored = session.snapshot.value?.cursor?.takeIf { it.commandId == id }
        val ahead = stored == null || compareValuesBy(cursor, stored, { it.stage }, { it.index }) > 0
        val place = { local.update { it.copy(dayId = id, dayAt = to.at, resumed = false) } }
        when {
            !ahead -> place()
            cursor.stage == DayCursor.Stage.TOMORROW -> viewModelScope.launch { session.run(Op.MoveCursor(cursor)); place() }
            else -> { place(); viewModelScope.launch { session.run(Op.MoveCursor(cursor)) } }
        }
    }

    /** The next card. Nothing follows the Blessing card but a choice, and nothing follows the last card but [acknowledge]. */
    fun next() {
        val s = shopDay() ?: return
        if (!s.resumed && s.position.beat != Beat.Blessing && !s.position.isLast) moveTo(s.position.at + 1)
    }

    /** "Resume the day" on the Resume prompt: the day goes on from its saved card. */
    fun resumeDay() = local.update { it.copy(resumed = false) }

    /** Never asks: nothing is lost by skipping. Lands on Forge fallen, Blessing or Tomorrow. */
    fun skipDay() {
        val s = shopDay() ?: return
        if (s.position.at < s.position.ending) moveTo(s.position.ending) else resumeDay()
    }

    fun setSpeed(speed: ShopDaySpeed) = viewModelScope.launch { settings.setShopDaySpeed(speed) }
    fun openGazette() = local.update { it.copy(gazetteOpen = true) }
    fun closeGazette() = local.update { it.copy(gazetteOpen = false) }

    /** The one command the shop day issues: a next-day planning command that draws no RNG. Then the Tomorrow card. */
    fun chooseBlessing(id: BlessingId) {
        val s = shopDay() ?: return
        if (s.position.beat != Beat.Blessing) return
        val day = s.state.lastResolution?.commandId?.value ?: return
        launch(Op.Dispatch(Command.ChooseBlessing(id), s.state.runId)) { local.update { it.copy(dayId = day, dayAt = Int.MAX_VALUE) } }
    }

    /** "Decide later": the offer stays in the saved game, where the Shop shows it. */
    fun decideLater() {
        val s = shopDay() ?: return
        if (s.position.beat == Beat.Blessing) moveTo(s.position.at + 1)
    }

    /**
     * "Begin day N" on the Tomorrow card and "See the legacy" on Forge fallen: the day has been watched, so its cursor
     * moves to DONE (awaited), which unlocks planning or opens the run end. Only those two cards acknowledge a day.
     */
    fun acknowledge() {
        val s = shopDay() ?: return
        val last = s.state.lastResolution ?: return
        if (s.resumed || !s.position.isLast) return
        // An offer still open was put off on the Blessing card: planning does not ask again today.
        edit { it.copy(dest = Dest.SHOP, forgeReveal = 0, blessingOfferDismissedDay = if (s.state.pendingBlessingOffer.isNotEmpty()) s.state.day else it.blessingOfferDismissedDay) }
        viewModelScope.launch {
            session.run(Op.MoveCursor(DayCursor(last.commandId.value, DayCursor.Stage.DONE)))
            local.update { it.copy(dayId = null, sheet = null, gazetteOpen = false) }
        }
    }

    /**
     * System back and a tap outside a dialog. Returns true when it was consumed. It never acknowledges a day: in the
     * shop day it closes what is open, else steps back one card (on the Blessing card it means "Decide later"), and on
     * the first card or the Resume prompt it is not consumed. Away from Shop it returns to Shop; on Shop it is not consumed.
     * Where it is not consumed the screen opens the main menu, which changes nothing of the day or the run.
     */
    fun back(): Boolean {
        val day = shopDay()
        if (day != null) {
            when {
                !day.backIsConsumed -> return false
                day.gazetteOpen -> closeGazette()
                day.sheet != null -> closeSheet()
                day.position.beat == Beat.Blessing -> decideLater()
                else -> moveTo(day.position.at - 1)
            }
            return true
        }
        val s = now() as? UiState.Playing ?: return false
        if (s.sheet != null) { closeSheet(); return true }
        if (s.dest == Dest.SHOP) return false
        selectDest(Dest.SHOP)
        return true
    }

    fun dispatch(command: Command) {
        val run = session.snapshot.value?.run ?: return
        if (command !is Command.Forge) return launch(Op.Dispatch(command, run.runId))
        // The journal on either side of the accepted forge: what the result card says was learned.
        val before = run.legacy.journal
        launch(Op.Dispatch(command, run.runId)) {
            val after = session.snapshot.value?.run?.legacy?.journal ?: return@launch
            edit { it.copy(learning = engine.forgeLearning(before, after, command)) }
        }
    }

    /**
     * A bulk action from Storage: one command per blade, in the order given, each saved before the next is issued. The
     * first one the engine refuses (no energy left, the armory full) is shown and the rest are not issued. Storage then
     * says how many were done.
     */
    fun dispatchAll(commands: List<Command>) {
        val run = session.snapshot.value?.run ?: return
        if (session.status.value is Status.Working) return
        viewModelScope.launch {
            var done = 0
            for (command in commands) {
                val result = session.run(Op.Dispatch(command, run.runId))
                show(result)
                if (result !is Result.Done) break
                done++
            }
            val blades = if (done == 1) "1 blade" else "$done blades"
            val said = when (commands.firstOrNull()) {
                is Command.Salvage -> "$blades melted down."
                is Command.DonateWeapon -> "$blades given to the town watch."
                else -> null
            }
            if (done > 0 && said != null) edit { it.copy(notice = said) }
        }
    }

    /** Deterministic per-day command ID: retrying after a crash cannot simulate the day twice. */
    fun endDay() {
        val run = session.snapshot.value?.run ?: return
        dispatch(Command.EndDay(CommandId("${run.runId.value}:day${run.day}")))
    }

    fun claimLegacy() {
        val run = session.snapshot.value?.run ?: return
        launch(Op.Claim(run.runId))
    }

    fun buyUpgrade(id: UpgradeId) {
        val snap = session.snapshot.value ?: return
        launch(Op.BuyUpgrade(id, snap.run?.runId))
    }

    fun beginNextEra() {
        if (now() is UiState.RunEnded) beginEra(null)
    }

    /** "Try again": reads the save again when it could not be loaded, otherwise repeats the op whose save failed. */
    fun retry() {
        if (session.snapshot.value != null) { viewModelScope.launch { show(session.retry()) }; return }
        if (!local.value.loading) viewModelScope.launch { open { session.retry() } }
    }

    /** "Start over (keeps a backup)" on the load-failed screen. */
    fun startOver() {
        if (!local.value.loading) viewModelScope.launch { open { session.startOverKeepingBackup() } }
    }

    /** "Keep working": drop the op whose save failed; the screen already shows the last saved state. */
    fun dismissSaveFailure() = session.dismissFailure()

    fun setReducedMotion(value: Boolean) = viewModelScope.launch { settings.setReducedMotion(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { settings.setHaptics(value) }
    fun dismissTip(id: String) = viewModelScope.launch { settings.markTipSeen(id) }

    /** Player-facing text for an engine error; the UI also uses it to explain disabled choices. */
    fun describe(e: GameError): String = when (e) {
        GameError.RunEnded -> "The forge has fallen; this era is over."
        is GameError.NotEnoughEnergy -> "Not enough energy (need ${e.needed}, have ${e.available}, overwork left ${e.overworkAvailable})."
        is GameError.MissingMaterial -> "You are out of ${engine.content.material(e.materialId).name}."
        is GameError.UnknownContent -> "Unknown item ${e.id}."
        is GameError.WrongMaterialCategory -> "${engine.content.material(e.materialId).name} is not a ${e.expected.lowercase()}."
        is GameError.CatalystRequiresAdvanced -> "Catalysts need the Advanced Forge."
        is GameError.TechniqueRequiresAdvanced -> "Techniques need the Advanced Forge."
        is GameError.WeaponNotFound -> "That weapon is gone."
        is GameError.WeaponNotAvailable -> "That weapon is not in the shop."
        GameError.ShelfFull -> "All ${session.snapshot.value?.run?.let { engine.shelfSlots(it) } ?: engine.config.shelfSlots} shelf slots are full."
        is GameError.InvalidPrice -> "Price must be zero or more."
        is GameError.InvalidQuantity -> "Quantity must be positive."
        is GameError.NotEnoughGold -> "Not enough gold (need ${e.needed}, have ${e.available})."
        is GameError.SupplierOutOfStock -> "The supplier is out of ${engine.content.material(e.materialId).name} today."
        is GameError.CommissionNotFound, is GameError.CommissionNotOpen -> "That request is no longer open."
        GameError.NoBlessingOffer, is GameError.BlessingNotOffered -> "No such blessing is offered."
        GameError.RunNotEnded -> "The run is still going."
        is GameError.AlreadyClaimed -> "This era's legacy was already claimed."
        is GameError.NotEnoughLegacyPoints -> "Need ${e.needed} legacy points, have ${e.available}."
        is GameError.UpgradeMaxed -> "That upgrade is already at its highest level."
        is GameError.AlreadyHoned -> "That weapon has already been honed."
        is GameError.ToolMaxed -> "That tool is already at its highest level."
        GameError.ArmoryFull -> "The town watch armory is full."
        // Core grows concurrently; unmapped errors still get a readable line instead of a build break.
        else -> "The forge cannot do that right now."
    }

    companion object {
        private const val KEY_DEST = "dest"
        private const val KEY_RECORDS = "records"
        private const val KEY_DRAFT = "draft"
        private const val KEY_REVEAL = "reveal"
        private const val KEY_BLESSING_DAY = "blessing_day"
        private const val KEY_SHEET = "sheet"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val engine = GameEngine()
                GameViewModel(engine, GameSession(engine, SaveStore.create(app)), SettingsStore(app), createSavedStateHandle())
            }
        }
    }
}
