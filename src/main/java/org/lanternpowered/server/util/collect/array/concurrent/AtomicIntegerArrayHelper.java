package org.lanternpowered.server.util.collect.array.concurrent;

import java.util.concurrent.atomic.AtomicIntegerArray;

public final class AtomicIntegerArrayHelper {

    public static int[] toArray(AtomicIntegerArray atomicIntegerArray) {
        final int[] array = new int[atomicIntegerArray.length()];
        for (int i = 0; i < array.length; i++) {
            array[i] = atomicIntegerArray.get(i);
        }
        return array;
    }

    private AtomicIntegerArrayHelper() {
    }
}
