package com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime;

import java.util.function.Predicate;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterBookCodec;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterBookState;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterDefinition;
import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterPage;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;

/** Count-aware owned-item codec; all mutations return a new item and independently copied NBT. */
public final class FilterBookStore {

    private final Item book;

    public FilterBookStore(Item book) {
        if (book == null) throw new IllegalArgumentException("Missing book item");
        this.book = book;
    }

    public boolean isBook(ItemStack stack) {
        return stack != null && stack.getItem() == book
            && stack.getItemDamage() == 0
            && stack.stackSize > 0
            && stack.stackSize <= book.getItemStackLimit();
    }

    public FilterBookState read(ItemStack stack) {
        if (!isBook(stack)) throw new IllegalArgumentException("Not a legal filtering book stack");
        return FilterBookCodec.read(stack.getTagCompound());
    }

    public ItemStack defaults(int count) {
        if (count < 1 || count > book.getItemStackLimit()) throw new IllegalArgumentException("Invalid book count");
        return new ItemStack(book, count, 0);
    }

    public ItemStack writeSingle(ItemStack original, FilterBookState state) {
        if (!isBook(original) || original.stackSize != 1)
            throw new IllegalArgumentException("Split books before editing");
        ItemStack changed = original.copy();
        changed.setTagCompound(FilterBookCodec.write(state, original.getTagCompound()));
        return changed;
    }

    /** Snapshot once per recipe preparation. Malformed/stale data rejects preparation, never becomes an empty list. */
    public Predicate<ItemIdentity> predicate(ItemStack stack, FilterDefinition definition) {
        if (stack == null) return item -> true;
        FilterPage page = read(stack).page(definition);
        return item -> definition.items()
            .contains(item.signature()) && page.keeps(item.signature());
    }
}
