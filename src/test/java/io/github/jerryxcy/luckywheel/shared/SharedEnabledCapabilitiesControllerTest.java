package io.github.jerryxcy.luckywheel.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CapabilitiesController.class,
        properties = "lucky-wheel.shared.enabled=true"
)
class SharedEnabledCapabilitiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sharedDeploymentReportsSharedWheelsAvailable() throws Exception {
        mockMvc.perform(get("/api/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedWheels").value(true));
    }
}
