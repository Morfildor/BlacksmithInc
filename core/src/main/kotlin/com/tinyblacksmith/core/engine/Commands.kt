package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.model.*

/**
 * Advanced Forge techniques (GDD 4.3): different decisions, not strictly better.
 * TEMPER lowers both defect and exceptional chance; QUENCH forces the augment's element affix at a quality cost;
 * ETCH adds an affix slot but raises defect chance.
 */
enum class Technique { TEMPER, QUENCH, ETCH }

/** Typed player commands (GDD 13.2). Every gameplay change goes through one of these. */
sealed interface Command {
    data class Forge(
        val mode: ForgeMode,
        val familyId: WeaponFamilyId,
        val coreId: MaterialId,
        val augmentId: MaterialId,
        val catalystId: MaterialId? = null,
        val risk: Risk,
        val technique: Technique? = null,
        /** Ashen Bellows: one forge a day may take `depth.bellowsDebt` from tomorrow's energy for an extra affix slot. */
        val bellows: Boolean = false,
    ) : Command

    data class ToggleShelf(val weaponId: WeaponId, val listed: Boolean, val price: Int? = null) : Command
    data class SetPrice(val weaponId: WeaponId, val price: Int) : Command
    data class BuyMaterial(val materialId: MaterialId, val quantity: Int = 1) : Command
    data class AcceptCommission(val commissionId: CommissionId) : Command
    data class DeclineCommission(val commissionId: CommissionId) : Command
    data class ChooseBlessing(val blessingId: BlessingId) : Command
    /** Melts a weapon in storage or on the shelf back into its core material. */
    data class Salvage(val weaponId: WeaponId) : Command
    /**
     * Carts weapons from storage or the shelf to the scrap heap in one go: no energy, and one unit of a core material
     * back for every [com.tinyblacksmith.core.config.SaveGrowthConfig.scrapBladesPerMaterial] blades made of it. All or nothing.
     */
    data class Scrap(val weaponIds: List<WeaponId>) : Command
    /** Reworks a weapon once: energy plus one unit of its core material for a fixed quality gain. */
    data class Hone(val weaponId: WeaponId) : Command
    /** Gives a weapon to the town watch; part of its power joins the town's defense. */
    data class DonateWeapon(val weaponId: WeaponId) : Command
    data class BuyTool(val toolId: String) : Command
    /** Answers this morning's visitor with one of its options. Applied once per [commandId]; the same ID again changes nothing. */
    data class ResolveEncounter(val instanceId: String, val optionId: String, val commandId: CommandId) : Command
    /** Takes an offered relic; [replaceId] names the held relic it replaces, required once every slot is filled. */
    data class ChooseRelic(val relicId: String, val replaceId: String? = null) : Command
    data object DeclineRelicOffer : Command
    data class EndDay(val commandId: CommandId) : Command

    // ---- a guild run only (docs/GUILD_EVOLUTION_PLAN.md). Each states what should be, so the same command twice changes nothing. ----
    /** Signs a resident who is among today's candidates, for the fee shown. */
    data class RecruitHero(val heroId: HeroId) : Command
    /** Lets a member in town go; a loan in their hands returns to storage. The signing fee is not returned. */
    data class DismissHero(val heroId: HeroId) : Command
    /** Loans a blade from storage or the shelf to a member in town; the blade they held on loan goes back to storage. */
    data class LoanWeapon(val heroId: HeroId, val weaponId: WeaponId) : Command
    data class RecallLoan(val weaponId: WeaponId) : Command
    /** Today's party, whole: replaces any plan made earlier today. The contract's fee is paid now and returned if the plan is cancelled or replaced. */
    data class PlanDeployment(val offerId: String, val heroIds: List<HeroId>, val posture: com.tinyblacksmith.core.combat.Posture) : Command
    data object CancelPlannedDeployment : Command
    data class ReserveDefender(val heroId: HeroId, val reserved: Boolean) : Command
    /** At a checkpoint: push into the deeper stage or return. Unanswered, End Day returns. */
    data class ChooseMissionCheckpoint(val instanceId: String, val choice: CheckpointChoice) : Command
    /** Closes the run as a success once [milestoneId] is earned. */
    data class RetireAfterMilestone(val milestoneId: String) : Command
    data class ChooseSpeciality(val heroId: HeroId, val specialityId: String) : Command
    data class SetGuildRank(val rank: Int) : Command
}

