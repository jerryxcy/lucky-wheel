package io.github.jerryxcy.luckywheel;

import java.util.List;

/** Request body for {@code POST /api/spins}: the eligible members and how many to draw. */
public record SpinRequest(List<String> members, int count) {
}
