package io.github.jerryxcy.luckywheel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The spin itself: a pure function of (members, count, random source).
 * Shuffles the members (Fisher-Yates) and takes the first {@code count}.
 */
public final class Spin {

    private Spin() {
    }

    public static List<String> draw(List<String> members, int count, RandomGenerator random) {
        List<String> shuffled = new ArrayList<>(members);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(shuffled, i, j);
        }
        return List.copyOf(shuffled.subList(0, count));
    }
}
