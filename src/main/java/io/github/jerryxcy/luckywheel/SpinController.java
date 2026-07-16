package io.github.jerryxcy.luckywheel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** The server's single endpoint: turns a member list into a draw order. */
@RestController
@RequestMapping("/api/spins")
class SpinController {

    private final RandomGenerator randomGenerator;

    SpinController(RandomGenerator randomGenerator) {
        this.randomGenerator = randomGenerator;
    }

    @PostMapping
    SpinResponse spin(@RequestBody SpinRequest request) {
        List<String> members = validate(request);
        List<String> drawOrder = Spin.draw(members, request.count(), randomGenerator);
        return new SpinResponse(drawOrder);
    }

    /**
     * Validates the request and returns the trimmed member names to draw from.
     * Checks run in a fixed order so each bad-input case reports its own message.
     */
    private static List<String> validate(SpinRequest request) {
        List<String> members = request.members();
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("Members list must not be empty.");
        }

        List<String> trimmed = members.stream()
                .map(member -> member == null ? null : member.trim())
                .toList();
        if (trimmed.stream().anyMatch(member -> member == null || member.isEmpty())) {
            throw new IllegalArgumentException("Member names must not be blank.");
        }

        if (Set.copyOf(trimmed).size() != trimmed.size()) {
            throw new IllegalArgumentException("Member names must be unique (after trimming whitespace).");
        }

        int count = request.count();
        if (count < 1 || count > trimmed.size()) {
            throw new IllegalArgumentException("Count must be between 1 and " + trimmed.size() + ".");
        }

        return trimmed;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleInvalidRequest(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }
}
