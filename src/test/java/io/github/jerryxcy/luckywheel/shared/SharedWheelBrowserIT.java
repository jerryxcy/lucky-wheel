package io.github.jerryxcy.luckywheel.shared;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lucky-wheel.shared.enabled=true"
)
class SharedWheelBrowserIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeEach
    void openBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
        context = browser.newContext();
        context.grantPermissions(List.of("clipboard-read", "clipboard-write"));
        page = context.newPage();
    }

    @AfterEach
    void closeBrowser() {
        context.close();
        browser.close();
        playwright.close();
    }

    @Test
    void createsCopiesOpensAndRefreshesASharedWheel() {
        page.navigate(baseUrl());
        page.evaluate("""
                localStorage.setItem('luckyWheel.roster', JSON.stringify([
                  {name: 'Alice', eligible: false},
                  {name: 'Bob', eligible: true}
                ]));
                localStorage.setItem('luckyWheel.autoRemove', 'true');
                """);
        page.reload();

        assertThat(page.locator("#mode-label")).hasText("Local Wheel");
        assertThat(page.locator("#mode-subtitle")).hasText("Only in this browser");

        page.locator("#mode-badge").click();
        assertThat(page.locator("#shared-wheel-command-sheet")).isVisible();
        assertThat(page.locator("#shared-wheel-command-heading")).hasText("Wheel mode");
        assertThat(page.locator("#local-wheel-mode")).hasClass(Pattern.compile(".*active.*"));
        page.locator("#shared-wheel-mode").click();
        assertThat(page.locator("#create-shared-wheel-dialog")).isVisible();
        page.locator("#shared-wheel-name-input").fill("On-call rotation");
        page.locator("#confirm-create-shared-wheel").click();

        Pattern sharedPath = Pattern.compile(".*/shared-wheels/[0-9a-f-]{36}$");
        page.waitForURL(sharedPath);
        String sharedUrl = page.url();
        assertThat(page.locator("#mode-label")).hasText("On-call rotation");
        assertThat(page.locator("#mode-subtitle")).hasText("Shared Wheel · saved on server");
        assertThat(page.locator("#member-list .member-name")).hasText(new String[]{"Alice", "Bob"});
        assertThat(page.locator("#member-list input").nth(0)).not().isChecked();
        assertThat(page.locator("#member-list input").nth(1)).isChecked();
        assertThat(page.locator("#auto-remove-toggle")).isChecked();
        assertThat(page.locator("#shared-wheel-command-sheet")).isVisible();
        assertThat(page.locator("#shared-wheel-created-reminder")).containsText("Shared Wheel created");
        assertThat(page.locator("#shared-wheel-created-reminder")).containsText("Save this link now");
        assertThat(page.locator("#shared-wheel-created-reminder")).containsText("Command+D");
        assertThat(page.locator("#shared-wheel-created-reminder")).containsText("Ctrl+D");

        UUID wheelId = UUID.fromString(sharedUrl.substring(sharedUrl.lastIndexOf('/') + 1));
        jdbc.update("""
                UPDATE shared_wheel
                SET expires_at = CAST(? AS TIMESTAMPTZ)
                WHERE id = ?
                """, "2030-01-01T08:00:00+08:00", wheelId);
        page.reload();

        page.locator("#mode-badge").click();
        assertThat(page.locator("#shared-wheel-created-reminder")).isHidden();
        String localExpiry = (String) page.evaluate(
                "new Date('2030-01-01T00:00:00Z').toLocaleString(document.documentElement.lang)"
        );
        assertThat(page.locator("#shared-wheel-expiry")).hasText("Expires " + localExpiry);
        page.locator("#copy-shared-wheel-link").click();
        String copiedUrl = (String) page.evaluate("navigator.clipboard.readText()");
        assertThat(copiedUrl).isEqualTo(sharedUrl);

        Page reopened = context.newPage();
        reopened.navigate(copiedUrl);
        assertThat(reopened.locator("#mode-badge")).containsText("Shared Wheel");
        assertThat(reopened.locator("#mode-label")).hasText("On-call rotation");
        assertThat(reopened.locator("#member-list .member-name"))
                .hasText(new String[]{"Alice", "Bob"});
        reopened.reload();
        assertThat(reopened.locator("#mode-label")).hasText("On-call rotation");
    }

    @Test
    void editsACompleteSharedWheelAndReopensTheServerConfirmedSnapshot() {
        page.navigate(baseUrl());
        page.evaluate("""
                localStorage.setItem('luckyWheel.roster', JSON.stringify([
                  {name: 'Alice', eligible: true},
                  {name: 'Bob', eligible: true}
                ]));
                """);
        page.reload();
        page.locator("#mode-badge").click();
        page.locator("#shared-wheel-mode").click();
        page.locator("#shared-wheel-name-input").fill("On-call");
        page.locator("#confirm-create-shared-wheel").click();
        page.waitForURL(Pattern.compile(".*/shared-wheels/[0-9a-f-]{36}$"));
        String sharedUrl = page.url();
        page.locator("#close-shared-wheel-command").click();

        page.locator("#drawer-toggle").click();
        page.locator("#member-list input").nth(0).click();
        assertThat(page.locator("#member-list input").nth(0)).not().isChecked();
        page.locator("#member-name-input").fill("Carol");
        page.locator("#add-member-form button[type='submit']").click();
        assertThat(page.locator("#member-list .member-name"))
                .hasText(new String[]{"Alice", "Bob", "Carol"});
        page.getByLabel("Remove Bob").click();
        page.locator("#bulk-import-textarea").fill("David, Eve");
        page.locator("#bulk-import-button").click();
        assertThat(page.locator("#member-list .member-name"))
                .hasText(new String[]{"Alice", "Carol", "David", "Eve"});
        page.locator("#recheck-all-button").click();
        assertThat(page.locator("#member-list input:not(:checked)")).hasCount(0);
        page.locator("#drawer-close").click();
        page.locator("#auto-remove-toggle").click();
        assertThat(page.locator("#auto-remove-toggle")).isChecked();

        page.locator("#mode-badge").click();
        page.locator("#rename-shared-wheel-input").fill("Primary on-call");
        page.locator("#rename-shared-wheel-button").click();
        assertThat(page.locator("#mode-label")).hasText("Primary on-call");

        page.reload();
        assertThat(page.locator("#mode-label")).hasText("Primary on-call");
        assertThat(page.locator("#member-list .member-name"))
                .hasText(new String[]{"Alice", "Carol", "David", "Eve"});
        assertThat(page.locator("#member-list input:not(:checked)")).hasCount(0);
        assertThat(page.locator("#auto-remove-toggle")).isChecked();

        Page reopened = context.newPage();
        reopened.navigate(sharedUrl);
        assertThat(reopened.locator("#mode-label")).hasText("Primary on-call");
        assertThat(reopened.locator("#member-list .member-name"))
                .hasText(new String[]{"Alice", "Carol", "David", "Eve"});
    }

    @Test
    void staleEditLoadsLatestStateRevertsTheControlAndPreservesUnsentText() {
        page.navigate(baseUrl());
        page.evaluate("""
                localStorage.setItem('luckyWheel.roster', JSON.stringify([
                  {name: 'Alice', eligible: true}
                ]));
                """);
        page.reload();
        page.locator("#mode-badge").click();
        page.locator("#shared-wheel-mode").click();
        page.locator("#shared-wheel-name-input").fill("On-call");
        page.locator("#confirm-create-shared-wheel").click();
        page.waitForURL(Pattern.compile(".*/shared-wheels/[0-9a-f-]{36}$"));
        String sharedUrl = page.url();
        page.locator("#close-shared-wheel-command").click();

        Page stalePage = context.newPage();
        stalePage.navigate(sharedUrl);
        assertThat(stalePage.locator("#mode-label")).hasText("On-call");
        stalePage.locator("#drawer-toggle").click();
        stalePage.locator("#member-name-input").fill("Draft name");

        page.locator("#drawer-toggle").click();
        page.locator("#member-name-input").fill("Changed elsewhere");
        page.locator("#add-member-form button[type='submit']").click();
        assertThat(page.locator("#member-list .member-name"))
                .hasText(new String[]{"Alice", "Changed elsewhere"});

        stalePage.locator("#member-list input").first().click();

        assertThat(stalePage.locator("#shared-roster-update-notice"))
                .containsText("changed in another browser");
        assertThat(stalePage.locator("#member-list input").first()).isChecked();
        assertThat(stalePage.locator("#member-list .member-name"))
                .hasText(new String[]{"Alice", "Changed elsewhere"});
        assertThat(stalePage.locator("#member-name-input")).hasValue("Draft name");
    }

    @Test
    void sharedUpdateValidationUsesLocalizedFeedbackAndPreservesTheInput() {
        page.navigate(baseUrl());
        page.evaluate("""
                localStorage.setItem('luckyWheel.roster', JSON.stringify([
                  {name: 'Alice', eligible: true}
                ]));
                localStorage.setItem('luckyWheel.language', 'zh-Hant');
                """);
        page.reload();
        page.locator("#mode-badge").click();
        page.locator("#shared-wheel-mode").click();
        page.locator("#shared-wheel-name-input").fill("值班");
        page.locator("#confirm-create-shared-wheel").click();
        page.waitForURL(Pattern.compile(".*/shared-wheels/[0-9a-f-]{36}$"));
        page.locator("#close-shared-wheel-command").click();
        page.route("**/api/shared-wheels/*", route -> route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(400)
                        .setContentType("application/problem+json")
                        .setBody("""
                                {"type":"https://github.com/jerryxcy/lucky-wheel/problems/shared-wheel-validation",
                                 "detail":"Shared Wheel validation failed."}
                                """)
        ));

        page.locator("#drawer-toggle").click();
        page.locator("#member-name-input").fill("Bob");
        page.locator("#add-member-form button[type='submit']").click();

        assertThat(page.locator("#shared-roster-update-notice"))
                .containsText("請檢查轉盤名稱與名單後再試一次");
        assertThat(page.locator("#member-name-input")).hasValue("Bob");
        assertThat(page.locator("#member-list .member-name")).hasText(new String[]{"Alice"});
    }

    @Test
    void localAndSharedRosterEditingUseTheSameDomainLimits() {
        page.navigate(baseUrl());
        page.locator("#drawer-toggle").click();
        page.locator("#bulk-import-textarea").fill("A".repeat(81));
        page.locator("#bulk-import-button").click();
        assertThat(page.locator("#bulk-import-notice"))
                .containsText("Names must be at most 80 characters");
        assertThat(page.locator("#member-list li")).hasCount(0);

        page.evaluate("""
                localStorage.setItem('luckyWheel.roster', JSON.stringify(
                  Array.from({length: 100}, (_, index) => ({
                    name: `Member ${index + 1}`,
                    eligible: true
                  }))
                ));
                """);
        page.reload();
        page.locator("#drawer-toggle").click();
        page.locator("#member-name-input").fill("Overflow");
        page.locator("#add-member-form button[type='submit']").click();
        assertThat(page.locator("#add-member-notice"))
                .containsText("A roster may contain at most 100 members");
        assertThat(page.locator("#member-list li")).hasCount(100);
        page.locator("#drawer-close").click();

        page.locator("#mode-badge").click();
        page.locator("#shared-wheel-mode").click();
        page.locator("#shared-wheel-name-input").fill("Full roster");
        page.locator("#confirm-create-shared-wheel").click();
        page.waitForURL(Pattern.compile(".*/shared-wheels/[0-9a-f-]{36}$"));
        page.locator("#close-shared-wheel-command").click();
        page.locator("#drawer-toggle").click();
        page.locator("#member-name-input").fill("Still overflow");
        page.locator("#add-member-form button[type='submit']").click();
        assertThat(page.locator("#add-member-notice"))
                .containsText("A roster may contain at most 100 members");
        assertThat(page.locator("#member-list li")).hasCount(100);
    }

    @Test
    void failedCreationPreservesTheLocalWheel() {
        page.navigate(baseUrl());
        page.evaluate("""
                localStorage.setItem('luckyWheel.roster', JSON.stringify([
                  {name: 'Alice', eligible: true}
                ]));
                localStorage.setItem('luckyWheel.language', 'zh-Hant');
                """);
        page.reload();
        page.route("**/api/shared-wheels", route -> route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(400)
                        .setContentType("application/problem+json")
                        .setBody("""
                                {"type":"https://github.com/jerryxcy/lucky-wheel/problems/shared-wheel-validation",
                                 "detail":"Shared Wheel validation failed."}
                                """)
        ));

        page.locator("#mode-badge").click();
        page.locator("#shared-wheel-mode").click();
        page.locator("#shared-wheel-name-input").fill("Still local");
        page.locator("#confirm-create-shared-wheel").click();

        assertThat(page).hasURL(baseUrl());
        assertThat(page.locator("#create-shared-wheel-error"))
                .containsText("請檢查轉盤名稱與名單後再試一次。");
        assertThat(page.locator("#mode-badge")).containsText("Local Wheel");
        assertThat(page.locator("#member-list .member-name")).hasText(new String[]{"Alice"});
        assertThat((String) page.evaluate("localStorage.getItem('luckyWheel.roster')"))
                .contains("Alice");
    }

    @Test
    void unknownDirectSharedUrlNeverFallsBackToLocal() {
        page.navigate(baseUrl() + "shared-wheels/not-a-uuid");

        assertThat(page.locator("#shared-wheel-unavailable")).isVisible();
        assertThat(page.locator("#shared-wheel-unavailable")).containsText("Shared Wheel unavailable");
        assertThat(page.locator("#mode-badge")).containsText("Shared Wheel");
    }

    @Test
    void hidesSharedCreationWhenTheCapabilityIsDisabled() {
        page.route("**/api/capabilities", route -> route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody("{\"sharedWheels\":false}")
        ));

        page.navigate(baseUrl());

        assertThat(page.locator("#mode-label")).hasText("Local Wheel");
        assertThat(page.locator("#mode-subtitle")).hasText("Only in this browser");
        page.locator("#mode-badge").click();
        assertThat(page.locator("#shared-wheel-mode")).isHidden();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port + "/";
    }

}
