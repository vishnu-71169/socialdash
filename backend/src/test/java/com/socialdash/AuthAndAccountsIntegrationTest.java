package com.socialdash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthAndAccountsIntegrationTest extends IntegrationTestBase {

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String payload = "{\"name\":\"A\",\"email\":\"dup@test.dev\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.dev\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void connectingAnAccountBackfillsAnalytics() throws Exception {
        String token = registerUser();
        connectAccount(token, "INSTAGRAM", "shop");

        JsonNode accounts = perform(get("/api/accounts"), token, 200);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).get("simulated").asBoolean()).isTrue();
        assertThat(accounts.get(0).get("followers").asLong()).isPositive();
        assertThat(accounts.get(0).has("accessTokenEncrypted")).isFalse();

        JsonNode overview = perform(get("/api/analytics/overview"), token, 200);
        assertThat(overview.get("accountCount").asInt()).isEqualTo(1);
        assertThat(overview.get("impressions").get("value").asDouble()).isPositive();

        JsonNode trends = perform(get("/api/analytics/trends?startDate=" + java.time.LocalDate.now().minusDays(6)),
                token, 200);
        assertThat(trends.size()).isBetween(6, 8);
    }

    @Test
    void usersCannotSeeEachOthersAccounts() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        long aliceAccount = connectAccount(alice, "TWITTER", "alice");

        perform(post("/api/accounts/" + aliceAccount + "/sync"), bob, 404);
        assertThat(perform(get("/api/accounts"), bob, 200)).isEmpty();
    }
}
