package com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Exact uncompressed size up to a bound; no byte array allocation or unbounded serialization. */
public final class BoundedNbtSize {

    private BoundedNbtSize() {}

    /** Returns the exact size, or limit + 1 for an oversized/unserializable record. */
    public static int measure(NBTTagCompound tag, int limit) {
        if (limit < 0 || limit == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid NBT size bound");
        int[] count = { 0 };
        try {
            CompressedStreamTools.write(tag, new DataOutputStream(new OutputStream() {

                @Override
                public void write(int value) throws IOException {
                    if (++count[0] > limit) throw new IOException("Output NBT exceeds bound");
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (length > limit - count[0]) throw new IOException("Output NBT exceeds bound");
                    count[0] += length;
                }
            }));
            return count[0];
        } catch (IOException oversizedOrInvalid) {
            return limit + 1;
        }
    }
}
