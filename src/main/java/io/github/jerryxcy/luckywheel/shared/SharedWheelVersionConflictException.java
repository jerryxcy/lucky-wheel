package io.github.jerryxcy.luckywheel.shared;

import java.util.UUID;

class SharedWheelVersionConflictException extends RuntimeException {

    private final long currentVersion;

    SharedWheelVersionConflictException(UUID wheelId, long currentVersion) {
        super("Shared Wheel " + wheelId + " has version " + currentVersion + ".");
        this.currentVersion = currentVersion;
    }

    SharedWheelVersionConflictException(UUID wheelId, long currentVersion, Throwable cause) {
        super("Shared Wheel " + wheelId + " has version " + currentVersion + ".", cause);
        this.currentVersion = currentVersion;
    }

    long currentVersion() {
        return currentVersion;
    }
}
