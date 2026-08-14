package io.github.jerryxcy.luckywheel.shared;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lucky-wheel.shared.enabled=true"
)
class SharedWheelApiIT {

    private static final String PROBLEM_BASE =
            "https://github.com/jerryxcy/lucky-wheel/problems/";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAndReopensCompleteSharedWheelSnapshot() {
        var request = Map.of(
                "name", "  On-call rotation  ",
                "autoRemove", true,
                "members", List.of(
                        Map.of("name", "  Alice  ", "eligible", false),
                        Map.of("name", "Bob", "eligible", true)
                )
        );

        ResponseEntity<JsonNode> created = http.postForEntity(
                "/api/shared-wheels",
                jsonRequest(request),
                JsonNode.class
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        URI location = created.getHeaders().getLocation();
        assertThat(location).isNotNull();

        JsonNode createdSnapshot = created.getBody();
        assertThat(createdSnapshot).isNotNull();
        UUID wheelId = UUID.fromString(createdSnapshot.required("id").textValue());
        assertThat(wheelId.version()).isEqualTo(4);
        assertThat(location.getPath()).isEqualTo("/api/shared-wheels/" + wheelId);
        assertThat(createdSnapshot.required("name").textValue()).isEqualTo("On-call rotation");
        assertThat(createdSnapshot.required("version").longValue()).isZero();
        assertThat(createdSnapshot.required("autoRemove").booleanValue()).isTrue();
        assertThat(createdSnapshot.required("members").get(0).required("name").textValue()).isEqualTo("Alice");
        assertThat(createdSnapshot.required("members").get(0).required("eligible").booleanValue()).isFalse();
        assertThat(createdSnapshot.required("members").get(0).has("id")).isFalse();
        assertThat(createdSnapshot.required("members").get(1).required("name").textValue()).isEqualTo("Bob");
        assertThat(createdSnapshot.required("members").get(1).required("eligible").booleanValue()).isTrue();
        assertThat(createdSnapshot.required("members").get(1).has("id")).isFalse();
        assertThat(createdSnapshot.required("latestSpin").isNull()).isTrue();
        assertThat(createdSnapshot.required("expiresAt").isNull()).isTrue();

        ResponseEntity<JsonNode> reopened = http.getForEntity(location, JsonNode.class);

        assertThat(reopened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reopened.getBody()).isEqualTo(createdSnapshot);
    }

    @Test
    void currentVersionPutReplacesTheCompleteAggregateAndAdvancesVersion() {
        ResponseEntity<JsonNode> created = http.postForEntity(
                "/api/shared-wheels",
                jsonRequest(Map.of(
                        "name", "Original",
                        "autoRemove", false,
                        "members", List.of(
                                Map.of("name", "Alice", "eligible", true),
                                Map.of("name", "Bob", "eligible", false)
                        )
                )),
                JsonNode.class
        );
        URI location = created.getHeaders().getLocation();

        ResponseEntity<JsonNode> updated = http.exchange(
                location,
                HttpMethod.PUT,
                jsonRequest(Map.of(
                        "expectedVersion", 0,
                        "name", "Updated",
                        "autoRemove", true,
                        "members", List.of(
                                Map.of("name", "Bob", "eligible", true),
                                Map.of("name", "Carol", "eligible", false)
                        )
                )),
                JsonNode.class
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().required("id")).isEqualTo(created.getBody().required("id"));
        assertThat(updated.getBody().required("name").textValue()).isEqualTo("Updated");
        assertThat(updated.getBody().required("version").longValue()).isEqualTo(1);
        assertThat(updated.getBody().required("autoRemove").booleanValue()).isTrue();
        assertThat(updated.getBody().required("members").findValuesAsText("name"))
                .containsExactly("Bob", "Carol");
        assertThat(updated.getBody().required("members").get(0).required("eligible").booleanValue()).isTrue();
        assertThat(updated.getBody().required("members").get(1).required("eligible").booleanValue()).isFalse();
        assertThat(updated.getBody().required("latestSpin").isNull()).isTrue();
        assertThat(updated.getBody().required("expiresAt").isNull()).isTrue();

        ResponseEntity<JsonNode> reopened = http.getForEntity(location, JsonNode.class);
        assertThat(reopened.getBody()).isEqualTo(updated.getBody());
    }

    @Test
    void childOnlyPutAdvancesTheAggregateVersionExactlyOnce() {
        ResponseEntity<JsonNode> created = createWheel(
                "Roster",
                false,
                List.of(
                        Map.of("name", "Alice", "eligible", true),
                        Map.of("name", "Bob", "eligible", true)
                )
        );

        ResponseEntity<JsonNode> updated = putWheel(
                created.getHeaders().getLocation(),
                Map.of(
                        "expectedVersion", 0,
                        "name", "Roster",
                        "autoRemove", false,
                        "members", List.of(
                                Map.of("name", "Bob", "eligible", false),
                                Map.of("name", "Alice", "eligible", true)
                        )
                )
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().required("version").longValue()).isEqualTo(1);
        assertThat(updated.getBody().required("members").findValuesAsText("name"))
                .containsExactly("Bob", "Alice");
        assertThat(http.getForEntity(created.getHeaders().getLocation(), JsonNode.class).getBody())
                .isEqualTo(updated.getBody());
    }

    @Test
    void identicalCurrentVersionPutIsANoop() {
        ResponseEntity<JsonNode> created = createWheel(
                "Roster",
                true,
                List.of(Map.of("name", "Alice", "eligible", false))
        );

        ResponseEntity<JsonNode> unchanged = putWheel(
                created.getHeaders().getLocation(),
                Map.of(
                        "expectedVersion", 0,
                        "name", "Roster",
                        "autoRemove", true,
                        "members", List.of(Map.of("name", "Alice", "eligible", false))
                )
        );

        assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unchanged.getBody()).isEqualTo(created.getBody());
        assertThat(http.getForEntity(created.getHeaders().getLocation(), JsonNode.class).getBody())
                .isEqualTo(created.getBody());
    }

