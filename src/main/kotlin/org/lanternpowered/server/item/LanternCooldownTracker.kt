/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://www.lanternpowered.org>
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.lanternpowered.server.item

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import org.lanternpowered.api.Lantern
import org.lanternpowered.api.cause.CauseStack
import org.lanternpowered.api.event.LanternEventFactory
import org.lanternpowered.api.ext.emptyOptionalDouble
import org.lanternpowered.api.ext.emptyOptionalInt
import org.lanternpowered.api.ext.optionalDouble
import org.lanternpowered.api.ext.optionalInt
import org.lanternpowered.server.entity.living.player.LanternPlayer
import org.lanternpowered.server.game.LanternGame
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayOutSetCooldown
import org.spongepowered.api.entity.living.player.CooldownTracker
import org.spongepowered.api.item.ItemType
import java.util.OptionalDouble
import java.util.OptionalInt

class LanternCooldownTracker(private val player: LanternPlayer) : CooldownTracker {

    private val map = Object2LongOpenHashMap<ItemType>().apply { defaultReturnValue(-1L) }

    override fun setCooldown(itemType: ItemType, ticks: Int): Boolean {
        var cooldown = ticks
        val current = LanternGame.currentTimeTicks()
        val time = this.map.getLong(itemType) - current
        if (time <= 0 && cooldown <= 0) {
            return false
        }
        val optionalStartCooldown = if (time <= 0) emptyOptionalInt() else time.toInt().optionalInt()
        val event = LanternEventFactory.createCooldownEventSet(CauseStack.current().currentCause,
                cooldown, cooldown, itemType, this.player, optionalStartCooldown)
        Lantern.eventManager.post(event)
        if (event.isCancelled)
            return false
        cooldown = event.newCooldown
        when {
            cooldown > 0 -> {
                this.map[itemType] = current + cooldown
            }
            time > 0 -> {
                this.map.removeLong(itemType)
                cooldown = 0
            }
            else -> cooldown = -1
        }
        if (cooldown >= 0) {
            this.player.connection.send(MessagePlayOutSetCooldown(itemType, cooldown))
        }
        return true
    }

    override fun resetCooldown(itemType: ItemType): Boolean {
        return setCooldown(itemType, 0)
    }

    override fun getCooldown(itemType: ItemType): OptionalInt {
        val time = this.map.getLong(itemType)
        if (time != -1L) {
            val current = LanternGame.currentTimeTicks()
            if (time > current) {
                return (time - current).toInt().optionalInt()
            }
        }
        return emptyOptionalInt()
    }

    override fun hasCooldown(itemType: ItemType): Boolean {
        val time = this.map.getLong(itemType)
        if (time != -1L) {
            val current = LanternGame.currentTimeTicks()
            if (time > current) {
                return true
            }
        }
        return false
    }

    override fun getFractionRemaining(type: ItemType): OptionalDouble {
        // TODO: Properly implement this
        return if (hasCooldown(type)) 1.0.optionalDouble() else emptyOptionalDouble()
    }

    fun process() {
        val current = LanternGame.currentTimeTicks()
        this.map.object2LongEntrySet().removeIf { entry ->
            if (entry.longValue < current) {
                val event = LanternEventFactory.createCooldownEventEnd(
                        CauseStack.current().currentCause, entry.key, this.player)
                Lantern.eventManager.post(event)
                true
            } else false
        }
    }
}