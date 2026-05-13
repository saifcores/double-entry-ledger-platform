package com.fintech.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintech.ledger.domain.AccountType;
import com.fintech.ledger.domain.JournalDirection;
import org.junit.jupiter.api.Test;

class BalanceMathTest {

  @Test
  void assetDebitIncreasesSignedBalance() {
    long d = BalanceMath.signedDelta(AccountType.ASSET, JournalDirection.DEBIT, 100);
    long c = BalanceMath.signedDelta(AccountType.ASSET, JournalDirection.CREDIT, 100);
    assertThat(d).isEqualTo(100);
    assertThat(c).isEqualTo(-100);
  }

  @Test
  void liabilityCreditIncreasesSignedBalance() {
    long d = BalanceMath.signedDelta(AccountType.LIABILITY, JournalDirection.DEBIT, 50);
    long c = BalanceMath.signedDelta(AccountType.LIABILITY, JournalDirection.CREDIT, 50);
    assertThat(d).isEqualTo(-50);
    assertThat(c).isEqualTo(50);
  }
}
