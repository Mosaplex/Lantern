package org.lanternpowered.server.world.chunk;

import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.schematic.BlockPalette;

public interface ChunkBlockPalette extends BlockPalette {

    /**
     * Assigns multiple {@link BlockState}s at the same time.
     * <p>Using this method has performance wise a benefit over
     * calling {@link #getOrAssign(BlockState)} multiple times.
     *
     * @param blockStates The block states
     * @return The assigned block ids
     */
    int[] getOrAssign(BlockState... blockStates);
}
