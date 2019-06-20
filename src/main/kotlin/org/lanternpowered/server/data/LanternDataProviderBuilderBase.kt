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
package org.lanternpowered.server.data

import org.lanternpowered.server.data.value.ValueFactory
import org.spongepowered.api.data.DataHolder
import org.spongepowered.api.data.DataTransactionResult
import org.spongepowered.api.data.Key
import org.spongepowered.api.data.value.Value

internal abstract class LanternDataProviderBuilderBase<V : Value<E>, E : Any>(private val key: Key<V>) {

    protected var allowAsyncAccess: (DataHolder.() -> Boolean)? = null
    protected var supportedByHandler: (DataHolder.() -> Boolean)? = null
    protected var removeHandler: (DataHolder.Mutable.() -> DataTransactionResult)? = null
    protected var removeFastHandler: (DataHolder.Mutable.() -> Boolean)? = null
    protected var offerValueHandler: (DataHolder.Mutable.(value: V) -> DataTransactionResult)? = null
    protected var offerValueFastHandler: (DataHolder.Mutable.(value: V) -> Boolean)? = null
    protected var offerHandler: (DataHolder.Mutable.(element: E) -> DataTransactionResult)? = null
    protected var offerFastHandler: (DataHolder.Mutable.(element: E) -> Boolean)? = null
    protected var getHandler: (DataHolder.() -> E?)? = null
    protected var getValueHandler: (DataHolder.() -> V?)? = null
    protected var withHandler: (DataHolder.Immutable<*>.(element: E) -> DataHolder.Immutable<*>?)? = null
    protected var withValueHandler: (DataHolder.Immutable<*>.(value: V) -> DataHolder.Immutable<*>?)? = null
    protected var withoutHandler: (DataHolder.Immutable<*>.() -> DataHolder.Immutable<*>?)? = null

    fun build(): LanternDataProvider<V, E> {
        val supportedTester = this.supportedByHandler ?: { true }

        // Generate missing remove handlers, defaults
        // to fail always

        var removeHandler = this.removeHandler
        var removeFastHandler = this.removeFastHandler
        if (removeHandler == null) {
            if (removeFastHandler != null) {
                removeHandler = removeFromRemoveFast(this.key, removeFastHandler)
            } else {
                // Default to non removable
                removeHandler = failRemoveHandler
                removeFastHandler = failRemoveFastHandler
            }
        }
        if (removeFastHandler == null) {
            removeFastHandler = removeFastFromRemove(this.key, removeHandler)
        }

        // Generate missing offer handlers, if none is specified,
        // offering will always fail

        var offerValueHandler = this.offerValueHandler
        var offerValueFastHandler = this.offerValueFastHandler
        var offerHandler = this.offerHandler
        var offerFastHandler = this.offerFastHandler

        if (offerValueHandler == null) {
            offerValueHandler = when {
                this.offerHandler != null -> offerValueFromOffer(this.key, offerHandler!!)
                this.offerValueFastHandler != null -> offerValueFromOfferValueFast(this.key, offerValueFastHandler!!)
                this.offerFastHandler != null -> offerValueFromOfferFast(this.key, offerFastHandler!!)
                else -> offerValueFromFail(this.key)
            }
        }
        if (offerValueFastHandler != null) {
            offerValueFastHandler = when {
                this.offerValueHandler != null -> offerValueFastFromOfferValue(this.key, offerValueHandler)
                this.offerFastHandler != null -> offerValueFastFromOfferFast(this.key, offerFastHandler!!)
                this.offerHandler != null -> offerValueFastFromOffer(this.key, offerHandler!!)
                else -> offerValueFastFromFail(this.key)
            }
        }
        offerValueFastHandler!! // Uhm, why is this needed?
        if (offerHandler != null) {
            offerHandler = when {
                this.offerValueHandler != null -> offerFromOfferValue(this.key, offerValueHandler)
                this.offerFastHandler != null -> offerFromOfferFast(this.key, offerFastHandler!!)
                this.offerValueFastHandler != null -> offerFromOfferValueFast(this.key, offerValueFastHandler)
                else -> offerFromFail(this.key)
            }
        }
        offerHandler!! // Uhm, why is this needed?
        if (offerFastHandler == null) {
            offerFastHandler = when {
                this.offerHandler != null -> offerFastFromOffer(this.key, offerHandler)
                this.offerValueFastHandler != null -> offerFastFromOfferValueFast(this.key, offerValueFastHandler)
                this.offerValueHandler != null -> offerFastFromOfferValue(this.key, offerValueHandler)
                else -> offerFastFromFail(this.key)
            }
        }

        // Generate missing retrieve handlers, at least
        // one must be specified

        var getHandler = this.getHandler
        var getValueHandler = this.getValueHandler

        if (getHandler == null) {
            if (getValueHandler == null) {
                throw IllegalStateException("At least one get handler must be set")
            }
            getHandler = getFromGetValue(this.key, getValueHandler)
        } else if (getValueHandler == null) {
            getValueHandler = getValueFromGet(this.key, getHandler)
        }

        // Generate missing with handlers

        var withHandler = this.withHandler
        var withValueHandler = this.withValueHandler

        if (withHandler == null) {
            if (withValueHandler != null) {
                withHandler = withFromWithValue(this.key, withValueHandler)
            } else {
                withHandler = { null }
                withValueHandler = { null }
            }
        } else {
            withValueHandler = withValueFromWith(this.key, withHandler)
        }

        val withoutHandler = this.withoutHandler ?: { null }
        val allowAsyncAccess = this.allowAsyncAccess ?: { false }
        val alwaysAsyncAccess = alwaysAsyncAccess === allowAsyncAccess

        return LanternDataProvider(this.key, alwaysAsyncAccess, allowAsyncAccess, supportedTester, removeHandler, removeFastHandler,
                offerValueHandler, offerValueFastHandler, offerHandler, offerFastHandler, getHandler, getValueHandler, withHandler,
                withValueHandler, withoutHandler)
    }

