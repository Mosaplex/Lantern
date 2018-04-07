package org.lanternpowered.server.world.chunk;

import static com.google.common.base.Preconditions.checkNotNull;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.lanternpowered.server.block.state.LanternBlockState;
import org.lanternpowered.server.game.registry.InternalIDRegistries;
import org.lanternpowered.server.game.registry.type.block.BlockRegistryModule;
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

public class LanternChunkBlockStateArray implements ChunkBlockStateArray {

    /**
     * The default block state of air.
     */
    private static final BlockState AIR = BlockTypes.AIR.getDefaultState();

    /**
     * A return value that is used when no id could
     * be found for a specific {@link BlockState}.
     */
    static final int INVALID_ID = -1;

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
            return BlockPaletteTypes.LOCAL;
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

    public LanternChunkBlockStateArray(int capacity) {
        expand(4, capacity);
    }

    int getOrAssignState(BlockState state) {
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
        expand(requiredBits(this.assignedStates + statesToAdd - 1));
    }

    private static int requiredBits(int value) {
        for (int i = Integer.SIZE - 1; i >= 0; i--) {
            if ((value >> i) != 0) {
                return i + 1;
            }
        }
        return 1; // 0 always needs one bit
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
        this.bits = bits;
        if (bits <= 8) {
            // Should only happen once
            if (bits <= 4) {
                this.internalPalette = new ArrayBackedInternalPalette(bits);
                // Air is always assigned to id 0
                this.internalPalette.assign(0, AIR);
            // Only upgrade if it isn't already upgraded to a MapBackedInternalPalette
            } else if (!(this.internalPalette instanceof MapBackedInternalPalette)) {
                this.internalPalette = new MapBackedInternalPalette(bits);
                // Air is always assigned to id 0
                this.internalPalette.assign(0, AIR);
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
    public BlockState get(int index) {
        final int id = this.blockStates.get(index);
        final BlockState blockState = this.internalPalette.get(id);
        checkNotNull(blockState);
        return blockState;
    }

    @Override
    public void set(int index, BlockState blockState) {
        final int id = getOrAssignState(blockState);
        this.blockStates.set(index, id);
    }

    @Override
    public ChunkBlockPalette getPalette() {
        return this.palette;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void serializeTo(DataView dataView) {
        final int capacity = this.blockStates.getCapacity();

        final MapBackedInternalPalette palette = new MapBackedInternalPalette(4);
        // Air is always assigned to id 0
        palette.assign(0, AIR);
        int id = 1;
        for (int i = 0; i < capacity; i++) {
            final BlockState state = this.internalPalette.get(this.blockStates.get(i));
            if (palette.get(state) == INVALID_ID) {
                palette.assign(id++, state);
            }
        }
        final int bits = requiredBits(id - 1);

        final VariableValueArray valueArray = new VariableValueArray(bits, capacity);
        for (int i = 0; i < capacity; i++) {
            valueArray.set(i, palette.get(this.internalPalette.get(this.blockStates.get(i))));
        }

        dataView.set(BLOCK_STATES_QUERY, valueArray.getBacking());
        dataView.set(PALETTE_QUERY, palette.getEntries().stream()
                .map(LanternBlockState::serialize)
                .collect(Collectors.toList()));
    }

    interface InternalPalette {

        @Nullable
        BlockState get(int id);

        int get(BlockState state);

        void assign(int id, BlockState state);

        Collection<BlockState> getEntries();
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
        public BlockState get(int id) {
            return BlockRegistryModule.get().getStateByInternalId(id).orElse(null);
        }

        @Override
        public Collection<BlockState> getEntries() {
            return Sponge.getRegistry().getAllOf(BlockState.class);
        }
    }

    static class ArrayBackedInternalPalette implements InternalPalette {

        private final BlockState[] blockStates;
        private final List<BlockState> blockStatesList;

        private int assignedStates = 0;

        ArrayBackedInternalPalette(int bits) {
            this.blockStates = new BlockState[1 << bits];
            this.blockStatesList = Arrays.asList(this.blockStates);
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
        }

        @Override
        public Collection<BlockState> getEntries() {
            return this.blockStatesList;
        }
    }

    static class MapBackedInternalPalette implements InternalPalette {

        private final List<BlockState> blockStates;
        private final Object2IntMap<BlockState> idByBlockState;

        MapBackedInternalPalette(int bits) {
            final int size = 1 << bits;
            this.blockStates = new ArrayList<>(size);
            this.idByBlockState = new Object2IntOpenHashMap<>(size);
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
    }
}
