package io.github.jerryxcy.luckywheel;

/** Body returned for every 400: a single human-readable message. */
public record ErrorResponse(String message) {
}