    @Test
    void stalePutReturnsConflictEvenWhenItsRepresentationMatchesCurrentState() {
        ResponseEntity<JsonNode> created = createWheel("Roster", false, List.of());
        URI location = created.getHeaders().getLocation();
        ResponseEntity<JsonNode> current = putWheel(
                location,
                Map.of(
                        "expectedVersion", 0,
                        "name", "Renamed",
                        "autoRemove", false,
                        "members", List.of()
                )
        );

        ResponseEntity<JsonNode> conflict = putWheel(
                location,
                Map.of(
                        "expectedVersion", 0,
                        "name", "Renamed",
                        "autoRemove", false,
                        "members", List.of()
                )
        );

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(conflict.getBody().required("type").textValue())
                .isEqualTo(PROBLEM_BASE + "shared-wheel-version-conflict");
        assertThat(conflict.getBody().required("status").intValue()).isEqualTo(409);
        assertThat(conflict.getBody().required("currentVersion").longValue()).isEqualTo(1);
        assertThat(conflict.getBody().toString()).doesNotContain("Exception", "shared_wheel", "SQL");
        assertThat(http.getForEntity(location, JsonNode.class).getBody()).isEqualTo(current.getBody());
    }

    @Test
    void concurrentPutsFromTheSameVersionDoNotLoseAnUpdate() throws Exception {
        ResponseEntity<JsonNode> created = createWheel(
                "Roster",
                false,
                List.of(Map.of("name", "Alice", "eligible", true))
        );
        URI location = created.getHeaders().getLocation();
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<ResponseEntity<JsonNode>> rename = workers.submit(() -> {
                start.await();
                return putWheel(location, Map.of(
                        "expectedVersion", 0,
                        "name", "Renamed",
                        "autoRemove", false,
                        "members", List.of(Map.of("name", "Alice", "eligible", true))
                ));
            });
            Future<ResponseEntity<JsonNode>> changeEligibility = workers.submit(() -> {
                start.await();
                return putWheel(location, Map.of(
                        "expectedVersion", 0,
                        "name", "Roster",
                        "autoRemove", false,
                        "members", List.of(Map.of("name", "Alice", "eligible", false))
                ));
            });

            start.countDown();
            List<ResponseEntity<JsonNode>> responses = List.of(rename.get(), changeEligibility.get());

            assertThat(responses).extracting(ResponseEntity::getStatusCode)
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            ResponseEntity<JsonNode> conflict = responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.CONFLICT)
                    .findFirst()
                    .orElseThrow();
            assertThat(conflict.getBody().required("currentVersion").longValue()).isEqualTo(1);
        }

        JsonNode finalSnapshot = http.getForEntity(location, JsonNode.class).getBody();
        assertThat(finalSnapshot.required("version").longValue()).isEqualTo(1);
        boolean renameWon = finalSnapshot.required("name").textValue().equals("Renamed");
        assertThat(renameWon
                ? finalSnapshot.required("members").get(0).required("eligible").booleanValue()
                : finalSnapshot.required("name").textValue().equals("Roster")
                        && !finalSnapshot.required("members").get(0).required("eligible").booleanValue())
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUpdateRequests")
    void invalidSharedWheelUpdateReturnsFieldProblemDetails(
            String scenario,
            String requestJson,
            String invalidField
    ) {
        ResponseEntity<JsonNode> created = createWheel("Roster", false, List.of());

        ResponseEntity<JsonNode> response = http.exchange(
                created.getHeaders().getLocation(),
                HttpMethod.PUT,
                jsonTextRequest(requestJson),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().required("type").textValue())
                .isEqualTo(PROBLEM_BASE + "shared-wheel-validation");
        assertThat(response.getBody().required("errors").has(invalidField)).isTrue();
        assertThat(response.getBody().toString()).doesNotContain("Exception", "shared_wheel", "SQL");
    }

    private static Stream<Arguments> invalidUpdateRequests() {
        return Stream.of(
                Arguments.of(
                        "missing expected version",
                        "{\"name\":\"Roster\",\"autoRemove\":false,\"members\":[]}",
                        "expectedVersion"
                ),
                Arguments.of(
                        "negative expected version",
                        "{\"expectedVersion\":-1,\"name\":\"Roster\",\"autoRemove\":false,\"members\":[]}",
                        "expectedVersion"
                ),
                Arguments.of(
                        "server-owned latest spin",
                        "{\"expectedVersion\":0,\"name\":\"Roster\",\"autoRemove\":false,\"members\":[],"
                                + "\"latestSpin\":{}}",
                        "latestSpin"
                )
        );
    }

    @Test
    void updatingMissingSharedWheelReturnsNotFound() {
        ResponseEntity<JsonNode> response = putWheel(
                URI.create(http.getRootUri() + "/api/shared-wheels/" + UUID.randomUUID()),
                Map.of(
                        "expectedVersion", 0,
                        "name", "Roster",
                        "autoRemove", false,
                        "members", List.of()
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().required("type").textValue())
                .isEqualTo(PROBLEM_BASE + "shared-wheel-not-found");
    }

    @Test
    void missingSharedWheelReturnsStableProblemDetails() {
        UUID missingWheelId = UUID.randomUUID();

        ResponseEntity<JsonNode> response = http.getForEntity(
                "/api/shared-wheels/" + missingWheelId,
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().required("type").textValue())
                .isEqualTo(PROBLEM_BASE + "shared-wheel-not-found");
        assertThat(response.getBody().required("status").intValue()).isEqualTo(404);
        assertThat(response.getBody().required("detail").textValue())
                .isEqualTo("Shared Wheel was not found.");
        assertThat(response.getBody().toString()).doesNotContain("Exception", "shared_wheel", "SQL");
    }

    @Test
    void supportsEmptyRostersCaseSensitiveMembersAndNonUniqueWheelNames() {
        ResponseEntity<JsonNode> emptyFirst = http.postForEntity(
                "/api/shared-wheels",
                jsonRequest(Map.of("name", "Planning", "autoRemove", false, "members", List.of())),
                JsonNode.class
        );
        ResponseEntity<JsonNode> emptySecond = http.postForEntity(
                "/api/shared-wheels",
                jsonRequest(Map.of("name", "Planning", "autoRemove", false, "members", List.of())),
                JsonNode.class
        );
        ResponseEntity<JsonNode> caseSensitive = http.postForEntity(
                "/api/shared-wheels",
                jsonRequest(Map.of(
                        "name", "Case-sensitive roster",
                        "autoRemove", false,
                        "members", List.of(
                                Map.of("name", "Alice", "eligible", true),
                                Map.of("name", "alice", "eligible", true)
                        )
                )),
                JsonNode.class
        );

        assertThat(emptyFirst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(emptySecond.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(emptyFirst.getBody().required("members").isEmpty()).isTrue();
        assertThat(emptyFirst.getBody().required("id").textValue())
                .isNotEqualTo(emptySecond.getBody().required("id").textValue());
        assertThat(caseSensitive.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(caseSensitive.getBody().required("members").findValuesAsText("name"))
                .containsExactly("Alice", "alice");
    }

    @Test
    void migrationEnforcesRosterIdentityOrderingAndCascadeInPostgreSql() {
        UUID wheelId = UUID.randomUUID();
        UUID firstMemberId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shared_wheel (id, name, version, auto_remove, expires_at)
                VALUES (?, ?, 0, false, NULL)
                """, wheelId, "Database contract");
        jdbc.update("""
                INSERT INTO shared_wheel_member (id, wheel_id, roster_position, name, eligible)
                VALUES (?, ?, 0, 'Alice', true)
                """, firstMemberId, wheelId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO shared_wheel_member (id, wheel_id, roster_position, name, eligible)
                VALUES (?, ?, 1, 'Alice', false)
                """, UUID.randomUUID(), wheelId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO shared_wheel_member (id, wheel_id, roster_position, name, eligible)
                VALUES (?, ?, 100, 'Bob', true)
                """, UUID.randomUUID(), wheelId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                UPDATE shared_wheel
                SET expires_at = CAST(? AS TIMESTAMPTZ)
                WHERE id = ?
                """, "2030-01-01T08:00:00+08:00", wheelId);
        ResponseEntity<JsonNode> timestampSnapshot = http.getForEntity(
                "/api/shared-wheels/" + wheelId,
                JsonNode.class
        );
        assertThat(timestampSnapshot.getBody().required("expiresAt").textValue())
                .isEqualTo("2030-01-01T00:00:00Z");

        String expiresAtType = jdbc.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'shared_wheel'
                  AND column_name = 'expires_at'
                """, String.class);
        assertThat(expiresAtType).isEqualTo("timestamp with time zone");

        jdbc.update("DELETE FROM shared_wheel WHERE id = ?", wheelId);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared_wheel_member WHERE id = ?",
                Integer.class,
                firstMemberId
        )).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedSharedRequests")
    void malformedSharedRequestReturnsStableProblemDetails(String scenario, String path, String requestJson) {
        ResponseEntity<JsonNode> response;
        if (requestJson == null) {
            response = http.getForEntity(path, JsonNode.class);
        } else {
            response = http.postForEntity(path, jsonTextRequest(requestJson), JsonNode.class);
        }

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().required("type").textValue())
                .isEqualTo(PROBLEM_BASE + "shared-wheel-invalid-request");
        assertThat(response.getBody().required("status").intValue()).isEqualTo(400);
        assertThat(response.getBody().toString()).doesNotContain("Exception", "shared_wheel", "SQL");
    }

    private static Stream<Arguments> malformedSharedRequests() {
        return Stream.of(
                Arguments.of("invalid UUID", "/api/shared-wheels/not-a-uuid", null),
                Arguments.of("malformed JSON", "/api/shared-wheels", "{\"name\":")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreateRequests")
    void invalidSharedWheelReturnsFieldProblemDetails(
            String scenario,
            String requestJson,
            String invalidField
    ) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/api/shared-wheels",
                jsonTextRequest(requestJson),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().required("type").textValue())
                .isEqualTo(PROBLEM_BASE + "shared-wheel-validation");
        assertThat(response.getBody().required("status").intValue()).isEqualTo(400);
        assertThat(response.getBody().required("errors").has(invalidField)).isTrue();
        assertThat(response.getBody().toString()).doesNotContain("Exception", "shared_wheel", "SQL");
    }

    private static Stream<Arguments> invalidCreateRequests() {
        String tooManyMembers = IntStream.range(0, 101)
                .mapToObj(index -> "{\"name\":\"Member " + index + "\",\"eligible\":true}")
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        return Stream.of(
                Arguments.of("missing name", "{\"autoRemove\":false,\"members\":[]}", "name"),
                Arguments.of("blank name", "{\"name\":\"   \",\"autoRemove\":false,\"members\":[]}", "name"),
                Arguments.of("long trimmed name", "{\"name\":\"" + "A".repeat(81)
                        + "\",\"autoRemove\":false,\"members\":[]}", "name"),
                Arguments.of("missing auto-remove", "{\"name\":\"Wheel\",\"members\":[]}", "autoRemove"),
                Arguments.of("missing members", "{\"name\":\"Wheel\",\"autoRemove\":false}", "members"),
                Arguments.of("too many members", "{\"name\":\"Wheel\",\"autoRemove\":false,\"members\":["
                        + tooManyMembers + "]}", "members"),
                Arguments.of("blank member name", "{\"name\":\"Wheel\",\"autoRemove\":false,\"members\":["
                        + "{\"name\":\"   \",\"eligible\":true}]}", "members[0].name"),
                Arguments.of("long member name", "{\"name\":\"Wheel\",\"autoRemove\":false,\"members\":["
                        + "{\"name\":\"" + "M".repeat(81) + "\",\"eligible\":true}]}", "members[0].name"),
                Arguments.of("missing eligibility", "{\"name\":\"Wheel\",\"autoRemove\":false,\"members\":["
                        + "{\"name\":\"Alice\"}]}", "members[0].eligible"),
                Arguments.of("duplicate trimmed names", "{\"name\":\"Wheel\",\"autoRemove\":false,\"members\":["
                        + "{\"name\":\"Alice\",\"eligible\":true},"
                        + "{\"name\":\" Alice \",\"eligible\":false}]}", "members[1].name")
        );
    }

    private static HttpEntity<Object> jsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<JsonNode> createWheel(
            String name,
            boolean autoRemove,
            List<?> members
    ) {
        return http.postForEntity(
                "/api/shared-wheels",
                jsonRequest(Map.of("name", name, "autoRemove", autoRemove, "members", members)),
                JsonNode.class
        );
    }

    private ResponseEntity<JsonNode> putWheel(URI location, Object body) {
        return http.exchange(location, HttpMethod.PUT, jsonRequest(body), JsonNode.class);
    }

    private static HttpEntity<String> jsonTextRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
