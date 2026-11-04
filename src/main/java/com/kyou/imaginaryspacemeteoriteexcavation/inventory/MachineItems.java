package com.kyou.imaginaryspacemeteoriteexcavation.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.kyou.imaginaryspacemeteoriteexcavation.ImaginarySpaceMeteoriteExcavation;
import com.kyou.imaginaryspacemeteoriteexcavation.item.FilteringBook;

import WayofTime.alchemicalWizardry.api.items.interfaces.IBloodOrb;
import appeng.api.AEApi;

/** GUI-only inventory. Native GT mInventory never contains proxy stacks. */
public final class MachineItems {

    public static final int ORB = 0;
    public static final int BOOK = 1;
    private final Runnable changed;
    private final BoundedItemCount<ItemStack> towers = new BoundedItemCount<>(
        MachineItems::sample,
        MachineItems::sameItem);
    private final ItemStackHandler dedicated;

    public MachineItems(Runnable changed) {
        this.changed = changed;
        dedicated = new ItemStackHandler(2) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == ORB ? isBoundOrb(stack)
                    : slot == BOOK && stack != null && stack.getItem() instanceof FilteringBook;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack != null && (!isItemValid(slot, stack) || stack.stackSize <= 0)) return stack;
                return super.insertItem(slot, stack, simulate);
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                if (stack != null && (stack.stackSize != 1 || !isItemValid(slot, stack))) {
                    throw new IllegalArgumentException("Invalid dedicated machine slot contents");
                }
                super.setStackInSlot(slot, stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                changed.run();
            }
        };
    }

    public ItemStackHandler dedicated() {
        return dedicated;
    }

    public int towerCount() {
        return towers.count();
    }

    public ItemStack towerStack(int limit) {
        ItemStack stack = towers.identity();
        if (stack != null) stack.stackSize = Math.min(Math.min(limit, stack.getMaxStackSize()), towers.count());
        return stack;
    }

    public int insertTowers(ItemStack offered, int requested) {
        if (!isSpatialPylon(offered) || offered.stackSize <= 0 || offered.stackSize > offered.getMaxStackSize())
            return 0;
        int accepted = towers.insert(sample(offered), Math.min(offered.stackSize, requested));
        if (accepted > 0) changed.run();
        return accepted;
    }

    public ItemStack extractTowers(int requested) {
        ItemStack result = towerStack(Math.max(0, requested));
        if (result == null || result.stackSize == 0) return null;
        towers.extract(result.stackSize);
        changed.run();
        return result;
    }

    public void write(NBTTagCompound parent) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Schema", 1);
        for (int i = 0; i < 2; i++) {
            ItemStack stack = dedicated.getStackInSlot(i);
            if (stack != null) data.setTag(i == ORB ? "Orb" : "Book", stack.writeToNBT(new NBTTagCompound()));
        }
        if (towers.count() > 0) {
            data.setTag(
                "Tower",
                towers.identity()
                    .writeToNBT(new NBTTagCompound()));
            data.setInteger("TowerCount", towers.count());
        }
        parent.setTag("ISMEInventory", data);
    }

    public void read(NBTTagCompound parent) {
        NBTTagCompound data = parent.getCompoundTag("ISMEInventory");
        for (int i = 0; i < 2; i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(data.getCompoundTag(i == ORB ? "Orb" : "Book"));
            if (stack != null && (stack.stackSize != 1 || !dedicated.isItemValid(i, stack))) {
                ImaginarySpaceMeteoriteExcavation.LOG.warn("Rejected invalid saved dedicated-slot contents");
                stack = null;
            }
            dedicated.setStackInSlot(i, stack);
        }
        ItemStack identity = ItemStack.loadItemStackFromNBT(data.getCompoundTag("Tower"));
        int count = data.hasKey("TowerCount", 3) ? data.getInteger("TowerCount") : 0;
        if (count < 0 || (count > 0 && (!isSpatialPylon(identity) || identity.stackSize != 1))) {
            ImaginarySpaceMeteoriteExcavation.LOG.warn("Rejected invalid saved spatial-pylon count or identity");
            count = 0;
        }
        towers.restore(count == 0 ? null : identity, count);
    }

    public static boolean isSpatialPylon(ItemStack stack) {
        return stack != null && AEApi.instance()
            .definitions()
            .blocks()
            .spatialPylon()
            .isSameAs(stack);
    }

    public static boolean isBoundOrb(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IBloodOrb && !owner(stack).isEmpty();
    }

    public static String owner(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound()
            .getString("ownerName") : "";
    }

    public static boolean sameItem(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static ItemStack sample(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
