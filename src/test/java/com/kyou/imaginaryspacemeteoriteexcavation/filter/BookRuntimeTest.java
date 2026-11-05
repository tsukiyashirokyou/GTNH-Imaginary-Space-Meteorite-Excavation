package com.kyou.imaginaryspacemeteoriteexcavation.filter;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.BeforeClass;
import org.junit.Test;

import com.cleanroommc.modularui.widgets.slot.InventoryCraftingWrapper;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.BookCraftingGrid;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.CopyContinuationGuard;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.CopyRemainderGuard;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.DefaultBookRecipe;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.FilterBookRecipe;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookSession;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeSnapshot;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.WeightedComponent;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.BoundNetwork;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MachinePower;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MeteorProcessPlanner;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.PreparedProcess;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessFailure;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.LocalStartResources;

/**
 * Actual development registry, recipes, ItemStack/NBT and local resource classes; no World or player is constructed.
 */
public final class BookRuntimeTest {

    private static Item book, filter, orb, ore, filler;
    private static FilterBookStore books;
    private static Fluid drilling;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        book = item(30001, "book");
        filter = item(30002, "filter").setHasSubtypes(true);
        orb = item(30003, "orb");
        ore = item(30004, "ore");
        filler = item(30005, "filler");
        books = new FilterBookStore(book);
        drilling = new Fluid("isme_book_test_drilling");
        FluidRegistry.registerFluid(drilling);
    }

    private static Item item(int id, String name) {
        Item item = new Item();
        Item.itemRegistry.addObject(id, "isme_test:" + name, item);
        return item;
    }

    private boolean delegated(String method) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        HeadlessForgeFixture.run(getClass().getName(), method);
        return true;
    }

    private static FilterDefinition definition() {
        return new FilterDefinition("pack:one", "focus:0", "hash", Arrays.asList("ore", "filler"));
    }

    private static ItemStack configured() {
        return books.writeSingle(
            books.defaults(1),
            FilterBookState.defaults()
                .toggle(definition(), "ore"));
    }

    @Test
    public void copyPreviewConsumesNothingAndSplitBooksHaveIndependentConfigurations() throws Exception {
        if (delegated("copyPreviewConsumesNothingAndSplitBooksHaveIndependentConfigurations")) return;
        InventoryCrafting grid = grid();
        ItemStack source = configured();
        source.stackSize = 3;
        ItemStack target = books.defaults(2);
        grid.setInventorySlotContents(2, source);
        grid.setInventorySlotContents(7, target);
        ItemStack before = source.copy();
        FilterBookRecipe copy = new FilterBookRecipe(books, true);
        assertTrue(copy.matches(grid, null));
        ItemStack output = copy.getCraftingResult(grid);
        assertEquals(2, output.stackSize);
        assertTrue(ItemStack.areItemStacksEqual(before, source));
        assertEquals(2, target.stackSize);
        ItemStack split = output.splitStack(1);
        ItemStack edited = books.writeSingle(
            split,
            books.read(split)
                .changeMode(definition()));
        assertEquals(
            FilterPage.Mode.BLACKLIST,
            books.read(edited)
                .page(definition())
                .mode());
        assertEquals(
            FilterPage.Mode.WHITELIST,
            books.read(output)
                .page(definition())
                .mode());
        assertEquals(
            FilterPage.Mode.WHITELIST,
            books.read(source)
                .page(definition())
                .mode());
        grid.decrStackSize(2, 1);
        grid.decrStackSize(7, 1);
        assertEquals(2, source.stackSize);
        assertEquals(1, target.stackSize);
        assertFalse(book.hasContainerItem(source));
        assertEquals(5, source.stackSize + target.stackSize + output.stackSize + split.stackSize);
    }

    @Test
    public void ambiguousInvalidAndExtraInputsRejectButClearRecoversMalformedData() throws Exception {
        if (delegated("ambiguousInvalidAndExtraInputsRejectButClearRecoversMalformedData")) return;
        InventoryCrafting grid = grid();
        FilterBookRecipe copy = new FilterBookRecipe(books, true);
        FilterBookRecipe clear = new FilterBookRecipe(books, false);
        grid.setInventorySlotContents(0, configured());
        grid.setInventorySlotContents(1, configured());
        assertNull(copy.getCraftingResult(grid));
        grid.setInventorySlotContents(1, books.defaults(1));
        grid.setInventorySlotContents(8, new ItemStack(ore));
        assertFalse(copy.matches(grid, null));
        grid.setInventorySlotContents(8, null);
        grid.setInventorySlotContents(1, null);
        ItemStack bad = configured();
        bad.getTagCompound()
            .getCompoundTag(FilterBookCodec.KEY)
            .setInteger("schema", 999);
        grid.setInventorySlotContents(0, bad);
        ItemStack cleared = clear.getCraftingResult(grid);
        assertNotNull(cleared);
        assertEquals(1, cleared.stackSize);
        assertFalse(
            books.read(cleared)
                .configured());
        assertEquals(
            999,
            bad.getTagCompound()
                .getCompoundTag(FilterBookCodec.KEY)
                .getInteger("schema"));
        grid.setInventorySlotContents(1, books.defaults(1));
        assertNull(copy.getCraftingResult(grid));
    }

    @Test
    public void approvedDefaultPatternAndUnsafeMuiBoundaryAreExplicit() throws Exception {
        if (delegated("approvedDefaultPatternAndUnsafeMuiBoundaryAreExplicit")) return;
        DefaultBookRecipe recipe = new DefaultBookRecipe(
            new ItemStack(filter, 1, 0),
            new ItemStack(orb),
            books.defaults(1));
        InventoryCrafting grid = grid();
        for (int slot = 0; slot < 9; slot++) grid.setInventorySlotContents(
            slot,
            slot == 4 ? new ItemStack(Items.book) : new ItemStack(slot % 2 == 0 ? filter : orb));
        assertTrue(recipe.matches(grid, null));
        assertFalse(
            books.read(recipe.getCraftingResult(grid))
                .configured());
        grid.setInventorySlotContents(0, new ItemStack(filter, 1, 1));
        assertFalse(recipe.matches(grid, null));
        assertTrue(BookCraftingGrid.supported(InventoryCrafting.class));
        assertFalse(BookCraftingGrid.supported(InventoryCraftingWrapper.class));
    }

    @Test
    public void singleBookSessionRejectsStaleRevisionReplacementCountAndForeignNbt() throws Exception {
        if (delegated("singleBookSessionRejectsStaleRevisionReplacementCountAndForeignNbt")) return;
        ItemStack[] cell = { books.defaults(1) };
        FilterBookSession session = new FilterBookSession(books, () -> cell[0], value -> cell[0] = value);
        assertTrue(session.edit(0, definition(), 0, "ore"));
        assertFalse(session.edit(0, definition(), 0, "filler"));
        assertTrue(session.edit(1, definition(), 1, null));
        cell[0] = cell[0].copy();
        assertFalse(session.edit(2, definition(), 2, null));
        cell[0] = books.defaults(2);
        FilterBookSession stack = new FilterBookSession(books, () -> cell[0], value -> cell[0] = value);
        assertFalse(stack.edit(0, definition(), 0, "ore"));
        cell[0] = books.defaults(1);
        FilterBookSession nbt = new FilterBookSession(books, () -> cell[0], value -> cell[0] = value);
        cell[0].setTagCompound(new NBTTagCompound());
        cell[0].getTagCompound()
            .setString("foreign", "changed");
        assertFalse(nbt.edit(0, definition(), 0, "ore"));
    }

    @Test
    public void copyRemainderRequiresTheActualResultObjectAndShiftDoesNotCascadeIntoClear() throws Exception {
        if (delegated("copyRemainderRequiresTheActualResultObjectAndShiftDoesNotCascadeIntoClear")) return;
        ItemStack expected = configured();
        expected.stackSize = 2;
        ItemStack residual = expected.copy();
        residual.stackSize = 1;
        assertTrue(CopyRemainderGuard.isPartialResult(expected, residual, residual));
        assertFalse(CopyRemainderGuard.isPartialResult(expected, residual, residual.copy()));
        assertFalse(CopyRemainderGuard.isPartialResult(expected, expected, expected));
        assertFalse(CopyRemainderGuard.isPartialResult(books.defaults(2), residual, residual));
        InventoryCrafting grid = grid();
        ItemStack source = configured();
        source.stackSize = 2;
        grid.setInventorySlotContents(0, source);
        grid.setInventorySlotContents(1, books.defaults(1));
        CopyContinuationGuard.remember(grid);
        grid.decrStackSize(0, 1);
        grid.decrStackSize(1, 1);
        FilterBookRecipe clear = new FilterBookRecipe(books, false);
        assertNull(clear.getCraftingResult(grid));
        grid.setInventorySlotContents(0, null);
        grid.setInventorySlotContents(2, source);
        assertNotNull(clear.getCraftingResult(grid));
    }

    @Test
    public void actualItemNbtIdentityFilteringDoesNotAlterPrefilterFluidCost() throws Exception {
        if (delegated("actualItemNbtIdentityFilteringDoesNotAlterPrefilterFluidCost")) return;
        ItemStack oreStack = new ItemStack(ore);
        oreStack.setTagCompound(new NBTTagCompound());
        oreStack.getTagCompound()
            .setString("variant", "one");
        ItemIdentity oreIdentity = new ItemIdentity(oreStack), fillerIdentity = new ItemIdentity(new ItemStack(filler));
        RecipeSnapshot<ItemIdentity> recipe = new RecipeSnapshot<>(
            ItemIdentity.focusSignature(new ItemStack(filter)),
            1,
            0,
            0,
            Arrays.asList(new WeightedComponent<>(oreIdentity, 8), new WeightedComponent<>(fillerIdentity, 87)),
            Collections.emptyList(),
            ItemIdentity::signature);
        RecipeCatalogue.Entry<ItemIdentity> entry = new RecipeCatalogue.Entry<>(
            "pack:actual",
            "actual.json",
            "source",
            recipe);
        FilterDefinition definition = new FilterDefinition(
            entry.id(),
            recipe.focusSignature(),
            recipe.configurationHash(),
            Arrays.asList(oreIdentity.signature(), fillerIdentity.signature()));
        ItemStack filtering = books.writeSingle(
            books.defaults(1),
            FilterBookState.defaults()
                .toggle(definition, oreIdentity.signature()));
        ItemStack variant = oreStack.copy();
        variant.getTagCompound()
            .setString("variant", "two");
        assertFalse(
            books.predicate(filtering, definition)
                .test(new ItemIdentity(variant)));
        assertTrue(
            books.predicate(null, definition)
                .test(new ItemIdentity(variant)));
        ItemStack[] focus = { new ItemStack(filter, 3) };
        FluidStack[] fluid = { new FluidStack(drilling, 100) };
        ProcessingPorts.LpAccess noLp = new ProcessingPorts.LpAccess() {

            public long balance(String owner) {
                throw new AssertionError("Zero LP must not query network");
            }

            public boolean debit(String owner, int amount) {
                throw new AssertionError("Zero LP must not debit");
            }
        };
        MeteorProcessPlanner<ItemIdentity> planner = new MeteorProcessPlanner<>((ticks, power, choose, advance) -> {
            int count = choose.applyAsInt(3);
            advance.accept(count);
            return ProcessingPorts.Schedule.success(count, 100, 3072);
        });
        PreparedProcess<ItemIdentity> process = planner.prepare(
            entry,
            12,
            () -> new BoundNetwork("owner", "orb", false),
            LocalStartResources.arrays(focus, fluid, drilling, () -> {}),
            noLp,
            MachinePower.fromHatches(1, 0, new long[] { 8192 }, new long[] { 2 }),
            books.predicate(filtering, definition),
            items -> !items.containsKey(oreIdentity),
            () -> true).process;
        assertEquals(
            56,
            process.prefilterYield()
                .prefilterTotal());
        assertEquals(
            Long.valueOf(52),
            process.retainedItems()
                .get(fillerIdentity));
        assertEquals(
            1,
            process.retainedItems()
                .size());
        assertEquals(ProcessFailure.NONE, process.start());
        assertEquals(44, fluid[0].amount);
        assertEquals(0, focus[0].stackSize);
    }

    @Test
    public void actualVanillaMergeAtZeroOneTwoSpacesConservesBothResults() throws Exception {
        if (delegated("actualVanillaMergeAtZeroOneTwoSpacesConservesBothResults")) return;
        for (int space = 0; space <= 2; space++) {
            ItemStack expected = configured();
            expected.stackSize = 2;
            ItemStack existing = configured();
            existing.stackSize = 64 - space;
            InventoryBasic inventory = new InventoryBasic("test", true, 1);
            inventory.setInventorySlotContents(0, existing);
            MergeContainer container = new MergeContainer(inventory);
            InventoryCraftResult result = new InventoryCraftResult();
            ItemStack residual = expected.copy();
            result.setInventorySlotContents(0, residual);
            boolean moved = container.accept(residual);
            if (residual.stackSize == 0) result.setInventorySlotContents(0, null);
            int[] delivered = { 0 };
            boolean protectedResult = CopyRemainderGuard.protectPartialResult(expected, residual, result, stack -> {
                delivered[0] += stack.stackSize;
                return true;
            });
            assertEquals(space == 1, protectedResult);
            assertEquals(space > 0, moved);
            assertEquals(space == 0 ? 0 : 2, existing.stackSize - (64 - space) + delivered[0]);
            assertFalse(
                CopyRemainderGuard.protectPartialResult(
                    expected,
                    residual,
                    result,
                    stack -> { throw new AssertionError("A repeated callback must not deliver twice"); }));
        }
        InventoryCraftResult result = new InventoryCraftResult();
        ItemStack expected = configured();
        expected.stackSize = 2;
        result.setInventorySlotContents(0, expected.copy());
        ItemStack cursor = result.decrStackSize(0, 1);
        assertEquals(2, cursor.stackSize); // Native right-click extraction removes the whole recipe result.
        assertFalse(
            CopyRemainderGuard.protectPartialResult(
                expected,
                cursor,
                result,
                stack -> { throw new AssertionError("Ordinary pickup is already delivered"); }));
        ItemStack partial = expected.copy();
        partial.stackSize = 1;
        result.setInventorySlotContents(0, partial);
        assertFalse(CopyRemainderGuard.protectPartialResult(expected, partial, result, stack -> false));
        assertSame(partial, result.getStackInSlot(0));
        assertEquals(1, partial.stackSize); // A rejected receiver is never reported as accepted or cleared.
    }

    private static final class MergeContainer extends Container {

        private MergeContainer(InventoryBasic inventory) {
            addSlotToContainer(new Slot(inventory, 0, 0, 0));
        }

        private boolean accept(ItemStack stack) {
            return mergeItemStack(stack, 0, 1, false);
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static InventoryCrafting grid() {
        return new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, 3, 3);
    }
}
