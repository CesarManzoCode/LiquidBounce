/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.MouseRotationEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRequirements
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.InterpolationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.point.PointTracker
import net.ccbluex.liquidbounce.utils.aiming.preference.LeastDifferencePreference
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.aiming.utils.setRotation
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.ccbluex.liquidbounce.utils.text.stripMinecraftColorCodes
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * Aimbot module
 *
 * Automatically faces selected entities around you.
 */
object ModuleAimbot : ClientModule("Aimbot", ModuleCategories.XTETRADOX, aliases = listOf("AimAssist", "AutoAim")) {

    private val range = float("Range", 4.2f, 1f..8f)

    val targetTracker = tree(TargetTracker(TargetPriority.DIRECTION, range = range))

    init {
        tree(TargetRenderer(this, targetTracker))
        tree(Whitelist)
    }
    private val pointTracker = tree(PointTracker(this))

    private val requires by multiEnumChoice<KillAuraRequirements>("Requires")

    private val requirementsMet
        get() = mc.gui.screen() == null && requires.all { it.asBoolean }

    private var angleSmooth = modes(this, "AngleSmooth") {
        arrayOf(
            InterpolationAngleSmooth(it),
            SigmoidAngleSmooth(it),
            LinearAngleSmooth(it)
        )
    }

    private val axis by multiEnumChoice<Axis>("Axis", Axis.HORIZONTAL, Axis.VERTICAL)

    private val ignores by multiEnumChoice<IgnoreOpened>("Ignore")

    /**
     * Target lock: once the player hits an entity, keep aiming at that exact
     * target for [lockDuration] seconds instead of switching to whoever gets
     * closest. Useful in crowds where you want to focus a single enemy.
     */
    private val lockOnTarget by boolean("LockTarget", false)
    private val lockDuration by float("LockDuration", 1f, 0.1f..10f, "s")

    private var lockedTarget: LivingEntity? = null
    private var lockExpiryTime = 0L

    // Entity ids already announced as auto-whitelisted, to avoid spamming the
    // notification every tick. Cleared when the module is disabled.
    private val notifiedAllies = mutableSetOf<Int>()

    private var targetRotation: Rotation? = null
    private var playerRotation: Rotation? = null

    @Suppress("unused", "ComplexCondition")
    private val tickHandler = handler<RotationUpdateEvent> { _ ->
        playerRotation = player.rotation

        if (!requirementsMet) {
            targetTracker.reset()
            targetRotation = null
            return@handler
        }

        targetRotation = findNextTargetRotation()?.let { (target, rotation) ->
            angleSmooth.activeMode.process(
                RotationTarget(
                    rotation = rotation.rotation,
                    entity = target,
                    processors = listOf(angleSmooth.activeMode),
                    ticksUntilReset = 1,
                    resetThreshold = 1f,
                    considerInventory = true,
                    movementCorrection = MovementCorrection.CHANGE_LOOK
                ),
                player.rotation,
                rotation.rotation
            )
        }

        // Update Auto Weapon
        ModuleAutoWeapon.onTarget(targetTracker.target)
    }

    /**
     * Whenever the player hits an entity, lock onto it for [lockDuration] seconds.
     */
    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        if (!lockOnTarget) {
            return@handler
        }

        val entity = event.entity as? LivingEntity ?: return@handler

        // Never lock onto a whitelisted entity.
        if (isWhitelisted(entity)) {
            return@handler
        }

        if (lockedTarget !== entity) {
            notification(
                "Aimbot",
                message("lockedOn", entity.name.string),
                NotificationEvent.Severity.INFO
            )
        }

