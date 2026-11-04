package com.kyou.imaginaryspacemeteoriteexcavation.inventory;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/** Stores an identity separately from its count; never exposes its mutable identity. */
public final class BoundedItemCount<T> {

    private final UnaryOperator<T> copy;
    private final BiPredicate<T, T> same;
    private T identity;
    private int count;

    public BoundedItemCount(UnaryOperator<T> copy, BiPredicate<T, T> same) {
        this.copy = Objects.requireNonNull(copy);
        this.same = Objects.requireNonNull(same);
    }

    public int count() {
        return count;
    }

    public T identity() {
        return identity == null ? null : copy.apply(identity);
    }

    public int insert(T offeredIdentity, int offered) {
        if (offeredIdentity == null || offered <= 0 || (identity != null && !same.test(identity, offeredIdentity))) {
            return 0;
        }
        int accepted = (int) Math.min((long) offered, (long) Integer.MAX_VALUE - count);
        if (accepted > 0) {
            if (identity == null) identity = copy.apply(offeredIdentity);
            count += accepted;
        }
        return accepted;
    }

    public int extract(int requested) {
        int extracted = Math.min(count, Math.max(0, requested));
        count -= extracted;
        if (count == 0) identity = null;
        return extracted;
    }

    public void restore(T savedIdentity, int savedCount) {
        if (savedCount < 0 || (savedCount > 0 && savedIdentity == null)) {
            throw new IllegalArgumentException("Invalid saved item count");
        }
        identity = savedCount == 0 ? null : copy.apply(savedIdentity);
        count = savedCount;
    }
}
