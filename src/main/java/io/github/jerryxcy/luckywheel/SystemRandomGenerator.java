package io.github.jerryxcy.luckywheel;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Production random source: the real, unseeded randomness a spin draws from. */
@Component
class SystemRandomGenerator implements RandomGenerator {

    @Override
    public int nextInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }
}
