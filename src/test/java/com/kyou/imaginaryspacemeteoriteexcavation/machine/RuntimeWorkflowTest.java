package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterBookState;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterDefinition;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationCycle;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.PaidBatch;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeSnapshot;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.WeightedComponent;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.output.runtime.NativeItemOutput;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.BoundNetwork;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MachinePower;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MeteorProcessPlanner;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.PreparedProcess;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessFailure;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.LocalStartResources;

import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/**
 * Real calculation/filter/local resource/native ordinary output modules; LP, scheduler and tile are declared doubles.
 */
public final class RuntimeWorkflowTest {

    private static Item focusItem, ore, filler, book;
    private static Fluid drilling;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        focusItem = item(30001, "focus");
        ore = item(30002, "ore");
        filler = item(30003, "filler");
        book = item(30004, "book");
        drilling = new Fluid("isme_workflow_drilling");
        FluidRegistry.registerFluid(drilling);
    }

    private static Item item(int id, String name) {
        Item item = new Item();
        Item.itemRegistry.addObject(id, "minecraft:isme_workflow_" + name, item);
        return item;
    }

    private boolean delegated(String name) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        HeadlessForgeFixture.run(getClass().getName(), name);
        return true;
    }

    private static RecipeCatalogue.Entry<ItemIdentity> recipe() {
        return new RecipeCatalogue.Entry<>(
            "standard:test",
            "test.json",
            "source",
            new RecipeSnapshot<>(
                ItemIdentity.focusSignature(new ItemStack(focusItem)),
                1,
                4,
                0,
                Arrays.asList(
                    new WeightedComponent<>(new ItemIdentity(new ItemStack(ore)), 8),
                    new WeightedComponent<>(new ItemIdentity(new ItemStack(filler)), 87)),
                Collections.emptyList(),
                ItemIdentity::signature));
    }

    private static MeteorProcessPlanner<ItemIdentity> planner() {
        return new MeteorProcessPlanner<>((ticks, power, choose, advance) -> {
            int batch = choose.applyAsInt(3);
            advance.accept(batch);
            return ProcessingPorts.Schedule.success(batch, 100, 3072);
        });
    }

    private static final class Network implements ProcessingPorts.LpAccess {

        long amount = 200;
        int charges, reads;

        public long balance(String owner) {
            reads++;
            return amount;
        }

        public boolean debit(String owner, int cost) {
            amount -= cost;
            charges++;
            return true;
        }
    }

    @Test
    public void paidFilteredBatchReloadsThenRecoversIntoActualNativeOutputOnlyOnce() throws Exception {
        if (delegated("paidFilteredBatchReloadsThenRecoversIntoActualNativeOutputOnlyOnce")) return;
        RecipeCatalogue.Entry<ItemIdentity> recipe = recipe();
        FilterDefinition definition = new FilterDefinition(
            recipe.id(),
            recipe.recipe()
                .focusSignature(),
            recipe.recipe()
                .configurationHash(),
            Arrays.asList(
                new ItemIdentity(new ItemStack(ore)).signature(),
                new ItemIdentity(new ItemStack(filler)).signature()));
        FilterBookStore books = new FilterBookStore(book);
        ItemStack filtering = books.writeSingle(
            books.defaults(1),
            FilterBookState.defaults()
                .toggle(definition, new ItemIdentity(new ItemStack(ore)).signature()));
        ExcavationCycle cycle = new ExcavationCycle();
        MTEHatchOutputBus bus = new MTEHatchOutputBus("workflow_output", 1, 1, new String[0], new ITexture[0][][]);
        PublicTileFixture tile = new PublicTileFixture();
        tile.bind(bus);
        NativeItemOutput port = new NativeItemOutput(
            cycle.pending(),
            () -> Collections.singletonList(bus),
            () -> tile.tick,
            () -> true);
        Network network = new Network();
        ItemStack[] focuses = { new ItemStack(focusItem, 3) };
        FluidStack[] fluid = { new FluidStack(drilling, 100) };
        PreparedProcess<ItemIdentity> process = planner().prepare(
            recipe,
            12,
            () -> new BoundNetwork("owner", "orb", false),
            LocalStartResources.arrays(focuses, fluid, drilling, () -> {}),
            network,
            MachinePower.fromHatches(1, 0, new long[] { 8192 }, new long[] { 2 }),
            books.predicate(filtering, definition),
            port,
            cycle::canStart).process;
        assertNotNull(process);
        PaidBatch paid = PaidBatch.prepare(process);
        assertEquals(56, paid.drilling);
        assertEquals(52, paid.kept);
        assertEquals(ProcessFailure.NONE, process.start());
        cycle.begin(paid);
        assertEquals(44, fluid[0].amount);
        assertEquals(0, focuses[0].stackSize);
        assertEquals(188, network.amount);
        assertEquals(1, network.charges);
        ExcavationCycle resumed = ExcavationCycle.load(cycle.save(), 50, 100, -3072);
        assertTrue(resumed.finish());
        assertFalse(resumed.finish());
        NativeItemOutput resumedPort = new NativeItemOutput(
            resumed.pending(),
            () -> Collections.singletonList(bus),
            () -> tile.tick,
            () -> true);
        bus.mInventory[0] = new ItemStack(ore, 64);
        assertEquals(
            0,
            resumed.pending()
                .flush(2, resumedPort));
        assertEquals(
            52,
            resumed.pending()
                .total());
        assertFalse(resumed.canStart());
        bus.mInventory[0] = null;
        tile.tick = 3;
        assertEquals(
            52,
            resumed.pending()
                .flush(3, resumedPort));
        assertEquals(filler, bus.mInventory[0].getItem());
        assertEquals(52, bus.mInventory[0].stackSize);
        assertEquals(
            0,
            resumed.pending()
                .flush(4, resumedPort));
        assertEquals(ProcessFailure.ALREADY_DECIDED, process.start());
        assertEquals(1, network.charges);
        assertEquals(44, fluid[0].amount);
        assertTrue(resumed.canStart());
    }

    @Test
    public void towerFailureSkipsLpAndFullOutputNeverChargesResources() throws Exception {
        if (delegated("towerFailureSkipsLpAndFullOutputNeverChargesResources")) return;
        Network network = new Network();
        ItemStack[] focuses = { new ItemStack(focusItem, 3) };
        FluidStack[] fluid = { new FluidStack(drilling, 100) };
        MachinePower power = MachinePower.fromHatches(1, 0, new long[] { 8192 }, new long[] { 2 });
        MeteorProcessPlanner.Result<ItemIdentity> lowTower = planner().prepare(
            recipe(),
            11,
            () -> new BoundNetwork("owner", "orb", false),
            LocalStartResources.arrays(focuses, fluid, drilling, () -> {}),
            network,
            power,
            item -> true,
            items -> false,
            () -> true);
        assertEquals(ProcessFailure.INSUFFICIENT_TOWERS, lowTower.failure);
        assertEquals(0, network.reads);
        MeteorProcessPlanner.Result<ItemIdentity> full = planner().prepare(
            recipe(),
            12,
            () -> new BoundNetwork("owner", "orb", false),
            LocalStartResources.arrays(focuses, fluid, drilling, () -> {}),
            network,
            power,
            item -> true,
            items -> false,
            () -> true);
        assertEquals(ProcessFailure.OUTPUT_FULL, full.failure);
        assertEquals(0, network.charges);
        assertEquals(200, network.amount);
        assertEquals(3, focuses[0].stackSize);
        assertEquals(100, fluid[0].amount);
    }
}
