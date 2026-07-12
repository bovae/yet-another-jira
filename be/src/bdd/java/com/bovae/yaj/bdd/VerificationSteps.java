package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class VerificationSteps {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[?&]token=([^&\\s]+)");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private VerificationProperties verificationProperties;

    @Autowired
    private SharedScenarioState sharedState;

    @Nullable
    private String extractedToken;

    @Nullable
    private String oldToken;

    @Nullable
    private ResponseEntity<String> lastResponse;

    @Nullable
    private String lastCapturedRecipient;

    // --- Background ---

    @Given("the SMTP capture server is running")
    public void theSmtpCaptureServerIsRunning() {
        assertTrue(TestcontainersConfig.GREEN_MAIL.isRunning(), "GreenMail SMTP capture server should be running");
    }

    // --- Signup step (shared with verification scenarios) ---

    @Given("a new user signs up with email {string} and password {string}")
    public void aNewUserSignsUpWithEmailAndPassword(String email, String password) {
        ResponseEntity<String> response = postJson("/api/v1/auth/signup", Map.of("email", email, "password", password));
        sharedState.setLastResponse(response);
        assertEquals(201, response.getStatusCode().value(), "Signup should return 201");
    }

    // --- Email capture ---

    @And("a verification email is captured for {string}")
    public void aVerificationEmailIsCapturedFor(String email) {
        assertExactlyOneEmailFor(email);
    }

    @And("a new verification email is captured for {string}")
    public void aNewVerificationEmailIsCapturedFor(String email) {
        assertExactlyOneEmailFor(email);
    }

    @And("no verification email is captured for {string}")
    public void noVerificationEmailIsCapturedFor(String email) {
        // Bounded wait: give async dispatch a chance to (wrongly) deliver, then assert none did.
        waitForEmailCountFor(email, 1, 500);
        assertEquals(0, emailCountFor(email), "No verification email should be captured for " + email);
    }

    @And("no further verification email is captured for {string} after the rate limit is hit")
    public void noFurtherVerificationEmailIsCapturedFor(String email) {
        // resendRateLimit requests are accepted (one email each); the next is rate-limited and never
        // publishes a dispatch event, so exactly resendRateLimit emails can ever arrive for this
        // recipient. Counting per-recipient is robust to async emails leaking in from other scenarios.
        int expected = verificationProperties.resendRateLimit();
        assertTrue(
                waitForEmailCountFor(email, expected, 5000),
                "Expected the " + expected + " accepted resend emails for " + email);
        assertEquals(
                expected,
                emailCountFor(email),
                "Exactly " + expected + " emails should be captured for " + email + "; the rate-limited request none");
    }

    // --- Token extraction ---

    @When("the raw token is extracted from the captured verification link")
    public void theRawTokenIsExtractedFromTheCapturedVerificationLink() throws Exception {
        extractedToken = extractTokenFromLatestEmail();
        assertNotNull(extractedToken, "Should extract a token from the verification email");
    }

    @When("the new raw token is extracted from the latest captured verification link")
    public void theNewRawTokenIsExtractedFromTheLatestCapturedVerificationLink() throws Exception {
        oldToken = extractedToken;
        extractedToken = extractTokenFromLatestEmail();
        assertNotNull(extractedToken, "Should extract a new token from the latest verification email");
    }

    // --- Verify via POST ---

    @When("the user submits POST \\/api\\/v1\\/auth\\/verify with the extracted token")
    public void theUserSubmitsPostVerifyWithTheExtractedToken() {
        lastResponse = postJson("/api/v1/auth/verify", Map.of("token", extractedToken));
        sharedState.setLastResponse(lastResponse);
    }

    @When("the user submits POST \\/api\\/v1\\/auth\\/verify with the same token again")
    public void theUserSubmitsPostVerifyWithTheSameTokenAgain() {
        lastResponse = postJson("/api/v1/auth/verify", Map.of("token", extractedToken));
        sharedState.setLastResponse(lastResponse);
    }

    @When("the user submits POST \\/api\\/v1\\/auth\\/verify with the old token")
    public void theUserSubmitsPostVerifyWithTheOldToken() {
        assertNotNull(oldToken, "Old token should be stored");
        lastResponse = postJson("/api/v1/auth/verify", Map.of("token", oldToken));
        sharedState.setLastResponse(lastResponse);
    }

    @When("the user submits POST \\/api\\/v1\\/auth\\/verify with the new token")
    public void theUserSubmitsPostVerifyWithTheNewToken() {
        assertNotNull(extractedToken, "New token should be stored");
        lastResponse = postJson("/api/v1/auth/verify", Map.of("token", extractedToken));
        sharedState.setLastResponse(lastResponse);
    }

    // --- Verify via GET ---

    @When("the user submits GET \\/api\\/v1\\/auth\\/verify with the extracted token as query param")
    public void theUserSubmitsGetVerifyWithTheExtractedTokenAsQueryParam() {
        // Use a RestTemplate that does NOT follow redirects
        RestTemplate noRedirectTemplate = createNoRedirectRestTemplate();
        String url = baseUrl() + "/api/v1/auth/verify?token=" + extractedToken;
        lastResponse = noRedirectTemplate.exchange(url, HttpMethod.GET, null, String.class);
        sharedState.setLastResponse(lastResponse);
    }

    // --- Response assertions ---

    @And("the response body indicates verification succeeded")
    public void theResponseBodyIndicatesVerificationSucceeded() {
        ResponseEntity<String> current = sharedState.getLastResponse();
        assertNotNull(current, "Response should not be null");
        assertNotNull(current.getBody(), "Response body should not be null");
        assertTrue(
                current.getBody().contains("\"verified\":true"),
                "Response should indicate verified=true. Body: " + current.getBody());
    }

    @And("the response Location header points to the configured redirect URL")
    public void theResponseLocationHeaderPointsToTheConfiguredRedirectUrl() {
        ResponseEntity<String> current = sharedState.getLastResponse();
        assertNotNull(current, "Response should not be null");
        URI location = current.getHeaders().getLocation();
        assertNotNull(location, "Location header should be present");
        assertEquals(
                verificationProperties.resultRedirectUrl(),
                location.toString(),
                "Location should point to the configured redirect URL");
    }

    @And("the response Location header points to the configured error redirect URL")
    public void theResponseLocationHeaderPointsToTheConfiguredErrorRedirectUrl() {
        ResponseEntity<String> current = sharedState.getLastResponse();
        assertNotNull(current, "Response should not be null");
        URI location = current.getHeaders().getLocation();
        assertNotNull(location, "Location header should be present");
        assertEquals(
                verificationProperties.resultErrorRedirectUrl(),
                location.toString(),
                "Location should point to the configured error redirect URL");
    }

    @Then("the last response status is {int}")
    public void theLastResponseStatusIs(int expectedStatus) {
        assertNotNull(lastResponse, "Last response should not be null");
        assertEquals(expectedStatus, lastResponse.getStatusCode().value(), "Last response HTTP status mismatch");
    }

    @And("the last response includes a Retry-After header")
    public void theLastResponseIncludesARetryAfterHeader() {
        assertNotNull(lastResponse, "Last response should not be null");
        String retryAfter = lastResponse.getHeaders().getFirst("Retry-After");
        assertNotNull(retryAfter, "Retry-After header should be present");
        // Must be numeric
        assertTrue(retryAfter.matches("\\d+"), "Retry-After should be numeric. Got: " + retryAfter);
    }

    // --- Database assertions ---

    @And("the user {string} has email_verified true in the database")
    public void theUserHasEmailVerifiedTrueInTheDatabase(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new AssertionError("User not found: " + email));
        assertTrue(user.isEmailVerified(), "User " + email + " should have email_verified=true");
    }

    @And("the token for {string} is expired in the database")
    public void theTokenForIsExpiredInTheDatabase(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new AssertionError("User not found: " + email));
        List<VerificationToken> tokens = verificationTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(user.getId()))
                .filter(t -> t.getConsumedAt() == null)
                .toList();
        // Expire all unconsumed tokens for this user
        for (VerificationToken token : tokens) {
            token.setExpiresAt(Instant.now().minusSeconds(3600));
            verificationTokenRepository.save(token);
        }
        verificationTokenRepository.flush();
    }

    // --- Resend ---

    @When("the user submits POST \\/api\\/v1\\/auth\\/verification\\/resend with email {string}")
    public void theUserSubmitsPostVerificationResendWithEmail(String email) {
        lastResponse = postJson("/api/v1/auth/verification/resend", Map.of("email", email));
        sharedState.setLastResponse(lastResponse);
    }

    @When("the user submits POST \\/api\\/v1\\/auth\\/verification\\/resend with email {string} {int} times")
    public void theUserSubmitsPostVerificationResendWithEmailNTimes(String email, int times) {
        for (int i = 0; i < times; i++) {
            lastResponse = postJson("/api/v1/auth/verification/resend", Map.of("email", email));
        }
        sharedState.setLastResponse(lastResponse);
    }

    // --- Captured messages management ---

    @And("the captured messages are cleared")
    public void theCapturedMessagesAreCleared() {
        try {
            TestcontainersConfig.GREEN_MAIL.purgeEmailFromAllMailboxes();
        } catch (com.icegreen.greenmail.store.FolderException e) {
            throw new RuntimeException("Failed to purge GreenMail mailboxes", e);
        }
    }

    // --- Preconditions for the privacy scenarios ---

    @Given("no account exists for {string}")
    public void noAccountExistsFor(String email) {
        // No-op: ensure no account by verifying (the test context is clean after @After)
        assertTrue(userRepository.findByEmail(email).isEmpty(), "No account should exist for " + email);
    }

    @Given("a user {string} exists with email_verified true")
    public void aUserExistsWithEmailVerifiedTrue(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$dummyhashforBDDtest000000000000000000000000000000");
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }

    // --- Helpers ---

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private ResponseEntity<String> postJson(String path, Map<String, String> body) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(new HashMap<>(body), headers);
        return restTemplate.postForEntity(baseUrl() + path, request, String.class);
    }

    private RestTemplate createNoRedirectRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setRequestFactory(new NoRedirectClientHttpRequestFactory());
        rt.setErrorHandler(new NoOpResponseErrorHandler());
        return rt;
    }

    @Nullable
    private String extractTokenFromLatestEmail() throws Exception {
        MimeMessage[] messages = TestcontainersConfig.GREEN_MAIL.getReceivedMessages();
        // Scan newest-first for the latest email addressed to the recipient we last captured, so a
        // leaked async email from another scenario cannot hand us the wrong token.
        for (int i = messages.length - 1; i >= 0; i--) {
            MimeMessage message = messages[i];
            if (lastCapturedRecipient != null && !isAddressedTo(message, lastCapturedRecipient)) {
                continue;
            }
            String body = (String) message.getContent();
            Matcher matcher = TOKEN_PATTERN.matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private void assertExactlyOneEmailFor(String email) {
        assertTrue(waitForEmailCountFor(email, 1, 5000), "Expected a verification email for " + email);
        assertEquals(1, emailCountFor(email), "Exactly one verification email should be captured for " + email);
        lastCapturedRecipient = email;
    }

    private int emailCountFor(String email) {
        int count = 0;
        for (MimeMessage message : TestcontainersConfig.GREEN_MAIL.getReceivedMessages()) {
            if (isAddressedTo(message, email)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Deadline-polls until at least {@code expected} messages addressed to {@code email} are present.
     * Counting per-recipient (not the whole mailbox) is robust to async emails from other scenarios
     * arriving late — they target different recipients.
     */
    private boolean waitForEmailCountFor(String email, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (emailCountFor(email) >= expected) {
                return true;
            }
            sleep(50);
        }
        return emailCountFor(email) >= expected;
    }

    private static boolean isAddressedTo(MimeMessage message, String email) {
        try {
            jakarta.mail.Address[] recipients = message.getAllRecipients();
            if (recipients == null) {
                return false;
            }
            for (jakarta.mail.Address addr : recipients) {
                if (addr.toString().equalsIgnoreCase(email)) {
                    return true;
                }
            }
        } catch (jakarta.mail.MessagingException e) {
            return false;
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A request factory that does NOT follow redirects, so we can assert 303 responses. */
    private static class NoRedirectClientHttpRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                throws java.io.IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }

    /** Suppresses exception-throwing on 4xx/5xx so we can assert status codes directly. */
    private static class NoOpResponseErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(ClientHttpResponse response) {
            return false;
        }
    }
}
