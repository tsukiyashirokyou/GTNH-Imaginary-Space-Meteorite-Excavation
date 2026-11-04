package com.kyou.imaginaryspacemeteoriteexcavation.inventory;

import java.util.Random;

import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.EnergyLayout;

/** Standalone Java 8 tests of production logic, without bootstrapping Minecraft. */
public final class InventoryFoundationTest {

    private static int checks;

    public InventoryFoundationTest() {}

    @Test
    public void countBoundaries() {
        boundaries();
    }

    @Test
    public void copiesAndIdentityAreIsolated() {
        identityIsolation();
    }

    @Test
    public void allTransfersConserveItems() {
        conservation();
    }

    @Test
    public void hatchesFollowApprovedLayout() {
        energyLayouts();
    }

    public static void main(String[] args) {
        boundaries();
        identityIsolation();
        conservation();
        energyLayouts();
        System.out.println("PASS: " + checks + " inventory/energy assertions (production modules, no game runtime)");
    }

    private static BoundedItemCount<Token> counter() {
        return new BoundedItemCount<>(Token::copy, Token::same);
    }

    private static void boundaries() {
        int[] boundaries = { 0, 1, 63, 64, 65, 98, 99, 127, 128, 32767, 32768, Integer.MAX_VALUE - 1,
            Integer.MAX_VALUE };
        for (int initial : boundaries) {
            BoundedItemCount<Token> store = counter();
            store.restore(new Token("pylon", 0, "named"), initial);
            int added = store.insert(new Token("pylon", 0, "named"), 64);
            equal(Math.min(64L, (long) Integer.MAX_VALUE - initial), added);
            equal((long) initial + added, store.count());
            int ordinaryStack = Math.min(64, store.count());
            equal(ordinaryStack, store.extract(ordinaryStack));
            equal((long) initial + added - ordinaryStack, store.count());
            equal(0, store.insert(new Token("pylon", 0, "named"), -1));
            equal(0, store.extract(Integer.MIN_VALUE));
            BoundedItemCount<Token> restored = counter();
            restored.restore(store.identity(), store.count());
            equal(store.count(), restored.count());
            equal(store.count(), restored.extract(Integer.MAX_VALUE));
            equal(0, restored.count());
            require(restored.identity() == null);
        }
        BoundedItemCount<Token> store = counter();
        equal(0, store.insert(null, 64));
        store.restore(new Token("pylon", 0, ""), 10);
        expectInvalid(() -> store.restore(null, 1));
        expectInvalid(() -> store.restore(new Token("pylon", 0, ""), -1));
        equal(10, store.count());
    }

    private static void identityIsolation() {
        BoundedItemCount<Token> store = counter();
        Token source = new Token("pylon", 0, "original");
        equal(64, store.insert(source, 64));
        source.tag.append(" changed externally");
        require(
            store.identity().tag.toString()
                .equals("original"));
        store.identity().tag.append(" changed via getter");
        require(
            store.identity().tag.toString()
                .equals("original"));
        equal(0, store.insert(new Token("pylon", 0, "different NBT"), 64));
        equal(0, store.insert(new Token("pylon", 1, "original"), 64));
        equal(0, store.insert(new Token("other item", 0, "original"), 64));
        equal(64, store.count());
        equal(64, store.extract(64));
        equal(1, store.insert(new Token("other item", 1, "new"), 1));
        require(store.identity().item.equals("other item"));
        Token saved = store.identity();
        store.restore(saved, Integer.MAX_VALUE);
        saved.tag.append(" changed after restore");
        require(
            store.identity().tag.toString()
                .equals("new"));
        equal(0, store.insert(new Token("other item", 1, "new"), 1));
    }

    private static void conservation() {
        BoundedItemCount<Token> store = counter();
        Token identity = new Token("pylon", 0, "state");
        Random random = new Random(1710L);
        long expected = 0;
        long inserted = 0;
        long extracted = 0;
        for (int i = 0; i < 20000; i++) {
            int requested = random.nextInt();
            if (random.nextBoolean()) {
                long accepted = requested <= 0 ? 0 : Math.min((long) requested, Integer.MAX_VALUE - expected);
                equal(accepted, store.insert(identity, requested));
                expected += accepted;
                inserted += accepted;
            } else {
                long taken = requested <= 0 ? 0 : Math.min(expected, requested);
                equal(taken, store.extract(requested));
                expected -= taken;
                extracted += taken;
            }
            equal(expected, store.count());
            equal(inserted - extracted, store.count());
            require((store.count() == 0) == (store.identity() == null));
        }
    }

    private static void energyLayouts() {
        for (int normal = -1; normal <= 8; normal++) {
            for (int multi = -1; multi <= 3; multi++) {
                boolean expected = (normal >= 1 && multi == 0) || (normal == 0 && multi == 1);
                require(EnergyLayout.isValid(normal, multi) == expected);
            }
        }
    }

    private static void expectInvalid(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected);
    }

    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
        checks++;
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Inventory invariant failed");
        checks++;
    }

    private static final class Token {

        private final String item;
        private final int meta;
        private final StringBuilder tag;

        private Token(String item, int meta, String tag) {
            this.item = item;
            this.meta = meta;
            this.tag = new StringBuilder(tag);
        }

        private Token copy() {
            return new Token(item, meta, tag.toString());
        }

        private boolean same(Token other) {
            return item.equals(other.item) && meta == other.meta
                && tag.toString()
                    .equals(other.tag.toString());
        }
    }
}
