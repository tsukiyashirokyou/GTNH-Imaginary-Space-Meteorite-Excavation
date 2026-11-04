package com.kyou.imaginaryspacemeteoriteexcavation.output.runtime;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.output.OutputLimits;
import com.kyou.imaginaryspacemeteoriteexcavation.output.PendingOutputs;

import cpw.mods.fml.common.registry.GameRegistry;

/** Versioned, bounded persistence; invalid records are quarantined and saved unchanged. */
public final class PendingItemOutput {

    private PendingOutputs<ItemIdentity> pending = new PendingOutputs<>();
    private NBTTagCompound quarantine;
    private String invalidReason = "";

    public boolean canStart(Map<ItemIdentity, Long> items) {
        return quarantine == null && pending.canStart(items) && encodable(items);
    }

    public void complete(Map<ItemIdentity, Long> items) {
        if (!canStart(items)) throw new IllegalStateException("Cannot capture completed output");
        pending.complete(items);
    }

    public long flush(long tick, PendingOutputs.Sink<ItemIdentity> sink) {
        return quarantine == null ? pending.flush(tick, sink) : 0;
    }

    public boolean blocksStart() {
        return quarantine != null || pending.blocksStart();
    }

    public String fault() {
        return quarantine != null ? "Invalid saved pending output: " + invalidReason : pending.fault();
    }

    public Map<ItemIdentity, Long> snapshot() {
        return pending.snapshot();
    }

    public long revision() {
        return pending.revision();
    }

    public NBTTagCompound save() {
        if (quarantine != null) return (NBTTagCompound) quarantine.copy();
        return encode(pending.snapshot(), pending.cursor(), pending.fault());
    }

    /** Caller distinguishes an absent machine tag (new machine) from an invalid present tag. */
    public static PendingItemOutput load(NBTTagCompound tag) {
        PendingItemOutput output = new PendingItemOutput();
        try {
            if (tag == null || !tag.hasKey("schema", 3)
                || tag.getInteger("schema") != 1
                || !tag.hasKey("cursor", 3)
                || !tag.hasKey("fault", 8)
                || !tag.hasKey("items", 9)
                || !within(tag, OutputLimits.SERIALIZED_BYTES)) throw new IllegalArgumentException("Invalid header");
            NBTTagList list = (NBTTagList) tag.getTag("items");
            if (list.tagCount() > OutputLimits.IDENTITIES || (list.tagCount() > 0 && list.func_150303_d() != 10)) {
                throw new IllegalArgumentException("Invalid item list");
            }
            Map<ItemIdentity, Long> items = new LinkedHashMap<>();
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                if (!entry.hasKey("registry", 8) || !entry.hasKey("damage", 3)
                    || !entry.hasKey("amount", 4)
                    || (entry.hasKey("tag") && !entry.hasKey("tag", 10))
                    || !within(entry, OutputLimits.IDENTITY_BYTES)) throw new IllegalArgumentException("Invalid item");
                String name = entry.getString("registry");
                Item item = (Item) Item.itemRegistry.getObject(name);
                if (item == null || !name.equals(
                    GameRegistry.findUniqueIdentifierFor(item)
                        .toString())) {
                    throw new IllegalArgumentException("Missing item registration");
                }
                ItemStack sample = new ItemStack(item, 1, entry.getInteger("damage"));
                if (entry.hasKey("tag", 10)) sample.setTagCompound(
                    (NBTTagCompound) entry.getCompoundTag("tag")
                        .copy());
                ItemIdentity id = new ItemIdentity(sample);
                if (items.put(id, entry.getLong("amount")) != null)
                    throw new IllegalArgumentException("Duplicate identity");
            }
            output.pending = PendingOutputs.restore(items, tag.getInteger("cursor"), tag.getString("fault"));
        } catch (RuntimeException invalid) {
            output.invalidReason = invalid.toString();
            output.quarantine = tag == null ? new NBTTagCompound() : (NBTTagCompound) tag.copy();
        }
        return output;
    }

    private static boolean encodable(Map<ItemIdentity, Long> items) {
        try {
            for (Map.Entry<ItemIdentity, Long> entry : items.entrySet()) {
                if (!within(encodeItem(entry.getKey(), entry.getValue()), OutputLimits.IDENTITY_BYTES)) return false;
            }
            return within(encode(items, 0, ""), OutputLimits.SERIALIZED_BYTES - 512);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static NBTTagCompound encode(Map<ItemIdentity, Long> items, int cursor, String fault) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", 1);
        tag.setInteger("cursor", cursor);
        tag.setString("fault", fault);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<ItemIdentity, Long> entry : items.entrySet())
            list.appendTag(encodeItem(entry.getKey(), entry.getValue()));
        tag.setTag("items", list);
        return tag;
    }

    private static NBTTagCompound encodeItem(ItemIdentity id, long amount) {
        ItemStack sample = id.template();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(
            "registry",
            GameRegistry.findUniqueIdentifierFor(sample.getItem())
                .toString());
        entry.setInteger("damage", sample.getItemDamage());
        entry.setLong("amount", amount);
        if (sample.hasTagCompound()) entry.setTag(
            "tag",
            sample.getTagCompound()
                .copy());
        return entry;
    }

    private static boolean within(NBTTagCompound tag, int limit) {
        try {
            CompressedStreamTools.write(tag, new DataOutputStream(new OutputStream() {

                private int count;

                @Override
                public void write(int value) throws IOException {
                    if (++count > limit) throw new IOException("Output NBT exceeds bound");
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (length > limit - count) throw new IOException("Output NBT exceeds bound");
                    count += length;
                }
            }));
            return true;
        } catch (IOException oversized) {
            return false;
        }
    }
}
