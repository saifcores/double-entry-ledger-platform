package com.fintech.ledger.ledger;

import com.fintech.ledger.domain.AccountType;
import com.fintech.ledger.domain.JournalDirection;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BalanceMath {

  public static long signedDelta(
      AccountType accountType, JournalDirection direction, long amountMinor) {
    return switch (accountType) {
      case ASSET, EXPENSE ->
        direction == JournalDirection.DEBIT ? amountMinor : -amountMinor;
      case LIABILITY, EQUITY, REVENUE ->
        direction == JournalDirection.CREDIT ? amountMinor : -amountMinor;
    };
  }
}
