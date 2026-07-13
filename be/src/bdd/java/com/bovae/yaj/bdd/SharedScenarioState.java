package com.bovae.yaj.bdd;

import io.cucumber.spring.ScenarioScope;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Holds shared state (latest HTTP response) across step-definition classes within a single scenario.
 * Scoped per Cucumber scenario so state does not leak between tests.
 */
@Component
@ScenarioScope
public class SharedScenarioState {

    @Nullable
    private ResponseEntity<String> lastResponse;

    @Nullable
    private String accessToken;

    @Nullable
    private String registeredEmail;

    @Nullable
    public ResponseEntity<String> getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(ResponseEntity<String> response) {
        this.lastResponse = response;
    }

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @Nullable
    public String getRegisteredEmail() {
        return registeredEmail;
    }

    public void setRegisteredEmail(String registeredEmail) {
        this.registeredEmail = registeredEmail;
    }
}
