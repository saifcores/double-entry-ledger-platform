package com.fintech.ledger.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintech.ledger.config.LedgerProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

  private static final String SECRET = "test-hmac-secret";

  private WebhookSignatureVerifier verifier;

  @BeforeEach
  void setUp() {
    LedgerProperties properties = new LedgerProperties();
    properties.getWebhook().setHmacSecret(SECRET);
    verifier = new WebhookSignatureVerifier(properties);
  }

  private String hmacHex(String secret, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void acceptsCorrectSignature() throws Exception {
    String body = "{\"amount\":100}";
    String signature = hmacHex(SECRET, body);

    assertThat(verifier.isValid(body, signature)).isTrue();
  }

  @Test
  void acceptsSignatureRegardlessOfHexCase() throws Exception {
    String body = "{\"amount\":100}";
    String signature = hmacHex(SECRET, body).toUpperCase();

    assertThat(verifier.isValid(body, signature)).isTrue();
  }

  @Test
  void rejectsSignature_whenBodyTampered() throws Exception {
    String signature = hmacHex(SECRET, "{\"amount\":100}");

    assertThat(verifier.isValid("{\"amount\":999}", signature)).isFalse();
  }

  @Test
  void rejectsSignature_computedWithWrongSecret() throws Exception {
    String body = "{\"amount\":100}";
    String signature = hmacHex("some-other-secret", body);

    assertThat(verifier.isValid(body, signature)).isFalse();
  }

  @Test
  void rejectsNullSignature() {
    assertThat(verifier.isValid("body", null)).isFalse();
  }

  @Test
  void rejectsBlankSignature() {
    assertThat(verifier.isValid("body", "   ")).isFalse();
  }

  @Test
  void rejectsMalformedSignature() {
    assertThat(verifier.isValid("body", "not-hex-and-wrong-length")).isFalse();
  }
}
