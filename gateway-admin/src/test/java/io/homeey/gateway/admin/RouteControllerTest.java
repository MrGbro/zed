package io.homeey.gateway.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateRouteAndPublish() throws Exception {
        String body = """
                {"id":"r1","path":"/orders","upstream":"order-service","plugins":["auth"]}
                """;

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r1"));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"));

        mockMvc.perform(post("/api/routes/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists());
    }
}
