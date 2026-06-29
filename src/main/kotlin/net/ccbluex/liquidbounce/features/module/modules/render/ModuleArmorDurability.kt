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

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * ArmorDurability module
 *
 * Renders the durability of each enemy armor piece as a number floating next to
 * the matching body part — the same information the "armor durability" resource
 * packs show, but built in: one click, no Optifine, no texture pack.
 *
 * The client knows enemy armor durability because the server sends the full
 * ItemStack (with its damage value) for entity equipment. This is the same data
 * the Nametags equipment bars already use.
 *
 * The numbers are placed at each piece's height and pushed slightly to the side
 * ([sideOffset]) so they do not block your view of the player.
 */
object ModuleArmorDurability :
    ClientModule("ArmorDurability", ModuleCategories.RENDER, aliases = listOf("ArmorOverlay")) {

    private val range by float("Range", 32f, 4f..64f, "blocks")
    private val scale by float("Scale", 1f, 0.25f..4f)

    /**
     * Horizontal screen offset so the numbers sit beside the body instead of on
     * top of it. 0 puts them right on each piece.
     */
    private val sideOffset by float("SideOffset", 10f, -40f..40f)

    /**
     * Round to the nearest 10, like the resource packs (e.g. 340, 210).
     */
    private val roundToTens by boolean("RoundToTens", true)

    private val hideFull by boolean("HideFull", false)

    private val colorByDurability by boolean("ColorByDurability", true)
    private val customColor by color("Color", Color4b.WHITE)

    /**
     * Armor slots paired with their approximate height on the body (as a fraction
     * of the entity height), so each number lines up with its piece.
     */
    private val armorSlots = arrayOf(
        EquipmentSlot.HEAD to 0.92,
        EquipmentSlot.CHEST to 0.62,
        EquipmentSlot.LEGS to 0.40,
        EquipmentSlot.FEET to 0.12,
    )

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        val rangeSq = range.sq().toDouble()

        for (target in world.players()) {
            if (target === player || target.isSpectator) {
                continue
            }

            if (target.distanceToSqr(player) > rangeSq) {
                continue
            }

            renderArmor(target, event)
        }
    }

    private fun renderArmor(target: Player, event: OverlayRenderEvent) {
        val base = target.interpolateCurrentPosition(event.tickDelta)
        val height = target.bbHeight.toDouble()

        for ((slot, fraction) in armorSlots) {
            val stack = target.getItemBySlot(slot)
            if (stack.isEmpty) {
                continue
            }

            val maxDamage = stack.maxDamage
            if (maxDamage <= 0) {
                continue
            }

            val durability = maxDamage - stack.damageValue
            if (hideFull && durability >= maxDamage) {
                continue
            }

            val screenPos = WorldToScreen.calculateScreenPos(
                Vec3(base.x, base.y + height * fraction, base.z)
            ) ?: continue

            val displayed = if (roundToTens) (durability + 5) / 10 * 10 else durability
            val drawColor = if (colorByDurability) durabilityColor(durability, maxDamage) else customColor

            with(event.context) {
                pose().pushMatrix()
                pose().translate(screenPos.x + sideOffset, screenPos.y)
                pose().scale(scale, scale)
                centeredText(mc.font, displayed.toString(), 0, 0, drawColor.argb)
                pose().popMatrix()
            }
        }
    }

    /**
     * Green when full, fading through yellow to red as the piece wears out.
     */
    private fun durabilityColor(durability: Int, maxDamage: Int): Color4b {
        val ratio = (durability.toFloat() / maxDamage).coerceIn(0f, 1f)
        return Color4b((255 * (1f - ratio)).toInt(), (255 * ratio).toInt(), 40)
    }
}
