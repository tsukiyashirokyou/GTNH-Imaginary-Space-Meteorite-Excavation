package com.kyou.imaginaryspacemeteoriteexcavation.gui;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.kyou.imaginaryspacemeteoriteexcavation.inventory.MachineItems;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.MeteoriteExcavation;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.MeteorGeometry;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public final class ExcavationGui extends MTEMultiBlockBaseGui<MeteoriteExcavation> {

    private IntSyncValue towerCount;
    private StringSyncValue orbOwner;
    private IntSyncValue normalEnergy;
    private IntSyncValue multiEnergy;
    private StringSyncValue state, recipe;
    private GenericSyncValue<NBTTagCompound, ?> outputPreview;
    private StringSyncValue diagnostic;
    private LongSyncValue balance, kept, voided, drilling, pending;
    private IntSyncValue batch, progress, duration;
    private BooleanSyncValue freeOrb;

    public ExcavationGui(MeteoriteExcavation machine) {
        super(machine);
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        settings.customContainer(() -> new ExcavationContainer(multiblock));
        return super.build(data, syncManager, settings);
    }

    @Override
    protected int getBasePanelHeight() {
        return 310;
    }

    @Override
    protected int getBasePanelWidth() {
        return 344;
    }

    @Override
    protected int getTerminalRowWidth() {
        return 336;
    }

    @Override
    protected int getTerminalRowHeight() {
        return 90;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        towerCount = new IntSyncValue(
            () -> multiblock.items()
                .towerCount());
        orbOwner = new StringSyncValue(() -> MachineItems.owner(multiblock.getControllerSlot()));
        normalEnergy = new IntSyncValue(multiblock::normalEnergyHatches);
        multiEnergy = new IntSyncValue(multiblock::multiAmpEnergyHatches);
        syncManager.syncValue("isme.towers", towerCount);
        syncManager.syncValue("isme.owner", orbOwner);
        syncManager.syncValue("isme.normalEnergy", normalEnergy);
        syncManager.syncValue("isme.multiEnergy", multiEnergy);
        state = new StringSyncValue(multiblock::runtimeState);
        diagnostic = new StringSyncValue(multiblock::runtimeDiagnostic);
        syncManager.syncValue("isme.diagnostic", diagnostic);
        recipe = new StringSyncValue(multiblock::currentFocusSignature);
        outputPreview = GenericSyncValue.builder(NBTTagCompound.class)
            .getter(multiblock::outputPreview)
            .adapter(ByteBufAdapters.NBT)
            .copy(value -> (NBTTagCompound) value.copy())
            .build();
        balance = new LongSyncValue(multiblock::lastLpBalance);
        kept = new LongSyncValue(multiblock::retainedCount);
        voided = new LongSyncValue(multiblock::voidedCount);
        drilling = new LongSyncValue(multiblock::drillingCount);
        pending = new LongSyncValue(multiblock::pendingCount);
        batch = new IntSyncValue(multiblock::currentBatch);
        progress = new IntSyncValue(multiblock::progressTicks);
        duration = new IntSyncValue(multiblock::durationTicks);
        freeOrb = new BooleanSyncValue(multiblock::hasFreeOrb);
        syncManager.syncValue("isme.state", state);
        syncManager.syncValue("isme.recipe", recipe);
        syncManager.syncValue("isme.preview", outputPreview);
        syncManager.syncValue("isme.lp", balance);
        syncManager.syncValue("isme.kept", kept);
        syncManager.syncValue("isme.voided", voided);
        syncManager.syncValue("isme.drilling", drilling);
        syncManager.syncValue("isme.pending", pending);
        syncManager.syncValue("isme.batch", batch);
        syncManager.syncValue("isme.progress", progress);
        syncManager.syncValue("isme.duration", duration);
        syncManager.syncValue("isme.free", freeOrb);
    }

    @Override
    public Flow createMainColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.column()
            .padding(4)
            .child(createTerminalRow(panel, syncManager))
            .child(
                Flow.row()
                    .height(4))
            .child(
                Flow.row()
                    .size(336, 128)
                    .child(setupPanel())
                    .child(
                        Flow.column()
                            .size(6, 128))
                    .child(processPanel()))
            .child(
                Flow.row()
                    .height(4))
            .child(createInventoryRow(panel, syncManager));
    }

    private ParentWidget<?> setupPanel() {
        ParentWidget<?> card = card(162);
        card.child(
            heading(IKey.lang("isme.gui.setup")).pos(6, 5)
                .size(150, 12));
        card.child(
            Flow.row()
                .pos(6, 20)
                .size(150, 34)
                .child(
                    slot(
                        "isme.gui.orb",
                        new ItemSlot().slot(
                            new ExcavationContainer.DedicatedSlot(multiblock.items(), MachineItems.ORB)
                                .singletonSlotGroup())))
                .child(
                    slot(
                        "isme.gui.book",
                        new ItemSlot().slot(
                            new ExcavationContainer.DedicatedSlot(multiblock.items(), MachineItems.BOOK)
                                .singletonSlotGroup())))
                .child(
                    slot(
                        "isme.gui.pylons",
                        new ItemSlot().slot(new ExcavationContainer.TowerSlot(multiblock).singletonSlotGroup()))));
        card.child(
            line(
                IKey.dynamic(
                    () -> StatCollector
                        .translateToLocalFormatted("isme.gui.tower_count", formatNumber(towerCount.getIntValue()))))
                            .pos(6, 56));
        card.child(
            line(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted(
                        "isme.gui.radius",
                        MeteorGeometry.maxRadiusForTowers(towerCount.getIntValue()) == 0
                            ? StatCollector.translateToLocal("isme.gui.none")
                            : formatNumber(MeteorGeometry.maxRadiusForTowers(towerCount.getIntValue()))))).pos(6, 68));
        card.child(
            line(
                IKey.dynamic(
                    () -> orbOwner.getStringValue()
                        .isEmpty() ? StatCollector.translateToLocal("isme.gui.missing_orb")
                            : StatCollector.translateToLocalFormatted("isme.gui.owner", orbOwner.getStringValue())))
                                .pos(6, 80));
        card.child(
            line(
                IKey.dynamic(
                    () -> freeOrb.getBoolValue() ? StatCollector.translateToLocal("isme.gui.free_lp")
                        : balance.getLongValue() < 0 ? StatCollector.translateToLocal("isme.gui.lp_unchecked")
                            : StatCollector
                                .translateToLocalFormatted("isme.gui.lp", formatNumber(balance.getLongValue()))))
                                    .pos(6, 92));
        card.child(
            line(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted(
                        "isme.gui.energy",
                        formatNumber(normalEnergy.getIntValue()),
                        formatNumber(multiEnergy.getIntValue())))).pos(6, 104));
        return card;
    }

    private ParentWidget<?> processPanel() {
        ParentWidget<?> card = card(168);
        card.child(
            heading(IKey.dynamic(() -> StatCollector.translateToLocal("isme.state." + state.getStringValue())))
                .scale(0.8f)
                .color(() -> PlayerText.stateColor(state.getStringValue()))
                .pos(6, 5)
                .size(156, 12)
                .tooltipBuilder(t -> {
                    String detail = PlayerText.detail(state.getStringValue(), diagnostic.getStringValue());
                    if (!detail.isEmpty()) t.add(detail);
                })
                .tooltipAutoUpdate(true));
        card.child(
            line(IKey.dynamic(() -> PlayerText.focusName(recipe.getStringValue()))).pos(6, 20)
                .size(156, 18)
                .tooltipBuilder(t -> t.add(PlayerText.focusName(recipe.getStringValue())))
                .tooltipAutoUpdate(true));
        card.child(
            new ProgressWidget().progress(() -> PlayerText.progress(progress.getIntValue(), duration.getIntValue()))
                .texture(GTGuiTextures.PROGRESSBAR_ARROW_MULTIPLE, 156)
                .pos(6, 42)
                .size(156, 6));
        card.child(
            line(
                IKey.dynamic(
                    () -> duration.getIntValue() <= 0 ? "—"
                        : StatCollector.translateToLocalFormatted(
                            "isme.gui.progress_percent",
                            formatNumber(PlayerText.percent(progress.getIntValue(), duration.getIntValue())))))
                                .pos(6, 50)
                                .size(156, 10));
        card.child(
            line(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted("isme.gui.batch", currentValue(batch.getIntValue()))))
                        .pos(6, 64));
        card.child(
            line(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted("isme.gui.kept", currentValue(kept.getLongValue()))))
                        .pos(6, 76));
        card.child(
            line(
                IKey.dynamic(
                    () -> StatCollector
                        .translateToLocalFormatted("isme.gui.voided", currentValue(voided.getLongValue()))))
                            .pos(6, 88));
        card.child(
            line(
                IKey.dynamic(
                    () -> StatCollector
                        .translateToLocalFormatted("isme.gui.drilling", currentValue(drilling.getLongValue()))))
                            .pos(6, 100));
        card.child(
            line(
                IKey.dynamic(
                    () -> pending.getLongValue() < 0 ? StatCollector.translateToLocal("isme.gui.pending_unknown")
                        : StatCollector
                            .translateToLocalFormatted("isme.gui.pending", formatNumber(pending.getLongValue()))))
                                .pos(6, 112)
                                .tooltipBuilder(t -> {
                                    t.add(StatCollector.translateToLocal("isme.gui.output_preview"));
                                    for (String value : PlayerText.outputLines(outputPreview.getValue())) t.add(value);
                                })
                                .tooltipAutoUpdate(true));
        return card;
    }

    private String currentValue(long value) {
        return duration.getIntValue() > 0 ? formatNumber(value) : "—";
    }

    private static ParentWidget<?> card(int width) {
        return new ParentWidget<>().size(width, 128)
            .widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL);
    }

    private static ParentWidget<?> slot(String label, ItemSlot slot) {
        return new ParentWidget<>().size(50, 34)
            .child(
                IKey.lang(label)
                    .asWidget()
                    .scale(0.75f)
                    .color(0xFFD0D8DD)
                    .shadow(false)
                    .pos(0, 0)
                    .size(50, 12))
            .child(
                slot.pos(15, 14)
                    .size(18));
    }

    private static TextWidget<?> heading(IKey key) {
        return key.asWidget()
            .color(0xFF7ACDD7)
            .shadow(false);
    }

    private static TextWidget<?> line(IKey key) {
        return key.asWidget()
            .height(12)
            .width(150)
            .scale(0.8f)
            .color(0xFFD0D8DD)
            .shadow(false);
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        // Replace the native controller slot: exactly the three dedicated slots above.
        return Flow.column()
            .width(18)
            .height(76)
            .leftRel(1, -2, 1)
            .mainAxisAlignment(Alignment.MainAxis.END)
            .child(createPowerSwitchButton())
            .child(createStructureUpdateButton(syncManager));
    }
}
