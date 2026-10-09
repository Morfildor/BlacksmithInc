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
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.legacy.RunEndResult
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.DayCursor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** The four places the player goes (plan 1.2); the bar shows these and nothing else. */
enum class Dest { SHOP, FORGE, TOWN, RECORDS }

/**
 * The fine-grained page inside a destination: HOME and MARKET are the two pages of Shop until the real Shop panel
 * (T2.8b) replaces them, GAZETTE ("News"), JOURNAL and LEGACY are the three segments of Records. Blocks on Home still
 * navigate with these names.
 */
enum class Panel(val dest: Dest) {
    HOME(Dest.SHOP), MARKET(Dest.SHOP), FORGE(Dest.FORGE), TOWN(Dest.TOWN), JOURNAL(Dest.RECORDS), GAZETTE(Dest.RECORDS), LEGACY(Dest.RECORDS)
}

data class ForgeDraft(
    val mode: ForgeMode = ForgeMode.QUICK,
    val familyId: WeaponFamilyId? = null,
    val coreId: MaterialId? = null,
    val augmentId: MaterialId? = null,
    val catalystId: MaterialId? = null,
    val risk: Risk = Risk.BALANCED,
    val technique: Technique? = null,
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
        val panel: Panel = Panel.HOME,
        val draft: ForgeDraft = ForgeDraft(),
        val revealWeaponId: WeaponId? = null,
        /** The last day's report while the player has not closed it (the day cursor is not at DONE). */
        val showReport: DayResolution? = null,
        val lastError: String? = null,
        override val op: Status = Status.Idle,
        /** Day on which the player chose "Decide later" for the blessing offer; UI-only, the offer itself stays in core state. */
        val blessingOfferDismissedDay: Int? = null,
    ) : UiState {
        val busy: Boolean get() = op is Status.Working
        val dest: Dest get() = panel.dest
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
class GameViewModel(val engine: GameEngine, private val session: GameSession, val settings: Settings, private val saved: SavedStateHandle) : ViewModel() {
    private data class Local(
        val panel: Panel = Panel.HOME,
        val draft: ForgeDraft = ForgeDraft(),
        val revealWeaponId: WeaponId? = null,
        val blessingOfferDismissedDay: Int? = null,
        val lastError: String? = null,
        val loadFailure: SaveFailure? = null,
        /** True until the first load (and the hand-over of the old report key) has finished, and again during Retry. */
        val loading: Boolean = true,
    )

    private val local = MutableStateFlow(restored())
    /** closeRun of the ended run, computed once per run instead of on every emission. */
    private var closed: Pair<GameState, RunEndResult>? = null

    val ui: StateFlow<UiState> = combine(session.snapshot, session.status, local, ::render).stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    init {
        viewModelScope.launch { open { session.load() } }
    }

    private fun render(snap: GameSession.Snapshot?, op: Status, l: Local): UiState {
        if (snap == null || l.loading) return l.loadFailure?.let { UiState.LoadFailed(it, working = l.loading) } ?: UiState.Loading
        val run = snap.run ?: return UiState.Title(snap.legacy, op)
        val watched = GameSession.pending(run, snap.legacy, snap.cursor) == DayCursor.Stage.DONE
        if (run.isEnded && watched) {
            val end = closed?.takeIf { it.first === run }?.second ?: engine.closeRun(run).also { closed = run to it }
            return UiState.RunEnded(run, end, snap.legacy, claimed = run.runId.value in snap.legacy.claimedRunIds, lastError = l.lastError, op = op)
        }
        // Until the shop-day screen exists the report dialog is the presentation of the day: it shows until the cursor is DONE.
        return UiState.Playing(run, l.panel, l.draft, l.revealWeaponId, run.lastResolution.takeIf { !watched }, l.lastError, op, l.blessingOfferDismissedDay)
    }

    /** The screen as it stands this instant ([ui] may be one dispatch behind). */
    private fun now(): UiState = render(session.snapshot.value, session.status.value, local.value)

    /** A load, a retried load or a start over: the screen leaves Loading / LoadFailed only when it has finished. */
    private suspend fun open(read: suspend () -> Result) {
        local.update { it.copy(loading = true) }
        val result = read()
        if (result is Result.Done) handOverDismissedReport()
        local.update { it.copy(loading = false, loadFailure = (result as? Result.Failed)?.failure) }
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
        saved[KEY_PANEL] = l.panel.name
        saved[KEY_REVEAL] = l.revealWeaponId?.value
        saved[KEY_BLESSING_DAY] = l.blessingOfferDismissedDay
        saved[KEY_DRAFT] = with(l.draft) { arrayListOf(mode.name, familyId?.value, coreId?.value, augmentId?.value, catalystId?.value, risk.name, technique?.name) }
    }

    private fun restored(): Local {
        val d = saved.get<ArrayList<String?>>(KEY_DRAFT)?.takeIf { it.size == 7 }
        return Local(
            panel = saved.get<String>(KEY_PANEL)?.let { name -> Panel.entries.firstOrNull { it.name == name } } ?: Panel.HOME,
            draft = if (d == null) ForgeDraft() else ForgeDraft(
                mode = ForgeMode.entries.firstOrNull { it.name == d[0] } ?: ForgeMode.QUICK,
                familyId = d[1]?.let(::WeaponFamilyId), coreId = d[2]?.let(::MaterialId), augmentId = d[3]?.let(::MaterialId), catalystId = d[4]?.let(::MaterialId),
                risk = Risk.entries.firstOrNull { it.name == d[5] } ?: Risk.BALANCED,
                technique = Technique.entries.firstOrNull { it.name == d[6] },
            ),
            revealWeaponId = saved.get<String>(KEY_REVEAL)?.let(::WeaponId),
            blessingOfferDismissedDay = saved.get<Int>(KEY_BLESSING_DAY),
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
        launch(Op.BeginEra(seed ?: (System.nanoTime() xor snap.legacy.eras.size.toLong()), snap.run?.runId)) { edit { Local(loading = false) } }
    }

    fun selectPanel(panel: Panel) = edit { it.copy(panel = panel) }
    /** A bar tap: the destination's first page, unless the screen is already inside it. */
    fun selectDest(dest: Dest) = edit { if (it.panel.dest == dest) it else it.copy(panel = when (dest) { Dest.SHOP -> Panel.HOME; Dest.FORGE -> Panel.FORGE; Dest.TOWN -> Panel.TOWN; Dest.RECORDS -> Panel.GAZETTE }) }
    fun updateDraft(transform: (ForgeDraft) -> ForgeDraft) = edit { it.copy(draft = transform(it.draft)) }
    fun dismissReveal() = edit { it.copy(revealWeaponId = null) }
    fun dismissError() = edit { it.copy(lastError = null) }
    fun dismissBlessingOffer() = edit { it.copy(blessingOfferDismissedDay = session.snapshot.value?.run?.day, panel = Panel.HOME) }
    fun reopenBlessingOffer() = edit { it.copy(blessingOfferDismissedDay = null) }

    /** The report's own button: the day has been read, so its cursor moves to DONE (which unlocks planning, or opens the run end). */
    fun dismissReport() {
        val last = session.snapshot.value?.run?.lastResolution ?: return
        edit { it.copy(panel = Panel.HOME) }
        viewModelScope.launch { session.run(Op.MoveCursor(DayCursor(last.commandId.value, DayCursor.Stage.DONE))) }
    }

    /**
     * System back and a tap outside a dialog. Returns true when it was consumed. It never acknowledges a report:
     * only the report's own button does. Away from Shop it returns to Shop; on Shop it is not consumed (leaves the app).
     */
    fun back(): Boolean {
        val s = now() as? UiState.Playing ?: return false
        if (s.showReport != null) return true
        if (s.dest == Dest.SHOP) return false
        selectDest(Dest.SHOP)
        return true
    }

    fun dispatch(command: Command) {
        val run = session.snapshot.value?.run ?: return
        launch(Op.Dispatch(command, run.runId))
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
        is GameError.CommissionNotFound, is GameError.CommissionNotOpen -> "That commission is no longer open."
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
        private const val KEY_PANEL = "panel"
        private const val KEY_DRAFT = "draft"
        private const val KEY_REVEAL = "reveal"
        private const val KEY_BLESSING_DAY = "blessing_day"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val engine = GameEngine()
                GameViewModel(engine, GameSession(engine, SaveStore.create(app)), SettingsStore(app), createSavedStateHandle())
            }
        }
    }
}
