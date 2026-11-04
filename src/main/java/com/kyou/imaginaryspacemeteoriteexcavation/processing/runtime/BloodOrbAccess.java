package com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.BoundNetwork;

import WayofTime.alchemicalWizardry.api.items.interfaces.IBloodOrb;
import cpw.mods.fml.common.registry.GameRegistry;

public final class BloodOrbAccess {

    private BloodOrbAccess() {}

    public static BoundNetwork read(ItemStack orb) {
        if (orb == null || orb.stackSize != 1 || !(orb.getItem() instanceof IBloodOrb) || !orb.hasTagCompound())
            return null;
        String owner = orb.getTagCompound()
            .getString("ownerName");
        if (owner.isEmpty()) return null;
        Item armok = GameRegistry.findItem("Avaritia", "Orb_Armok");
        return new BoundNetwork(owner, new ItemIdentity(orb).signature(), armok != null && orb.getItem() == armok);
    }
}
