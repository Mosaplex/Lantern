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
package org.lanternpowered.server.data.key

import com.google.common.reflect.TypeToken
import org.lanternpowered.api.cause.CauseStack
import org.lanternpowered.api.ext.*
import org.lanternpowered.api.util.ToStringHelper
import org.lanternpowered.server.event.RegisteredListener
import org.lanternpowered.server.game.Lantern
import org.spongepowered.api.CatalogKey
import org.spongepowered.api.CatalogType
import org.spongepowered.api.data.DataHolder
import org.spongepowered.api.data.Key
import org.spongepowered.api.data.value.Value
import org.spongepowered.api.event.EventListener
import org.spongepowered.api.event.Order
import org.spongepowered.api.event.data.ChangeDataHolderEvent
import org.spongepowered.api.plugin.PluginContainer
import java.util.Objects

open class ValueKey<V : Value<E>, E : Any> internal constructor(
        private val key: CatalogKey,
        private val valueToken: TypeToken<V>,
        private val elementToken: TypeToken<E>
) : Key<V>, CatalogType {

    private val mutableListeners = mutableListOf<RegisteredListener<ChangeDataHolderEvent.ValueChange>>()

    /**
     * An unmodifiable list of all the registered value change event listeners.
     */
    val listeners = this.mutableListeners.asUnmodifiableList()

    private val hashCode = Objects.hash(this.valueToken, this.key, this.elementToken)

    override fun getValueToken() = this.valueToken
    override fun getElementToken() = this.elementToken

    override fun <E : DataHolder> registerEvent(holderFilter: Class<E>, listener: EventListener<ChangeDataHolderEvent.ValueChange>) {
        val keyEventListener = ValueKeyEventListener(listener, holderFilter::isInstance, this)
        val plugin = CauseStack.current().first<PluginContainer>()
        val registeredListener = Lantern.getGame().eventManager.register(
                plugin, valueChangeEventTypeToken, Order.DEFAULT, keyEventListener)
        this.mutableListeners.add(registeredListener)
    }

    override fun getKey() = this.key

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val key = other as ValueKey<*,*>
        return this.valueToken == key.valueToken &&
                this.key == key.key &&
                this.elementToken == key.elementToken
    }

    override fun hashCode() = this.hashCode

    override fun toString(): String {
        return ToStringHelper(this)
                .add("id", this.key)
                .add("valueToken", this.valueToken)
                .add("elementToken", this.elementToken)
                .toString()
    }

    companion object {

        private val valueChangeEventTypeToken = typeTokenOf<ChangeDataHolderEvent.ValueChange>()
    }
}