package io.github.jerryxcy.luckywheel.shared;

import java.util.UUID;

class SharedWheelNotFoundException extends RuntimeException {

    SharedWheelNotFoundException(UUID wheelId) {
        super("Shared Wheel " + wheelId + " was not found.");
    }
}
