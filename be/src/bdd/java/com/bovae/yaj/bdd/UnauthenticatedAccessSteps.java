package com.bovae.yaj.bdd;

import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * One generic step backing the collapsed unauthenticated-401 {@code Scenario Outline}, replacing the
 * per-resource "an unauthenticated user lists ..." steps that each feature used to repeat.
 */
public class UnauthenticatedAccessSteps {

    @Autowired
    private ApiClient api;

    @Autowired
    private SharedScenarioState sharedState;

    @When("an unauthenticated user requests {string} {string}")
    public void anUnauthenticatedUserRequests(String method, String endpoint) {
        sharedState.setLastResponse(api.exchange(HttpMethod.valueOf(method), endpoint, null, false));
    }
}
