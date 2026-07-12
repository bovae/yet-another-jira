package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class AuthSessionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SharedScenarioState sharedState;

    @Nullable
    private String accessToken;

    @Nullable
    private String lastRegisteredEmail;

    @Given("a registered user with email {string} and password {string}")
    public void aRegisteredUserWithEmailAndPassword(String email, String password) {
        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(Map.of("email", email, "password", password), headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity(baseUrl() + "/api/v1/auth/signup", request, String.class);
        assertTrue(
                response.getStatusCode().is2xxSuccessful(),
                "Signup should succeed. Status: " + response.getStatusCode());
        lastRegisteredEmail = email;
    }

    @And("the user's email is verified")
    public void theUsersEmailIsVerified() {
        assertNotNull(lastRegisteredEmail, "A user must be registered before verifying");
        setEmailVerified(lastRegisteredEmail, true);
    }

    @And("the email-verified flag for {string} is {string}")
    public void theEmailVerifiedFlagForIs(String email, String verified) {
        setEmailVerified(email, Boolean.parseBoolean(verified));
    }

    @When("the user logs in with email {string} and password {string}")
    public void theUserLogsInWithEmailAndPassword(String email, String password) {
        sharedState.setLastResponse(login(email, password));
    }

    @When("the user attempts to log in {int} times with email {string} and password {string}")
    public void theUserAttemptsToLogInNTimes(int times, String email, String password) {
        ResponseEntity<String> response = null;
        for (int i = 0; i < times; i++) {
            response = login(email, password);
        }
        assertNotNull(response, "At least one login attempt must be made");
        sharedState.setLastResponse(response);
    }

    @And("the response includes a Retry-After header")
    public void theResponseIncludesARetryAfterHeader() {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        String retryAfter = response.getHeaders().getFirst("Retry-After");
        assertNotNull(retryAfter, "Retry-After header should be present");
        assertTrue(retryAfter.matches("\\d+"), "Retry-After should be numeric. Got: " + retryAfter);
    }

    @And("the response body contains an access token")
    public void theResponseBodyContainsAnAccessToken() {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        String body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body.contains("\"accessToken\":\""), "Response should contain accessToken. Body: " + body);
        // Extract the token for later use
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf("\"", start);
        accessToken = body.substring(start, end);
        assertNotNull(accessToken, "Extracted access token should not be null");
        // Publish to shared state so other step classes (e.g. team steps) can authenticate.
        sharedState.setAccessToken(accessToken);
    }

    @When("the user requests current-user without an access token")
    public void theUserRequestsCurrentUserWithoutAnAccessToken() {
        RestTemplate restTemplate = restTemplate();
        HttpEntity<Void> request = new HttpEntity<>(new HttpHeaders());
        ResponseEntity<String> response =
                restTemplate.exchange(baseUrl() + "/api/v1/auth/me", HttpMethod.GET, request, String.class);
        sharedState.setLastResponse(response);
    }

    @When("the user requests current-user with the access token")
    public void theUserRequestsCurrentUserWithTheAccessToken() {
        assertNotNull(accessToken, "Access token should be available");
        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response =
                restTemplate.exchange(baseUrl() + "/api/v1/auth/me", HttpMethod.GET, request, String.class);
        sharedState.setLastResponse(response);
    }

    @And("the response body contains email {string}")
    public void theResponseBodyContainsEmail(String expectedEmail) {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        String body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(
                body.contains("\"email\":\"" + expectedEmail + "\""),
                "Response should contain email " + expectedEmail + ". Body: " + body);
    }

    @And("the response body contains emailVerified true")
    public void theResponseBodyContainsEmailVerifiedTrue() {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        String body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(
                body.contains("\"emailVerified\":true"), "Response should contain emailVerified:true. Body: " + body);
    }

    @When("the user logs out with the access token")
    public void theUserLogsOutWithTheAccessToken() {
        assertNotNull(accessToken, "Access token should be available");
        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity(baseUrl() + "/api/v1/auth/logout", request, String.class);
        sharedState.setLastResponse(response);
    }

    @When("the user requests an unknown route with the access token")
    public void theUserRequestsAnUnknownRouteWithTheAccessToken() {
        assertNotNull(accessToken, "Access token should be available");
        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/does-not-exist-" + System.nanoTime(), HttpMethod.GET, request, String.class);
        sharedState.setLastResponse(response);
    }

    @Then("the response content type is {string}")
    public void theResponseContentTypeIs(String expectedContentType) {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType, "Content-Type header should be present");
        // Compare type and subtype, ignoring parameters like charset
        MediaType expected = MediaType.parseMediaType(expectedContentType);
        assertTrue(
                contentType.isCompatibleWith(expected),
                "Content-Type should be " + expectedContentType + " but was " + contentType);
    }

    @Then("the response body contains members: status, title, correlationId, timestamp")
    public void theResponseBodyContainsRequiredMembers() {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        String body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body.contains("\"status\""), "Body should contain 'status' member. Body: " + body);
        assertTrue(body.contains("\"title\""), "Body should contain 'title' member. Body: " + body);
        assertTrue(body.contains("\"correlationId\""), "Body should contain 'correlationId' member. Body: " + body);
        assertTrue(body.contains("\"timestamp\""), "Body should contain 'timestamp' member. Body: " + body);
    }

    // --- Helpers ---

    private ResponseEntity<String> login(String email, String password) {
        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(Map.of("email", email, "password", password), headers);
        return restTemplate.postForEntity(baseUrl() + "/api/v1/auth/login", request, String.class);
    }

    private void setEmailVerified(String email, boolean verified) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new AssertionError("User not found: " + email));
        user.setEmailVerified(verified);
        userRepository.saveAndFlush(user);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new NoOpResponseErrorHandler());
        return rt;
    }

    /** Suppresses exception-throwing on 4xx/5xx so we can assert status codes directly. */
    private static class NoOpResponseErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
            return false;
        }
    }
}
