package com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime;

import java.util.Optional;

import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;

public final class MeteorRecipeResolver {

    private MeteorRecipeResolver() {}

    public static Optional<RecipeCatalogue.Entry<ItemIdentity>> first(RecipeCatalogue<ItemIdentity> catalogue,
        Iterable<ItemStack> inputOrder) {
        for (ItemStack stack : inputOrder) {
            if (stack == null || stack.stackSize <= 0) continue;
            Optional<RecipeCatalogue.Entry<ItemIdentity>> match = catalogue
                .forFocus(ItemIdentity.focusSignature(stack));
            if (match.isPresent()) return match;
        }
        return Optional.empty();
    }
}
