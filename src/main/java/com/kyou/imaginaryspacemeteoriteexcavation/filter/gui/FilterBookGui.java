package com.kyou.imaginaryspacemeteoriteexcavation.filter.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;

/** Read-only item illustrations are buttons, not inventories or additional machine slots. */
public final class FilterBookGui {

    private FilterBookGui() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager manager, UISettings settings,
        FilterBookStore books) {
        FilterBookSync sync = new FilterBookSync(data, books, manager.isClient());
        manager.syncValue("isme.book", sync);
        ModularPanel panel = ModularPanel.defaultPanel("isme.filter_book", 280, 240);
        panel.child(
            IKey.lang("isme.book.title")
                .asWidget()
                .pos(8, 7)
                .size(250, 12));
        panel.child(ButtonWidget.panelCloseButton());
        panel.child(
            new ItemDisplayWidget().item(new ObjectValue.Dynamic<>(ItemStack.class, sync::focus, null))
                .pos(8, 22)
                .size(18));
        panel.child(
            IKey.dynamic(
                () -> sync.focus() == null ? ""
                    : sync.focus()
                        .getDisplayName())
                .asWidget()
                .pos(32, 22)
                .size(240, 20));
        panel.child(
            IKey.dynamic(sync::pageText)
                .asWidget()
                .pos(8, 43)
                .size(264, 10));
        panel.child(
            button("isme.book.previous", sync, FilterBookSync.PREVIOUS).pos(8, 56)
                .size(64, 16));
        panel.child(
            button("isme.book.next", sync, FilterBookSync.NEXT).pos(76, 56)
                .size(64, 16));
        panel.child(
            new ButtonWidget<>().overlay(IKey.dynamic(sync::modeText))
                .pos(144, 56)
                .size(60, 16)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    sync.command(FilterBookSync.MODE, 0);
                    return true;
                }));
        panel.child(
            button("isme.book.reset", sync, FilterBookSync.RESET).pos(208, 56)
                .size(64, 16));
        for (int cell = 0; cell < FilterBookSync.CELLS; cell++) {
            final int index = cell;
            ButtonWidget<?> button = new ButtonWidget<>().pos(8 + cell % 6 * 44, 76 + cell / 6 * 30)
                .size(42, 29)
                .child(
                    Flow.column()
                        .width(40)
                        .height(28)
                        .child(
                            new ItemDisplayWidget()
                                .item(new ObjectValue.Dynamic<>(ItemStack.class, () -> sync.item(index), null))
                                .size(18))
                        .child(
                            IKey.dynamic(() -> sync.cellText(index))
                                .asWidget()
                                .size(40, 10)))
                .setEnabledIf(widget -> sync.hasCell(index))
                .onMousePressed(mouse -> {
                    if (mouse != 0 || !sync.hasCell(index)) return false;
                    sync.command(FilterBookSync.TOGGLE, index);
                    return true;
                });
            button.tooltip()
                .addLine(
                    IKey.dynamic(
                        () -> sync.item(index) == null ? ""
                            : sync.item(index)
                                .getDisplayName()));
            panel.child(button);
        }
        panel.child(
            button("isme.book.items_previous", sync, FilterBookSync.ITEMS_PREVIOUS).pos(8, 199)
                .size(78, 16));
        panel.child(
            button("isme.book.items_next", sync, FilterBookSync.ITEMS_NEXT).pos(90, 199)
                .size(78, 16));
        panel.child(
            IKey.dynamic(() -> StatCollector.translateToLocalFormatted("isme.book.radius", sync.radius()))
                .asWidget()
                .pos(172, 199)
                .size(100, 16));
        panel.child(
            IKey.dynamic(sync::statusText)
                .asWidget()
                .pos(8, 219)
                .size(264, 18));
        return panel;
    }

    private static ButtonWidget<?> button(String key, FilterBookSync sync, int action) {
        return new ButtonWidget<>().overlay(IKey.lang(key))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                sync.command(action, 0);
                return true;
            });
    }
}
