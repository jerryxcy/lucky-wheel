package io.github.jerryxcy.luckywheel.shared;

import java.util.Map;

class SharedWheelValidationException extends RuntimeException {

    private final Map<String, String> errors;

    SharedWheelValidationException(Map<String, String> errors) {
        super("Shared Wheel validation failed.");
        this.errors = Map.copyOf(errors);
    }

    Map<String, String> errors() {
        return errors;
    }
}
