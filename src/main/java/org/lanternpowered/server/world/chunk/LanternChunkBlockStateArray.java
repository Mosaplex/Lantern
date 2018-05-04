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

import static org.lanternpowered.server.world.chunk.LanternChunk.CHUNK_SECTION_VOLUME;

import org.lanternpowered.server.block.state.LanternBlockState;
import org.lanternpowered.server.game.registry.type.block.BlockRegistryModule;
import org.lanternpowered.server.util.palette.GlobalPalette;
import org.lanternpowered.server.util.palette.LanternPaletteBasedArray;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.world.schematic.BlockPaletteType;
import org.spongepowered.api.world.schematic.BlockPaletteTypes;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("ConstantConditions")
public class LanternChunkBlockStateArray extends LanternPaletteBasedArray<BlockState> implements ChunkBlockStateArray {

    static class LanternGlobalPalette implements GlobalPalette<BlockState> {

        static final LanternGlobalPalette INSTANCE = new LanternGlobalPalette();

        @Override
        public int getId(BlockState object) {
            return BlockRegistryModule.get().getStateInternalId(object);
        }

        @Override
        public Optional<BlockState> get(int id) {
            return BlockRegistryModule.get().getStateByInternalId(id);
        }

        @Override
        public Collection<BlockState> getEntries() {
            return Sponge.getRegistry().getAllOf(BlockState.class);
        }
    }

    static class LanternBlockPalette extends LanternPalette<BlockState> implements ChunkBlockPalette {

        LanternBlockPalette(LanternChunkBlockStateArray array) {
            super(array);
        }

        @Override
        public BlockPaletteType getType() {
            return BlockPaletteTypes.LOCAL;
        }

        @Override
        public int getHighestId() {
            return ((LanternChunkBlockStateArray) this.array).assignedStates - 1;
        }

        @Override
        public Optional<Integer> get(BlockState state) {
            return super.get(state);
        }

        @Override
        public boolean remove(BlockState state) {
            throw new IllegalStateException("Cannot remove block states from the chunk block state palette.");
        }
    }

    /**
     * Deserializes the {@link LanternChunkBlockStateArray}
     * from the given {@link DataView}.
     *
     * @param dataView The data view
     * @return The deserialized block state array
     */
    public static LanternChunkBlockStateArray deserializeFrom(DataView dataView) {
        return new LanternChunkBlockStateArray(dataView);
    }

    /**
     * Serializes the {@link ChunkBlockStateArray} into the given {@link DataView}.
     *
     * @param dataView The data view
     * @param blockStatesArray The block state array to serialize
     */
    public static void serializeTo(DataView dataView, ChunkBlockStateArray blockStatesArray) {
        ((LanternChunkBlockStateArray) blockStatesArray).serializeTo(dataView);
    }

    /**
     * The default block state of air.
     */
    private static final BlockState AIR = BlockTypes.AIR.getDefaultState();

    private LanternChunkBlockStateArray(LanternChunkBlockStateArray other) {
        super(other);
    }

    LanternChunkBlockStateArray(int capacity) {
        super(LanternGlobalPalette.INSTANCE, capacity);
    }

    LanternChunkBlockStateArray(int[] stateIds) {
        super(LanternGlobalPalette.INSTANCE, stateIds);
    }

    private LanternChunkBlockStateArray(DataView dataView) {
        super(LanternGlobalPalette.INSTANCE,
                dataView.getViewList(PALETTE_QUERY).get().stream()
                        .map(LanternBlockState::deserialize)
                        .collect(Collectors.toList()),
                CHUNK_SECTION_VOLUME, (long[]) dataView.get(BLOCK_STATES_QUERY).get());
    }

    @Override
    protected void init(InternalPalette<BlockState> internalPalette) {
        internalPalette.assign(AIR_ID, AIR);
    }

    @Override
    protected LanternBlockPalette constructPalette() {
        return new LanternBlockPalette(this);
    }

    @Override
    public LanternChunkBlockStateArray copy() {
        return new LanternChunkBlockStateArray(this);
    }

    @Override
    public ChunkBlockPalette getPalette() {
        return (ChunkBlockPalette) super.getPalette();
    }

    private void serializeTo(DataView dataView) {
        final Tuple<Collection<BlockState>, long[]> serialized = serialize();

        dataView.set(BLOCK_STATES_QUERY, serialized.getSecond());
        dataView.set(PALETTE_QUERY, serialized.getFirst().stream()
                .map(LanternBlockState::serialize)
                .collect(Collectors.toList()));
    }
}
