package io.github.jerryxcy.luckywheel.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SharedWheelController.class,
        properties = "lucky-wheel.shared.enabled=true"
)
class SharedWheelProblemDetailsTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SharedWheelService service;

    @Test
    void unexpectedSharedApiFailureReturnsSanitizedProblemDetails() throws Exception {
        given(service.get(any(UUID.class)))
                .willThrow(new IllegalStateException("SQL shared_wheel internal detail"));

        mvc.perform(get("/api/shared-wheels/{wheelId}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(
                        "https://github.com/jerryxcy/lucky-wheel/problems/shared-api-error"
                ))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value(
                        "The Shared Wheel request could not be completed."
                ))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("shared_wheel")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("IllegalStateException")
                )));
    }
}
