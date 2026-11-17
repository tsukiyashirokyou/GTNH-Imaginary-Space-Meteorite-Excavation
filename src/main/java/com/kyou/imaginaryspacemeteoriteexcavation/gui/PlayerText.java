package com.kyou.imaginaryspacemeteoriteexcavation.gui;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationWaila;

import cpw.mods.fml.common.registry.GameRegistry;

/** Client-facing names and short status text; internal identities and diagnostics remain unchanged. */
public final class PlayerText {

    private PlayerText() {}

    public static String focusName(String signature) {
        String[] parts = signature.split(":", -1);
        if (parts.length != 3) return translateToLocal("isme.gui.no_recipe");
        try {
            ItemStack item = GameRegistry.findItemStack(parts[0], parts[1], 1);
            if (item != null) {
                item.setItemDamage(Integer.parseInt(parts[2]));
                return item.getDisplayName();
            }
        } catch (RuntimeException unavailable) {
            // A removed or unrepresentable focus is not a player-facing registry name.
        }
        return translateToLocal("isme.gui.no_recipe");
    }

    public static List<String> outputLines(NBTTagCompound preview) {
        List<String> lines = new ArrayList<>();
        if (preview == null) return List.of(translateToLocal("isme.gui.no_output"));
        NBTTagList items = preview.getTagList("items", 10);
        for (int i = 0; i < Math.min(ExcavationWaila.PREVIEW_ITEMS, items.tagCount()); i++) {
            NBTTagCompound item = items.getCompoundTagAt(i);
            Item type = (Item) Item.itemRegistry.getObject(item.getString("registry"));
            String name = translateToLocal("isme.gui.unknown_item");
            if (type != null) {
                ItemStack sample = new ItemStack(type, 1, item.getInteger("damage"));
                if (item.hasKey("tag", 10)) sample.setTagCompound(
                    (NBTTagCompound) item.getCompoundTag("tag")
                        .copy());
                try {
                    name = sample.getDisplayName();
                } catch (RuntimeException requiresFullItemData) {
                    // Large item tags may be omitted from this bounded display snapshot.
                }
            }
            lines.add(name + " × " + formatNumber(item.getLong("amount")));
        }
        int more = preview.getInteger("types") - Math.min(ExcavationWaila.PREVIEW_ITEMS, items.tagCount());
        if (more > 0) lines.add(translateToLocalFormatted("isme.gui.more_outputs", formatNumber(more)));
        if (lines.isEmpty()) lines.add(translateToLocal("isme.gui.no_output"));
        return lines;
    }

    public static String detail(String state, String diagnostic) {
        if ("fault".equals(state)) return translateToLocal("isme.detail.record");
        if ("unsupported_input".equals(state)) return translateToLocal(
            diagnostic.startsWith("Invalid filtering book") ? "isme.detail.book" : "isme.detail.input");
        if (("output_full".equals(state) || "output_wait".equals(state)) && !diagnostic.isEmpty())
            return translateToLocal("isme.detail.output");
        return "";
    }

    public static int stateColor(String state) {
        return switch (state) {
            case "running" -> 0xFF8ED49A;
            case "fault", "failed", "structure", "unsupported_input" -> 0xFFFF8A80;
            case "idle", "none" -> 0xFFC0CBD0;
            default -> 0xFFFFD080;
        };
    }

    public static double progress(int elapsed, int duration) {
        return duration <= 0 ? 0 : Math.max(0, Math.min(1, (double) elapsed / duration));
    }

    public static long percent(int elapsed, int duration) {
        return duration <= 0 ? 0 : 100L * Math.max(0, Math.min(elapsed, duration)) / duration;
    }
}
