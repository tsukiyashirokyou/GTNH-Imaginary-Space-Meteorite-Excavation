package com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime;

import net.minecraft.server.MinecraftServer;

import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;

import WayofTime.alchemicalWizardry.api.soulNetwork.SoulNetworkHandler;
import cpw.mods.fml.common.FMLCommonHandler;

/** Only called from server-side prepare/start; never from item constructors, GUI rendering or asynchronous workers. */
public final class BloodMagicLpAccess implements ProcessingPorts.LpAccess {

    @Override
    public long balance(String owner) {
        requireOwner(owner);
        return available() ? SoulNetworkHandler.getCurrentEssence(owner) : -1;
    }

    @Override
    public boolean debit(String owner, int amount) {
        requireOwner(owner);
        if (amount < 0) throw new IllegalArgumentException("Negative LP debit");
        if (amount == 0) return true;
        return available() && SoulNetworkHandler.syphonFromNetwork(owner, amount) == amount;
    }

    private static boolean available() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        return server != null && server.worldServers != null
            && server.worldServers.length > 0
            && server.worldServers[0] != null
            && !server.worldServers[0].isRemote;
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.isEmpty()) throw new IllegalArgumentException("Unbound Soul Network");
    }
}
