package org.lanternpowered.server.world.chunk;

import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.world.schematic.BlockPalette;

public interface ChunkBlockStateArray {

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
     */
    void set(int index, BlockState blockState);

    /**
     * Gets the {@link BlockPalette} of this block
     * state array. Does not allow removal of {@link BlockState}s.
     *
     * @return The block palette
     */
    ChunkBlockPalette getPalette();

    /**
     * Serializes the block states and
     * palette to the target {@link DataView}.
     *
     * @param dataView The data view
     */
    void serializeTo(DataView dataView);
}
