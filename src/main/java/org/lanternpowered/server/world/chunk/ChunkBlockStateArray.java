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
package org.lanternpowered.server.world.chunk;

import org.lanternpowered.server.util.collect.array.VariableValueArray;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.world.schematic.BlockPalette;

public interface ChunkBlockStateArray {

    /**
     * This is the integer id that is always assigned to
     * the {@link BlockState} of {@link BlockTypes#AIR}.
     */
    int AIR_ID = 0;

    /**
     * The {@link DataQuery} of the palette
     * data within the {@link DataView}.
     */
    DataQuery PALETTE_QUERY = DataQuery.of("Palette");

    /**
     * The {@link DataQuery} of the block state
     * data within the {@link DataView}.
     */
    DataQuery BLOCK_STATES_QUERY = DataQuery.of("BlockStates");

    /**
     * Gets the capacity.
     *
     * @return The capacity
     */
    int getCapacity();

    /**
     * Gets the {@link BlockState} at the given index.
     *
     * @param index The index
     * @return The block state
     */
    BlockState get(int index);

    /**
     * Sets the {@link BlockState} at the given index.
     *
     * @param index The index
     * @param blockState The block state
     * @return Whether the value has changed
     */
    BlockState set(int index, BlockState blockState);

    /**
     * Gets the {@link BlockPalette} of this block
     * state array. Does not allow removal of {@link BlockState}s.
     *
     * @return The block palette
     */
    ChunkBlockPalette getPalette();

    /**
     * Gets the backing {@link VariableValueArray} which
     * holds the integer block state ids.
     *
     * @return The backing block states array
     */
    VariableValueArray getStates();

    /**
     * Creates a copy of this {@link ChunkBlockStateArray}.
     *
     * @return The copy
     */
    ChunkBlockStateArray copy();
}
