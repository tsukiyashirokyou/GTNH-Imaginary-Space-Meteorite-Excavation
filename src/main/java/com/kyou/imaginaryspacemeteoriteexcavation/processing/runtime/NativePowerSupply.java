package com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime;

import java.util.List;

import com.kyou.imaginaryspacemeteoriteexcavation.processing.MachinePower;

import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchEnergyDebug;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;

public final class NativePowerSupply {

    private NativePowerSupply() {}

    public static MachinePower capture(List<MTEHatchEnergy> normal, List<? extends MTEHatch> multiAmp) {
        int count = normal.size() + multiAmp.size();
        long[] voltages = new long[count];
        long[] amps = new long[count];
        boolean debug = false;
        for (int i = 0; i < count; i++) {
            MTEHatch hatch = i < normal.size() ? normal.get(i) : multiAmp.get(i - normal.size());
            if (!hatch.isValid()) throw new IllegalArgumentException("Invalid energy hatch");
            if (i < normal.size() && hatch instanceof MTEHatchEnergyDebug) debug = true;
            if (i >= normal.size()
                && (!(hatch instanceof MTEHatchEnergyMulti) || ((MTEHatchEnergyMulti) hatch).getHatchType() != 1)) {
                throw new IllegalArgumentException("Not a native multi-amp energy input");
            }
            voltages[i] = hatch.getBaseMetaTileEntity()
                .getInputVoltage();
            amps[i] = hatch.maxWorkingAmperesIn();
        }
        return MachinePower.fromHatches(normal.size(), multiAmp.size(), voltages, amps, debug);
    }
}
