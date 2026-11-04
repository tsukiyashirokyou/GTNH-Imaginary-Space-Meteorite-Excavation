package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Detached, immutable resolved recipe content. The versioned hash is independent of registry order. */
public final class RecipeSnapshot<K> {

    private final String focus;
    private final int radius;
    private final int lpCost;
    private final String hash;
    private final ExpectedYield<K> yield;

    public RecipeSnapshot(String focus, int radius, int lpCost, float fillerChance, List<WeightedComponent<K>> ores,
        List<WeightedComponent<K>> filler, Function<K, String> signature) {
        if (focus == null || focus.isEmpty() || lpCost < 0) throw new IllegalArgumentException("Invalid recipe header");
        this.focus = focus;
        this.radius = radius;
        this.lpCost = lpCost;
        List<WeightedComponent<K>> oreCopy = Collections.unmodifiableList(new ArrayList<>(ores));
        List<WeightedComponent<K>> fillerCopy = Collections.unmodifiableList(new ArrayList<>(filler));
        yield = new ExpectedYield<>(radius, fillerChance, oreCopy, fillerCopy);
        MessageDigest digest = sha256();
        token(digest, "isme-meteor-model-v1");
        token(digest, focus);
        token(digest, Integer.toString(radius));
        token(digest, Integer.toString(lpCost));
        token(digest, Integer.toString(Float.floatToIntBits(fillerChance)));
        hashComponents(digest, oreCopy, signature);
        hashComponents(digest, fillerCopy, signature);
        hash = hex(digest.digest());
    }

    private static <K> void hashComponents(MessageDigest digest, List<WeightedComponent<K>> values,
        Function<K, String> signature) {
        token(digest, Integer.toString(values.size()));
        for (WeightedComponent<K> value : values) {
            token(digest, signature.apply(value.item()));
            token(digest, Integer.toString(value.weight()));
        }
    }

    private static void token(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(
            ByteBuffer.allocate(4)
                .putInt(bytes.length)
                .array());
        digest.update(bytes);
    }

    public static String sha256(byte[] bytes) {
        return hex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            value.append(Character.forDigit((b >>> 4) & 15, 16));
            value.append(Character.forDigit(b & 15, 16));
        }
        return value.toString();
    }

    public String focusSignature() {
        return focus;
    }

    public int radius() {
        return radius;
    }

    public int lpCost() {
        return lpCost;
    }

    public String configurationHash() {
        return hash;
    }

    public ExpectedYield<K> yield() {
        return yield;
    }
}
