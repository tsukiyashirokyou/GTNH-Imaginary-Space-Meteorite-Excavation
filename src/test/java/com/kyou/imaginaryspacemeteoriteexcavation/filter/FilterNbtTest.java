package com.kyou.imaginaryspacemeteoriteexcavation.filter;

import static org.junit.Assert.*;

import java.util.Arrays;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.Test;

public final class FilterNbtTest {

    private final FilterDefinition definition = new FilterDefinition(
        "pack:meteor",
        "focus:1",
        "hash",
        Arrays.asList("ore", "filler"));

    @Test
    public void roundTripPreservesEmptySelectionAndIndependentNbt() {
        FilterBookState empty = FilterBookState.defaults()
            .toggle(definition, "ore")
            .toggle(definition, "filler");
        NBTTagCompound cosmetic = new NBTTagCompound();
        cosmetic.setString("unrelated", "preserved");
        NBTTagCompound encoded = FilterBookCodec.write(empty, cosmetic);
        FilterBookState decoded = FilterBookCodec.read(encoded);
        assertEquals(2, decoded.revision());
        assertTrue(
            decoded.page(definition)
                .selected()
                .isEmpty());
        assertFalse(
            decoded.page(definition)
                .keeps("ore"));
        assertEquals("preserved", encoded.getString("unrelated"));
        assertFalse(cosmetic.hasKey(FilterBookCodec.KEY));
        NBTTagCompound independent = FilterBookCodec.write(decoded, encoded);
        independent.getCompoundTag(FilterBookCodec.KEY)
            .setLong("revision", 99);
        assertEquals(
            2,
            FilterBookCodec.read(encoded)
                .revision());
        assertEquals(2, decoded.revision());
    }

    @Test
    public void readingDefaultsDoesNotWriteAndResetKeepsConfiguredHistory() {
        NBTTagCompound tag = new NBTTagCompound();
        assertFalse(
            FilterBookCodec.read(tag)
                .configured());
        assertFalse(tag.hasKey(FilterBookCodec.KEY));
        FilterBookState reset = FilterBookState.defaults()
            .reset(definition);
        FilterBookState decoded = FilterBookCodec.read(FilterBookCodec.write(reset, null));
        assertTrue(decoded.configured());
        assertTrue(
            decoded.pages()
                .isEmpty());
        assertEquals(
            definition.items(),
            decoded.page(definition)
                .selected());
    }

    @Test
    public void malformedSchemaTypesDuplicatesAndModeFailClosed() {
        NBTTagCompound wrongType = new NBTTagCompound();
        wrongType.setString(FilterBookCodec.KEY, "invalid");
        reject(wrongType);
        NBTTagCompound valid = FilterBookCodec.write(
            FilterBookState.defaults()
                .toggle(definition, "ore"),
            null);
        NBTTagCompound schema = (NBTTagCompound) valid.copy();
        schema.getCompoundTag(FilterBookCodec.KEY)
            .setInteger("schema", 999);
        reject(schema);
        NBTTagCompound negative = (NBTTagCompound) valid.copy();
        negative.getCompoundTag(FilterBookCodec.KEY)
            .setLong("revision", -1);
        reject(negative);
        NBTTagCompound mode = (NBTTagCompound) valid.copy();
        mode.getCompoundTag(FilterBookCodec.KEY)
            .getTagList("pages", 10)
            .getCompoundTagAt(0)
            .setString("mode", "anything");
        reject(mode);
        NBTTagCompound duplicate = (NBTTagCompound) valid.copy();
        NBTTagList pages = duplicate.getCompoundTag(FilterBookCodec.KEY)
            .getTagList("pages", 10);
        pages.appendTag(
            pages.getCompoundTagAt(0)
                .copy());
        reject(duplicate);
        NBTTagCompound entries = (NBTTagCompound) valid.copy();
        NBTTagList selected = entries.getCompoundTag(FilterBookCodec.KEY)
            .getTagList("pages", 10)
            .getCompoundTagAt(0)
            .getTagList("entries", 8);
        selected.appendTag(new NBTTagString("filler"));
        reject(entries);
        NBTTagCompound type = (NBTTagCompound) valid.copy();
        type.getCompoundTag(FilterBookCodec.KEY)
            .setString("pages", "not-a-list");
        reject(type);
    }

    @Test
    public void boundedPayloadAndUnrecognisedSelectionsAreNotSilentlyApplied() {
        NBTTagCompound valid = FilterBookCodec.write(
            FilterBookState.defaults()
                .toggle(definition, "ore"),
            null);
        NBTTagCompound page = valid.getCompoundTag(FilterBookCodec.KEY)
            .getTagList("pages", 10)
            .getCompoundTagAt(0);
        NBTTagList huge = new NBTTagList();
        for (int i = 0; i <= FilterBookCodec.MAX_ITEMS; i++) huge.appendTag(new NBTTagString("item:" + i));
        page.setTag("entries", huge);
        reject(valid);
        NBTTagList foreign = new NBTTagList();
        foreign.appendTag(new NBTTagString("unknown-item"));
        page.setTag("entries", foreign);
        FilterBookState state = FilterBookCodec.read(valid);
        try {
            state.page(definition);
            fail("Unknown selection must reject this page");
        } catch (IllegalArgumentException expected) {}
    }

    private static void reject(NBTTagCompound tag) {
        try {
            FilterBookCodec.read(tag);
            fail("Expected malformed NBT rejection");
        } catch (IllegalArgumentException expected) {}
    }
}
