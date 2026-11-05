package com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.registry.GameRegistry;

/** Immutable identity and a private count-one template; all exposed stacks are fresh copies. */
public final class ItemIdentity {

    private final ItemStack template;
    private final Item item;
    private final int metadata;
    private final String nbt;
    private final String signature;
    private final String registry;
    private NBTTagCompound savedRecord;
    private int measuredLimit = -1, measuredSize;

    public ItemIdentity(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.getItemDamage() < 0) {
            throw new IllegalArgumentException("Invalid item identity");
        }
        template = stack.copy();
        template.stackSize = 1;
        item = template.getItem();
        metadata = template.getItemDamage();
        nbt = CanonicalNbt.encode(template.getTagCompound());
        GameRegistry.UniqueIdentifier name = GameRegistry.findUniqueIdentifierFor(item);
        if (name == null) throw new IllegalArgumentException("Unregistered item");
        registry = name.toString();
        signature = registry + ":" + metadata + "/" + nbt;
    }

    /** Blood Magic's exact focus comparison uses Item + metadata, and ignores stack size and NBT. */
    public static String focusSignature(ItemStack stack) {
        if (stack == null || stack.getItem() == null) throw new IllegalArgumentException("Missing focus item");
        GameRegistry.UniqueIdentifier name = GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (name == null) throw new IllegalArgumentException("Unregistered item");
        return name.toString() + ":" + stack.getItemDamage();
    }

    public ItemStack template() {
        return template.copy();
    }

    public String signature() {
        return signature;
    }

    /** Stable count-independent record; callers always receive their own mutable NBT copy. */
    public NBTTagCompound save(long amount) {
        NBTTagCompound record = (NBTTagCompound) savedRecord().copy();
        record.setLong("amount", amount);
        return record;
    }

    /** Cached pure serialization size. The long amount occupies eight bytes at every value. */
    public synchronized int savedSize(int limit) {
        if (limit < 0 || limit == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid NBT size bound");
        if (measuredLimit >= 0 && (measuredSize <= measuredLimit || limit <= measuredLimit)) return measuredSize;
        measuredSize = BoundedNbtSize.measure(savedRecord(), limit);
        measuredLimit = limit;
        return measuredSize;
    }

    /** Bounded HUD fallback only; production persistence always uses save() with the complete item tag. */
    public NBTTagCompound saveWithoutItemTag(long amount) {
        NBTTagCompound record = new NBTTagCompound();
        record.setString("registry", registry);
        record.setInteger("damage", metadata);
        record.setLong("amount", amount);
        return record;
    }

    private synchronized NBTTagCompound savedRecord() {
        if (savedRecord == null) {
            savedRecord = new NBTTagCompound();
            savedRecord.setString("registry", registry);
            savedRecord.setInteger("damage", metadata);
            savedRecord.setLong("amount", 0);
            // Both owners stay private and immutable; save()/template() make detached copies for callers.
            if (template.hasTagCompound()) savedRecord.setTag("tag", template.getTagCompound());
        }
        return savedRecord;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ItemIdentity)) return false;
        ItemIdentity other = (ItemIdentity) object;
        return item == other.item && metadata == other.metadata && nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(item);
        result = 31 * result + metadata;
        return 31 * result + nbt.hashCode();
    }
}
