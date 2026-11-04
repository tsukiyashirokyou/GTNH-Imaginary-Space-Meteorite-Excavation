package com.kyou.imaginaryspacemeteoriteexcavation.output.runtime;

/** A known pre-commit rejection: no new outputs changed ownership, so normal retry remains safe. */
final class OutputUnavailableException extends IllegalStateException {

    OutputUnavailableException(String message) {
        super(message);
    }
}
