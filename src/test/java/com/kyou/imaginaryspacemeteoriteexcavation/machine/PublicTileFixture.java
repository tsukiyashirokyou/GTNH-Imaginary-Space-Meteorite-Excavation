package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import java.lang.reflect.Proxy;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Test-only public interface proxy. No World, private access or production substitute classes. */
public final class PublicTileFixture {

    public boolean allowed = true;
    public long tick = 1, energy, voltage = 8192;
    public int dirty;
    public ShutDownReason shutdown = ShutDownReasonRegistry.NONE;

    public IGregTechTileEntity bind(MetaTileEntity meta) {
        IGregTechTileEntity tile = (IGregTechTileEntity) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { IGregTechTileEntity.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getMetaTileEntity":
                        return meta;
                    case "isServerSide":
                        return true;
                    case "isAllowedToWork":
                        return allowed;
                    case "enableWorking":
                        allowed = true;
                        return null;
                    case "disableWorking":
                        allowed = false;
                        return null;
                    case "getTimer":
                        return tick;
                    case "getStoredEU":
                        return energy;
                    case "getInputVoltage":
                        return voltage;
                    case "getFrontFacing":
                        return net.minecraftforge.common.util.ForgeDirection.NORTH;
                    case "decreaseStoredEnergyUnits":
                        long amount = (long) args[0];
                        if (amount < 0 || amount > energy) return false;
                        energy -= amount;
                        return true;
                    case "markDirty":
                        dirty++;
                        return null;
                    case "getLastShutDownReason":
                        return shutdown;
                    case "setShutDownReason":
                        shutdown = (ShutDownReason) args[0];
                        return null;
                    case "isMuffled":
                        return true;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "Headless public tile fixture";
                    default:
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) return false;
                        if (type == int.class) return 0;
                        if (type == long.class) return 0L;
                        if (type == byte.class) return (byte) 0;
                        if (type == short.class) return (short) 0;
                        if (type == float.class) return 0F;
                        if (type == double.class) return 0D;
                        return null;
                }
            });
        meta.setBaseMetaTileEntity(tile);
        return tile;
    }
}
