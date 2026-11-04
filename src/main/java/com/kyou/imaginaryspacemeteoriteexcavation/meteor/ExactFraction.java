package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import java.math.BigInteger;
import java.util.Objects;

/** An immutable, reduced rational. No floating point is used for yield arithmetic. */
public final class ExactFraction {

    public static final ExactFraction ZERO = of(0, 1);
    public static final ExactFraction ONE = of(1, 1);

    private final BigInteger numerator;
    private final BigInteger denominator;

    private ExactFraction(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() <= 0) throw new IllegalArgumentException("Denominator must be positive");
        BigInteger gcd = numerator.gcd(denominator);
        this.numerator = numerator.divide(gcd);
        this.denominator = denominator.divide(gcd);
    }

    public static ExactFraction of(long numerator, long denominator) {
        return new ExactFraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public ExactFraction add(ExactFraction other) {
        BigInteger gcd = denominator.gcd(other.denominator);
        BigInteger left = other.denominator.divide(gcd);
        BigInteger right = denominator.divide(gcd);
        return new ExactFraction(
            numerator.multiply(left)
                .add(other.numerator.multiply(right)),
            denominator.multiply(left));
    }

    public ExactFraction multiply(ExactFraction other) {
        return new ExactFraction(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }

    public ExactFraction multiply(long multiplier) {
        return new ExactFraction(numerator.multiply(BigInteger.valueOf(multiplier)), denominator);
    }

    public ExactFraction complement() {
        return new ExactFraction(denominator.subtract(numerator), denominator);
    }

    public int signum() {
        return numerator.signum();
    }

    public long floorLongExact() {
        BigInteger[] parts = numerator.divideAndRemainder(denominator);
        if (numerator.signum() < 0 && parts[1].signum() != 0) parts[0] = parts[0].subtract(BigInteger.ONE);
        return parts[0].longValueExact();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ExactFraction)) return false;
        ExactFraction other = (ExactFraction) object;
        return numerator.equals(other.numerator) && denominator.equals(other.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
