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
package org.lanternpowered.server.network.item;

import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import org.lanternpowered.server.data.io.store.item.ItemStackStore;
import org.lanternpowered.server.inventory.LanternItemStack;
import org.lanternpowered.server.network.buffer.ByteBuffer;
import org.lanternpowered.server.network.buffer.objects.ValueSerializer;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

public class ItemStackValueSerializer implements ValueSerializer<ItemStack> {

    private final static DataQuery INTERNAL_ID = DataQuery.of("_%$iid");
    private final static DataQuery UNIQUE_ID = DataQuery.of("_%$uid");

    @Override
    public void write(ByteBuffer buf, @Nullable ItemStack object) throws CodecException {
        if (object == null || object.isEmpty()) {
            buf.writeShort((short) -1);
        } else {
            DataView dataView = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED);
            // Add all the NBT data
            ItemStackStore.INSTANCE.serialize((LanternItemStack) object, dataView);
            // We only need to write the tag data
            dataView = dataView.getView(ItemStackStore.TAG).orElseGet(() -> DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED));
            // Add our custom data
            final int[] ids = NetworkItemTypeRegistry.itemTypeToInternalAndNetworkId.get(object.getType());
            if (ids == null) {
                throw new EncoderException("Invalid vanilla/modded item type id: " + object.getType().getId());
            }
            // Add the server assigned internal id
            dataView.set(INTERNAL_ID, ids[0]);
            // Add a unique id to the stack to prevent it from stacking
            if (object.getMaxStackQuantity() == 1) {
                dataView.set(UNIQUE_ID, ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
            }
            // Write the data
            buf.writeShort((short) ids[1]); // Network id
            buf.writeByte((byte) object.getQuantity());
            buf.writeDataView(dataView);
        }
    }

    @Nullable
    @Override
    public ItemStack read(ByteBuffer buf) throws CodecException {
        final short networkId = buf.readShort();
        if (networkId == -1) {
            return null;
        }
        final int amount = buf.readByte();
        final DataView tag = buf.readDataView();
        ItemType itemType = null;
        if (tag != null) {
            final int internalId = tag.getInt(INTERNAL_ID).orElse(-1);
            if (internalId != -1) {
                itemType = NetworkItemTypeRegistry.internalIdToItemType.get(internalId);
                if (itemType == null) {
                    throw new DecoderException("Received ItemStack with unknown internal id: " + internalId);
                }
            }
            tag.remove(INTERNAL_ID);
            tag.remove(UNIQUE_ID);
        }
        if (itemType == null) {
            itemType = NetworkItemTypeRegistry.networkIdToItemType.get(networkId);
            if (itemType == null) {
                // We know the id, but it's not implemented yet
                if (NetworkItemTypeRegistry.networkIdToNormal.containsKey(networkId)) {
                    return null;
                } else {
                    throw new DecoderException("Received ItemStack with unknown network id: " + networkId);
                }
            }
        }
        final LanternItemStack itemStack = new LanternItemStack(itemType, amount);
        final DataView dataView = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED);
        dataView.set(ItemStackStore.QUANTITY, amount);
        if (tag != null) {
            dataView.set(ItemStackStore.TAG, tag);
        }
        ItemStackStore.INSTANCE.deserialize(itemStack, dataView);
        return itemStack;
    }
}
