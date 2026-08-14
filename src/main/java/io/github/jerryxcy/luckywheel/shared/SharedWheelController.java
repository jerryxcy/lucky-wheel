package io.github.jerryxcy.luckywheel.shared;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/shared-wheels")
@ConditionalOnProperty(name = "lucky-wheel.shared.enabled", havingValue = "true")
class SharedWheelController {

    private final SharedWheelService service;

    SharedWheelController(SharedWheelService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<SharedWheelSnapshot> create(@RequestBody CreateSharedWheelRequest request) {
        SharedWheelSnapshot snapshot = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{wheelId}")
                .buildAndExpand(snapshot.id())
                .toUri();
        return ResponseEntity.created(location).body(snapshot);
    }

    @GetMapping("/{wheelId}")
    SharedWheelSnapshot get(@PathVariable UUID wheelId) {
        return service.get(wheelId);
    }

    @PutMapping("/{wheelId}")
    SharedWheelSnapshot update(
            @PathVariable UUID wheelId,
            @RequestBody UpdateSharedWheelRequest request
    ) {
        return service.update(wheelId, request);
    }
}
