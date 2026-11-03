package com.socialdash.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialdash.IntegrationTestBase;
import com.socialdash.integration.PlatformApiException;
import com.socialdash.integration.SocialPlatformClient.DailyMetrics;
import com.socialdash.integration.meta.MetaGraphClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class MetaConnectIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    MetaGraphClient meta;

    @Autowired
    SocialAccountRepository accounts;

    private static String connectBody(String platform, String id) {
        return "{\"platform\":\"" + platform + "\",\"accountId\":\"" + id + "\",\"accessToken\":\"EAAtest\"}";
    }

    @Test
    void connectingALiveAccountVerifiesItStoresTheTokenEncryptedAndFetchesHistory() throws Exception {
        String token = registerUser();
        when(meta.verify(Platform.INSTAGRAM, "17841", "EAAtest"))
                .thenReturn(new MetaGraphClient.Profile("17841", "@piccolo", "Piccolo", "EAAtest", 250));
        when(meta.fetchDailyMetrics(any(), any())).thenReturn(new DailyMetrics(250, 900, 30, 4, 1));
        when(meta.fetchEngagements(any(), any(), any())).thenReturn(List.of());

        JsonNode account = perform(post("/api/accounts/meta").content(connectBody("INSTAGRAM", "17841")), token, 201);

        assertThat(account.get("simulated").asBoolean()).isFalse();
        assertThat(account.get("handle").asText()).isEqualTo("@piccolo");
        assertThat(account.get("followers").asLong()).isEqualTo(250);
        assertThat(account.toString()).doesNotContain("EAAtest");
        SocialAccount stored = accounts.findById(account.get("id").asLong()).orElseThrow();
        assertThat(stored.getAccessTokenEncrypted()).isNotBlank().doesNotContain("EAAtest");

        perform(post("/api/accounts/meta").content(connectBody("INSTAGRAM", "17841")), token, 409);
    }

    @Test
    void aTokenMetaRejectsIsA400AndNothingIsSaved() throws Exception {
        String token = registerUser();
        when(meta.verify(eq(Platform.FACEBOOK), any(), any()))
                .thenThrow(new PlatformApiException("Meta API #190: Invalid OAuth access token", false));

        JsonNode error = perform(post("/api/accounts/meta").content(connectBody("FACEBOOK", "42")), token, 400);

        assertThat(error.get("message").asText()).contains("Invalid OAuth access token");
        assertThat(perform(get("/api/accounts"), token, 200)).isEmpty();
    }

    @Test
    void textPostsCantTargetALiveInstagramAccount() throws Exception {
        String token = registerUser();
        when(meta.verify(Platform.INSTAGRAM, "17841", "EAAtest"))
                .thenReturn(new MetaGraphClient.Profile("17841", "@piccolo", "Piccolo", "EAAtest", 250));
        when(meta.fetchDailyMetrics(any(), any())).thenReturn(new DailyMetrics(250, 900, 30, 4, 1));
        when(meta.fetchEngagements(any(), any(), any())).thenReturn(List.of());
        long id = perform(post("/api/accounts/meta").content(connectBody("INSTAGRAM", "17841")), token, 201)
                .get("id").asLong();

        JsonNode error = perform(post("/api/posts").content("""
                {"content":"Hello","accountIds":[%d],"action":"PUBLISH_NOW"}""".formatted(id)), token, 400);

        assertThat(error.get("message").asText()).contains("photo or video");
    }
}
