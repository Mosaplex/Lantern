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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.lanternpowered.server.world.TrackerIdAllocator.INVALID_ID;
import static org.lanternpowered.server.world.chunk.LanternChunk.CHUNK_SECTION_VOLUME;

import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.lanternpowered.server.block.state.LanternBlockState;
import org.lanternpowered.server.game.registry.InternalIDRegistries;
import org.lanternpowered.server.game.registry.type.block.BlockRegistryModule;
import org.lanternpowered.server.util.BitHelper;
import org.lanternpowered.server.util.collect.array.VariableValueArray;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.world.schematic.BlockPaletteType;
import org.spongepowered.api.world.schematic.BlockPaletteTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

@SuppressWarnings("ConstantConditions")
public class LanternChunkBlockStateArray implements ChunkBlockStateArray {

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

    /**
     * The array that holds all the block state ids for
     * each block that is present in the current chunk.
     */
    private VariableValueArray blockStates;

    /**
     * The internal block palette.
     */
    private InternalPalette internalPalette;

    /**
     * The amount of bits used for each value.
     */
    private int bits;

    /**
     * The amount of block states that are assigned. Start
     * at 1, air is always assigned.
     */
    private int assignedStates = 1;

    private final ChunkBlockPalette palette = new ChunkBlockPalette() {

        @Override
        public BlockPaletteType getType() {
            return internalPalette.isLocal() ? BlockPaletteTypes.LOCAL : BlockPaletteTypes.GLOBAL;
        }

        @Override
        public int getHighestId() {
            return assignedStates - 1;
        }

        @Override
        public Optional<BlockState> get(int id) {
            return Optional.ofNullable(internalPalette.get(id));
        }

        @Override
        public Optional<Integer> get(BlockState state) {
            final int id = internalPalette.get(state);
            return id == INVALID_ID ? Optional.empty() : Optional.of(id);
        }

        @Override
        public int getOrAssign(BlockState state) {
            return getOrAssignState(state);
        }

        @Override
        public boolean remove(BlockState state) {
            throw new IllegalStateException("Cannot remove block states from the chunk block state palette.");
        }

        @Override
        public Collection<BlockState> getEntries() {
            return Collections.unmodifiableCollection(internalPalette.getEntries());
        }

        @Override
        public int[] getOrAssign(BlockState... blockStates) {
            final int[] ids = new int[blockStates.length];
            int toAssign = 0;
            for (int i = 0; i < blockStates.length; i++) {
                final int id = internalPalette.get(blockStates[i]);
                if (id == INVALID_ID) {
                    toAssign++;
                } else {
                    ids[i] = id;
                }
            }
            if (toAssign != 0) {
                expandForAssign(toAssign);
                for (int i = 0; i < blockStates.length; i++) {
                    ids[i] = getOrAssign(blockStates[i]);
                }
            }
            return ids;
        }
    };

    private LanternChunkBlockStateArray(LanternChunkBlockStateArray other) {
        this.blockStates = other.blockStates.copy();
        this.internalPalette = other.internalPalette.copy();
        this.assignedStates = other.assignedStates;
        this.bits = other.bits;
    }

    public LanternChunkBlockStateArray(int capacity) {
        expand(4, capacity);
    }

    /**
     * Creates a {@link LanternChunkBlockStateArray} from a integer
     * array of global block state ids.
     *
     * @param stateIds The state ids
     */
    LanternChunkBlockStateArray(int[] stateIds) {
        final MapBackedInternalPalette palette = new MapBackedInternalPalette(16);
        // Air is always assigned to id 0
        palette.assign(AIR_ID, AIR);
        int id = 1;

        for (int stateId : stateIds) {
            final BlockState state = BlockRegistryModule.get().getStateByInternalId(stateId).get();
            if (palette.get(state) == INVALID_ID) {
                palette.assign(id++, state);
            }
        }
        this.bits = BitHelper.requiredBits(id - 1);

        if (this.bits <= 8) {
            if (this.bits <= 4) {
                this.bits = 4;
                this.internalPalette = new ArrayBackedInternalPalette(this.bits);
                id = 0;
                for (BlockState blockState : palette.getEntries()) {
                    this.internalPalette.assign(id++, blockState);
                }
            } else {
                this.internalPalette = palette;
            }
            this.assignedStates = palette.getEntries().size();
        } else {
            this.internalPalette = GlobalInternalPalette.INSTANCE;
            this.assignedStates = InternalIDRegistries.BLOCK_TYPE_IDS.size();
        }

        this.blockStates = new VariableValueArray(this.bits, stateIds.length);

        // Just copy over the ids
        if (this.internalPalette == GlobalInternalPalette.INSTANCE) {
            for (int i = 0; i < stateIds.length; i++) {
                this.blockStates.set(i, stateIds[i]);
            }
        } else {
            // Set remapped based on the palette
            for (int i = 0; i < stateIds.length; i++) {
                final BlockState state = BlockRegistryModule.get().getStateByInternalId(stateIds[i]).get();
                this.blockStates.set(i, this.internalPalette.get(state));
            }
        }
    }

