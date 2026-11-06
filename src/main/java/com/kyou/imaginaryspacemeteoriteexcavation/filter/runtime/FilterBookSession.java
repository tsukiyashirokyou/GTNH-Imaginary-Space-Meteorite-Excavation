package com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterBookState;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterDefinition;

/** Server-owned edit session: anchored inventory slot, full item value and revision, then copy-on-write. */
public final class FilterBookSession {

    private final FilterBookStore store;
    private final Supplier<ItemStack> get;
    private final Consumer<ItemStack> set;
    private ItemStack reference;
    private ItemStack expected;
    private FilterBookState state;
    private boolean valid;

    public FilterBookSession(FilterBookStore store, Supplier<ItemStack> get, Consumer<ItemStack> set) {
        this.store = store;
        this.get = get;
        this.set = set;
        reference = get.get();
        if (reference != null) expected = reference.copy();
        try {
            state = store.read(reference);
            valid = reference.stackSize == 1;
        } catch (IllegalArgumentException invalid) {
            valid = false;
        }
    }

    public boolean unchanged() {
        ItemStack current = get.get();
        // Vanilla's right-click packet replaces the held stack with an equal copy after opening the GUI.
        if (!valid || current == null || current.stackSize != 1 || !ItemStack.areItemStacksEqual(current, expected))
            return false;
        reference = current;
        return true;
    }

    public FilterBookState state() {
        return state;
    }

    public boolean edit(long revision, FilterDefinition definition, int action, String item) {
        if (!unchanged() || revision != state.revision()) return false;
        FilterBookState changed;
        try {
            if (action == 0) changed = state.toggle(definition, item);
            else if (action == 1) changed = state.changeMode(definition);
            else if (action == 2) changed = state.reset(definition);
            else return false;
            ItemStack next = store.writeSingle(reference, changed);
            set.accept(next);
            reference = next;
            expected = next.copy();
            state = changed;
            return true;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return false;
        }
    }
}
