package com.fintech.ledger.ledger;

import com.fintech.ledger.domain.JournalDirection;
import java.util.UUID;

public record PostingLine(
    UUID accountId, JournalDirection direction, long amountMinor, String memo) {

  public PostingLine {
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("amountMinor must be positive");
    }
  }
}
