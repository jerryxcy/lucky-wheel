package io.github.jerryxcy.luckywheel.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/capabilities")
class CapabilitiesController {

    private final boolean sharedWheels;

    CapabilitiesController(@Value("${lucky-wheel.shared.enabled:false}") boolean sharedWheels) {
        this.sharedWheels = sharedWheels;
    }

    @GetMapping
    Capabilities capabilities() {
        return new Capabilities(sharedWheels);
    }

    record Capabilities(boolean sharedWheels) {
    }
}
