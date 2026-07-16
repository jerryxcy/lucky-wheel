package io.github.jerryxcy.luckywheel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validRequestReturns200WithDrawOrderOfRequestedSize() throws Exception {
        mockMvc.perform(post("/api/spins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members": ["Alice", "Bob", "Carol"], "count": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drawOrder").isArray())
                .andExpect(jsonPath("$.drawOrder.length()").value(2));
    }

    @Test
    void emptyMemberListReturns400WithErrorMessage() throws Exception {
        mockMvc.perform(post("/api/spins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members": [], "count": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void blankMemberNameReturns400WithErrorMessage() throws Exception {
        mockMvc.perform(post("/api/spins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members": ["Alice", "   "], "count": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void duplicateMemberNameAfterTrimmingReturns400WithErrorMessage() throws Exception {
        mockMvc.perform(post("/api/spins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members": ["Alice", " Alice "], "count": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void countAboveMemberCountReturns400WithErrorMessage() throws Exception {
        mockMvc.perform(post("/api/spins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members": ["Alice", "Bob"], "count": 3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void countBelowOneReturns400WithErrorMessage() throws Exception {
        mockMvc.perform(post("/api/spins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members": ["Alice", "Bob"], "count": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }
}
