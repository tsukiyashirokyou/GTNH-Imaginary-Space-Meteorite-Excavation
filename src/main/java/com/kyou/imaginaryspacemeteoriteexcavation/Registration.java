package com.kyou.imaginaryspacemeteoriteexcavation;

import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.CopyRemainderGuard;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.DefaultBookRecipe;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting.FilterBookRecipe;
import com.kyou.imaginaryspacemeteoriteexcavation.item.FilteringBook;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.MeteoriteExcavation;

import WayofTime.alchemicalWizardry.ModBlocks;
import appeng.api.AEApi;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;

public final class Registration {

    public static FilteringBook filteringBook;
    private static ItemStack controller;

    private Registration() {}

    public static void registerItems() {
        filteringBook = new FilteringBook();
        GameRegistry.registerItem(filteringBook, "filtering_book");
    }

    public static void registerController() {
        int id = Config.controllerId;
        if (id >= GregTechAPI.METATILEENTITIES.length || GregTechAPI.METATILEENTITIES[id] != null) {
            throw new IllegalStateException(
                "ISME controllerId " + id + " is occupied/out of range. Choose an unused ID in the ISME config.");
        }
        controller = new MeteoriteExcavation(
            id,
            ImaginarySpaceMeteoriteExcavation.MODID + ".meteorite_excavation",
            "Imaginary Space Meteorite Excavation").getStackForm(1L);
    }

    public static void registerControllerRecipe() {
        ItemStack io = AEApi.instance()
            .definitions()
            .blocks()
            .spatialIOPort()
            .maybeStack(1)
            .orNull();
        ItemStack component = AEApi.instance()
            .definitions()
            .materials()
            .cell128SpatialPart()
            .maybeStack(1)
            .orNull();
        ItemStack plane = AEApi.instance()
            .definitions()
            .parts()
            .identityAnnihilationPlane()
            .maybeStack(1)
            .orNull();
        if (io == null || component == null || plane == null) {
            throw new IllegalStateException("Required AE2 spatial IO, 128^3 component or precision plane is disabled");
        }
        ItemStack pipe = GTModHandler.getIC2Item("miningPipe", 64);
        if (pipe == null || Materials.Void.getMolten(9216L) == null) {
            throw new IllegalStateException("Required mining pipe or molten void metal is unavailable");
        }
        GTValues.RA.stdBuilder()
            .itemInputs(
                io,
                new ItemStack(ModBlocks.blockMasterStone),
                component,
                new ItemStack(ModBlocks.ritualStone, 64),
                ItemList.OreDrill2.get(1L),
                new ItemStack(ModBlocks.ritualStone, 64),
                pipe.copy(),
                plane,
                pipe.copy())
            .fluidInputs(Materials.Void.getMolten(9216L))
            .itemOutputs(controller.copy())
            .duration(600 * 20)
            .eut(TierEU.RECIPE_EV)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    public static void registerBookRecipes() {
        // EnderIO 2.10.51 ItemBigItemFilter.init(); metadata 0 is the non-advanced 24-entry filter.
        ItemStack filter = GameRegistry.findItemStack("EnderIO", "itemBigFilterUpgrade", 1);
        if (filter == null) throw new IllegalStateException("GTNH 2.9.0 Big Item Filter is unavailable");
        filter.setItemDamage(0);
        GameRegistry.addRecipe(
            new DefaultBookRecipe(
                filter,
                ItemList.Tool_DataOrb.get(1L),
                filteringBook.books()
                    .defaults(1)));
        GameRegistry.addRecipe(new FilterBookRecipe(filteringBook.books(), true));
        GameRegistry.addRecipe(new FilterBookRecipe(filteringBook.books(), false));
        FMLCommonHandler.instance()
            .bus()
            .register(new CopyRemainderGuard(filteringBook.books()));
    }
}
