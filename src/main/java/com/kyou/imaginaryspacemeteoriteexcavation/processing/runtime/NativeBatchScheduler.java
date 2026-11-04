package com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.kyou.imaginaryspacemeteoriteexcavation.processing.MachinePower;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessFailure;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingTime;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

/** Calls target-version native scheduling. No recipe is added to a GT recipe map. */
public final class NativeBatchScheduler implements ProcessingPorts.Scheduler {

    // MTEMultiBlockBase.getMaxBatchSize(); the independent 128-tick time ceiling is inside ParallelHelper.
    private static final int NATIVE_BATCH_SIZE = 128;
    private final Map<Integer, GTRecipe> recipes = new LinkedHashMap<>();

    @Override
    public ProcessingPorts.Schedule calculate(int baseTicks, MachinePower power, IntUnaryOperator admissibleIncrement,
        IntConsumer plannedIncrement) {
        if (baseTicks <= 0) throw new IllegalArgumentException("Invalid duration");
        if (power.eut() < ProcessingTime.BASE_EUT)
            return ProcessingPorts.Schedule.failure(ProcessFailure.INSUFFICIENT_POWER);
        OverclockCalculator calculator = new OverclockCalculator().setRecipeEUt(ProcessingTime.BASE_EUT)
            .setDuration(baseTicks)
            .setEUt(power.voltage())
            .setAmperage(power.amperage())
            .setMaxTierSkips(1)
            .setAmperageOC(true)
            .enablePerfectOC();
        ParallelHelper helper = new ParallelHelper().setRecipe(recipe(baseTicks))
            .setItemInputs(new ItemStack[0])
            .setFluidInputs(new FluidStack[0])
            .setAvailableEUt(power.eut())
            .setCalculator(calculator)
            .setMaxParallel(1)
            .enableBatchMode(NATIVE_BATCH_SIZE)
            .setConsumption(false)
            .setOutputCalculation(false)
            .setMaxParallelCalculator((recipe, maximum, fluids, items) -> admissibleIncrement.applyAsInt(maximum))
            .setInputConsumer((recipe, increment, fluids, items) -> plannedIncrement.accept(increment))
            .build();
        if (!helper.getResult()
            .wasSuccessful()) return ProcessingPorts.Schedule.failure(ProcessFailure.INSUFFICIENT_POWER);
        long eut = calculator.getConsumption();
        double duration = calculator.getDuration() * helper.getDurationMultiplierDouble();
        if (eut <= 0 || eut > power.eut() || duration <= 0 || duration >= Integer.MAX_VALUE) {
            return ProcessingPorts.Schedule.failure(ProcessFailure.OVERFLOW);
        }
        return ProcessingPorts.Schedule.success(helper.getCurrentParallel(), (int) duration, eut);
    }

    private synchronized GTRecipe recipe(int duration) {
        GTRecipe recipe = recipes.get(duration);
        if (recipe == null) {
            recipe = new GTRecipe(
                false,
                new ItemStack[0],
                new ItemStack[0],
                null,
                null,
                null,
                null,
                null,
                new FluidStack[0],
                new FluidStack[0],
                duration,
                ProcessingTime.BASE_EUT,
                0);
            // These owned scheduling descriptors have no outputs. Null also avoids native machine-output-limit access.
            recipe.mOutputs = null;
            recipe.mFluidOutputs = null;
            if (recipes.size() >= 32) recipes.remove(
                recipes.keySet()
                    .iterator()
                    .next());
            recipes.put(duration, recipe);
        }
        return recipe;
    }
}