sealed interface GameError {
    data object RunEnded : GameError
    data class NotEnoughEnergy(val needed: Int, val available: Int, val overworkAvailable: Int) : GameError
    data class MissingMaterial(val materialId: MaterialId) : GameError
    data class UnknownContent(val id: String) : GameError
    data class WrongMaterialCategory(val materialId: MaterialId, val expected: String) : GameError
    data class CatalystRequiresAdvanced(val catalystId: MaterialId) : GameError
    data class TechniqueRequiresAdvanced(val technique: Technique) : GameError
    data class WeaponNotFound(val weaponId: WeaponId) : GameError
    data class WeaponNotAvailable(val weaponId: WeaponId, val location: WeaponLocation) : GameError
    data object ShelfFull : GameError
    data class InvalidPrice(val price: Int) : GameError
    data class InvalidQuantity(val quantity: Int) : GameError
    data class NotEnoughGold(val needed: Int, val available: Int) : GameError
    data class SupplierOutOfStock(val materialId: MaterialId) : GameError
    data class CommissionNotFound(val commissionId: CommissionId) : GameError
    data class CommissionNotOpen(val commissionId: CommissionId, val status: CommissionStatus) : GameError
    data object NoBlessingOffer : GameError
    data class BlessingNotOffered(val blessingId: BlessingId) : GameError
    data object RunNotEnded : GameError
    data class AlreadyClaimed(val runId: RunId) : GameError
    data class NotEnoughLegacyPoints(val needed: Int, val available: Int) : GameError
    data class UpgradeMaxed(val upgradeId: UpgradeId) : GameError
    data class AlreadyHoned(val weaponId: WeaponId) : GameError
    data class ToolMaxed(val toolId: String) : GameError
    data object ArmoryFull : GameError
    data object NoEncounter : GameError
    data class EncounterNotOpen(val instanceId: String) : GameError
    data class EncounterOptionBlocked(val optionId: String, val reason: String) : GameError
    data object NoRelicOffer : GameError
    data class RelicNotOffered(val relicId: String) : GameError
    data class RelicNotOwned(val relicId: String) : GameError
    data object RelicSlotsFull : GameError
    /** A once-a-day relic has already been used today. */
    data class RelicSpent(val relicId: String) : GameError
    /** The blade is kept for an open order. */
    data class WeaponPromised(val weaponId: WeaponId, val commissionId: CommissionId) : GameError
    /** The run was written under other rules or content than this engine's; [Compatibility.admit] brings it forward first. */
    data class IncompatibleRun(val rulesVersion: Int, val contentVersion: Int) : GameError
    /** A guild command on a run that has no guild. */
    data object NotAGuildRun : GameError
    data class NotAMember(val heroId: HeroId) : GameError
    data class NotACandidate(val heroId: HeroId) : GameError
    data object RosterFull : GameError
    /** [reason] finishes the sentence "<name> ...": "is away with the party". */
    data class MemberUnavailable(val heroId: HeroId, val reason: String) : GameError
    data class WeaponOnLoan(val weaponId: WeaponId, val heroId: HeroId) : GameError
    data class NotOnLoan(val weaponId: WeaponId) : GameError
    data class MissionNotOffered(val offerId: String) : GameError
    data class PartyInvalid(val reason: String) : GameError
    data object PartyAway : GameError
    data object NoDeploymentPlanned : GameError
    data object NoMissionCheckpoint : GameError
    data class MilestoneNotEarned(val milestoneId: String) : GameError
}

sealed interface CommandOutcome {
    data class Accepted(
        val state: GameState,
        val events: List<EventRecord>,
        val forgedWeaponId: WeaponId? = null,
        val resolution: DayResolution? = null,
    ) : CommandOutcome

    data class Rejected(val error: GameError) : CommandOutcome
}

fun CommandOutcome.stateOrThrow(): GameState = when (this) {
    is CommandOutcome.Accepted -> state
    is CommandOutcome.Rejected -> throw IllegalStateException("Command rejected: $error")
}

fun CommandOutcome.acceptedOrThrow(): CommandOutcome.Accepted = when (this) {
    is CommandOutcome.Accepted -> this
    is CommandOutcome.Rejected -> throw IllegalStateException("Command rejected: $error")
}