        lockedTarget = entity
        lockExpiryTime = System.currentTimeMillis() + (lockDuration * 1000f).toLong()
    }

    override fun onDisabled() {
        targetTracker.reset()
        lockedTarget = null
        notifiedAllies.clear()
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack
        val partialTicks = event.partialTicks
        val target = targetTracker.target ?: return@handler

        if (IgnoreOpened.SCREEN !in ignores && mc.gui.screen() != null) {
            return@handler
        }

        if (IgnoreOpened.CONTAINER !in ignores && (InventoryManager.isInventoryOpen ||
                mc.gui.screen() is AbstractContainerScreen<*>)) {
            return@handler
        }

        lookAt(partialTicks)
    }

    @Suppress("unused")
    private val mouseMovement = handler<MouseRotationEvent> { event ->
        fun updateRotation(rotation: Rotation): Rotation =
            RotationUtil.applyMouseTurnDelta(rotation, event.cursorDeltaX, event.cursorDeltaY)

        playerRotation?.let { rotation ->
            playerRotation = updateRotation(rotation)
        }

        targetRotation?.let { rotation ->
            targetRotation = updateRotation(rotation)
        }
    }

    /**
     * Looks at the target rotation, with interpolation based on the timer speed and partial ticks to make it smooth.
     */
    private fun lookAt(partialTicks: Float) {
        val playerRotation = playerRotation ?: return
        val targetRotation = targetRotation ?: return
        val timerSpeed = Timer.timerSpeed
        val interpolatedRotation = playerRotation.interpolateTo(targetRotation, timerSpeed * partialTicks)

        player.setRotation(
            Rotation(
                yaw = if (Axis.HORIZONTAL in axis) interpolatedRotation.yaw else playerRotation.yaw,
                pitch = if (Axis.VERTICAL in axis) interpolatedRotation.pitch else playerRotation.pitch,
            )
        )
    }

    /**
     * Returns the locked target if target lock is active and the lock has not
     * expired yet. The lock is intentionally "sticky": it is kept regardless of
     * FOV and range so the aim does not jump to someone else when the target
     * strafes behind you or steps just out of reach. It is only released when the
     * time runs out or the entity stops being a valid enemy (dead, despawned, or
     * no longer attackable). When it cannot be aimed at right now (out of range,
     * behind a wall) the lock is still held and aiming simply resumes once the
     * target is reachable again.
     */
    private fun lockedTargetOrNull(): LivingEntity? {
        if (!lockOnTarget) {
            lockedTarget = null
            return null
        }

        val entity = lockedTarget ?: return null

        if (System.currentTimeMillis() >= lockExpiryTime || !entity.isAlive || !entity.shouldBeAttacked()) {
            lockedTarget = null
            return null
        }

        return entity
    }

    private fun findNextTargetRotation(): Pair<Entity, RotationWithVector>? {
        val locked = lockedTargetOrNull()
        val candidates = (if (locked != null) listOf(locked) else targetTracker.targets())
            .filter { !isWhitelisted(it) }

        for (entity in candidates) {
            val eyes = player.eyePosition
            val point = pointTracker.findPoint(eyes, entity)

            debugGeometry("Box") { ModuleDebug.DebuggedBox(point.box, Color4b.ORANGE.with(a = 90)) }
            debugGeometry("Point") { ModuleDebug.DebuggedPoint(point.pos, Color4b.WHITE, size = 0.1) }

            val rotationPreference = LeastDifferencePreference.leastDifferenceToLastPoint(eyes, point.pos)
            val rotation = raytraceBox(
                eyes = eyes,
                box = point.box,
                range = targetTracker.maxRange.toDouble(),
                wallsRange = 0.0,
                rotationPreference = rotationPreference
            ) ?: continue

            targetTracker.target = entity
            return entity to rotation
        }

        targetTracker.reset()
        return null
    }

    /**
     * Whether the Aimbot must never aim at [entity]. Manual names are silent
     * (you typed them yourself); auto-detected allies trigger a one-time
     * notification so the detection is visible and verifiable.
     */
    private fun isWhitelisted(entity: LivingEntity): Boolean {
        if (isManuallyWhitelisted(entity)) {
            return true
        }

        val reason = autoTeamReason(entity) ?: return false

        if (Whitelist.notify && notifiedAllies.add(entity.id)) {
            notification(
                "Aimbot",
                message("whitelisted", entity.name.string, reason.tag),
                NotificationEvent.Severity.INFO
            )
        }

        return true
    }

    private fun isManuallyWhitelisted(entity: LivingEntity): Boolean =
        entity is Player && Whitelist.usernames.any { it.equals(entity.gameProfile.name, ignoreCase = true) }

    /**
     * Returns the first reliable team/clan signal that matches [entity], or null
     * when auto whitelisting is off or nothing matches.
     */
    private fun autoTeamReason(entity: LivingEntity): TeamSource? {
        if (!Whitelist.autoTeam) {
            return null
        }

        return Whitelist.teamSources.firstOrNull { matchesTeamSource(it, entity) }
    }

    private fun matchesTeamSource(source: TeamSource, entity: LivingEntity): Boolean = when (source) {
        TeamSource.SCOREBOARD_TEAM -> player.isAlliedTo(entity)
        TeamSource.NAME_COLOR -> sameNameColor(entity)
        TeamSource.PREFIX -> samePrefix(entity)
    }

    private fun sameNameColor(entity: LivingEntity): Boolean {
        val ownColor = player.displayName?.style?.color ?: return false
        val otherColor = entity.displayName?.style?.color ?: return false
        return ownColor == otherColor
    }

    private fun samePrefix(entity: LivingEntity): Boolean {
        val ownSplit = player.displayName?.string?.stripMinecraftColorCodes()?.split(" ") ?: return false
        val otherSplit = entity.displayName?.string?.stripMinecraftColorCodes()?.split(" ") ?: return false
        return ownSplit.size > 1 && otherSplit.size > 1 && ownSplit[0] == otherSplit[0]
    }

    /**
     * Whitelist of entities the Aimbot must never target. Only affects the
     * Aimbot; other combat modules (e.g. KillAura) are untouched.
     */
    private object Whitelist : ValueGroup("Whitelist") {

        /**
         * Names the Aimbot will never aim at. Fully reliable: you type them.
         */
        val usernames by textList("Usernames", mutableListOf<String>())

        /**
         * Automatically whitelist teammates/clan members. There is no universal
         * clan API, so this only uses signals the server already exposes to the
         * client; armor color is intentionally excluded because enemies can wear
         * the same color. Detections are announced so false positives are visible.
         */
        val autoTeam by boolean("AutoTeam", false)

        val teamSources by multiEnumChoice("AutoTeamSources", enumSetOf(TeamSource.SCOREBOARD_TEAM))

        val notify by boolean("Notify", true)
    }

    /**
     * Reliable client-side signals to detect a teammate/clan member.
     */
    private enum class TeamSource(override val tag: String) : Tagged {
        /** Vanilla scoreboard team allies. The most reliable signal. */
        SCOREBOARD_TEAM("ScoreboardTeam"),

        /** Same display-name color (e.g. green allies vs red enemies). */
        NAME_COLOR("NameColor"),

        /** Same nametag/tab prefix, i.e. the same clan tag (e.g. "[ELITE]"). */
        PREFIX("Prefix")
    }

    private enum class IgnoreOpened(
        override val tag: String
    ) : Tagged {
        SCREEN("Screen"),
        CONTAINER("Container")
    }

    private enum class Axis(override val tag: String) : Tagged {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical")
    }
}
