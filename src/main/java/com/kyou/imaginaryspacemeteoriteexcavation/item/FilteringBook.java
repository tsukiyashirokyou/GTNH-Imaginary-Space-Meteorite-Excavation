package com.kyou.imaginaryspacemeteoriteexcavation.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.kyou.imaginaryspacemeteoriteexcavation.ImaginarySpaceMeteoriteExcavation;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.gui.FilterBookGui;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class FilteringBook extends Item implements IGuiHolder<PlayerInventoryGuiData> {

    private final FilterBookStore books = new FilterBookStore(this);

    public FilteringBook() {
        setUnlocalizedName(ImaginarySpaceMeteoriteExcavation.MODID + ".filtering_book");
        setTextureName("minecraft:book_normal");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(64);
    }

    public FilterBookStore books() {
        return books;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            if (stack.stackSize != 1) player.addChatMessage(new ChatComponentTranslation("isme.book.split"));
            else PlayerInventoryGuiFactory.INSTANCE.openFromMainHand(player);
        }
        return stack;
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager manager, UISettings settings) {
        return FilterBookGui.build(data, manager, settings, books);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel panel) {
        return new ModularScreen(ImaginarySpaceMeteoriteExcavation.MODID, panel);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("isme.book.tooltip.function"));
        try {
            tooltip.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal(
                    books.read(stack)
                        .configured() ? "isme.book.configured" : "isme.book.default"));
        } catch (IllegalArgumentException invalid) {
            tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("isme.book.status.2"));
        }
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("isme.book.tooltip.void"));
        tooltip.add("");
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("isme.book.tooltip.copy"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("isme.book.tooltip.clear"));
        tooltip.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("isme.book.open"));
        if (stack.stackSize != 1)
            tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("isme.book.split"));
    }
}
