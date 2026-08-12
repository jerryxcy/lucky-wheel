package io.github.jerryxcy.luckywheel.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "shared_wheel_member")
class SharedWheelMember {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wheel_id", nullable = false, updatable = false)
    private SharedWheel wheel;

    @Column(name = "roster_position", nullable = false)
    private int rosterPosition;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean eligible;

    protected SharedWheelMember() {
    }

    static SharedWheelMember create(
            SharedWheel wheel,
            int rosterPosition,
            String name,
            boolean eligible
    ) {
        SharedWheelMember member = new SharedWheelMember();
        member.id = UUID.randomUUID();
        member.wheel = wheel;
        member.rosterPosition = rosterPosition;
        member.name = name;
        member.eligible = eligible;
        return member;
    }

    String name() {
        return name;
    }

    boolean eligible() {
        return eligible;
    }
}
