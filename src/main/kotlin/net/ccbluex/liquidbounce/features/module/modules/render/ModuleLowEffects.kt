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
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories

/**
 * LowEffects module
 *
 * Bundles small first-person visual tweaks for PvP into a single module, each
 * one toggleable on its own. The kind of things people usually get from "low"
 * resource packs, but with one click and no Optifine.
 *
 * Designed to be easily extended: add a new [ToggleableValueGroup] (or a simple
 * toggle) here and gate the matching mixin on its `running`/value, following the
 * [LowFire] / [lowTotem] examples.
 *
 * Note: some related tweaks already exist in [ModuleAntiBlind] (fire opacity via
 * `FireOpacity`, and removing the totem pop globally via `FloatingItems`). This
 * module only adds what AntiBlind does not: lowering the fire *geometry* and a
 * totem-specific pop removal.
 */
object ModuleLowEffects : ClientModule("LowEffects", ModuleCategories.XTETRADOX) {

    /**
     * Removes the totem of undying "pop" animation (the big spinning totem that
     * covers the screen when a totem activates). Unlike AntiBlind's FloatingItems
     * option, this only affects totems, so other item activations stay visible.
     *
     * @see net.ccbluex.liquidbounce.injection.mixins.minecraft.render.MixinGameRenderer
     */
    val lowTotem by boolean("LowTotem", true)

    init {
        tree(LowFire)
    }

    /**
     * Lowers and shrinks the first-person fire overlay so the flames cover less
     * of the screen while burning (the classic "low fire" pack effect, as opposed
     * to just making it transparent). [scale] shrinks the flames vertically and
     * [offset] shifts them up/down. Tune both in-game to taste.
     *
     * @see net.ccbluex.liquidbounce.injection.mixins.minecraft.render.MixinScreenEffectRenderer
     */
    object LowFire : ToggleableValueGroup(this, "LowFire", true) {
        val scale by float("Scale", 0.5f, 0.1f..1f)
        val offset by float("Offset", 0f, -1f..1f)
    }
}