    private LanternChunkBlockStateArray(DataView dataView) {
        final long[] rawBlockStates = (long[]) dataView.get(BLOCK_STATES_QUERY).get();
        final List<BlockState> palette = dataView.getViewList(PALETTE_QUERY).get().stream()
                .map(LanternBlockState::deserialize)
                .collect(Collectors.toList());
        int bits = BitHelper.requiredBits(palette.size() - 1);
        if (bits <= 8) {
            if (bits <= 4) {
                bits = 4;
                this.internalPalette = new ArrayBackedInternalPalette(bits);
            } else {
                this.internalPalette = new MapBackedInternalPalette(palette.size());
            }
            for (int i = 0; i < palette.size(); i++) {
                this.internalPalette.assign(i, palette.get(i));
            }
        }
        this.blockStates = new VariableValueArray(bits, CHUNK_SECTION_VOLUME, rawBlockStates);
    }

    private int getOrAssignState(BlockState state) {
        final int id = this.internalPalette.get(state);
        if (id != INVALID_ID) {
            return id;
        }
        return assignState(state);
    }

    private int assignState(BlockState state) {
        final int id = this.assignedStates++;
        // Check if more bits are needed
        if (1 << this.bits == id) {
            // The backing palette/data array isn't big enough, so
            // it should be expanded
            expand(this.bits + 1);
        }
        // Things got expanded to the global
        // palette, just return the global id
        if (this.internalPalette == GlobalInternalPalette.INSTANCE) {
            // The assigned states value got maxed
            this.assignedStates = InternalIDRegistries.BLOCK_TYPE_IDS.size();
            return this.internalPalette.get(state);
        }
        // Now the next id can be assigned
        this.internalPalette.assign(id, state);
        return id;
    }

    private void expandForAssign(int statesToAdd) {
        expand(BitHelper.requiredBits(this.assignedStates + statesToAdd - 1));
    }

    private void expand(int bits) {
        expand(bits, this.blockStates.getCapacity());
    }

    @SuppressWarnings("ConstantConditions")
    private void expand(int bits, int capacity) {
        // Don't shrink using this method
        if (bits <= this.bits) {
            return;
        }
        final InternalPalette old = this.internalPalette;
        // Create the new internal palette
        // Bits will never be smaller then 4
        if (bits < 4) {
            bits = 4;
        }
        if (bits <= 8) {
            // Should only happen once
            if (bits <= 4) {
                this.internalPalette = new ArrayBackedInternalPalette(bits);
                // Air is always assigned to id 0
                this.internalPalette.assign(AIR_ID, AIR);
            // Only upgrade if it isn't already upgraded to a MapBackedInternalPalette
            } else if (!(this.internalPalette instanceof MapBackedInternalPalette)) {
                this.internalPalette = new MapBackedInternalPalette((1 << this.bits) + 1);
                // Air is always assigned to id 0
                this.internalPalette.assign(AIR_ID, AIR);
                // Copy the old contents
                if (old != null) {
                    int i = 0;
                    for (BlockState blockState : old.getEntries()) {
                        this.internalPalette.assign(i++, blockState);
                    }
                }
            }
        } else {
            // Upgrade even more, just use the global palette
            this.internalPalette = GlobalInternalPalette.INSTANCE;
        }
        this.bits = bits;
        final VariableValueArray oldStates = this.blockStates;
        this.blockStates = new VariableValueArray(bits, capacity);
        // Copy the states to the new array
        if (oldStates != null) {
            for (int i = 0; i < capacity; i++) {
                this.blockStates.set(i, oldStates.get(i));
            }
        }
    }

    @Override
    public int getCapacity() {
        return this.blockStates.getCapacity();
    }

    @Override
    public BlockState get(int index) {
        final int id = this.blockStates.get(index);
        final BlockState blockState = this.internalPalette.get(id);
        if (blockState == null) {
            System.out.println(Joiner.on(", ").join(this.internalPalette.getEntries().stream().map(BlockState::getId).collect(Collectors.toList())));
        }
        checkNotNull(blockState, "Failed to find mapping for %s", id);
        return blockState;
    }

    @Override
    public BlockState set(int index, BlockState blockState) {
        final BlockState oldState = get(index);
        if (oldState == blockState) {
            return oldState;
        }
        final int id = getOrAssignState(blockState);
        this.blockStates.set(index, id);
        return oldState;
    }

    @Override
    public ChunkBlockPalette getPalette() {
        return this.palette;
    }

    @Override
    public VariableValueArray getStates() {
        return this.blockStates;
    }

