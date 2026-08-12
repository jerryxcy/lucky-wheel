package io.github.jerryxcy.luckywheel.shared;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SharedWheelRepository extends JpaRepository<SharedWheel, UUID> {
}
