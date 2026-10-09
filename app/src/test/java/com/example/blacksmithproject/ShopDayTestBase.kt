package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.data.Settings
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The ViewModel over a fake store, for the shop-day tests. A "kill" is [open] again on the same repository: a new
 * session and ViewModel that know only the stored rows. Days are real: resolved by the engine from seed 42.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ShopDayTestBase {
    protected val engine get() = Companion.engine
    private val repos = mutableListOf<FakeGameRepository>()

    /** The session commits under NonCancellable: every repository is released on the way out, or a failed test would hang. */
    protected fun vmTest(body: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // What the ViewModels still have queued runs before Main is taken away from them.
        try { body() } finally { repos.forEach { it.releaseAll() }; testScheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    }

    protected fun repo(run: GameState?, legacy: LegacyProfile = run?.legacy ?: LegacyProfile()) =
        FakeGameRepository(run?.let { SaveCodec.encodeRun(it) }, SaveCodec.encodeLegacy(legacy)).also { repos += it }

    /** A store opened on planning: the run's last day, if it has one, was watched to its end. */
    protected fun planning(run: GameState) = repo(run).also { r -> run.lastResolution?.let { r.cursor = DayCursor(it.commandId.value, DayCursor.Stage.DONE).encode() } }

    /** A second store holding the same rows: the same save taken down another path. */
    protected fun FakeGameRepository.twin() = FakeGameRepository(run, legacy).also { it.cursor = cursor; repos += it }

    protected fun TestScope.session(repo: FakeGameRepository) = GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler))

    protected suspend fun TestScope.open(
        repo: FakeGameRepository, settings: Settings = FakeSettings(), saved: SavedStateHandle = SavedStateHandle(), session: GameSession = session(repo),
    ): GameViewModel = GameViewModel(engine, session, settings, saved, compute = StandardTestDispatcher(testScheduler)).also { advanceUntilIdle() }

    protected fun endDay(s: GameState) = Companion.endDay(s)
    protected fun GameState.nextDay(): GameState = (engine.handle(this, endDay(this)) as CommandOutcome.Accepted).state
    protected val fresh get() = Companion.fresh
    protected val forgeSword get() = Companion.forgeSword
    protected val stocked get() = Companion.stocked
    protected val blessingEve get() = Companion.blessingEve
    protected val lastEve get() = Companion.lastEve

    /** Built once for all tests: the engine is deterministic, so these are the same states every time. */
    private companion object {
        val engine = GameEngine()
        fun endDay(s: GameState) = Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))
        fun GameState.after(command: Command): GameState = (engine.handle(this, command) as CommandOutcome.Accepted).state

        val fresh: GameState by lazy { engine.newRun(LegacyProfile(), 42L) }
        val forgeSword = Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)

        private fun GameState.tryTo(command: Command): GameState? = (engine.handle(this, command) as? CommandOutcome.Accepted)?.state

        /** Buys, forges and lists at half the suggested price until the day's energy or the shelf runs out: a day with customers. */
        fun stock(start: GameState): GameState {
            var s = start
            while (true) {
                s = s.tryTo(Command.BuyMaterial(LaunchContent.IRON)) ?: s
                s = s.tryTo(Command.BuyMaterial(LaunchContent.EMBER_RESIN)) ?: s
                val out = engine.handle(s, forgeSword) as? CommandOutcome.Accepted ?: return s
                val blade = out.state.weapons.getValue(out.forgedWeaponId!!)
                s = out.state.tryTo(Command.ToggleShelf(blade.id, true, engine.suggestedPrice(blade) / 2)) ?: return out.state
            }
        }

        /** Seed 42, day 1, planned: two customers are shown at the counter that day. */
        val stocked: GameState by lazy { stock(fresh) }

        /** A smith who arms the town (seed 43): the planned day before the town holds a siege and offers a blessing. */
        val blessingEve: GameState by lazy {
            generateSequence(stock(engine.newRun(LegacyProfile(), 43L))) { if (it.isEnded) null else stock(it.after(endDay(it))) }.take(60)
                .firstOrNull { !it.isEnded && it.after(endDay(it)).pendingBlessingOffer.isNotEmpty() } ?: error("seed 43 never holds a siege")
        }

        /** A smith who never forges: the day before the forge falls. */
        val lastEve: GameState by lazy { generateSequence(fresh) { it.after(endDay(it)) }.first { it.after(endDay(it)).isEnded } }
    }

    protected fun GameViewModel.day() = ui.value as UiState.ShopDay
    protected fun GameViewModel.playing() = ui.value as UiState.Playing
    protected fun FakeGameRepository.storedCursor() = cursor?.let { DayCursor.decode(it) }

    /** Every card to the day's last one, as a player tapping Next would (Decide later on the Blessing card). */
    protected fun TestScope.watchToTheEnd(vm: GameViewModel) {
        while (!vm.day().position.isLast) {
            if (vm.day().position.beat == Beat.Blessing) vm.decideLater() else vm.next()
            advanceUntilIdle()
        }
    }
}
