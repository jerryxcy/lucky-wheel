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
        ValidatedCreate validated = validate(request);
        SharedWheel wheel = new SharedWheel();
        wheel.id = UUID.randomUUID();
        wheel.name = validated.name();
        wheel.autoRemove = validated.autoRemove();
        for (int position = 0; position < validated.members().size(); position++) {
            SharedWheelMemberInput member = validated.members().get(position);
            wheel.members.add(SharedWheelMember.create(
                    wheel,
                    position,
                    member.name(),
                    member.eligible()
            ));
        }
        return wheel;
    }

    private static ValidatedCreate validate(CreateSharedWheelRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request == null) {
            errors.put("request", "Request body is required.");
            throw new SharedWheelValidationException(errors);
        }

        String normalizedName = normalizeName(request.name());
        validateName("name", normalizedName, "Wheel name", errors);

        if (request.autoRemove() == null) {
            errors.put("autoRemove", "Auto-remove is required.");
        }

        List<SharedWheelMemberInput> normalizedMembers = new ArrayList<>();
        List<SharedWheelMemberInput> requestedMembers = request.members();
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
        return new ValidatedCreate(normalizedName, request.autoRemove(), List.copyOf(normalizedMembers));
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

    private record ValidatedCreate(
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
