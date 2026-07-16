package io.github.jerryxcy.luckywheel;

import java.util.List;

/** Response body for {@code POST /api/spins}: the picked members, in the order drawn. */
public record SpinResponse(List<String> drawOrder) {
}
