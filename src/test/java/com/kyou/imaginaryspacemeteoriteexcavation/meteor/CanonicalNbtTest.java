package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.CanonicalNbt;

/** Exercises the actual development NBT classes, without a World or game instance. */
public final class CanonicalNbtTest {

    @Test
    public void compoundOrderIsStableAndListsArraysTypesRemainDistinct() {
        NBTTagCompound first = new NBTTagCompound();
        first.setInteger("b", 2);
        first.setString("a", "same");
        NBTTagCompound second = new NBTTagCompound();
        second.setString("a", "same");
        second.setInteger("b", 2);
        assertEquals(CanonicalNbt.encode(first), CanonicalNbt.encode(second));
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("left"));
        list.appendTag(new NBTTagString("right"));
        first.setTag("list", list);
        String before = CanonicalNbt.encode(first);
        assertEquals(2, list.tagCount());
        assertEquals(before, CanonicalNbt.encode(first));
        NBTTagList reverse = new NBTTagList();
        reverse.appendTag(new NBTTagString("right"));
        reverse.appendTag(new NBTTagString("left"));
        second.setTag("list", reverse);
        assertFalse(before.equals(CanonicalNbt.encode(second)));
        NBTTagCompound array = new NBTTagCompound();
        array.setByteArray("bytes", new byte[] { 1, 2 });
        String bytes = CanonicalNbt.encode(array);
        array.setByteArray("bytes", new byte[] { 2, 1 });
        assertFalse(bytes.equals(CanonicalNbt.encode(array)));
        array.setIntArray("bytes", new int[] { 1, 2 });
        assertFalse(bytes.equals(CanonicalNbt.encode(array)));
        assertFalse(
            CanonicalNbt.encode(null)
                .equals(CanonicalNbt.encode(new NBTTagCompound())));
    }
}
