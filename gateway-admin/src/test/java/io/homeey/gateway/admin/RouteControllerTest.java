package io.homeey.gateway.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String uniqueId(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test
    void shouldCreateRouteAndPublish() throws Exception {
        String routeId = uniqueId("r1");
        String body = """
                {"id":"%s","host":"api.example.com","pathPrefix":"/orders","method":"GET","headers":{"x-env":"prod"},"upstreamService":"order-service","upstreamPath":"/orders"}
                """.formatted(routeId);

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routeId));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(routeId)));

        String bindings = """
                [{"name":"auth","routeId":"%s","order":10,"enabled":true,"failPolicy":"FAIL_CLOSE","config":{"mode":"strict"}}]
                """.formatted(routeId);

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
        String routeId = uniqueId("r2");
        String badRoute = """
                {"id":"%s","host":"","pathPrefix":"","method":"GET","upstreamService":"order-service"}
                """.formatted(routeId);

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRoute))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/routes/publish"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldAllowStaticRoutePublish() throws Exception {
        String routeId = uniqueId("r-static");
        String body = """
                {"id":"%s","host":"api.example.com","pathPrefix":"/assets","method":"GET","headers":{},"upstreamService":"static","upstreamPath":"/hello.txt"}
                """.formatted(routeId);

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routeId));

        mockMvc.perform(post("/api/routes/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    void shouldManageBindingsAndDeleteRoute() throws Exception {
        String routeId = uniqueId("r3");
        String route = """
                {"id":"%s","host":"api.example.com","pathPrefix":"/inventory","method":"GET","headers":{},"upstreamService":"inventory-service","upstreamPath":"/inventory"}
                """.formatted(routeId);
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(route))
                .andExpect(status().isOk());

        String bindings = """
                [{"name":"auth","routeId":"%s","order":10,"enabled":true,"failPolicy":"FAIL_CLOSE","config":{"mode":"strict"}},
                 {"name":"ratelimit","routeId":"","order":20,"enabled":true,"failPolicy":"FAIL_OPEN","config":{"qps":100}}]
                """.formatted(routeId);
        mockMvc.perform(post("/api/routes/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindings))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/routes/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("auth")))
                .andExpect(jsonPath("$[*].name", hasItem("ratelimit")));

        mockMvc.perform(delete("/api/routes/plugins/auth")
                        .param("routeId", routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedBinding").value("auth"));

        mockMvc.perform(get("/api/routes/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem("auth"))));

        mockMvc.perform(delete("/api/routes/" + routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedRouteId").value(routeId));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(routeId))));
    }

    @Test
    void shouldPublishWithOperatorAndSummary() throws Exception {
        String routeId = uniqueId("r4");
        String route = """
                {"id":"%s","host":"api.example.com","pathPrefix":"/pay","method":"POST","headers":{},"upstreamService":"pay-service","upstreamPath":"/pay"}
                """.formatted(routeId);
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(route))
                .andExpect(status().isOk());

        String publishCommand = """
                {"operator":"alice","summary":"manual release","policySet":{"env":"prod"}}
                """;
        mockMvc.perform(post("/api/routes/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishCommand))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.publishedAt").exists());

        mockMvc.perform(get("/api/routes/publish-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operator").value("alice"))
                .andExpect(jsonPath("$[0].summary").value("manual release"));
    }
}
