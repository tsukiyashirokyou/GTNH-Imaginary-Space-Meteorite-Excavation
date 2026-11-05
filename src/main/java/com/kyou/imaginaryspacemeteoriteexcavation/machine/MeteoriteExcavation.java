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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.kyou.imaginaryspacemeteoriteexcavation.Registration;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterDefinition;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterCatalogueView;
import com.kyou.imaginaryspacemeteoriteexcavation.gui.ExcavationGui;
import com.kyou.imaginaryspacemeteoriteexcavation.inventory.MachineItems;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationCycle;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationWaila;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.PaidBatch;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.RetryThrottle;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.MeteorGeometry;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.BoundedNbtSize;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.MeteorCatalogueService;
import com.kyou.imaginaryspacemeteoriteexcavation.output.runtime.NativeItemOutput;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.BoundNetwork;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MachinePower;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MeteorProcessPlanner;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.PreparedProcess;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessFailure;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.BloodMagicLpAccess;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.BloodOrbAccess;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.LocalStartResources;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.MeteorRecipeResolver;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.NativeBatchScheduler;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.NativePowerSupply;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/** Native multiblock runtime with a single owned paid batch and bounded completed output. */
public final class MeteoriteExcavation extends MTEExtendedPowerMultiBlockBase<MeteoriteExcavation>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String MAIN = "main";
    private static final int CASING_TEXTURE = 62;
    private static final ItemStack[] COMPLETION_TRIGGER = new ItemStack[0];
    private static IStructureDefinition<MeteoriteExcavation> structure;
    private final MachineItems items = new MachineItems(this::inventoryChanged);
    private ExcavationCycle cycle = new ExcavationCycle();
    private final MeteorProcessPlanner<ItemIdentity> planner = new MeteorProcessPlanner<>(new NativeBatchScheduler());
    private final RetryThrottle recipeRetry = new RetryThrottle();
    private final RetryThrottle outputRetry = new RetryThrottle();
    private final BloodMagicLpAccess nativeLp = new BloodMagicLpAccess();
    private NativeItemOutput outputPort;
    private ProcessFailure failure = ProcessFailure.NO_RECIPE;
    private ItemStack cachedBook;
    private FilterDefinition cachedDefinition;
    private Predicate<ItemIdentity> cachedFilter;
    private String recipeLabel = "";
    private int lastBatch;
    private long lastKept, lastVoided, lastDrilling, lastLp = -1;
    private boolean freeOrb;
    private NBTBase invalidCycleTag;
    private String outputSummary = "";
    private Object previewOwner;
    private long previewRevision = Long.MIN_VALUE;
    private NBTTagCompound wailaPreview = new NBTTagCompound();

    private final ProcessingPorts.LpAccess lp = new ProcessingPorts.LpAccess() {

        public long balance(String owner) {
            return lastLp = nativeLp.balance(owner);
        }

        public boolean debit(String owner, int amount) {
            boolean charged = nativeLp.debit(owner, amount);
            if (charged && lastLp >= 0) lastLp = Math.max(0, lastLp - amount);
            return charged;
        }
    };

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
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isServerSide()) {
            lastLp = -1;
            freeOrb = false;
            markDirty();
            scheduleRecipeCheckImmediate();
        }
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
            .addInfo("Standard meteorite expectations; final block items, floor after batching.")
            .addInfo("3072 EU/t base, perfect overclocking and native 128-tick batching.")
            .addInfo("Focus + LP + 1 L drilling fluid per pre-filter item are charged at start.")
            .addInfo("Output blockage retains completed items. Power loss discards unfinished yield.")
            .addInfo("Power switch pauses. Normal saves and controller drops preserve the paid plan.")
            .addInfo("Native ordinary/compressed/ME output; no void or overflow cells in the ME bus.")
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
        if (!serverReady() || mMaxProgresstime > 0) return rejected(ProcessFailure.CONDITIONS_CHANGED);
        if (!cycle.canStart()) return rejected(ProcessFailure.OUTPUT_FULL);
        long tick = serverTick();
        if (!recipeRetry.acquire(tick)) return rejected(failure);
        RecipeCatalogue<ItemIdentity> catalogue = MeteorCatalogueService.catalogue();
        PreparedProcess<ItemIdentity> prepared;
        PaidBatch paid;
        try {
            verifyLocalInputs();
            List<ItemStack> order = new ArrayList<>();
            for (MTEHatchInputBus bus : mInputBusses) {
                for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                    if (slot != bus.getCircuitSlot()) order.add(bus.getStackInSlot(slot));
                }
            }
            Optional<RecipeCatalogue.Entry<ItemIdentity>> found = MeteorRecipeResolver.first(catalogue, order);
            if (!found.isPresent()) {
                recipeLabel = "";
                return rejected(ProcessFailure.NO_RECIPE);
            }
            RecipeCatalogue.Entry<ItemIdentity> recipe = found.get();
            recipeLabel = recipe.id();
            // Before reading the orb/network or constructing a resource commitment.
            if (!MeteorGeometry.hasEnoughTowers(
                recipe.recipe()
                    .radius(),
                items.towerCount())) {
                return rejected(ProcessFailure.INSUFFICIENT_TOWERS);
            }
            BoundNetwork orb = BloodOrbAccess.read(getControllerSlot());
            if (orb == null) return rejected(ProcessFailure.UNBOUND_ORB);
            freeOrb = orb.free();
            if (freeOrb) lastLp = -1;
            MachinePower power = NativePowerSupply.capture(mEnergyHatches, mExoticEnergyHatches);
            StartSnapshot snapshot = new StartSnapshot(catalogue, power);
            FilterCatalogueView.Ritual ritual = FilterCatalogueView.current()
                .forId(recipe.id());
            if (ritual == null || ritual.entry() != recipe) return rejected(ProcessFailure.CONDITIONS_CHANGED);
            Predicate<ItemIdentity> keep;
            try {
                keep = filter(ritual.definition());
            } catch (IllegalArgumentException invalidBook) {
                return rejected(ProcessFailure.UNSUPPORTED_INPUT);
            }
            MeteorProcessPlanner.Result<ItemIdentity> result = planner.prepare(
                recipe,
                items.towerCount(),
                () -> orb,
                LocalStartResources.ordinary(mInputBusses, mInputHatches),
                lp,
                power,
                keep,
                output(),
                snapshot::unchanged);
            if (result.failure != ProcessFailure.NONE) return rejected(result.failure);
            prepared = result.process;
            // Allocate and validate persistence before the only real resource commitment.
            paid = PaidBatch.prepare(prepared);
            CheckRecipeResult post = postCheckRecipe(CheckRecipeResultRegistry.SUCCESSFUL, processingLogic);
            if (!post.wasSuccessful()) return post;
        } catch (ArithmeticException overflow) {
            return rejected(ProcessFailure.OVERFLOW);
        } catch (UnsupportedOperationException unsupported) {
            return rejected(ProcessFailure.UNSUPPORTED_INPUT);
        } catch (IllegalArgumentException invalidPowerOrInput) {
            return rejected(ProcessFailure.INSUFFICIENT_POWER);
        }
        ProcessFailure started = prepared.start();
        if (started != ProcessFailure.NONE) return rejected(started);
        cycle.begin(paid);
        mProgresstime = 0;
        mMaxProgresstime = paid.duration;
        lEUt = -paid.eut;
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        mEfficiencyIncrease = 10000;
        lastParallel = paid.batch;
        // Empty completion trigger only. The owned long plan lives exclusively in cycle.running().
        mOutputItems = COMPLETION_TRIGGER;
        mOutputFluids = null;
        lastBatch = paid.batch;
        lastKept = paid.kept;
        lastVoided = paid.drilling - paid.kept;
        lastDrilling = paid.drilling;
        failure = ProcessFailure.NONE;
        recipeRetry.progressed(tick);
        updateSlots();
        markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        // Context for native post-checks/statistics; actual scheduling is the verified NativeBatchScheduler.
        return new ProcessingLogic();
    }

    private CheckRecipeResult rejected(ProcessFailure reason) {
        failure = reason == ProcessFailure.NONE ? ProcessFailure.CONDITIONS_CHANGED : reason;
        return SimpleCheckRecipeResult.ofFailure(
            "isme." + failure.name()
                .toLowerCase(Locale.ROOT));
    }

    private Predicate<ItemIdentity> filter(FilterDefinition definition) {
        ItemStack book = items.dedicated()
            .getStackInSlot(MachineItems.BOOK);
        if (cachedFilter == null || cachedDefinition != definition || !ItemStack.areItemStacksEqual(book, cachedBook)) {
            cachedBook = book == null ? null : book.copy();
            cachedDefinition = definition;
            cachedFilter = new FilterBookStore(Registration.filteringBook).predicate(book, definition);
        }
        return cachedFilter;
    }

    private long serverTick() {
        return getBaseMetaTileEntity() == null ? -1 : getBaseMetaTileEntity().getTimer();
    }

    private boolean serverReady() {
        return getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isServerSide()
            && getBaseMetaTileEntity().isAllowedToWork()
            && mMachine
            && mStartUpCheck < 0
            && getRepairStatus() > 0;
    }

    private NativeItemOutput output() {
        if (outputPort == null) outputPort = new NativeItemOutput(
            cycle.pending(),
            () -> mOutputBusses,
            this::serverTick,
            this::serverReady);
        return outputPort;
    }

    private void verifyLocalInputs() {
        Set<MTEHatch> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus == null || bus.getClass() != MTEHatchInputBus.class || !bus.isValid() || !unique.add(bus))
                throw new UnsupportedOperationException("Unverified/duplicate item input");
        }
        for (MTEHatchInput hatch : mInputHatches) {
            if (hatch == null || hatch.getClass() != MTEHatchInput.class || !hatch.isValid() || !unique.add(hatch))
                throw new UnsupportedOperationException("Unverified/duplicate fluid input");
        }
        for (MTEHatch hatch : mEnergyHatches) {
            if (!unique.add(hatch)) throw new UnsupportedOperationException("Duplicate energy input");
        }
        for (MTEHatch hatch : mExoticEnergyHatches) {
            if (!unique.add(hatch)) throw new UnsupportedOperationException("Duplicate energy input");
        }
    }

    private final class StartSnapshot {

        private final long tick = serverTick();
        private final RecipeCatalogue<ItemIdentity> catalogue;
        private final MachinePower power;
        private final int towers = items.towerCount(), repairs = getRepairStatus();
        private final ItemStack orb = copy(getControllerSlot());
        private final ItemStack book = copy(
            items.dedicated()
                .getStackInSlot(MachineItems.BOOK));
        private final ItemStack tower = items.towerStack(1);
        private final List<MTEHatchInputBus> inputs = new ArrayList<>(mInputBusses);
        private final List<MTEHatchInput> fluids = new ArrayList<>(mInputHatches);
        private final List<MTEHatchOutputBus> outputs = new ArrayList<>(mOutputBusses);
        private final List<MTEHatchEnergy> normal = new ArrayList<>(mEnergyHatches);
        private final List<MTEHatch> multi = new ArrayList<>(mExoticEnergyHatches);

        private StartSnapshot(RecipeCatalogue<ItemIdentity> catalogue, MachinePower power) {
            this.catalogue = catalogue;
            this.power = power;
        }

        private boolean unchanged() {
            if (!serverReady() || tick != serverTick()
                || !cycle.canStart()
                || mMaxProgresstime != 0
                || catalogue != MeteorCatalogueService.catalogue()
                || repairs != getRepairStatus()
                || towers != items.towerCount()
                || !ItemStack.areItemStacksEqual(orb, getControllerSlot())
                || !ItemStack.areItemStacksEqual(
                    book,
                    items.dedicated()
                        .getStackInSlot(MachineItems.BOOK))
                || !ItemStack.areItemStacksEqual(tower, items.towerStack(1))
                || !sameHatches(inputs, mInputBusses)
                || !sameHatches(fluids, mInputHatches)
                || !sameHatches(outputs, mOutputBusses)
                || !sameHatches(normal, mEnergyHatches)
                || !sameHatches(multi, mExoticEnergyHatches)) return false;
            MachinePower current = NativePowerSupply.capture(mEnergyHatches, mExoticEnergyHatches);
            return current.voltage() == power.voltage() && current.amperage() == power.amperage()
                && current.eut() == power.eut();
        }
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static boolean sameHatches(List<? extends MTEHatch> previous, List<? extends MTEHatch> current) {
        if (previous.size() != current.size()) return false;
        for (int i = 0; i < previous.size(); i++) {
            if (previous.get(i) != current.get(i) || !previous.get(i)
                .isValid()) return false;
        }
        return true;
    }

    @Override
    protected boolean shouldCheckRecipeThisTick(long tick) {
        return super.shouldCheckRecipeThisTick(tick) || (mTotalRunTime % 20 == 0);
    }

    @Override
    protected void runMachine(IGregTechTileEntity tile, long tick) {
        if (!tile.isAllowedToWork() || !cycle.fault()
            .isEmpty()) return;
        super.runMachine(tile, tick);
    }

    @Override
    public boolean onRunningTick(ItemStack stack) {
        if (getBaseMetaTileEntity() == null || !getBaseMetaTileEntity().isAllowedToWork()
            || cycle.running() == null
            || !cycle.fault()
                .isEmpty())
            return false;
        return super.onRunningTick(stack);
    }

    @Override
    public boolean addItemOutputs(ItemStack[] ignoredNativeSentinel) {
        boolean completed = cycle.finish();
        markDirty();
        return completed;
    }

    @Override
    protected void outputAfterRecipe() {
        lEUt = 0;
    }

    @Override
    public void stopMachine(ShutDownReason reason) {
        if (cycle != null) cycle.abort(reason.getID());
        super.stopMachine(reason);
        markDirty();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        // Native unload clears hatch references. Rebuild them before any resumed paid energy tick.
        mMachine = false;
        mStartUpCheck = 100;
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (tile.isServerSide() && !tile.isAllowedToWork()) tile.setActive(false);
        if (tile.isServerSide() && serverReady()
            && cycle.fault()
                .isEmpty()
            && cycle.pending()
                .blocksStart()
            && outputRetry.acquire(serverTick())) {
            long revision = cycle.pending()
                .revision();
            long accepted = cycle.pending()
                .flush(serverTick(), output());
            if (accepted > 0) outputRetry.progressed(serverTick());
            if (revision != cycle.pending()
                .revision()) markDirty();
            if (!cycle.pending()
                .blocksStart()) scheduleRecipeCheckImmediate();
        }
        if (tile.isServerSide()) refreshOutputSummary();
        if (tile.isServerSide() && tick % 20 == 0) {
            BoundNetwork orb = BloodOrbAccess.read(getControllerSlot());
            freeOrb = orb != null && orb.free();
        }
    }

    public String runtimeState() {
        if (!cycle.fault()
            .isEmpty()) return "fault";
        if (getBaseMetaTileEntity() != null && !getBaseMetaTileEntity().isAllowedToWork()) return cycle.lastAbort()
            .isEmpty() ? "paused" : "failed";
        if (!mMachine) return "structure";
        if (cycle.running() != null) return "running";
        if (cycle.pending()
            .blocksStart()) return "output_wait";
        if (!cycle.lastAbort()
            .isEmpty()) return "failed";
        return failure == ProcessFailure.NONE ? "idle"
            : failure.name()
                .toLowerCase(Locale.ROOT);
    }

    public String currentRecipe() {
        return cycle.running() == null ? recipeLabel : cycle.running().recipe;
    }

    public int currentBatch() {
        return cycle.running() == null ? lastBatch : cycle.running().batch;
    }

    public long retainedCount() {
        return cycle.running() == null ? lastKept : cycle.running().kept;
    }

    public long voidedCount() {
        return cycle.running() == null ? lastVoided : cycle.running().drilling - cycle.running().kept;
    }

    public long drillingCount() {
        return cycle.running() == null ? lastDrilling : cycle.running().drilling;
    }

    public long pendingCount() {
        return cycle.fault()
            .isEmpty()
                ? cycle.pending()
                    .total()
                : -1;
    }

    public String runtimeDiagnostic() {
        String detail = !cycle.fault()
            .isEmpty() ? cycle.fault() : outputPort == null ? "" : outputPort.lastFailure();
        return detail == null ? "" : detail.substring(0, Math.min(512, detail.length()));
    }

    public long lastLpBalance() {
        return lastLp;
    }

    public boolean hasFreeOrb() {
        return freeOrb;
    }

    public int progressTicks() {
        return mProgresstime;
    }

    public int durationTicks() {
        return mMaxProgresstime;
    }

    public String outputSummary() {
        return outputSummary;
    }

    private void refreshOutputSummary() {
        Object owner = cycle.running() == null ? cycle.pending() : cycle.running();
        long revision = cycle.running() == null ? cycle.pending()
            .revision() : 0;
        if (owner == previewOwner && revision == previewRevision) return;
        previewOwner = owner;
        previewRevision = revision;
        java.util.Map<ItemIdentity, Long> source = cycle.running() == null ? cycle.pending()
            .snapshot()
            : cycle.running()
                .items();
        wailaPreview = ExcavationWaila.preview(source);
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (java.util.Map.Entry<ItemIdentity, Long> entry : source.entrySet()) {
            if (count++ == 3) break;
            if (text.length() > 0) text.append("; ");
            ItemStack sample = entry.getKey()
                .template();
            String name = ItemIdentity.focusSignature(sample);
            text.append(name.substring(0, Math.min(64, name.length())))
                .append(" x")
                .append(entry.getValue());
        }
        if (source.size() > 3) text.append(" (+")
            .append(source.size() - 3)
            .append(")");
        outputSummary = text.toString();
    }

    @Override
    public void getExtraWailaNBT(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        refreshOutputSummary();
        NBTTagCompound hud = new NBTTagCompound();
        PaidBatch paid = cycle.running();
        String fault = cycle.fault();
        boolean hasBatch = paid != null && fault.isEmpty();
        String state = runtimeState();
        if ("running".equals(state) && mStartUpCheck >= 0) state = "starting";
        if ("running".equals(state) && getRepairStatus() <= 0) state = "maintenance";
        hud.setString("state", state);
        hud.setBoolean("hasBatch", hasBatch);
        hud.setInteger("towers", items.towerCount());
        hud.setLong(
            "pending",
            fault.isEmpty() ? cycle.pending()
                .total() : -1);
        if (hasBatch) {
            hud.setInteger("radius", paid.radius);
            hud.setInteger("batch", paid.batch);
            hud.setLong("kept", paid.kept);
            hud.setInteger("duration", paid.duration);
            hud.setInteger("progress", mProgresstime);
            hud.setLong("demand", getActualEnergyUsage());
        }
        if (hasBatch || hud.getLong("pending") > 0) hud.setTag("preview", wailaPreview.copy());
        String diagnostic = !fault.isEmpty() ? fault : "output_wait".equals(state) ? runtimeDiagnostic() : "";
        hud.setString("diagnostic", diagnostic.substring(0, Math.min(128, diagnostic.length())));
        if ("failed".equals(state) && getBaseMetaTileEntity() != null) {
            ShutDownReason reason = getBaseMetaTileEntity().getLastShutDownReason();
            if (reason != null && !"none".equals(reason.getKey())) {
                NBTTagCompound data = reason.writeToNBT(new NBTTagCompound());
                if (BoundedNbtSize.measure(data, 512) <= 512) {
                    NBTTagCompound shutdown = new NBTTagCompound();
                    shutdown.setString("id", reason.getID());
                    shutdown.setTag("data", data);
                    hud.setTag("shutdown", shutdown);
                }
            }
        }
        tag.setTag(ExcavationWaila.KEY, hud);
        // Keep native progress/energy rendering, but describe this controller's paid lifecycle rather than a
        // stale active bit (e.g. immediately after pausing). No machine state is changed by the HUD callback.
        boolean active = hasBatch && "running".equals(state) && serverReady();
        tag.setBoolean("isActive", active);
        tag.setBoolean("isAllowedToWork", getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isAllowedToWork());
        tag.setBoolean("incompleteStructure", !mMachine);
        if (active) tag.setLong("energyUsage", getActualEnergyUsage());
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        // Append after the native incomplete-structure clear, so paused/pending/fault state remains visible.
        ExcavationWaila.append(tip, accessor.getNBTData());
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
        nbt.setTag("ISMECycle", invalidCycleTag == null ? cycle.save() : invalidCycleTag.copy());
        NBTTagCompound summary = new NBTTagCompound();
        summary.setString("recipe", recipeLabel);
        summary.setInteger("batch", lastBatch);
        summary.setLong("kept", lastKept);
        summary.setLong("voided", lastVoided);
        summary.setLong("drilling", lastDrilling);
        nbt.setTag("ISMESummary", summary);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        items.read(nbt);
        invalidCycleTag = nbt.hasKey("ISMECycle") && !nbt.hasKey("ISMECycle", 10) ? nbt.getTag("ISMECycle")
            .copy() : null;
        cycle = ExcavationCycle.load(
            nbt.hasKey("ISMECycle", 10) ? nbt.getCompoundTag("ISMECycle")
                : nbt.hasKey("ISMECycle") ? new NBTTagCompound() : null,
            mProgresstime,
            mMaxProgresstime,
            lEUt);
        if (!cycle.fault()
            .isEmpty() || cycle.running() == null) {
            mProgresstime = 0;
            mMaxProgresstime = 0;
            lEUt = 0;
            mRunningOnLoad = false;
        }
        mOutputItems = cycle.running() == null ? null : COMPLETION_TRIGGER;
        mOutputFluids = null;
        outputPort = null;
        cachedFilter = null;
        NBTTagCompound summary = nbt.hasKey("ISMESummary", 10) ? nbt.getCompoundTag("ISMESummary")
            : new NBTTagCompound();
        String label = summary.getString("recipe");
        long kept = summary.getLong("kept"), voided = summary.getLong("voided"), drilling = summary.getLong("drilling");
        if (label.length() <= 256 && summary.getInteger("batch") >= 0
            && kept >= 0
            && voided >= 0
            && drilling >= kept
            && drilling - kept == voided) {
            recipeLabel = label;
            lastBatch = summary.getInteger("batch");
            lastKept = kept;
            lastVoided = voided;
            lastDrilling = drilling;
        }
        if (cycle.running() != null) {
            PaidBatch paid = cycle.running();
            recipeLabel = paid.recipe;
            lastBatch = paid.batch;
            lastKept = paid.kept;
            lastVoided = paid.drilling - paid.kept;
            lastDrilling = paid.drilling;
        }
        refreshOutputSummary();
    }

    @Override
    public void setItemNBT(NBTTagCompound nbt) {
        super.setItemNBT(nbt);
        saveNBTData(nbt);
        if (getBaseMetaTileEntity() != null) nbt.setBoolean("mWorks", !getBaseMetaTileEntity().isAllowedToWork());
    }
}
