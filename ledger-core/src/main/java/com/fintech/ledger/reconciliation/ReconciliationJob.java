package com.fintech.ledger.reconciliation;

import com.fintech.ledger.config.LedgerProperties;
import com.fintech.ledger.persistence.entity.ReconciliationReportEntity;
import com.fintech.ledger.persistence.repository.AccountRepository;
import com.fintech.ledger.persistence.repository.ReconciliationReportRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

  private final AccountRepository accountRepository;
  private final ReconciliationReportRepository reconciliationReportRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final LedgerProperties ledgerProperties;
  private final JdbcTemplate jdbcTemplate;

  @Scheduled(cron = "${ledger.reconciliation.cron:0 15 * * * *}")
  public void reconcileProviders() {
    Instant end = Instant.now();
    Instant start = end.minus(1, ChronoUnit.HOURS);
    for (String currency : List.of("USD", "EUR")) {
      String clearingCode = "PROVIDER_CLEARING_" + currency;
      long ledgerMinor = accountRepository.findByCode(clearingCode).map(a -> a.getBalanceMinor()).orElse(0L);
      Long providerMinor = jdbcTemplate.queryForObject(
          "select coalesce(sum(amount_minor),0) from provider_transactions "
              + "where currency = ? and created_at between ? and ? "
              + "and status = 'SETTLED'",
          Long.class,
          currency,
          start,
          end);
      long provider = providerMinor == null ? 0L : providerMinor;
      long drift = Math.abs(ledgerMinor - provider);
      if (drift > 0) {
        var report = new ReconciliationReportEntity();
        report.setProvider("AGGREGATE");
        report.setPeriodStart(start);
        report.setPeriodEnd(end);
        report.setStatus("MISMATCH");
        report.setMismatchCount(1);
        Map<String, Object> details = new HashMap<>();
        details.put("currency", currency);
        details.put("clearingLedgerMinor", ledgerMinor);
        details.put("providerMinorWindow", provider);
        details.put("drift", drift);
        report.setDetails(details);
        reconciliationReportRepository.save(report);
        kafkaTemplate.send(
            ledgerProperties.getKafka().getReconciliationMismatch(),
            clearingCode,
            Map.of(
                "currency",
                currency,
                "drift",
                drift,
                "periodStart",
                start.toString(),
                "periodEnd",
                end.toString()));
        log.warn("Reconciliation drift {} {} minor {}", currency, clearingCode, drift);
      }
    }
  }
}
