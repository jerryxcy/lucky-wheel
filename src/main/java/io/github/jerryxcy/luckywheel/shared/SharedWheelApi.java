package io.github.jerryxcy.luckywheel.shared;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CreateSharedWheelRequest(
        String name,
        Boolean autoRemove,
        List<SharedWheelMemberInput> members
) {
}

record SharedWheelMemberInput(String name, Boolean eligible) {
}

record SharedWheelSnapshot(
        UUID id,
        String name,
        long version,
        boolean autoRemove,
        List<SharedWheelMemberSnapshot> members,
        SharedSpinSnapshot latestSpin,
        Instant expiresAt
) {
}

record SharedWheelMemberSnapshot(String name, boolean eligible) {
}

record SharedSpinSnapshot(
        UUID id,
        Instant occurredAt,
        List<String> eligibleMembers,
        List<String> drawOrder,
        boolean autoRemoveApplied
) {
}