    private fun <H> withValueFromWith(key: Key<V>, handler: H.(element: E) -> H?): H.(value: V) -> H? {
        return { value ->
            handler(value.get())
        }
    }

    private fun <H> withFromWithValue(key: Key<V>, handler: H.(value: V) -> H?): H.(element: E) -> H? {
        return { element ->
            handler(ValueFactory.mutableOf(key, element))
        }
    }

    private fun <H>  getFromGetValue(key: Key<V>, handler: H.() -> V?): H.() -> E? {
        return { handler()?.get() }
    }

    private fun <H>  getValueFromGet(key: Key<V>, handler: H.() -> E?): H.() -> V? {
        return {
            val element = handler()
            if (element != null) ValueFactory.mutableOf(key, element) else null
        }
    }

    private fun <H> offerFromFail(key: Key<V>): H.(value: E) -> DataTransactionResult {
        return { value -> DataTransactionResult.failResult(ValueFactory.immutableOf(key, value).asImmutable()) }
    }

    private fun <H> offerFromOfferValueFast(key: Key<V>, handler: H.(value: V) -> Boolean): H.(value: E) -> DataTransactionResult {
        return { element ->
            val value = ValueFactory.immutableOf(key, element)
            if (handler(value)) {
                DataTransactionResult.successResult(value.asImmutable())
            } else {
                DataTransactionResult.failResult(value.asImmutable())
            }
        }
    }

    private fun <H> offerFromOfferFast(key: Key<V>, handler: H.(value: E) -> Boolean): H.(value: E) -> DataTransactionResult {
        return { element ->
            val value = org.lanternpowered.server.data.value.ValueFactory.immutableOf(key, element).asImmutable()
            if (handler(element)) {
                DataTransactionResult.successResult(value)
            } else {
                DataTransactionResult.failResult(value)
            }
        }
    }

    private fun <H> offerFromOfferValue(key: Key<V>, handler: H.(value: V) -> DataTransactionResult): H.(value: E) -> DataTransactionResult {
        return { value -> handler(ValueFactory.mutableOf(key, value)) }
    }

    private fun <H> offerValueFastFromFail(key: Key<V>): H.(value: V) -> Boolean {
        return { false }
    }

    private fun <H> offerValueFastFromOffer(key: Key<V>, handler: H.(value: E) -> DataTransactionResult): H.(value: V) -> Boolean {
        return { value -> handler(value.get()).isSuccessful }
    }

    private fun <H> offerValueFastFromOfferFast(key: Key<V>, handler: H.(value: E) -> Boolean): H.(value: V) -> Boolean {
        return { value -> handler(value.get()) }
    }

    private fun <H> offerValueFastFromOfferValue(key: Key<V>, handler: H.(value: V) -> DataTransactionResult): H.(value: V) -> Boolean {
        return { value -> handler(value).isSuccessful }
    }

    private fun <H> offerFastFromFail(key: Key<V>): H.(value: E) -> Boolean {
        return { false }
    }

    private fun <H> offerFastFromOfferValue(key: Key<V>, handler: H.(value: V) -> DataTransactionResult): H.(value: E) -> Boolean {
        return { value -> handler(ValueFactory.mutableOf(key, value)).isSuccessful }
    }

    private fun <H>  offerFastFromOfferValueFast(key: Key<V>, handler: H.(value: V) -> Boolean): H.(value: E) -> Boolean {
        return { value -> handler(ValueFactory.mutableOf(key, value)) }
    }

    private fun <H> offerFastFromOffer(key: Key<V>, handler: H.(element: E) -> DataTransactionResult): H.(value: E) -> Boolean {
        return { value -> handler(value).isSuccessful }
    }

    private fun <H> offerValueFromFail(key: Key<V>): H.(value: V) -> DataTransactionResult {
        return { value -> DataTransactionResult.failResult(value.asImmutable()) }
    }

    private fun <H> offerValueFromOfferFast(key: Key<V>, handler: H.(element: E) -> Boolean): H.(value: V) -> DataTransactionResult {
        return { value ->
            if (handler(value.get())) {
                DataTransactionResult.successResult(value.asImmutable())
            } else {
                DataTransactionResult.failResult(value.asImmutable())
            }
        }
    }

    private fun <H> offerValueFromOffer(key: Key<V>, handler: H.(element: E) -> DataTransactionResult): H.(value: V) -> DataTransactionResult {
        return { value -> handler(value.get()) }
    }

    private fun <H> offerValueFromOfferValueFast(key: Key<V>, handler: H.(value: V) -> Boolean): H.(value: V) -> DataTransactionResult {
        return { value ->
            if (handler(value)) {
                DataTransactionResult.successResult(value.asImmutable())
            } else {
                DataTransactionResult.failResult(value.asImmutable())
            }
        }
    }

    private fun <H> removeFromRemoveFast(key: Key<V>, handler: H.() -> Boolean): H.() -> DataTransactionResult {
        return {
            if (handler()) {
                DataTransactionResult.successNoData()
            } else {
                DataTransactionResult.failNoData()
            }
        }
    }

    private fun <H> removeFastFromRemove(key: Key<V>, handler: H.() -> DataTransactionResult): H.() -> Boolean {
        return { handler(this).isSuccessful }
    }

    companion object {

        val alwaysAsyncAccess: DataHolder.() -> Boolean = { true }

        val failRemoveHandler: DataHolder.() -> DataTransactionResult = { DataTransactionResult.failNoData() }
        val failRemoveFastHandler: DataHolder.() -> Boolean = { false }
    }
}