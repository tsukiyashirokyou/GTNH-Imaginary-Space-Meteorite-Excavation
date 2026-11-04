package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.MultiAmpEnergy;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.kyou.imaginaryspacemeteoriteexcavation.gui.ExcavationGui;
import com.kyou.imaginaryspacemeteoriteexcavation.inventory.MachineItems;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** Stage 2 foundation. Deliberately never runs a meteorite recipe. */
public final class MeteoriteExcavation extends MTEExtendedPowerMultiBlockBase<MeteoriteExcavation>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String MAIN = "main";
    private static final int CASING_TEXTURE = 62;
    private static IStructureDefinition<MeteoriteExcavation> structure;
    private final MachineItems items = new MachineItems(this::inventoryChanged);

    public MeteoriteExcavation(int id, String name, String displayName) {
        super(id, name, displayName);
    }

    private MeteoriteExcavation(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MeteoriteExcavation(mName);
    }

    public MachineItems items() {
        return items;
    }

    private void inventoryChanged() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isServerSide()) markDirty();
    }

    @Override
    public IStructureDefinition<MeteoriteExcavation> getStructureDefinition() {
        if (structure == null) {
            ItemStack casing = ItemList.Casing_MiningOsmiridium.get(1);
            structure = StructureDefinition.<MeteoriteExcavation>builder()
                .addShape(
                    MAIN,
                    transpose(
                        new String[][] { { "   ", " f ", "   " }, { "   ", " f ", "   " }, { "   ", " f ", "   " },
                            { " f ", "fcf", " f " }, { " f ", "fcf", " f " }, { " f ", "fcf", " f " },
                            { "b~b", "bbb", "bbb" } }))
                .addElement('f', ofFrame(Materials.Osmiridium))
                .addElement('c', ofBlock(ItemList.Casing_MiningOsmiridium.getBlock(), casing.getItemDamage()))
                .addElement(
                    'b',
                    buildHatchAdder(MeteoriteExcavation.class)
                        .atLeast(InputBus, InputHatch, OutputBus, Maintenance, Energy.or(MultiAmpEnergy))
                        .casingIndex(CASING_TEXTURE)
                        .hint(1)
                        .buildAndChain(ItemList.Casing_MiningOsmiridium.getBlock(), casing.getItemDamage()))
                .build();
        }
        return structure;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack controller, List<StructureError> errors) {
        if (!checkPiece(MAIN, 1, 6, 0, errors)) return;
        if (mExoticEnergyHatches.isEmpty()) {
            checkHasEnergyHatch(errors);
        } else if (!EnergyLayout.isValid(mEnergyHatches.size(), mExoticEnergyHatches.size())) {
            errors.add(StructureErrorRegistry.ONE_ENERGY_HATCH_ON_MULTI_OR_LASER);
        }
        checkHasMaintenanceHatch(errors);
        checkHasInputBus(errors);
        checkHasInputHatch(errors);
        checkHasOutputBus(errors);
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(MAIN, stack, hintsOnly, 1, 6, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment environment) {
        if (mMachine) return -1;
        return survivalBuildPiece(MAIN, stack, 1, 6, 0, budget, environment, false, true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Imaginary Space Meteorite Excavation")
            .addInfo("Stage 2: structure and inventory foundation; processing is not yet available.")
            .addInfo("Three slots: bound Blood Orb, optional Filtering Book, spatial pylons.")
            .addInfo("Spatial pylons: up to 2,147,483,647; only normal stacks can be withdrawn.")
            .addInfo("Multiple normal energy hatches OR one multi-amp hatch. No mixing or lasers.")
            .beginStructureBlock(3, 7, 3, false)
            .addController("Bottom front center")
            .addCasing("3", "Mining Osmiridium Casing", false)
            .addStructureInfo("15 Osmiridium frames; 8 bottom positions accept casing or required hatches.")
            .addInputBus("1+", "Bottom layer", 1)
            .addInputHatch("1+", "Bottom layer", 1)
            .addOutputBus("1+", "Bottom layer", 1)
            .addMaintenanceHatch("1+", "Bottom layer", 1)
            .addEnergyHatch("1+ normal OR 1 multi-amp", "Bottom layer", 1)
            .toolTipFinisher();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            facing,
            active,
            Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL,
            Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_GLOW,
            Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE,
            Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Textures.BlockIcons.getCasingTextureForId(CASING_TEXTURE);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public ItemStack getControllerSlot() {
        return items == null ? null
            : items.dedicated()
                .getStackInSlot(MachineItems.ORB);
    }

    @Override
    public boolean isValidSlot(int index) {
        return false;
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new ExcavationGui(this);
    }

    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    public int normalEnergyHatches() {
        return mEnergyHatches.size();
    }

    public int multiAmpEnergyHatches() {
        return mExoticEnergyHatches.size();
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        items.write(nbt);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        items.read(nbt);
        // This stage cannot resume any injected/legacy production plan.
        mProgresstime = 0;
        mMaxProgresstime = 0;
        mOutputItems = null;
        mOutputFluids = null;
        lEUt = 0;
    }

    @Override
    public void setItemNBT(NBTTagCompound nbt) {
        super.setItemNBT(nbt);
        items.write(nbt);
    }
}
