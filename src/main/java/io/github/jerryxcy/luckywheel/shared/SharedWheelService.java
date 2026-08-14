package io.github.jerryxcy.luckywheel.shared;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "lucky-wheel.shared.enabled", havingValue = "true")
class SharedWheelService {

    private final SharedWheelRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    SharedWheelService(
            SharedWheelRepository repository,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
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

    SharedWheelSnapshot update(UUID wheelId, UpdateSharedWheelRequest request) {
        try {
            transactions.executeWithoutResult(status -> updateInTransaction(wheelId, request));
        } catch (ConcurrencyFailureException | OptimisticLockException failure) {
            throw concurrentVersionConflict(wheelId, failure);
        }
        return transactions.execute(status -> repository.findById(wheelId)
                .map(this::snapshot)
                .orElseThrow(() -> new SharedWheelNotFoundException(wheelId)));
    }

    private void updateInTransaction(UUID wheelId, UpdateSharedWheelRequest request) {
        SharedWheel wheel = repository.findById(wheelId)
                .orElseThrow(() -> new SharedWheelNotFoundException(wheelId));
        SharedWheel.Replacement replacement = wheel.replace(request);
        if (replacement == SharedWheel.Replacement.MEMBERS_ONLY) {
            entityManager.lock(wheel, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        if (replacement != SharedWheel.Replacement.UNCHANGED) {
            entityManager.flush();
        }
    }

    private SharedWheelVersionConflictException concurrentVersionConflict(
            UUID wheelId,
            RuntimeException failure
    ) {
        long currentVersion = transactions.execute(status -> repository.findById(wheelId)
                .map(SharedWheel::version)
                .orElseThrow(() -> new SharedWheelNotFoundException(wheelId)));
        return new SharedWheelVersionConflictException(wheelId, currentVersion, failure);
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
