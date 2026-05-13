package com.fintech.ledger.webhook;

import com.fintech.ledger.config.LedgerProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookSignatureVerifier {

  private final LedgerProperties properties;

  public boolean isValid(String rawBody, String providedHexSignature) {
    if (providedHexSignature == null || providedHexSignature.isBlank()) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getWebhook().getHmacSecret().getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"));
      byte[] sig = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
      String expected = HexFormat.of().formatHex(sig);
      return MessageDigestEquality.constantTimeEquals(expected, providedSigNormalized(providedHexSignature));
    } catch (Exception e) {
      return false;
    }
  }

  private static final class MessageDigestEquality {

    private MessageDigestEquality() {
    }

    static boolean constantTimeEquals(String a, String b) {
      if (a.length() != b.length()) {
        return false;
      }
      int r = 0;
      for (int i = 0; i < a.length(); i++) {
        r |= a.charAt(i) ^ b.charAt(i);
      }
      return r == 0;
    }
  }

  private static String providedSigNormalized(String providedHexSignature) {
    return providedHexSignature.trim().toLowerCase();
  }
}