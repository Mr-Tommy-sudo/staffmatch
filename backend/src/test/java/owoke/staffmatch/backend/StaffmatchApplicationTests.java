package owoke.staffmatch.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import owoke.staffmatch.backend.auth.filter.MaxAuthenticationFilter;
import owoke.staffmatch.backend.auth.support.MaxInitDataFixtures;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "MAX_BOT_TOKEN=" + MaxInitDataFixtures.BOT_TOKEN)
@AutoConfigureMockMvc
class StaffmatchApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthIsPublicAndMaxInitDataIsRequired() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredFrontendOriginCanSendPreflightRequest() throws Exception {
        mockMvc.perform(options("/api/v1/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Max-Init-Data"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void userIsReusedAndRoleCannotBeChanged() throws Exception {
        String initData = MaxInitDataFixtures.signed(900001L, Instant.now());
        String firstResponse = mockMvc.perform(get("/api/v1/me")
                        .header(MaxAuthenticationFilter.INIT_DATA_HEADER, initData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(get("/api/v1/me")
                        .header(MaxAuthenticationFilter.INIT_DATA_HEADER, initData))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(firstResponse, secondResponse);

        mockMvc.perform(put("/api/v1/me/role")
                        .header(MaxAuthenticationFilter.INIT_DATA_HEADER, initData)
                        .contentType("application/json")
                        .content("{\"role\":\"CANDIDATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CANDIDATE"));

        mockMvc.perform(put("/api/v1/me/role")
                        .header(MaxAuthenticationFilter.INIT_DATA_HEADER, initData)
                        .contentType("application/json")
                        .content("{\"role\":\"CANDIDATE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/me/role")
                        .header(MaxAuthenticationFilter.INIT_DATA_HEADER, initData)
                        .contentType("application/json")
                        .content("{\"role\":\"EMPLOYER\"}"))
                .andExpect(status().isConflict());
    }
}
