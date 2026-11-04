package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.EnergyLayout;

/** Native average-voltage/working-amperage budget, capped by actual weighted input power. */
public final class MachinePower {

    private final long voltage;
    private final long amperage;
    private final long eut;

    private MachinePower(long voltage, long amperage) {
        if (voltage <= 0 || amperage <= 0) throw new IllegalArgumentException("No usable input power");
        this.voltage = voltage;
        this.amperage = amperage;
        eut = Math.multiplyExact(voltage, amperage);
    }

    public static MachinePower fromHatches(int normal, int multiAmp, long[] voltages, long[] workingAmperes) {
        if (!EnergyLayout.isValid(normal, multiAmp) || voltages.length != normal + multiAmp
            || workingAmperes.length != voltages.length) {
            throw new IllegalArgumentException("Invalid energy hatch layout");
        }
        long voltageSum = 0;
        long amps = 0;
        long actualPower = 0;
        for (int i = 0; i < voltages.length; i++) {
            if (voltages[i] <= 0 || workingAmperes[i] <= 0) throw new IllegalArgumentException("Invalid hatch power");
            voltageSum = Math.addExact(voltageSum, voltages[i]);
            amps = Math.addExact(amps, workingAmperes[i]);
            actualPower = Math.addExact(actualPower, Math.multiplyExact(voltages[i], workingAmperes[i]));
        }
        long average = voltageSum / voltages.length;
        // Native setupProcessingLogic uses one amp for exactly one ordinary hatch.
        if (normal == 1 && multiAmp == 0) amps = 1;
        amps = Math.min(amps, actualPower / average);
        return new MachinePower(average, amps);
    }

    public long voltage() {
        return voltage;
    }

    public long amperage() {
        return amperage;
    }

    public long eut() {
        return eut;
    }
}
