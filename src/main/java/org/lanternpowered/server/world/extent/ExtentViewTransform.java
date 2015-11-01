/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://github.com/LanternPowered/LanternServer>
 * Copyright (c) Contributors
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
package org.lanternpowered.server.world.extent;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.lanternpowered.server.util.VecHelper;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.ScheduledBlockUpdate;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.Property;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.data.value.immutable.ImmutableValue;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.persistence.InvalidDataException;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.util.Functional;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.extent.Extent;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.Preconditions;

public class ExtentViewTransform extends AbstractExtent {

    public static class DiscreteTransform3to2 {

        private final DiscreteTransform3 transform;
        private final boolean valid;

        private DiscreteTransform3to2(DiscreteTransform3 transform) {
            this.transform = transform;

            /*
                Biomes are 2 dimensional and form a plane on the x and z axes.
                The y axis is ignored when converting from 3D to 2D and is
                perpendicular to these 2 axes.
                We can only sample biomes if they stay in the xz plane. If we
                have something different we effectively have no biomes. We can
                transform the x and z axes to figure this out, finding the
                perpendicular axis using the cross product.
            */

            final Vector3i xTransformed = transform.transform(Vector3i.UNIT_X);
            final Vector3i zTransformed = transform.transform(Vector3i.UNIT_Z);
            final Vector3i perpendicular = zTransformed.cross(xTransformed);

            final float xSign = Math.copySign(1, perpendicular.getX());
            final float ySign = Math.copySign(1, perpendicular.getY());
            final float zSign = Math.copySign(1, perpendicular.getZ());
            this.valid = xSign == zSign && xSign != ySign;
        }

        public int transformX(int x, int y) {
            Preconditions.checkState(valid, "Cannot access biomes when rotated around an axis that isn't y");
            return transform.transformX(x, 0, y);
        }

        public int transformZ(int x, int y) {
            Preconditions.checkState(valid, "Cannot access biomes when rotated around an axis that isn't y");
            return transform.transformZ(x, 0, y);
        }

    }

    private final Extent extent;
    private final DiscreteTransform3 transform;
    private final DiscreteTransform3 inverseTransform;
    private final DiscreteTransform3to2 inverseTransform2;
    private final Vector3i blockMin;
    private final Vector3i blockMax;
    private final Vector3i blockSize;
    private final Vector2i biomeMin;
    private final Vector2i biomeMax;
    private final Vector2i biomeSize;

    public ExtentViewTransform(Extent extent, DiscreteTransform3 transform) {
        this.extent = extent;
        this.transform = transform;
        this.inverseTransform = transform.invert();
        this.inverseTransform2 = new DiscreteTransform3to2(this.inverseTransform);

        final Vector3i blockA = transform.transform(extent.getBlockMin());
        final Vector3i blockB = transform.transform(extent.getBlockMax());
        this.blockMin = blockA.min(blockB);
        this.blockMax = blockA.max(blockB);
        this.blockSize = this.blockMax.sub(this.blockMin).add(Vector3i.ONE);

        final Vector2i biomeMin = extent.getBiomeMin();
        final Vector2i biomeMax = extent.getBiomeMax();
        final Vector2i biomeA = transform.transform(new Vector3i(biomeMin.getX(), 0, biomeMin.getY())).toVector2(true);
        final Vector2i biomeB = transform.transform(new Vector3i(biomeMax.getX(), 0, biomeMax.getY())).toVector2(true);
        this.biomeMin = biomeA.min(biomeB);
        this.biomeMax = biomeA.max(biomeB);
        this.biomeSize = this.biomeMax.sub(this.biomeMin).add(Vector2i.ONE);
    }

    @Override
    public Location<? extends Extent> getLocation(int x, int y, int z) {
        // TODO: Is this right?
        return new Location<ExtentViewTransform>(this, x, y, z);
    }

    @Override
    public Location<? extends Extent> getLocation(double x, double y, double z) {
        // TODO: Is this right?
        return new Location<ExtentViewTransform>(this, x, y, z);
    }

    @Override
    public UUID getUniqueId() {
        return this.extent.getUniqueId();
    }

    @Override
    public boolean isLoaded() {
        return this.extent.isLoaded();
    }

    @Override
    public Vector2i getBiomeMin() {
        return this.biomeMin;
    }

    @Override
    public Vector2i getBiomeMax() {
        return this.biomeMax;
    }

    @Override
    public Vector2i getBiomeSize() {
        return this.biomeSize;
    }

    @Override
    public boolean containsBiome(int x, int z) {
        return this.extent.containsBiome(this.inverseTransform2.transformX(x, z), this.inverseTransform2.transformZ(x, z));
    }