    void serializeTo(DataView dataView) {
        final int capacity = this.blockStates.getCapacity();

        final MapBackedInternalPalette palette = new MapBackedInternalPalette(16);
        // Air is always assigned to id 0
        palette.assign(AIR_ID, AIR);
        int id = 1;
        for (int i = 0; i < capacity; i++) {
            final BlockState state = this.internalPalette.get(this.blockStates.get(i));
            if (palette.get(state) == INVALID_ID) {
                palette.assign(id++, state);
            }
        }
        final int bits = BitHelper.requiredBits(id - 1);

        final VariableValueArray valueArray = new VariableValueArray(bits, capacity);
        for (int i = 0; i < capacity; i++) {
            valueArray.set(i, palette.get(this.internalPalette.get(this.blockStates.get(i))));
        }

        dataView.set(BLOCK_STATES_QUERY, valueArray.getBacking());
        dataView.set(PALETTE_QUERY, palette.getEntries().stream()
                .map(LanternBlockState::serialize)
                .collect(Collectors.toList()));
    }

    @Override
    public ChunkBlockStateArray copy() {
        return new LanternChunkBlockStateArray(this);
    }

    interface InternalPalette {

        default boolean isLocal() {
            return true;
        }

        @Nullable
        BlockState get(int id);

        int get(BlockState state);

        void assign(int id, BlockState state);

        Collection<BlockState> getEntries();

        InternalPalette copy();
    }

    static class GlobalInternalPalette implements InternalPalette {

        static final GlobalInternalPalette INSTANCE = new GlobalInternalPalette();

        @Override
        public int get(BlockState state) {
            return BlockRegistryModule.get().getStateInternalId(state);
        }

        @Override
        public void assign(int id, BlockState state) {
            throw new IllegalStateException("This should never happen");
        }

        @Override
        public boolean isLocal() {
            return false;
        }

        @Override
        public BlockState get(int id) {
            return BlockRegistryModule.get().getStateByInternalId(id).orElse(null);
        }

        @Override
        public Collection<BlockState> getEntries() {
            return Sponge.getRegistry().getAllOf(BlockState.class);
        }

        @Override
        public InternalPalette copy() {
            return this;
        }
    }

    static class ArrayBackedInternalPalette implements InternalPalette {

        private final BlockState[] blockStates;
        private final List<BlockState> blockStatesList;

        private int assignedStates = 0;

        ArrayBackedInternalPalette(int bits) {
            this(new BlockState[1 << bits], 0);
        }

        private ArrayBackedInternalPalette(BlockState[] blockStates, int assignedStates) {
            this.blockStatesList = Arrays.asList(blockStates);
            this.blockStates = blockStates;
            this.assignedStates = assignedStates;
        }

        @Override
        public BlockState get(int id) {
            return id < this.assignedStates ? this.blockStates[id] : null;
        }

        @Override
        public int get(BlockState state) {
            for (int i = 0; i < this.assignedStates; i++) {
                final BlockState other = this.blockStates[i];
                if (other == state) {
                    return i;
                } else if (other == null) {
                    break;
                }
            }
            return INVALID_ID;
        }

        @Override
        public void assign(int id, BlockState state) {
            this.blockStates[id] = state;
            this.assignedStates = Math.max(this.assignedStates, id + 1);
        }

        @Override
        public Collection<BlockState> getEntries() {
            return this.blockStatesList.subList(0, this.assignedStates);
        }

        @Override
        public InternalPalette copy() {
            return new ArrayBackedInternalPalette(Arrays.copyOf(this.blockStates, this.blockStates.length), this.assignedStates);
        }
    }

    static class MapBackedInternalPalette implements InternalPalette {

        private final List<BlockState> blockStates;
        private final Object2IntMap<BlockState> idByBlockState;

        MapBackedInternalPalette(int size) {
            this(new ArrayList<>(size), new Object2IntOpenHashMap<>(size));
        }

        private MapBackedInternalPalette(List<BlockState> blockStates,
                Object2IntMap<BlockState> idByBlockState) {
            this.blockStates = blockStates;
            this.idByBlockState = idByBlockState;
            this.idByBlockState.defaultReturnValue(INVALID_ID);
        }

        @Override
        public BlockState get(int id) {
            return id < this.blockStates.size() ? this.blockStates.get(id) : null;
        }

        @Override
        public int get(BlockState state) {
            return this.idByBlockState.getInt(state);
        }

        @Override
        public void assign(int id, BlockState state) {
            this.idByBlockState.put(state, id);
            this.blockStates.add(state);
        }

        @Override
        public Collection<BlockState> getEntries() {
            return this.blockStates;
        }

        @Override
        public InternalPalette copy() {
            return new MapBackedInternalPalette(new ArrayList<>(this.blockStates),
                    new Object2IntOpenHashMap<>(this.idByBlockState));
        }
    }
}
