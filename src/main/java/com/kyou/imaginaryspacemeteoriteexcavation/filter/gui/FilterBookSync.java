package com.kyou.imaginaryspacemeteoriteexcavation.filter.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterBookState;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterPage;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookSession;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterCatalogueView;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Fixed widgets, bounded current-page snapshots, and versioned operation intents only. */
public final class FilterBookSync extends SyncHandler<FilterBookSync> {

    public static final int CELLS = 24;
    public static final int PREVIOUS = 0, NEXT = 1, MODE = 2, RESET = 3, TOGGLE = 4, ITEMS_PREVIOUS = 5, ITEMS_NEXT = 6;
    private final PlayerInventoryGuiData data;
    private final FilterBookSession session;
    private final FilterCatalogueView catalogue;
    private View view = new View();
    private int ritualIndex;
    private int offset;
    private long token;
    private boolean dirty = true;
    private boolean lastValid;
    private int poll;
    private long commandTick = Long.MIN_VALUE;
    private int commands;

    public FilterBookSync(PlayerInventoryGuiData data, FilterBookStore books, boolean client) {
        this.data = data;
        catalogue = client ? null : FilterCatalogueView.current();
        session = client ? null : new FilterBookSession(books, data::getUsedItemStack, stack -> {
            data.setUsedItemStack(stack);
            data.getPlayer().inventory.markDirty();
        });
        allowC2S();
    }

    @SideOnly(Side.CLIENT)
    public void command(int action, int cell) {
        syncToServer(0, buffer -> {
            buffer.writeLong(view.token);
            buffer.writeLong(view.revision);
            buffer.writeInt(action);
            buffer.writeInt(cell);
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 0 || buffer.readableBytes() != 24 || session == null) return;
        long expectedToken = buffer.readLong();
        long expectedRevision = buffer.readLong();
        int action = buffer.readInt();
        int cell = buffer.readInt();
        long tick = data.getPlayer().worldObj.getTotalWorldTime();
        if (tick != commandTick) {
            commandTick = tick;
            commands = 0;
        }
        if (++commands > 16) return;
        dirty = true;
        if (!valid() || expectedToken != view.token
            || expectedRevision != session.state()
                .revision())
            return;
        List<FilterCatalogueView.Ritual> rituals = catalogue.rituals();
        if (rituals.isEmpty()) return;
        FilterCatalogueView.Ritual ritual = rituals.get(ritualIndex);
        if (action == PREVIOUS || action == NEXT) {
            int next = ritualIndex + (action == PREVIOUS ? -1 : 1);
            if (next < 0 || next >= rituals.size()) return;
            ritualIndex = next;
            offset = 0;
        } else if (action == ITEMS_PREVIOUS || action == ITEMS_NEXT) {
            int next = offset + (action == ITEMS_PREVIOUS ? -CELLS : CELLS);
            if (next < 0 || next >= ritual.items()
                .size()) return;
            offset = next;
        } else if (action == MODE || action == RESET) {
            session.edit(expectedRevision, ritual.definition(), action == MODE ? 1 : 2, null);
        } else if (action == TOGGLE && cell >= 0
            && cell < CELLS
            && offset + cell < ritual.items()
                .size()) {
                    session.edit(
                        expectedRevision,
                        ritual.definition(),
                        0,
                        ritual.items()
                            .get(offset + cell)
                            .signature());
                }
        // Consume the view version immediately, before the next client packet can reuse it.
        token = Math.addExact(token, 1L);
        view = snapshot();
    }

    private boolean valid() {
        return catalogue == FilterCatalogueView.current() && session.unchanged();
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!init && !dirty && ++poll < 10) return;
        poll = 0;
        boolean valid = valid();
        if (!init && !dirty && valid == lastValid) return;
        lastValid = valid;
        dirty = false;
        view = snapshot();
        syncToClient(0, this::writeView);
    }

