package com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting;

import java.util.function.Predicate;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.ImaginarySpaceMeteoriteExcavation;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/** Targeted vanilla copy-result remainder protection. Never applies to MUI, AE or unknown container subclasses. */
public final class CopyRemainderGuard {

    private final FilterBookRecipe copy;

    public CopyRemainderGuard(FilterBookStore books) {
        copy = new FilterBookRecipe(books, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void crafted(PlayerEvent.ItemCraftedEvent event) {
        EntityPlayer player = event.player;
        if (player.worldObj.isRemote || !(event.craftMatrix instanceof InventoryCrafting)) return;
        Container container = player.openContainer;
        InventoryCrafting grid;
        IInventory result;
        if (container.getClass() == ContainerWorkbench.class) {
            grid = ((ContainerWorkbench) container).craftMatrix;
            result = ((ContainerWorkbench) container).craftResult;
        } else if (container.getClass() == ContainerPlayer.class) {
            grid = ((ContainerPlayer) container).craftMatrix;
            result = ((ContainerPlayer) container).craftResult;
        } else return;
        if (event.craftMatrix != grid) return;
        ItemStack preview = copy.getCraftingResult(grid);
        ItemStack residual = event.crafting;
        if (preview == null || residual == null
            || residual.stackSize < 0
            || residual.stackSize > 1
            || !sameKind(preview, residual)) return;
        CopyContinuationGuard.remember(grid);
        if (!isPartialResult(preview, residual, result.getStackInSlot(0))) return;
        if (!protectPartialResult(preview, residual, result, remainder -> deliver(player, remainder))) {
            // User declined persistent crafting remainders. An external drop cancellation remains an explicit limit.
            ImaginarySpaceMeteoriteExcavation.LOG.warn(
                "Filtering book copy remainder rejected by external drop event for {}",
                player.getCommandSenderName());
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("isme.book.drop_rejected"));
            return;
        }
    }

    public static boolean protectPartialResult(ItemStack expected, ItemStack argument, IInventory result,
        Predicate<ItemStack> receiver) {
        if (!isPartialResult(expected, argument, result.getStackInSlot(0))) return false;
        if (!receiver.test(argument.copy())) return false;
        argument.stackSize = 0;
        result.setInventorySlotContents(0, null);
        return true;
    }

    public static boolean isPartialResult(ItemStack expected, ItemStack argument, ItemStack resultCell) {
        return expected != null && expected.stackSize == 2
            && argument != null
            && argument == resultCell
            && argument.stackSize == 1
            && sameKind(expected, argument);
    }

    private static boolean sameKind(ItemStack first, ItemStack second) {
        return first != null && second != null
            && first.getItem() == second.getItem()
            && first.getItemDamage() == second.getItemDamage()
            && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static boolean deliver(EntityPlayer player, ItemStack remainder) {
        player.inventory.addItemStackToInventory(remainder);
        if (remainder.stackSize <= 0) return true;
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null) {
            player.inventory.setItemStack(remainder.copy());
            if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).updateHeldItem();
            return true;
        }
        if (sameKind(cursor, remainder) && cursor.stackSize > 0
            && (long) cursor.stackSize + remainder.stackSize <= cursor.getMaxStackSize()) {
            cursor.stackSize += remainder.stackSize;
            if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).updateHeldItem();
            return true;
        }
        EntityItem dropped = player.dropPlayerItemWithRandomChoice(remainder.copy(), false);
        return dropped != null && !dropped.isDead && player.worldObj.getEntityByID(dropped.getEntityId()) == dropped;
    }
}