    @Override
    public BiomeType getBiome(int x, int z) {
        return this.extent.getBiome(this.inverseTransform2.transformX(x, z), this.inverseTransform2.transformZ(x, z));
    }

    @Override
    public void setBiome(int x, int z, BiomeType biome) {
        this.extent.setBiome(this.inverseTransform2.transformX(x, z), this.inverseTransform2.transformZ(x, z), biome);
    }

    @Override
    public Vector3i getBlockMax() {
        return this.blockMax;
    }

    @Override
    public Vector3i getBlockMin() {
        return this.blockMin;
    }

    @Override
    public Vector3i getBlockSize() {
        return this.blockSize;
    }

    @Override
    public boolean containsBlock(int x, int y, int z) {
        return this.extent.containsBlock(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z), this.inverseTransform
            .transformZ(x, y, z));
    }

    @Override
    public BlockType getBlockType(int x, int y, int z) {
        return this.getBlock(x, y, z).getType();
    }

    @Override
    public BlockState getBlock(int x, int y, int z) {
        return this.extent.getBlock(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z), this.inverseTransform
            .transformZ(x, y, z));
    }

    @Override
    public <T extends Property<?, ?>> Optional<T> getProperty(int x, int y, int z, Direction direction, Class<T> propertyClass) {
        return this.extent.getProperty(new Vector3i(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
                this.inverseTransform.transformZ(x, y, z)), direction, propertyClass);
    }

    /**
     * TODO: Use true/false as default notifyNeighbors setting?
     */
    @Override
    public void setBlock(int x, int y, int z, BlockState block, boolean notifyNeighbors) {
        this.extent.setBlock(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z), this.inverseTransform
                .transformZ(x, y, z), block, notifyNeighbors);
    }

    @Override
    public void setBlock(int x, int y, int z, BlockState block) {
        this.extent.setBlock(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z), this.inverseTransform
            .transformZ(x, y, z), block);
    }

    @Override
    public BlockSnapshot createSnapshot(int x, int y, int z) {
        return this.extent.createSnapshot(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
                this.inverseTransform.transformZ(x, y, z));
    }

    @Override
    public boolean restoreSnapshot(int x, int y, int z, BlockSnapshot snapshot, boolean force, boolean notifyNeighbors) {
        return this.extent.restoreSnapshot(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
                this.inverseTransform.transformZ(x, y, z), snapshot, force, notifyNeighbors);
    }

    @Override
    public Optional<Entity> restoreSnapshot(EntitySnapshot snapshot, Vector3d position) {
        return this.extent.restoreSnapshot(snapshot, this.inverseTransform(position));
    }

    @Override
    public boolean spawnEntity(Entity entity, Cause cause) {
        final Location<World> location = entity.getLocation();
        entity.setLocation(new Location<World>(location.getExtent(), this.inverseTransform(location.getPosition())));
        return this.extent.spawnEntity(entity, cause);
    }

    @Override
    public Collection<ScheduledBlockUpdate> getScheduledUpdates(int x, int y, int z) {
        return this.extent.getScheduledUpdates(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z));
    }

    @Override
    public ScheduledBlockUpdate addScheduledUpdate(int x, int y, int z, int priority, int ticks) {
        return this.extent.addScheduledUpdate(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), priority, ticks);
    }

    @Override
    public void removeScheduledUpdate(int x, int y, int z, ScheduledBlockUpdate update) {
        this.extent.removeScheduledUpdate(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), update);
    }

    @Override
    public <T extends Property<?, ?>> Optional<T> getProperty(int x, int y, int z, Class<T> propertyClass) {
        return this.extent.getProperty(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), propertyClass);
    }

    @Override
    public Collection<Property<?, ?>> getProperties(int x, int y, int z) {
        return this.extent.getProperties(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z));
    }

    @Override
    public <E> Optional<E> get(int x, int y, int z, Key<? extends BaseValue<E>> key) {
        return this.extent.get(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), key);
    }

    @Override
    public <T extends DataManipulator<?, ?>> Optional<T> get(int x, int y, int z, Class<T> manipulatorClass) {
        return this.extent.get(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulatorClass);
    }

    @Override
    public Set<ImmutableValue<?>> getValues(int x, int y, int z) {
        return this.extent.getValues(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z));
    }

    @Override
    public <T extends DataManipulator<?, ?>> Optional<T> getOrCreate(int x, int y, int z, Class<T> manipulatorClass) {
        return this.extent.getOrCreate(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulatorClass);
    }

    @Override
    public <E> E getOrNull(int x, int y, int z, Key<? extends BaseValue<E>> key) {
        return this.extent.getOrNull(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), key);
    }

    @Override
    public <E> E getOrElse(int x, int y, int z, Key<? extends BaseValue<E>> key, E defaultValue) {
        return this.extent.getOrElse(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), key, defaultValue);
    }

    @Override
    public <E, V extends BaseValue<E>> Optional<V> getValue(int x, int y, int z, Key<V> key) {
        return this.extent.getValue(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), key);
    }

    @Override
    public boolean supports(int x, int y, int z, Key<?> key) {
        return this.extent.supports(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), key);
    }

    @Override
    public boolean supports(int x, int y, int z, BaseValue<?> value) {
        return this.extent.supports(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), value);
    }

    @Override
    public boolean supports(int x, int y, int z, Class<? extends DataManipulator<?, ?>> manipulatorClass) {
        return this.extent.supports(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulatorClass);
    }

    @Override
    public boolean supports(int x, int y, int z, DataManipulator<?, ?> manipulator) {
        return this.extent.supports(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulator);
    }

    @Override
    public Set<Key<?>> getKeys(int x, int y, int z) {
        return this.extent.getKeys(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z));
    }

    @Override
    public <E> DataTransactionResult transform(int x, int y, int z, Key<? extends BaseValue<E>> key, Function<E, E> function) {
        return this.extent.transform(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), key, function);
    }

    @Override
    public <E> DataTransactionResult offer(int x, int y, int z, BaseValue<E> value) {
        return this.extent.offer(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), value);
    }

    @Override
    public <E> DataTransactionResult offer(int x, int y, int z, Key<? extends BaseValue<E>> key, E value) {
        return this.extent.offer(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z),key, value);
    }

    @Override
    public DataTransactionResult offer(int x, int y, int z, DataManipulator<?, ?> manipulator) {
        return this.extent.offer(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulator);
    }

    @Override
    public DataTransactionResult offer(int x, int y, int z, DataManipulator<?, ?> manipulator, MergeFunction function) {
        return this.extent.offer(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulator, function);
    }

    @Override
    public DataTransactionResult offer(int x, int y, int z, Iterable<DataManipulator<?, ?>> manipulators) {
        return this.extent.offer(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulators);
    }

    @Override
    public DataTransactionResult offer(Vector3i blockPosition, Iterable<DataManipulator<?, ?>> values, MergeFunction function) {
        return this.extent.offer(this.inverseTransform.transform(blockPosition), values, function);
    }

    @Override
    public DataTransactionResult remove(int x, int y, int z, Key<?> key) {
        return this.extent.remove(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), key);
    }

    @Override
    public DataTransactionResult remove(int x, int y, int z, Class<? extends DataManipulator<?, ?>> manipulatorClass) {
        return this.extent.remove(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), manipulatorClass);
    }

    @Override
    public DataTransactionResult undo(int x, int y, int z, DataTransactionResult result) {
        return this.extent.undo(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), result);
    }

    @Override
    public Collection<DataManipulator<?, ?>> getManipulators(int x, int y, int z) {
        return this.extent.getManipulators(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z));
    }

    @Override
    public boolean validateRawData(int x, int y, int z, DataView container) {
        return this.extent.validateRawData(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), container);
    }

    @Override
    public void setRawData(int x, int y, int z, DataView container) throws InvalidDataException {
        this.extent.setRawData(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z), container);
    }

    @Override
    public DataTransactionResult copyFrom(int xTo, int yTo, int zTo, DataHolder from) {
        return this.extent.copyFrom(this.inverseTransform.transformX(xTo, yTo, zTo), this.inverseTransform.transformY(xTo, yTo, zTo),
            this.inverseTransform.transformZ(xTo, yTo, zTo), from);
    }

    @Override
    public DataTransactionResult copyFrom(int xTo, int yTo, int zTo, int xFrom, int yFrom, int zFrom) {
        return this.extent.copyFrom(this.inverseTransform.transformX(xTo, yTo, zTo), this.inverseTransform.transformY(xTo, yTo, zTo),
            this.inverseTransform.transformZ(xTo, yTo, zTo), this.inverseTransform.transformX(xFrom, yFrom, zFrom),
            this.inverseTransform.transformY(xFrom, yFrom, zFrom), this.inverseTransform.transformZ(xFrom, yFrom, zFrom));
    }

    @Override
    public DataTransactionResult copyFrom(int xTo, int yTo, int zTo, DataHolder from, MergeFunction function) {
        return this.extent.copyFrom(this.inverseTransform.transformX(xTo, yTo, zTo), this.inverseTransform.transformY(xTo, yTo, zTo),
            this.inverseTransform.transformZ(xTo, yTo, zTo), from, function);
    }

    @Override
    public DataTransactionResult copyFrom(int xTo, int yTo, int zTo, int xFrom, int yFrom, int zFrom, MergeFunction function) {
        return this.extent.copyFrom(this.inverseTransform.transformX(xTo, yTo, zTo), this.inverseTransform.transformY(xTo, yTo, zTo),
            this.inverseTransform.transformZ(xTo, yTo, zTo), this.inverseTransform.transformX(xFrom, yFrom, zFrom),
            this.inverseTransform.transformY(xFrom, yFrom, zFrom), this.inverseTransform.transformZ(xFrom, yFrom, zFrom), function);
    }

    @Override
    public Collection<TileEntity> getTileEntities() {
        final Collection<TileEntity> tileEntities = this.extent.getTileEntities();
        for (Iterator<TileEntity> iterator = tileEntities.iterator(); iterator.hasNext(); ) {
            final TileEntity tileEntity = iterator.next();
            final Location<World> block = tileEntity.getLocation();
            if (!VecHelper.inBounds(block.getX(), block.getY(), block.getZ(), this.blockMin, this.blockMax)) {
                iterator.remove();
            }
        }
        return tileEntities;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<TileEntity> getTileEntities(Predicate<TileEntity> filter) {
        // Order matters! Bounds filter before the argument filter so it doesn't see out of bounds entities
        return this.extent.getTileEntities(Functional.predicateAnd(
                new TileEntityInBounds(this.blockMin, this.blockMax), filter));
    }

    @Override
    public Optional<TileEntity> getTileEntity(int x, int y, int z) {
        return this.extent.getTileEntity(this.inverseTransform.transformX(x, y, z), this.inverseTransform.transformY(x, y, z),
            this.inverseTransform.transformZ(x, y, z));
    }

    @Override
    public Optional<Entity> createEntity(EntityType type, Vector3i position) {
        return this.extent.createEntity(type, this.inverseTransform.transform(position));
    }

    private Vector3d inverseTransform(Vector3d vector) {
        return this.inverseTransform.getMatrix().transform(vector.getX(), vector.getY(), vector.getZ(), 1).toVector3();
    }

    @Override
    public Collection<Entity> getEntities() {
        final Collection<Entity> entities = this.extent.getEntities();
        for (Iterator<Entity> iterator = entities.iterator(); iterator.hasNext(); ) {
            final Entity tileEntity = iterator.next();
            final Location<World> block = tileEntity.getLocation();
            if (!VecHelper.inBounds(block.getX(), block.getY(), block.getZ(), this.blockMin, this.blockMax)) {
                iterator.remove();
            }
        }
        return entities;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Entity> getEntities(Predicate<Entity> filter) {
        // Order matters! Bounds filter before the argument filter so it doesn't see out of bounds entities
        return this.extent.getEntities(Functional.predicateAnd(
                new EntityInBounds(this.blockMin, this.blockMax), filter));
    }

    @Override
    public Optional<Entity> createEntity(EntityType type, Vector3d position) {
        return this.extent.createEntity(type, inverseTransform(position));
    }

    @Override
    public Optional<Entity> createEntity(DataContainer entityContainer) {
        // TODO once entity containers are implemented
        return Optional.empty();
    }

    @Override
    public Optional<Entity> createEntity(DataContainer entityContainer, Vector3d position) {
        return this.extent.createEntity(entityContainer, inverseTransform(position));
    }

    @Override
    public Extent getExtentView(Vector3i newMin, Vector3i newMax) {
        return new ExtentViewDownsize(this.extent, this.inverseTransform.transform(newMin), this.inverseTransform.transform(newMax))
            .getExtentView(this.transform);
    }

    @Override
    public Extent getExtentView(DiscreteTransform3 transform) {
        return new ExtentViewTransform(this.extent, this.transform.withTransformation(transform));
    }

    @Override
    public Extent getRelativeExtentView() {
        return this.getExtentView(DiscreteTransform3.fromTranslation(getBlockMin().negate()));
    }

    private static class EntityInBounds implements Predicate<Entity> {

        private final Vector3i min;
        private final Vector3i max;

        private EntityInBounds(Vector3i min, Vector3i max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean test(Entity input) {
            final Location<World> block = input.getLocation();
            return VecHelper.inBounds(block.getX(), block.getY(), block.getZ(), this.min, this.max);
        }
    }

    private static class TileEntityInBounds implements Predicate<TileEntity> {

        private final Vector3i min;
        private final Vector3i max;

        private TileEntityInBounds(Vector3i min, Vector3i max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean test(TileEntity input) {
            final Location<World> block = input.getLocation();
            return VecHelper.inBounds(block.getX(), block.getY(), block.getZ(), this.min, this.max);
        }
    }
}
