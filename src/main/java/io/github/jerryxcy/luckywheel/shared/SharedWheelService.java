package io.github.jerryxcy.luckywheel.shared;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "lucky-wheel.shared.enabled", havingValue = "true")
class SharedWheelService {

    private final SharedWheelRepository repository;

    SharedWheelService(SharedWheelRepository repository) {
        this.repository = repository;
    }

    @Transactional
    SharedWheelSnapshot create(CreateSharedWheelRequest request) {
        SharedWheel wheel = SharedWheel.create(request);
        return snapshot(repository.saveAndFlush(wheel));
    }

    @Transactional(readOnly = true)
    SharedWheelSnapshot get(UUID wheelId) {
        return repository.findById(wheelId)
                .map(this::snapshot)
                .orElseThrow(() -> new SharedWheelNotFoundException(wheelId));
    }

    private SharedWheelSnapshot snapshot(SharedWheel wheel) {
        return new SharedWheelSnapshot(
                wheel.id(),
                wheel.name(),
                wheel.version(),
                wheel.autoRemove(),
                wheel.members().stream()
                        .map(member -> new SharedWheelMemberSnapshot(member.name(), member.eligible()))
                        .toList(),
                null,
                wheel.expiresAt()
        );
    }
}
