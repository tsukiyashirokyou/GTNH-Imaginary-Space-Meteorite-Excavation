package com.kyou.imaginaryspacemeteoriteexcavation.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.kyou.imaginaryspacemeteoriteexcavation.inventory.MachineItems;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.MeteoriteExcavation;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public final class ExcavationGui extends MTEMultiBlockBaseGui<MeteoriteExcavation> {

    private IntSyncValue towerCount;
    private StringSyncValue orbOwner;
    private IntSyncValue normalEnergy;
    private IntSyncValue multiEnergy;

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
        return 278;
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
            .child(
                IKey.dynamic(
                    () -> towerCount.getIntValue() == 0 ? StatCollector.translateToLocal("isme.gui.missing_pylons")
                        : StatCollector.translateToLocalFormatted("isme.gui.tower_count", towerCount.getIntValue()))
                    .asWidget()
                    .height(12)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted(
                        "isme.gui.radius",
                        towerCount.getIntValue() < 6 ? StatCollector.translateToLocal("isme.gui.none")
                            : Integer.toString(towerCount.getIntValue() / 3 - 1)))
                    .asWidget()
                    .height(12)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> orbOwner.getStringValue()
                        .isEmpty() ? StatCollector.translateToLocal("isme.gui.missing_orb")
                            : StatCollector.translateToLocalFormatted("isme.gui.owner", orbOwner.getStringValue()))
                    .asWidget()
                    .height(12)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted(
                        "isme.gui.energy",
                        normalEnergy.getIntValue(),
                        multiEnergy.getIntValue()))
                    .asWidget()
                    .height(12)
                    .fullWidth())
            .child(
                IKey.lang("isme.gui.foundation")
                    .asWidget()
                    .height(12)
                    .fullWidth())
            .child(createInventoryRow(panel, syncManager));
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
