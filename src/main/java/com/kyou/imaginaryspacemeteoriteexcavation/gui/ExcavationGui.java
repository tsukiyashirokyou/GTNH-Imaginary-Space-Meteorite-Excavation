package com.kyou.imaginaryspacemeteoriteexcavation.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.kyou.imaginaryspacemeteoriteexcavation.inventory.MachineItems;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.MeteoriteExcavation;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.MeteorGeometry;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public final class ExcavationGui extends MTEMultiBlockBaseGui<MeteoriteExcavation> {

    private IntSyncValue towerCount;
    private StringSyncValue orbOwner;
    private IntSyncValue normalEnergy;
    private IntSyncValue multiEnergy;
    private StringSyncValue state, recipe, outputPreview;
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
        recipe = new StringSyncValue(multiblock::currentRecipe);
        outputPreview = new StringSyncValue(multiblock::outputSummary);
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
                    .fullWidth()
                    .height(36)
                    .child(
                        Flow.column()
                            .width(62)
                            .height(34)
                            .child(
                                IKey.lang("isme.gui.orb")
                                    .asWidget()
                                    .height(12))
                            .child(
                                new ItemSlot().slot(
                                    new ExcavationContainer.DedicatedSlot(multiblock.items(), MachineItems.ORB)
                                        .singletonSlotGroup())))
                    .child(
                        Flow.column()
                            .width(62)
                            .height(34)
                            .child(
                                IKey.lang("isme.gui.book")
                                    .asWidget()
                                    .height(12))
                            .child(
                                new ItemSlot().slot(
                                    new ExcavationContainer.DedicatedSlot(multiblock.items(), MachineItems.BOOK)
                                        .singletonSlotGroup())))
                    .child(
                        Flow.column()
                            .width(62)
                            .height(34)
                            .child(
                                IKey.lang("isme.gui.pylons")
                                    .asWidget()
                                    .height(12))
                            .child(
                                new ItemSlot()
                                    .slot(new ExcavationContainer.TowerSlot(multiblock).singletonSlotGroup()))))
            .child(statusColumns())
            .child(
                line(
                    IKey.dynamic(
                        () -> pending.getLongValue() < 0 ? StatCollector.translateToLocal("isme.gui.pending_unknown")
                            : StatCollector.translateToLocalFormatted("isme.gui.pending", pending.getLongValue())))
                                .tooltipBuilder(t -> t.add(outputPreview.getStringValue()))
                                .tooltipAutoUpdate(true))
            .child(createInventoryRow(panel, syncManager));
    }

    private Flow statusColumns() {
        Flow left = Flow.column()
            .width(166)
            .height(72)
            .child(
                line(
                    IKey.dynamic(
                        () -> StatCollector
                            .translateToLocalFormatted("isme.gui.tower_count", towerCount.getIntValue()))))
            .child(
                line(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted(
                            "isme.gui.radius",
                            MeteorGeometry.maxRadiusForTowers(towerCount.getIntValue()) == 0
                                ? StatCollector.translateToLocal("isme.gui.none")
                                : Integer.toString(MeteorGeometry.maxRadiusForTowers(towerCount.getIntValue()))))))
            .child(
                line(
                    IKey.dynamic(
                        () -> orbOwner.getStringValue()
                            .isEmpty()
                                ? StatCollector.translateToLocal("isme.gui.missing_orb")
                                : StatCollector
                                    .translateToLocalFormatted("isme.gui.owner", orbOwner.getStringValue()))))
            .child(
                line(
                    IKey.dynamic(
                        () -> freeOrb.getBoolValue() ? StatCollector.translateToLocal("isme.gui.free_lp")
                            : balance.getLongValue() < 0 ? StatCollector.translateToLocal("isme.gui.lp_unchecked")
                                : StatCollector.translateToLocalFormatted("isme.gui.lp", balance.getLongValue()))))
            .child(
                line(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted(
                            "isme.gui.energy",
                            normalEnergy.getIntValue(),
                            multiEnergy.getIntValue()))))
            .child(
                line(IKey.dynamic(() -> StatCollector.translateToLocal("isme.state." + state.getStringValue())))
                    .tooltipBuilder(t -> t.add(diagnostic.getStringValue()))
                    .tooltipAutoUpdate(true));
        Flow right = Flow.column()
            .width(166)
            .height(72)
            .child(
                line(IKey.dynamic(() -> StatCollector.translateToLocalFormatted("isme.gui.recipe", shortRecipe())))
                    .tooltipBuilder(t -> t.add(recipe.getStringValue()))
                    .tooltipAutoUpdate(true))
            .child(
                line(
                    IKey.dynamic(() -> StatCollector.translateToLocalFormatted("isme.gui.batch", batch.getIntValue()))))
            .child(
                line(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted(
                            "isme.gui.progress",
                            progress.getIntValue(),
                            duration.getIntValue()))))
            .child(
                line(IKey.dynamic(() -> StatCollector.translateToLocalFormatted("isme.gui.kept", kept.getLongValue()))))
            .child(
                line(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted("isme.gui.voided", voided.getLongValue()))))
            .child(
                line(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted("isme.gui.drilling", drilling.getLongValue()))));
        return Flow.row()
            .fullWidth()
            .height(72)
            .child(left)
            .child(right);
    }

    private String shortRecipe() {
        String value = recipe.getStringValue();
        return value.length() > 20 ? value.substring(0, 17) + "..." : value;
    }

    private static TextWidget<?> line(IKey key) {
        return key.asWidget()
            .height(12)
            .fullWidth();
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        // Replace the native controller slot: exactly the three dedicated slots above.
        return Flow.column()
            .width(18)
            .child(createPowerSwitchButton())
            .child(createStructureUpdateButton(syncManager));
    }
}
