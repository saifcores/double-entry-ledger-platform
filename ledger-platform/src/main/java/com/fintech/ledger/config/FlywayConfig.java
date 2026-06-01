package com.fintech.ledger.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Recovers local databases where Flyway history and Hibernate-managed schema
 * diverged
 * (for example {@code create-drop} removed tables but left Flyway at V3, or
 * {@code baseline-on-migrate} skipped {@code V1__schema.sql}).
 */
@Configuration
@Slf4j
public class FlywayConfig {

  @Bean
  FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
    return flyway -> {
      if (shouldResetPublicSchema(dataSource)) {
        log.warn("Ledger schema missing or inconsistent; resetting public schema");
        resetPublicSchema(dataSource);
      }
      try {
        flyway.migrate();
      } catch (FlywayException e) {
        if (isNonEmptySchemaWithoutHistory(e)) {
          log.warn("Non-empty schema without Flyway history; resetting and re-migrating");
          resetPublicSchema(dataSource);
          flyway.migrate();
        } else {
          throw e;
        }
      }
    };
  }

  private static boolean shouldResetPublicSchema(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData meta = connection.getMetaData();
      if (!tableExists(meta, connection, "accounts")) {
        return true;
      }
      return tableExists(meta, connection, "flyway_schema_history")
          && !migrationApplied(dataSource, "1");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to inspect database schema", e);
    }
  }

  private static boolean migrationApplied(DataSource dataSource, String version) {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.prepareStatement(
            "SELECT 1 FROM flyway_schema_history WHERE version = ? AND success = TRUE")) {
      statement.setString(1, version);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next();
      }
    } catch (SQLException e) {
      return false;
    }
  }

  private static boolean isNonEmptySchemaWithoutHistory(FlywayException e) {
    String message = e.getMessage();
    return message != null && message.contains("no schema history table");
  }

  private static void resetPublicSchema(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA public CASCADE");
      statement.execute("CREATE SCHEMA public");
      statement.execute("GRANT ALL ON SCHEMA public TO public");
      String user = connection.getMetaData().getUserName();
      if (user != null && !user.isBlank()) {
        statement.execute("GRANT ALL ON SCHEMA public TO " + quoteIdentifier(user));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to reset public schema", e);
    }
  }

  private static String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  private static boolean tableExists(
      DatabaseMetaData meta, Connection connection, String tableName) throws SQLException {
    String catalog = connection.getCatalog();
    String schema = connection.getSchema();
    if (schema == null || schema.isBlank()) {
      schema = "public";
    }
    try (ResultSet tables = meta.getTables(catalog, schema, tableName, new String[] { "TABLE" })) {
      return tables.next();
    }
  }
}