    private View snapshot() {
        View result = new View();
        result.token = token;
        if (!valid()) {
            result.status = 2;
            return result;
        }
        FilterBookState state = session.state();
        result.revision = state.revision();
        result.ritualCount = catalogue.rituals()
            .size();
        if (result.ritualCount == 0) {
            result.status = 1;
            return result;
        }
        FilterCatalogueView.Ritual ritual = catalogue.rituals()
            .get(ritualIndex);
        result.ritualIndex = ritualIndex;
        result.itemCount = ritual.items()
            .size();
        result.offset = offset;
        result.focus = focus(
            ritual.definition()
                .focus());
        result.radius = ritual.entry()
            .recipe()
            .radius();
        FilterPage page;
        try {
            page = state.page(ritual.definition());
        } catch (IllegalArgumentException stale) {
            result.status = 3;
            return result;
        }
        result.status = 0;
        for (String id : state.pages()
            .keySet()) {
            if (catalogue.forId(id) == null) {
                result.status = 5;
                break;
            }
        }
        result.blacklist = page.mode() == FilterPage.Mode.BLACKLIST;
        for (int item = offset; item < Math.min(result.itemCount, offset + CELLS); item++) {
            ItemIdentity identity = ritual.items()
                .get(item);
            result.items.add(identity.template());
            result.kept.add(page.keeps(identity.signature()));
        }
        return result;
    }

    private static ItemStack focus(String signature) {
        String[] parts = signature.split(":", -1);
        if (parts.length != 3) return null;
        ItemStack item = GameRegistry.findItemStack(parts[0], parts[1], 1);
        if (item != null) item.setItemDamage(Integer.parseInt(parts[2]));
        return item;
    }

    private void writeView(PacketBuffer buffer) throws IOException {
        buffer.writeLong(view.token);
        buffer.writeLong(view.revision);
        buffer.writeInt(view.status);
        buffer.writeInt(view.ritualIndex);
        buffer.writeInt(view.ritualCount);
        buffer.writeInt(view.offset);
        buffer.writeInt(view.itemCount);
        buffer.writeInt(view.radius);
        buffer.writeBoolean(view.blacklist);
        buffer.writeItemStackToBuffer(view.focus);
        buffer.writeInt(view.items.size());
        for (int cell = 0; cell < view.items.size(); cell++) {
            buffer.writeItemStackToBuffer(view.items.get(cell));
            buffer.writeBoolean(view.kept.get(cell));
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) return;
        View next = new View();
        next.token = buffer.readLong();
        next.revision = buffer.readLong();
        next.status = buffer.readInt();
        next.ritualIndex = buffer.readInt();
        next.ritualCount = buffer.readInt();
        next.offset = buffer.readInt();
        next.itemCount = buffer.readInt();
        next.radius = buffer.readInt();
        next.blacklist = buffer.readBoolean();
        next.focus = buffer.readItemStackFromBuffer();
        int count = buffer.readInt();
        if (count < 0 || count > CELLS) throw new IOException("Invalid filtering book view size");
        for (int cell = 0; cell < count; cell++) {
            next.items.add(buffer.readItemStackFromBuffer());
            next.kept.add(buffer.readBoolean());
        }
        view = next;
    }

    public ItemStack item(int cell) {
        return cell < view.items.size() ? view.items.get(cell) : null;
    }

    public ItemStack focus() {
        return view.focus;
    }

    public boolean hasCell(int cell) {
        return cell < view.items.size();
    }

    public String cellText(int cell) {
        return !hasCell(cell) ? ""
            : StatCollector.translateToLocal(view.kept.get(cell) ? "isme.book.keep" : "isme.book.void");
    }

    public String modeText() {
        return StatCollector.translateToLocal(view.blacklist ? "isme.book.blacklist" : "isme.book.whitelist");
    }

    public String pageText() {
        return view.ritualCount == 0 ? ""
            : StatCollector.translateToLocalFormatted(
                "isme.book.page",
                view.ritualIndex + 1,
                view.ritualCount,
                view.offset / CELLS + 1,
                Math.max(1, (view.itemCount + CELLS - 1) / CELLS));
    }

    public String statusText() {
        return StatCollector.translateToLocal("isme.book.status." + view.status);
    }

    public int radius() {
        return view.radius;
    }

    private static final class View {

        private long token, revision;
        private int status = 4, ritualIndex, ritualCount, offset, itemCount, radius;
        private boolean blacklist;
        private ItemStack focus;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<Boolean> kept = new ArrayList<>();
    }
}
