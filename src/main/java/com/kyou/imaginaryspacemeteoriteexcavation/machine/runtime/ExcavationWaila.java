package com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.MeteorGeometry;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.BoundedNbtSize;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;

import gregtech.api.util.GTWaila;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import mcp.mobius.waila.overlay.tooltiprenderers.TTRenderStack;

/** Read-only bounded HUD protocol. No recipe math, resource access or output transactions. */
public final class ExcavationWaila {

    public static final String KEY = "ISMEHud";
    public static final int PREVIEW_ITEMS = 3;
    public static final int ITEM_BYTES = 2048;

    private ExcavationWaila() {}

    /** Called only when the owned batch or pending revision changes, never to recompute a yield. */
    public static NBTTagCompound preview(Map<ItemIdentity, Long> items) {
        NBTTagCompound preview = new NBTTagCompound();
        preview.setInteger("types", items.size());
        NBTTagList list = new NBTTagList();
        for (Map.Entry<ItemIdentity, Long> entry : items.entrySet()) {
            if (list.tagCount() == PREVIEW_ITEMS) break;
            ItemIdentity id = entry.getKey();
            boolean simplified = id.savedSize(ITEM_BYTES) > ITEM_BYTES;
            NBTTagCompound item = simplified ? id.saveWithoutItemTag(entry.getValue()) : id.save(entry.getValue());
            if (simplified && BoundedNbtSize.measure(item, ITEM_BYTES) > ITEM_BYTES) {
                String name = item.getString("registry");
                item = new NBTTagCompound();
                item.setLong("amount", entry.getValue());
                item.setString("fallback", name.substring(0, Math.min(128, name.length())));
            }
            if (simplified) item.setBoolean("simplified", true);
            list.appendTag(item);
        }
        preview.setTag("items", list);
        return preview;
    }

    /** Client localization/rendering from the synchronized packet only. */
    public static void append(List<String> tip, NBTTagCompound root) {
        if (!root.hasKey(KEY, 10)) return;
        NBTTagCompound tag = root.getCompoundTag(KEY);
        String state = tag.getString("state");
        tip.add(translateToLocalFormatted("isme.waila.state", translateToLocal("isme.state." + state)));
        boolean running = tag.getBoolean("hasBatch");
        if (running) {
            if (!root.getBoolean("isActive") || root.getBoolean("incompleteStructure")) {
                tip.add(
                    GTWaila
                        .getMachineProgressString(true, true, tag.getInteger("duration"), tag.getInteger("progress")));
                tip.add(translateToLocalFormatted("isme.waila.paused_energy", formatNumber(tag.getLong("demand"))));
            }
            int radius = tag.getInteger("radius");
            tip.add(
                translateToLocalFormatted(
                    "isme.waila.geometry",
                    radius >= 0 ? formatNumber(radius) : translateToLocal("isme.waila.unknown"),
                    formatNumber(tag.getInteger("towers")),
                    radius >= 0 ? formatNumber(MeteorGeometry.requiredTowers(radius))
                        : translateToLocal("isme.waila.unknown")));
            tip.add(
                translateToLocalFormatted(
                    "isme.waila.batch",
                    formatNumber(tag.getInteger("batch")),
                    formatNumber(tag.getLong("kept"))));
            tip.add(translateToLocal("GT5U.waila.producing"));
            appendItems(tip, tag.getCompoundTag("preview"));
        } else {
            tip.add(translateToLocalFormatted("isme.waila.towers", formatNumber(tag.getInteger("towers"))));
        }
        long pending = tag.getLong("pending");
        if (pending != 0) {
            tip.add(
                translateToLocalFormatted(
                    "isme.waila.pending",
                    pending < 0 ? translateToLocal("isme.waila.unknown") : formatNumber(pending)));
            if (pending > 0) appendItems(tip, tag.getCompoundTag("preview"));
        }
        if (tag.hasKey("shutdown", 10)) {
            NBTTagCompound shutdown = tag.getCompoundTag("shutdown");
            String id = shutdown.getString("id");
            if (ShutDownReasonRegistry.isRegistered(id)) {
                ShutDownReason reason = ShutDownReasonRegistry.getSampleFromRegistry(id)
                    .newInstance();
                reason.readFromNBT(shutdown.getCompoundTag("data"));
                tip.add(EnumChatFormatting.RED + clipped(reason.getDisplayString()));
            }
        }
        String diagnostic = tag.getString("diagnostic");
        if (!diagnostic.isEmpty()) tip.add(translateToLocalFormatted("isme.waila.detail", clipped(diagnostic)));
    }

    private static void appendItems(List<String> tip, NBTTagCompound preview) {
        NBTTagList list = preview.getTagList("items", 10);
        for (int i = 0; i < Math.min(PREVIEW_ITEMS, list.tagCount()); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            Item type = (Item) Item.itemRegistry.getObject(item.getString("registry"));
            String name = item.hasKey("fallback") ? item.getString("fallback") : item.getString("registry");
            String icon = "";
            if (type != null) {
                ItemStack sample = new ItemStack(type, 1, item.getInteger("damage"));
                if (item.hasKey("tag", 10)) sample.setTagCompound(
                    (NBTTagCompound) item.getCompoundTag("tag")
                        .copy());
                try {
                    name = sample.getDisplayName();
                    icon = TTRenderStack.create(sample, true);
                } catch (RuntimeException requiresFullItemData) {
                    // A bounded preview may omit a large tag required by a third-party name/icon implementation.
                    name = item.getString("registry");
                    icon = "";
                }
            }
            if (item.getBoolean("simplified")) name += translateToLocal("isme.waila.simplified");
            tip.add(
                "  " + icon
                    + GTWaila.getStackListLine(
                        EnumChatFormatting.AQUA + clipped(name) + EnumChatFormatting.RESET,
                        item.getLong("amount")));
        }
        if (preview.getInteger("types") > PREVIEW_ITEMS) tip.add(
            translateToLocalFormatted(
                "GT5U.waila.producing.andmore",
                formatNumber(preview.getInteger("types") - PREVIEW_ITEMS)));
    }

    private static String clipped(String value) {
        return value.substring(0, Math.min(128, value.length()))
            .replace('\n', ' ')
            .replace('\r', ' ');
    }
}
