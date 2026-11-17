package com.kyou.imaginaryspacemeteoriteexcavation.filter.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;

/** Read-only item illustrations are buttons, not inventories or additional machine slots. */
public final class FilterBookGui {

    private FilterBookGui() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager manager, UISettings settings,
        FilterBookStore books) {
        FilterBookSync sync = new FilterBookSync(data, books, manager.isClient());
        manager.syncValue("isme.book", sync);
        boolean[] searchOpen = { false };
        ModularPanel panel = ModularPanel.defaultPanel("isme.filter_book", 280, 240);
        panel.child(
            IKey.lang("isme.book.title")
                .asWidget()
                .pos(8, 7)
                .size(250, 12));
        panel.child(ButtonWidget.panelCloseButton());
        ParentWidget<?> editor = new ParentWidget<>().pos(0, 22)
            .size(280, 218)
            .setEnabledIf(widget -> !searchOpen[0]);
        panel.child(editor);
        editor.child(
            new ItemDisplayWidget().item(new ObjectValue.Dynamic<>(ItemStack.class, sync::focus, null))
                .pos(8, 0)
                .size(18));
        editor.child(
            IKey.dynamic(
                () -> sync.focus() == null ? ""
                    : sync.focus()
                        .getDisplayName())
                .asWidget()
                .pos(32, 0)
                .size(240, 20));
        editor.child(
            IKey.dynamic(sync::pageText)
                .asWidget()
                .pos(8, 21)
                .size(204, 10));
        editor.child(
            new ButtonWidget<>().overlay(IKey.lang("isme.book.search"))
                .pos(216, 18)
                .size(56, 14)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    searchOpen[0] = true;
                    return true;
                }));
        editor.child(
            button("isme.book.previous", sync, FilterBookSync.PREVIOUS).pos(8, 34)
                .size(64, 16));
        editor.child(
            button("isme.book.next", sync, FilterBookSync.NEXT).pos(76, 34)
                .size(64, 16));
        editor.child(
            new ButtonWidget<>().overlay(IKey.dynamic(sync::modeText))
                .pos(144, 34)
                .size(60, 16)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    sync.command(FilterBookSync.MODE, 0);
                    return true;
                }));
        editor.child(
            button("isme.book.reset", sync, FilterBookSync.RESET).pos(208, 34)
                .size(64, 16));
        for (int cell = 0; cell < FilterBookSync.CELLS; cell++) {
            final int index = cell;
            ButtonWidget<?> button = new ButtonWidget<>().pos(8 + cell % 6 * 44, 54 + cell / 6 * 30)
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
            editor.child(button);
        }
        editor.child(
            button("isme.book.items_previous", sync, FilterBookSync.ITEMS_PREVIOUS).pos(8, 177)
                .size(78, 16));
        editor.child(
            button("isme.book.items_next", sync, FilterBookSync.ITEMS_NEXT).pos(90, 177)
                .size(78, 16));
        editor.child(
            IKey.dynamic(() -> StatCollector.translateToLocalFormatted("isme.book.radius", sync.radius()))
                .asWidget()
                .pos(172, 177)
                .size(100, 16));
        editor.child(
            IKey.dynamic(sync::statusText)
                .asWidget()
                .pos(8, 197)
                .size(264, 18));
        panel.child(searchPanel(sync, searchOpen));
        return panel;
    }

    private static ParentWidget<?> searchPanel(FilterBookSync sync, boolean[] searchOpen) {
        FilterBookSearch search = sync.search();
        ParentWidget<?> panel = new ParentWidget<>().pos(0, 22)
            .size(280, 210)
            .setEnabledIf(widget -> searchOpen[0]);
        panel.child(
            new TextFieldWidget().value(new StringValue.Dynamic(search::query, search::query))
                .autoUpdateOnChange(true)
                .setMaxLength(FilterBookSearch.MAX_QUERY)
                .hintText(StatCollector.translateToLocal("isme.book.search_hint"))
                .pos(8, 0)
                .size(218, 18));
        panel.child(
            new ButtonWidget<>().overlay(IKey.lang("isme.book.search_back"))
                .pos(230, 0)
                .size(42, 18)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    searchOpen[0] = false;
                    return true;
                }));
        for (int row = 0; row < FilterBookSearch.ROWS; row++) {
            final int index = row;
            ButtonWidget<?> button = new ButtonWidget<>().pos(8, 24 + row * 24)
                .size(264, 22)
                .setEnabledIf(widget -> search.result(index) != null)
                .child(
                    new ParentWidget<>().size(264, 22)
                        .child(
                            new ItemDisplayWidget()
                                .item(
                                    new ObjectValue.Dynamic<>(
                                        ItemStack.class,
                                        () -> search.result(index) == null ? null
                                            : search.result(index)
                                                .entry()
                                                .focus(),
                                        null))
                                .pos(2, 2)
                                .size(18))
                        .child(
                            IKey.dynamic(
                                () -> search.result(index) == null ? ""
                                    : search.result(index)
                                        .name())
                                .asWidget()
                                .scale(0.8f)
                                .pos(24, 2)
                                .size(236, 18)))
                .onMousePressed(mouse -> {
                    FilterBookSearch.Result result = search.result(index);
                    if (mouse != 0 || result == null) return false;
                    sync.command(FilterBookSync.JUMP, result.index());
                    searchOpen[0] = false;
                    return true;
                });
            button.tooltip()
                .addLine(
                    IKey.dynamic(
                        () -> search.result(index) == null ? ""
                            : search.result(index)
                                .name()))
                .addLine(
                    IKey.dynamic(
                        () -> search.result(index) == null ? ""
                            : search.result(index)
                                .entry()
                                .signature()))
                .addLine(
                    IKey.dynamic(
                        () -> search.result(index) == null ? ""
                            : search.result(index)
                                .entry()
                                .id()))
                .addLine(
                    IKey.dynamic(
                        () -> search.result(index) == null ? ""
                            : StatCollector.translateToLocalFormatted(
                                "isme.book.radius",
                                search.result(index)
                                    .entry()
                                    .radius())));
            panel.child(button);
        }
        panel.child(
            searchPageButton("isme.book.search_previous", search, -1).pos(8, 174)
                .size(54, 18));
        panel.child(
            searchPageButton("isme.book.search_next", search, 1).pos(66, 174)
                .size(54, 18));
        panel.child(
            IKey.dynamic(
                () -> search.count() == 0 ? StatCollector.translateToLocal("isme.book.search_empty")
                    : StatCollector.translateToLocalFormatted(
                        "isme.book.search_results",
                        search.count(),
                        search.page() + 1,
                        search.pages()))
                .asWidget()
                .scale(0.8f)
                .pos(126, 174)
                .size(146, 18));
        panel.child(
            IKey.lang("isme.book.search_help")
                .asWidget()
                .scale(0.8f)
                .pos(8, 195)
                .size(264, 14));
        return panel;
    }

    private static ButtonWidget<?> searchPageButton(String key, FilterBookSearch search, int direction) {
        return new ButtonWidget<>().overlay(IKey.lang(key))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                search.move(direction);
                return true;
            });
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
