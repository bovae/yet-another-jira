package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class SignupSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private SharedScenarioState sharedState;

    private String email;
    private String password;
    private ResponseEntity<String> response;

    @After("@auth")
    public void cleanup() {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        try {
            TestcontainersConfig.GREEN_MAIL.purgeEmailFromAllMailboxes();
        } catch (com.icegreen.greenmail.store.FolderException e) {
            throw new RuntimeException("Failed to purge GreenMail mailboxes", e);
        }
        // Valkey rate-limit/denylist keys are purged globally by ValkeyCleanupHooks.
    }

    @Given("a new user with email {string} and password {string}")
    public void aNewUserWithEmailAndPassword(String email, String password) {
        this.email = email;
        this.password = password;
    }

    @When("the user submits a signup request")
    public void theUserSubmitsASignupRequest() {
        TestRestTemplate restTemplate = new TestRestTemplate();

        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        response =
                restTemplate.postForEntity("http://localhost:" + port + "/api/v1/auth/signup", request, String.class);
        sharedState.setLastResponse(response);
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expectedStatus) {
        ResponseEntity<String> current = sharedState.getLastResponse();
        assertNotNull(current, "Response should not be null");
        assertEquals(expectedStatus, current.getStatusCode().value(), "HTTP status mismatch");
    }

    @And("the response body contains the email {string}")
    public void theResponseBodyContainsTheEmail(String expectedEmail) {
        ResponseEntity<String> current = sharedState.getLastResponse();
        assertNotNull(current, "Response should not be null");
        assertNotNull(current.getBody(), "Response body should not be null");
        assertTrue(
                current.getBody().contains("\"email\":\"" + expectedEmail + "\""),
                "Response body should contain email. Body: " + current.getBody());
    }

    @And("the response body shows emailVerified is false")
    public void theResponseBodyShowsEmailVerifiedIsFalse() {
        ResponseEntity<String> current = sharedState.getLastResponse();
        assertNotNull(current, "Response should not be null");
        assertNotNull(current.getBody(), "Response body should not be null");
        assertTrue(
                current.getBody().contains("\"emailVerified\":false"),
                "Response body should show emailVerified=false. Body: " + current.getBody());
    }

    @And("the user exists in the database with email {string} and email_verified false")
    public void theUserExistsInTheDatabaseWithEmailAndEmailVerifiedFalse(String expectedEmail) {
        Optional<User> user = userRepository.findByEmail(expectedEmail);
        assertTrue(user.isPresent(), "User should exist in database with email: " + expectedEmail);
        assertFalse(user.get().isEmailVerified(), "User email_verified should be false");
    }
}
