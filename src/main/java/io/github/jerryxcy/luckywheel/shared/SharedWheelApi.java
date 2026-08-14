package io.github.jerryxcy.luckywheel.shared;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record CreateSharedWheelRequest(
        String name,
        Boolean autoRemove,
        List<SharedWheelMemberInput> members
) {
}

final class UpdateSharedWheelRequest {

    private final Long expectedVersion;
    private final String name;
    private final Boolean autoRemove;
    private final List<SharedWheelMemberInput> members;
    private final Map<String, Object> unexpectedFields = new LinkedHashMap<>();

    @JsonCreator
    UpdateSharedWheelRequest(
            @JsonProperty("expectedVersion") Long expectedVersion,
            @JsonProperty("name") String name,
            @JsonProperty("autoRemove") Boolean autoRemove,
            @JsonProperty("members") List<SharedWheelMemberInput> members
    ) {
        this.expectedVersion = expectedVersion;
        this.name = name;
        this.autoRemove = autoRemove;
        this.members = members;
    }

    @JsonAnySetter
    void rejectUnexpectedField(String field, Object value) {
        unexpectedFields.put(field, value);
    }

    Long expectedVersion() {
        return expectedVersion;
    }

    String name() {
        return name;
    }

    Boolean autoRemove() {
        return autoRemove;
    }

    List<SharedWheelMemberInput> members() {
        return members;
    }

    Map<String, Object> unexpectedFields() {
        return Map.copyOf(unexpectedFields);
    }
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
