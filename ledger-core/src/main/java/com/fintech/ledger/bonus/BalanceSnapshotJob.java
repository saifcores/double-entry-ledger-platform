package com.fintech.ledger.bonus;

import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.LedgerBalanceSnapshotEntity;
import com.fintech.ledger.persistence.repository.AccountRepository;
import com.fintech.ledger.persistence.repository.LedgerBalanceSnapshotRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceSnapshotJob {

  private final AccountRepository accountRepository;
  private final LedgerBalanceSnapshotRepository snapshotRepository;

  @Scheduled(cron = "${ledger.snapshot.cron:0 0 0,12 * * *}")
  @Transactional
  public void captureSystemSnapshots() {
    Instant asOf = Instant.now();
    for (String code : java.util.List.of("PROVIDER_CLEARING_USD", "PROVIDER_CLEARING_EUR")) {
      accountRepository
          .findByCode(code)
          .ifPresent(
              acc -> persistSnapshot(acc, asOf));
    }
  }

  private void persistSnapshot(AccountEntity acc, Instant asOf) {
    LedgerBalanceSnapshotEntity s = new LedgerBalanceSnapshotEntity();
    s.setAccount(acc);
    s.setAsOf(asOf);
    s.setBalanceMinor(acc.getBalanceMinor());
    s.setCurrency(acc.getCurrency());
    snapshotRepository.save(s);
    log.info("Captured snapshot for {} at {}", acc.getCode(), asOf);
  }
}
