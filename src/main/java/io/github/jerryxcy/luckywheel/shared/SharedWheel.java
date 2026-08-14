package io.github.jerryxcy.luckywheel.shared;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "shared_wheel")
class SharedWheel {

    enum Replacement {
        UNCHANGED,
        ROOT_CHANGED,
        MEMBERS_ONLY
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "auto_remove", nullable = false)
    private boolean autoRemove;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "wheel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("rosterPosition ASC")
    private List<SharedWheelMember> members = new ArrayList<>();

    protected SharedWheel() {
    }

    static SharedWheel create(CreateSharedWheelRequest request) {
        if (request == null) {
            throw requiredRequest();
        }
        ValidatedWheelState validated = validateState(
                request.name(),
                request.autoRemove(),
                request.members()
        );
        SharedWheel wheel = new SharedWheel();
        wheel.id = UUID.randomUUID();
        wheel.name = validated.name();
        wheel.autoRemove = validated.autoRemove();
        wheel.replaceMembers(validated.members());
        return wheel;
    }

    private static SharedWheelValidationException requiredRequest() {
        return new SharedWheelValidationException(Map.of("request", "Request body is required."));
    }

    private static ValidatedWheelState validateState(
            String requestedName,
            Boolean requestedAutoRemove,
            List<SharedWheelMemberInput> requestedMembers
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        String normalizedName = normalizeName(requestedName);
        validateName("name", normalizedName, "Wheel name", errors);

        if (requestedAutoRemove == null) {
            errors.put("autoRemove", "Auto-remove is required.");
        }

        List<SharedWheelMemberInput> normalizedMembers = new ArrayList<>();
        if (requestedMembers == null) {
            errors.put("members", "Members are required; use an empty array for an empty roster.");
        } else {
            if (requestedMembers.size() > 100) {
                errors.put("members", "A roster may contain at most 100 members.");
            }
            Set<String> names = new HashSet<>();
            for (int index = 0; index < requestedMembers.size(); index++) {
                SharedWheelMemberInput member = requestedMembers.get(index);
                String field = "members[" + index + "]";
                if (member == null) {
                    errors.put(field, "Member is required.");
                    continue;
                }

                String normalizedMemberName = normalizeName(member.name());
                validateName(field + ".name", normalizedMemberName, "Member name", errors);
                if (normalizedMemberName != null
                        && !normalizedMemberName.isEmpty()
                        && normalizedMemberName.length() <= 80
                        && !names.add(normalizedMemberName)) {
                    errors.put(field + ".name", "Member names must be unique within a Shared Wheel.");
                }
                if (member.eligible() == null) {
                    errors.put(field + ".eligible", "Eligibility is required.");
                }
                normalizedMembers.add(new SharedWheelMemberInput(normalizedMemberName, member.eligible()));
            }
        }

        if (!errors.isEmpty()) {
            throw new SharedWheelValidationException(errors);
        }
        return new ValidatedWheelState(
                normalizedName,
                requestedAutoRemove,
                List.copyOf(normalizedMembers)
        );
    }

    private static String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private static void validateName(
            String field,
            String name,
            String label,
            Map<String, String> errors
    ) {
        if (name == null || name.isEmpty()) {
            errors.put(field, label + " is required.");
        } else if (name.length() > 80) {
            errors.put(field, label + " must be at most 80 characters.");
        }
    }

    private record ValidatedWheelState(
            String name,
            boolean autoRemove,
            List<SharedWheelMemberInput> members
    ) {
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    long version() {
        return version;
    }

    boolean autoRemove() {
        return autoRemove;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    List<SharedWheelMember> members() {
        return Collections.unmodifiableList(members);
    }

    Replacement replace(UpdateSharedWheelRequest request) {
        if (request == null) {
            throw requiredRequest();
        }
        validateExpectedVersion(request.expectedVersion());
        if (request.expectedVersion() != version) {
            throw new SharedWheelVersionConflictException(id, version);
        }
        if (!request.unexpectedFields().isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            request.unexpectedFields().keySet()
                    .forEach(field -> errors.put(field, "Field is not accepted by Shared Wheel updates."));
            throw new SharedWheelValidationException(errors);
        }

        ValidatedWheelState validated = validateState(
                request.name(),
                request.autoRemove(),
                request.members()
        );
        boolean rootChanged = !name.equals(validated.name())
                || autoRemove != validated.autoRemove();
        boolean membersChanged = !membersMatch(validated.members());
        if (!rootChanged && !membersChanged) {
            return Replacement.UNCHANGED;
        }

        name = validated.name();
        autoRemove = validated.autoRemove();
        replaceMembers(validated.members());
        return rootChanged ? Replacement.ROOT_CHANGED : Replacement.MEMBERS_ONLY;
    }

    private void validateExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw new SharedWheelValidationException(Map.of(
                    "expectedVersion", "Expected version is required."
            ));
        }
        if (expectedVersion < 0) {
            throw new SharedWheelValidationException(Map.of(
                    "expectedVersion", "Expected version must not be negative."
            ));
        }
    }

    private boolean membersMatch(List<SharedWheelMemberInput> requestedMembers) {
        if (members.size() != requestedMembers.size()) {
            return false;
        }
        for (int index = 0; index < members.size(); index++) {
            SharedWheelMember current = members.get(index);
            SharedWheelMemberInput requested = requestedMembers.get(index);
            if (!current.name().equals(requested.name())
                    || current.eligible() != requested.eligible()) {
                return false;
            }
        }
        return true;
    }

    private void replaceMembers(List<SharedWheelMemberInput> replacements) {
        members.clear();
        for (int position = 0; position < replacements.size(); position++) {
            SharedWheelMemberInput member = replacements.get(position);
            members.add(SharedWheelMember.create(
                    this,
                    position,
                    member.name(),
                    member.eligible()
            ));
        }
    }

    void rename(String requestedName) {
        String normalizedName = normalizeName(requestedName);
        Map<String, String> errors = new LinkedHashMap<>();
        validateName("name", normalizedName, "Wheel name", errors);
        if (!errors.isEmpty()) {
            throw new SharedWheelValidationException(errors);
        }
        name = normalizedName;
    }
}
