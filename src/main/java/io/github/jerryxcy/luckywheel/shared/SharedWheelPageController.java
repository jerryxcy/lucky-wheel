package io.github.jerryxcy.luckywheel.shared;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SharedWheelPageController {

    @GetMapping("/shared-wheels/{wheelId}")
    String sharedWheelPage() {
        return "forward:/index.html";
    }
}
