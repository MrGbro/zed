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
                {"id":"r1","host":"api.example.com","pathPrefix":"/orders","method":"GET","headers":{"x-env":"prod"},"upstreamService":"order-service","upstreamPath":"/orders"}
                """;

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r1"));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"));

        String bindings = """
                [{"name":"auth","routeId":"r1","order":10,"enabled":true,"failPolicy":"FAIL_CLOSE","config":{"mode":"strict"}}]
                """;

        mockMvc.perform(post("/api/routes/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindings))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("auth"));

        mockMvc.perform(post("/api/routes/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists());

        mockMvc.perform(get("/api/routes/publish-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").exists());
    }

    @Test
    void shouldRejectPublishWhenRouteInvalid() throws Exception {
        String badRoute = """
                {"id":"r2","host":"","pathPrefix":"","method":"GET","upstreamService":"order-service"}
                """;

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRoute))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/routes/publish"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
    }
}
