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
package org.lanternpowered.server.inventory.vanilla;

import org.lanternpowered.server.inventory.AbstractGridInventory;
import org.lanternpowered.server.inventory.AbstractOrderedInventory;
import org.lanternpowered.server.inventory.IInventory;
import org.lanternpowered.server.inventory.transformation.InventoryTransforms;
import org.spongepowered.api.item.inventory.InventoryTransformation;
import org.spongepowered.api.item.inventory.entity.MainPlayerInventory;
import org.spongepowered.api.item.inventory.query.QueryOperation;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;

public class LanternMainPlayerInventory extends AbstractGridInventory implements MainPlayerInventory {

    private static final class Holder {

        private static final QueryOperation<?> GRID_INVENTORY_OPERATION =
                QueryOperationTypes.INVENTORY_TYPE.of(AbstractGridInventory.class);
        private static final QueryOperation<?> HOTBAR_OPERATION =
                QueryOperationTypes.INVENTORY_TYPE.of(LanternHotbarInventory.class);
    }

    private LanternHotbarInventory hotbar;
    private AbstractGridInventory grid;

    private AbstractGridInventory priorityHotbar;
    private AbstractOrderedInventory reverse;

    @Override
    protected void init() {
        super.init();

        this.grid = query(Holder.GRID_INVENTORY_OPERATION).first();
        this.hotbar = query(Holder.HOTBAR_OPERATION).first();

        this.priorityHotbar = AbstractGridInventory.rowsViewBuilder()
                .grid(0, this.grid)
                .row(this.grid.getRows(), this.hotbar, 1050) // Higher priority for the hotbar
                .build();
        this.reverse = (AbstractOrderedInventory) InventoryTransforms.REVERSE.transform(this);
    }

    @Override
    public IInventory transform(InventoryTransformation transformation) {
        // Cache some transformations that will be used often
        if (transformation == InventoryTransforms.PRIORITY_HOTBAR) {
            return this.priorityHotbar;
        } else if (transformation == InventoryTransforms.REVERSE) {
            return this.reverse;
        }
        return super.transform(transformation);
    }

    @Override
    public LanternHotbarInventory getHotbar() {
        return this.hotbar;
    }

    @Override
    public AbstractGridInventory getGrid() {
        return this.grid;
    }
}
