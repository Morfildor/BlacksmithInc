package com.example.blacksmithproject

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blacksmithproject.data.GameRepository
import com.example.blacksmithproject.data.SaveStore
import com.example.blacksmithproject.data.SettingsStore
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.legacy.RunEndResult
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Panel { HOME, FORGE, MARKET, TOWN, JOURNAL, GAZETTE, LEGACY }

data class ForgeDraft(
    val mode: ForgeMode = ForgeMode.QUICK,
    val familyId: WeaponFamilyId? = null,
    val coreId: MaterialId? = null,
    val augmentId: MaterialId? = null,
    val catalystId: MaterialId? = null,
    val risk: Risk = Risk.BALANCED,
    val technique: Technique? = null,
)

sealed interface UiState {
    data object Loading : UiState
    data class Title(val legacy: LegacyProfile, val hasSavedRun: Boolean) : UiState
    data class Playing(
        val state: GameState,
        val panel: Panel = Panel.HOME,
        val draft: ForgeDraft = ForgeDraft(),
        val revealWeaponId: WeaponId? = null,
        val showReport: DayResolution? = null,
        val lastError: String? = null,
        val busy: Boolean = false,
        /** Day on which the player chose "Decide later" for the blessing offer; UI-only, the offer itself stays in core state. */
        val blessingOfferDismissedDay: Int? = null,
    ) : UiState
    data class RunEnded(val runEnd: RunEndResult, val legacy: LegacyProfile, val claimed: Boolean, val lastError: String? = null) : UiState
}

/**
 * UI is an observer (GDD 13.2): it dispatches typed commands to the pure engine and persists accepted results
 * atomically before showing them. It never computes gameplay outcomes itself.
 */
class GameViewModel(private val repo: GameRepository, val settings: SettingsStore) : ViewModel() {
    val engine = GameEngine()

