package com.fintech.ledger.wallet;

import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.repository.AccountRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SystemAccountService {

  private final AccountRepository accountRepository;

  public UUID clearingAccountId(String currency) {
    return findByCode("PROVIDER_CLEARING_" + currency.toUpperCase());
  }

  public UUID settlementAccountId(String currency) {
    return findByCode("PROVIDER_SETTLEMENT_" + currency.toUpperCase());
  }

  public UUID feeRevenueAccountId(String currency) {
    return findByCode("PLATFORM_FEE_REVENUE_" + currency.toUpperCase());
  }

  private UUID findByCode(String code) {
    AccountEntity acc = accountRepository
        .findByCode(code)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.FAILED_DEPENDENCY, "Missing system account " + code));
    return acc.getId();
  }
}
