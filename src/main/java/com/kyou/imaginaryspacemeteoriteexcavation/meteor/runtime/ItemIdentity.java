package com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

/** Immutable identity and a private count-one template; all exposed stacks are fresh copies. */
public final class ItemIdentity {

    private final ItemStack template;
    private final Item item;
    private final int metadata;
    private final String nbt;
    private final String signature;

    public ItemIdentity(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.getItemDamage() < 0) {
            throw new IllegalArgumentException("Invalid item identity");
        }
        template = stack.copy();
        template.stackSize = 1;
        item = template.getItem();
        metadata = template.getItemDamage();
        nbt = CanonicalNbt.encode(template.getTagCompound());
        signature = focusSignature(template) + "/" + nbt;
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