    private val _ui = MutableStateFlow<UiState>(UiState.Loading)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val legacy = loadLegacy()
            val run = loadRun()
            // The engine saves the last day's report with the run; one the player never closed reopens (GDD 3.3).
            val unread = run?.lastResolution?.let { r -> r.takeIf { it.commandId.value != settings.dismissedReport() } }
            _ui.value = when {
                run == null -> UiState.Title(legacy, hasSavedRun = false)
                run.isEnded && unread == null -> UiState.RunEnded(engine.closeRun(run), legacy, claimed = run.runId.value in legacy.claimedRunIds)
                else -> UiState.Playing(run, showReport = unread)
            }
        }
    }

    private suspend fun loadRun(): GameState? = withContext(Dispatchers.IO) { repo.load().run?.let { SaveCodec.decodeRun(it) } }

    private suspend fun loadLegacy(): LegacyProfile = withContext(Dispatchers.IO) { repo.load().legacy?.let { SaveCodec.decodeLegacy(it) } ?: LegacyProfile() }

    private suspend fun save(run: GameState?, legacy: LegacyProfile) =
        withContext(Dispatchers.IO) { repo.commit(run?.let { SaveCodec.encodeRun(it) }, SaveCodec.encodeLegacy(legacy)) }

    fun newRun() = startRun(null)

    /** Debug launch extra only (MainActivity checks the debuggable flag): a fixed-seed run, and only when no run is saved. */
    fun startSeededRun(seed: Long) {
        viewModelScope.launch { if (_ui.first { it !is UiState.Loading } is UiState.Title) startRun(seed) }
    }

    private fun startRun(seed: Long?) = viewModelScope.launch {
        val legacy = loadLegacy()
        val state = engine.newRun(legacy, seed ?: (System.nanoTime() xor legacy.eras.size.toLong()))
        save(state, state.legacy)
        _ui.value = UiState.Playing(state)
    }

    fun continueRun() = viewModelScope.launch {
        val run = loadRun() ?: return@launch
        _ui.value = if (run.isEnded) UiState.RunEnded(engine.closeRun(run), run.legacy, claimed = false) else UiState.Playing(run)
    }

    fun selectPanel(panel: Panel) = _ui.update { if (it is UiState.Playing) it.copy(panel = panel) else it }
    fun updateDraft(transform: (ForgeDraft) -> ForgeDraft) = _ui.update { if (it is UiState.Playing) it.copy(draft = transform(it.draft)) else it }
    fun dismissReveal() = _ui.update { if (it is UiState.Playing) it.copy(revealWeaponId = null) else it }
    fun dismissError() = _ui.update { if (it is UiState.Playing) it.copy(lastError = null) else it }
    fun dismissBlessingOffer() = _ui.update { if (it is UiState.Playing) it.copy(blessingOfferDismissedDay = it.state.day, panel = Panel.HOME) else it }
    fun reopenBlessingOffer() = _ui.update { if (it is UiState.Playing) it.copy(blessingOfferDismissedDay = null) else it }

    fun dismissReport() {
        val current = _ui.value as? UiState.Playing ?: return
        current.showReport?.let { r -> viewModelScope.launch { settings.setDismissedReport(r.commandId.value) } }
        if (current.state.isEnded) {
            _ui.value = UiState.RunEnded(engine.closeRun(current.state), current.state.legacy, claimed = false)
        } else {
            _ui.value = current.copy(showReport = null, panel = Panel.HOME)
        }
    }

    fun dispatch(command: Command) {
        val current = _ui.value as? UiState.Playing ?: return
        if (current.busy) return
        _ui.value = current.copy(busy = true)
        viewModelScope.launch {
            when (val out = engine.handle(current.state, command)) {
                is CommandOutcome.Accepted -> {
                    save(out.state, out.state.legacy)
                    _ui.update { ui ->
                        if (ui !is UiState.Playing) ui
                        else ui.copy(state = out.state, busy = false, revealWeaponId = out.forgedWeaponId ?: ui.revealWeaponId, showReport = out.resolution ?: ui.showReport, lastError = null)
                    }
                }
                is CommandOutcome.Rejected -> _ui.update { ui -> if (ui is UiState.Playing) ui.copy(busy = false, lastError = describe(out.error)) else ui }
            }
        }
    }

    /** Deterministic per-day command ID: retrying after a crash cannot simulate the day twice. */
    fun endDay() {
        val current = _ui.value as? UiState.Playing ?: return
        dispatch(Command.EndDay(CommandId("${current.state.runId.value}:day${current.state.day}")))
    }

    fun claimLegacy() {
        val current = _ui.value as? UiState.RunEnded ?: return
        viewModelScope.launch {
            val legacy = loadLegacy()
            when (val out = engine.claimLegacy(legacy, current.runEnd)) {
                is LegacyOutcome.Updated -> {
                    save(null, out.legacy)
                    _ui.value = current.copy(legacy = out.legacy, claimed = true, lastError = null)
                }
                is LegacyOutcome.Rejected -> {
                    save(null, out.legacy)
                    _ui.value = current.copy(legacy = out.legacy, claimed = true, lastError = describe(out.error))
                }
            }
        }
    }

    fun buyUpgrade(id: UpgradeId) {
        val current = _ui.value as? UiState.RunEnded ?: return
        viewModelScope.launch {
            when (val out = engine.purchaseUpgrade(current.legacy, id)) {
                is LegacyOutcome.Updated -> {
                    save(null, out.legacy)
                    _ui.value = current.copy(legacy = out.legacy, lastError = null)
                }
                is LegacyOutcome.Rejected -> _ui.value = current.copy(lastError = describe(out.error))
            }
        }
    }

    fun beginNextEra() {
        val current = _ui.value as? UiState.RunEnded ?: return
        if (!current.claimed) return
        newRun()
    }

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
        GameError.ShelfFull -> "All ${(_ui.value as? UiState.Playing)?.let { engine.shelfSlots(it.state) } ?: engine.config.shelfSlots} shelf slots are full."
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                GameViewModel(SaveStore.create(app), SettingsStore(app))
            }
        }
    }
}
