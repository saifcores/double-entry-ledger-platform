package com.fintech.ledger;

import com.fintech.ledger.config.LedgerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.fintech.ledger", exclude = RedisRepositoriesAutoConfiguration.class)
@EntityScan(basePackages = "com.fintech.ledger.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fintech.ledger.persistence.repository")
@EnableScheduling
@EnableConfigurationProperties(LedgerProperties.class)
public class LedgerPlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(LedgerPlatformApplication.class, args);
  }
}
