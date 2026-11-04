package com.kyou.imaginaryspacemeteoriteexcavation.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.kyou.imaginaryspacemeteoriteexcavation.inventory.MachineItems;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.MeteoriteExcavation;

/** Only this container may transfer the counted slot; all exported stacks are normal. */
public final class ExcavationContainer extends ModularContainer {

    private final MeteoriteExcavation machine;

    public ExcavationContainer(MeteoriteExcavation machine) {
        this.machine = machine;
    }

    public static final class DedicatedSlot extends ModularSlot {

        public DedicatedSlot(MachineItems items, int index) {
            super(items.dedicated(), index);
        }
    }

    public static final class TowerSlot extends ModularSlot {

        public TowerSlot(MeteoriteExcavation machine) {
            super(new ItemStackHandler(1) {

                @Override
                public ItemStack getStackInSlot(int slot) {
                    return machine.getBaseMetaTileEntity()
                        .isClientSide() ? super.getStackInSlot(slot)
                            : machine.items()
                                .towerStack(64);
                }

                @Override
                public void setStackInSlot(int slot, ItemStack stack) {
                    if (machine.getBaseMetaTileEntity()
                        .isClientSide()) {
                        if (stack == null || (stack.stackSize > 0 && stack.stackSize <= stack.getMaxStackSize())) {
                            super.setStackInSlot(slot, stack == null ? null : stack.copy());
                        }
                    }
                }

                @Override
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    return stack;
                }

                @Override
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    return null;
                }
            }, 0);
            canDragInto(false);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (slotId < 0 || slotId >= inventorySlots.size()) {
            return slotId == -999 ? super.slotClick(slotId, button, mode, player) : null;
        }
        Slot slot = getSlot(slotId);
        if (mode == 1) {
            if (!player.worldObj.isRemote && (button == 0 || button == 1)) transferStackInSlot(player, slotId);
            // Do not invoke native quick-move's unbounded repeat loop for the proxy slot.
            return null;
        }
        if (slot instanceof TowerSlot) {
            if (!player.worldObj.isRemote && mode == 0 && (button == 0 || button == 1)) {
                clickTowers(player, button);
                detectAndSendChanges();
            }
            // Hotbar swap, clone, drop, drag and pickup-all never see a mutable proxy.
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    private void clickTowers(EntityPlayer player, int button) {
        MachineItems items = machine.items();
        ItemStack held = player.inventory.getItemStack();
        if (held == null) {
            ItemStack visible = items.towerStack(64);
            if (visible == null) return;
            int amount = button == 0 ? visible.stackSize : (visible.stackSize + 1) / 2;
            player.inventory.setItemStack(items.extractTowers(amount));
        } else {
            int accepted = items.insertTowers(held, button == 0 ? held.stackSize : 1);
            if (accepted > 0) {
                held.stackSize -= accepted;
                if (held.stackSize == 0) player.inventory.setItemStack(null);
            }
        }
        player.inventory.markDirty();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (player.worldObj.isRemote || index < 0 || index >= inventorySlots.size()) return null;
        Slot slot = getSlot(index);
        MachineItems items = machine.items();
        if (slot instanceof TowerSlot) {
            ItemStack stack = items.towerStack(64);
            if (stack != null) items.extractTowers(insertPlayer(player, stack));
        } else if (slot instanceof DedicatedSlot) {
            ItemStack stack = slot.getStack();
            if (stack != null && insertPlayer(player, stack.copy()) == 1) slot.putStack(null);
        } else if (slot instanceof ModularSlot && ModularSlot.isPlayerSlot((ModularSlot) slot)) {
            ItemStack stack = slot.getStack();
            if (stack == null || stack.stackSize <= 0 || stack.stackSize > stack.getMaxStackSize()) return null;
            int moved = items.insertTowers(stack, stack.stackSize);
            if (moved == 0) {
                for (int i = 0; i < 2; i++) {
                    ItemStack remainder = items.dedicated()
                        .insertItem(i, stack.copy(), false);
                    moved = stack.stackSize - (remainder == null ? 0 : remainder.stackSize);
                    if (moved > 0) break;
                }
            }
            if (moved > 0) {
                ItemStack remainder = stack.copy();
                remainder.stackSize -= moved;
                slot.putStack(remainder.stackSize == 0 ? null : remainder);
                slot.onSlotChanged();
            }
        }
        player.inventory.markDirty();
        detectAndSendChanges();
        return null;
    }

    /** At most one ordinary stack, no creative-mode deletion or dropped remainder. */
    private static int insertPlayer(EntityPlayer player, ItemStack offered) {
        int initial = offered.stackSize;
        for (int pass = 0; pass < 2 && offered.stackSize > 0; pass++) {
            for (int i = 0; i < player.inventory.mainInventory.length && offered.stackSize > 0; i++) {
                ItemStack existing = player.inventory.mainInventory[i];
                if (pass == 0 && MachineItems.sameItem(existing, offered)) {
                    int limit = Math.min(existing.getMaxStackSize(), player.inventory.getInventoryStackLimit());
                    int moved = Math.min(offered.stackSize, Math.max(0, limit - existing.stackSize));
                    existing.stackSize += moved;
                    offered.stackSize -= moved;
                } else if (pass == 1 && existing == null) {
                    int moved = Math.min(
                        offered.stackSize,
                        Math.min(offered.getMaxStackSize(), player.inventory.getInventoryStackLimit()));
                    ItemStack inserted = offered.copy();
                    inserted.stackSize = moved;
                    player.inventory.setInventorySlotContents(i, inserted);
                    offered.stackSize -= moved;
                }
            }
        }
        return initial - offered.stackSize;
    }
}
