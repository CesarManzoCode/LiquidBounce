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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.client.gui.Gui
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items

/**
 * AntiBlind module
 *
 * Protects you from potentially annoying screen effects that block your view.
 */
@Suppress("MagicNumber")
object ModuleAntiBlind : ClientModule("AntiBlind", ModuleCategories.XTETRADOX, aliases = listOf("NoRender")) {
    /**
     * @see Items.CARVED_PUMPKIN
     * @see Gui.renderCameraOverlays
     */
    @JvmField
    val TEXTURE_PUMPKIN_BLUR: Identifier = Identifier.withDefaultNamespace("textures/misc/pumpkinblur.png")

    init {
        tree(LowFire)
    }

    /**
     * Lowers and shrinks the first-person fire overlay so the flames cover less
     * of the screen while burning (the "low fire" pack effect), as opposed to
     * just making it transparent with [fireOpacity]. [scale] shrinks the flames
     * vertically and [offset] shifts them up/down. Tune both in-game.
     *
     * @see net.ccbluex.liquidbounce.injection.mixins.minecraft.render.MixinScreenEffectRenderer
     */
    object LowFire : ToggleableValueGroup(this, "LowFire", false) {
        val scale by float("Scale", 0.5f, 0.1f..1f)
        val offset by float("Offset", 0f, -1f..1f)
    }

    private val render = multiEnumChoice("DoRender",
        DoRender.ARMOR,
        DoRender.MOB_IN_SPAWNER,
        DoRender.ENCHANT_TABLE_BOOK,
        DoRender.EAT_PARTICLES,
        DoRender.BLOCK_BREAK_PARTICLES,
        DoRender.BLOCK_BREAK_OVERLAY,
        DoRender.TITLE,
        DoRender.MAP_CONTENTS,
        DoRender.MAP_MARKERS,
        DoRender.FALLING_BLOCKS,
        DoRender.BEACON_BEAMS,
        DoRender.SKYLIGHT_UPDATES,
        DoRender.GUI_BACKGROUND,
        DoRender.SPYGLASS_OVERLAY,
        DoRender.SIGN_TEXT,
        DoRender.INVISIBLE_ENTITIES,
        DoRender.BOSS_BARS,
        DoRender.EXPLOSION_PARTICLES,
        DoRender.WORLD_BORDER,
    )

    private val fireOpacity by int("FireOpacity", 100, 0..100, suffix = "%")

    @JvmStatic
    fun canRender(choice: DoRender) = !running || choice in render

    val fireOpacityPercentage get() =
        if (running) {
            fireOpacity / 100.0f
        } else {
            1.0f
        }
}

enum class DoRender(override val tag: String) : Tagged {
    BLINDING("Blinding"),
    DARKNESS("Darkness"),
    NAUSEA("Nausea"),
    ARMOR("Armor"),
    MOB_IN_SPAWNER("MobInSpawner"),
    ENCHANT_TABLE_BOOK("EnchantTableBook"),
    EAT_PARTICLES("EatParticles"),
    BLOCK_BREAK_PARTICLES("BlockBreakParticles"),
    BLOCK_BREAK_OVERLAY("BlockBreakOverlay"),
    TITLE("Title"),
    PUMPKIN_BLUR("PumpkinBlur"),
    LIQUIDS_FOG("LiquidsFog"),
    POWDER_SNOW_FOG("PowderSnowFog"),
    FLOATING_ITEMS("FloatingItems"),
    MAP_CONTENTS("MapContents"),
    MAP_MARKERS("MapMarkers"),
    PORTAL_OVERLAY("PortalOverlay"),
    WALL_OVERLAY("WallOverlay"),
    FALLING_BLOCKS("FallingBlocks"),
    BEACON_BEAMS("BeaconBeams"),
    SKYLIGHT_UPDATES("SkylightUpdates"),
    GUI_BACKGROUND("GuiBackground"),
    SPYGLASS_OVERLAY("SpyglassOverlay"),
    SIGN_TEXT("SignText"),
    INVISIBLE_ENTITIES("InvisibleEntities"),
    BOSS_BARS("BossBars"),
    EXPLOSION_PARTICLES("ExplosionParticles"),
    WORLD_BORDER("WorldBorder"),
}
