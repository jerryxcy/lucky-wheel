package io.github.jerryxcy.luckywheel;

/**
 * Seam for randomness: everything that decides a spin's outcome goes through
 * this interface so tests can supply a seeded, reproducible source.
 */
@FunctionalInterface
public interface RandomGenerator {

    /**
     * Returns a random int in {@code [0, bound)}, same contract as
     * {@link java.util.Random#nextInt(int)}.
     */
    int nextInt(int bound);
}
