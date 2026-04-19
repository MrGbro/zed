package io.homeey.gateway.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void shouldRunReleaseGovernanceLifecycle() throws Exception {
        String routeId = uniqueId("r-release");
        String route = """
                {"id":"%s","host":"api.example.com","pathPrefix":"/gov","method":"GET","headers":{},"upstreamService":"gov-service","upstreamPath":"/gov"}
                """.formatted(routeId);
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(route))
                .andExpect(status().isOk());

        String draft1Command = """
                {"operator":"alice","summary":"release-1","policySet":{"env":"prod"},"canary":{"mode":"header","header":"x-canary","value":"v1","enabled":true}}
                """;
        String draft1Payload = mockMvc.perform(post("/api/routes/releases/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draft1Command))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseId").exists())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode draft1 = objectMapper.readTree(draft1Payload);
        String release1Id = draft1.path("releaseId").asText();

        mockMvc.perform(post("/api/routes/releases/{releaseId}/validate", release1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VALIDATED"));

        mockMvc.perform(post("/api/routes/releases/{releaseId}/approve", release1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approver":"reviewer-a","comment":"looks good"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value("reviewer-a"));

        mockMvc.perform(post("/api/routes/releases/{releaseId}/publish", release1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedVersion").exists());

        String draft2Payload = mockMvc.perform(post("/api/routes/releases/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"alice","summary":"release-2","policySet":{"env":"prod"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode draft2 = objectMapper.readTree(draft2Payload);
        String release2Id = draft2.path("releaseId").asText();

        mockMvc.perform(post("/api/routes/releases/{releaseId}/validate", release2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VALIDATED"));

        mockMvc.perform(post("/api/routes/releases/{releaseId}/approve", release2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approver":"reviewer-b","comment":"approve release-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value("reviewer-b"));

        mockMvc.perform(post("/api/routes/releases/{releaseId}/publish", release2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));

        mockMvc.perform(post("/api/routes/releases/{releaseId}/rollback", release2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"ops","comment":"rollback to previous stable","targetReleaseId":"%s"}
                                """.formatted(release1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.rollbackToReleaseId").value(release1Id));

        mockMvc.perform(get("/api/routes/releases/{releaseId}", release2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ROLLED_BACK"));

        mockMvc.perform(get("/api/routes/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].releaseId", hasItem(release1Id)))
                .andExpect(jsonPath("$[*].releaseId", hasItem(release2Id)));
    }

    @Test
    void shouldRejectReleasePublishWhenNotApproved() throws Exception {
        String routeId = uniqueId("r-release-invalid");
        String route = """
                {"id":"%s","host":"api.example.com","pathPrefix":"/gov-invalid","method":"GET","headers":{},"upstreamService":"gov-service","upstreamPath":"/gov-invalid"}
                """.formatted(routeId);
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(route))
                .andExpect(status().isOk());

        String draftPayload = mockMvc.perform(post("/api/routes/releases/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"alice","summary":"release-invalid"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String releaseId = objectMapper.readTree(draftPayload).path("releaseId").asText();

        mockMvc.perform(post("/api/routes/releases/{releaseId}/publish", releaseId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
