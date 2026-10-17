package com.socialdash;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "app.demo.seed=false",
        "app.scheduling.enabled=false",
        "app.simulation.publish-failure-rate=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** Registers a fresh user and returns a bearer token. */
    protected String registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.dev";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    protected JsonNode perform(MockHttpServletRequestBuilder req, String token, int expectedStatus) throws Exception {
        String body = mvc.perform(req.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    protected long connectAccount(String token, String platform, String handle) throws Exception {
        JsonNode account = perform(post("/api/accounts/simulated")
                .content("{\"platform\":\"" + platform + "\",\"handle\":\"" + handle + "\"}"), token, 201);
        return account.get("id").asLong();
    }
}
