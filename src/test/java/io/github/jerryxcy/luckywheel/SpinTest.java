package io.github.jerryxcy.luckywheel;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SpinTest {

    @Test
    void drawOrderHasExactlyCountEntries() {
        RandomGenerator random = new Random(42)::nextInt;
        List<String> members = List.of("Alice", "Bob", "Carol", "Dave");

        List<String> drawOrder = Spin.draw(members, 2, random);

        assertThat(drawOrder).hasSize(2);
    }

    @Test
    void drawOrderHasNoDuplicates() {
        RandomGenerator random = new Random(7)::nextInt;
        List<String> members = List.of("Alice", "Bob", "Carol", "Dave", "Erin");

        List<String> drawOrder = Spin.draw(members, 5, random);

        assertThat(drawOrder).doesNotHaveDuplicates();
    }

    @Test
    void drawOrderIsSubsetOfMembers() {
        RandomGenerator random = new Random(99)::nextInt;
        List<String> members = List.of("Alice", "Bob", "Carol", "Dave", "Erin");

        List<String> drawOrder = Spin.draw(members, 3, random);

        assertThat(members).containsAll(drawOrder);
    }

    @Test
    void sameSeedReproducesSameDrawOrder() {
        List<String> members = List.of("Alice", "Bob", "Carol", "Dave", "Erin");

        List<String> first = Spin.draw(members, 5, new Random(2024)::nextInt);
        List<String> second = Spin.draw(members, 5, new Random(2024)::nextInt);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void countEqualToMemberCountYieldsPermutationOfFullList() {
        RandomGenerator random = new Random(555)::nextInt;
        List<String> members = List.of("Alice", "Bob", "Carol", "Dave", "Erin");

        List<String> drawOrder = Spin.draw(members, members.size(), random);

        assertThat(drawOrder).hasSize(members.size());
        assertThat(drawOrder).containsExactlyInAnyOrderElementsOf(members);
    }

    @Test
    void largeSampleDrawIsRoughlyUniform() {
        List<String> members = List.of("Alice", "Bob", "Carol", "Dave", "Erin");
        Map<String, Integer> firstPickCounts = new HashMap<>();
        for (String member : members) {
            firstPickCounts.put(member, 0);
        }

        int trials = 50_000;
        Random source = new Random(1);
        for (int i = 0; i < trials; i++) {
            String winner = Spin.draw(members, 1, source::nextInt).get(0);
            firstPickCounts.merge(winner, 1, Integer::sum);
        }

        int expectedPerMember = trials / members.size();
        int tolerance = (int) (expectedPerMember * 0.1);
        assertThat(firstPickCounts.values())
                .allSatisfy(count -> assertThat(count).isCloseTo(expectedPerMember, org.assertj.core.data.Offset.offset(tolerance)));
    }
}
