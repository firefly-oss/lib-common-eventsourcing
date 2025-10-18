# Quick Start Guide

Get up and running with the Firefly Event Sourcing Library in 5 minutes.

## Prerequisites

- Java 21+
- Spring Boot 3.2+
- R2DBC compatible database (PostgreSQL recommended)
- Maven or Gradle

## Step 1: Add Dependencies

### Maven

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-eventsourcing</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Database driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
</dependency>
```

### Gradle

```kotlin
implementation 'com.firefly:lib-common-eventsourcing:1.0.0-SNAPSHOT'
implementation 'org.postgresql:r2dbc-postgresql'
```

## Step 2: Database Setup

### Option 1: Use the provided initialization script

```bash
psql -h localhost -U firefly -d firefly -f docs/schema/postgresql-init.sql
```

### Option 2: Create tables manually

```sql
-- PostgreSQL
CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    global_sequence BIGSERIAL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(aggregate_id, aggregate_version)
);

-- Optional: Create snapshots table for performance optimization
CREATE TABLE snapshots (
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, aggregate_type)
);

-- Indexes
CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);
CREATE INDEX idx_events_global_sequence ON events(global_sequence);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_snapshots_version ON snapshots(aggregate_version);
```

## Step 3: Configuration

Add to your `application.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/firefly
    username: firefly
    password: password

firefly:
  eventsourcing:
    enabled: true
    store:
      type: r2dbc
      batch-size: 100
    snapshot:
      enabled: true
      threshold: 50
    publisher:
      enabled: true
      type: AUTO
```

## Step 4: Create Domain Events

```java
package com.example.domain.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.firefly.common.eventsourcing.domain.Event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonTypeName("account.created")
public record AccountCreatedEvent(
    UUID aggregateId,
    String accountNumber,
    BigDecimal initialBalance
) implements Event {
    
    @Override
    public String getEventType() {
        return "account.created";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return Instant.now();
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of("source", "banking-service");
    }
}

@JsonTypeName("money.deposited")
public record MoneyDepositedEvent(
    UUID aggregateId,
    BigDecimal amount,
    String reference
) implements Event {
    
    @Override
    public String getEventType() {
        return "money.deposited";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return Instant.now();
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of("source", "banking-service");
    }
}
```

## Step 5: Create Aggregate

```java
package com.example.domain.aggregates;

import com.firefly.common.eventsourcing.aggregate.AggregateRoot;
import com.example.domain.events.*;

import java.math.BigDecimal;
import java.util.UUID;

public class Account extends AggregateRoot {
    
    private String accountNumber;
    private BigDecimal balance;
    private boolean active;
    
    // Constructor for new aggregates
    public Account(UUID id, String accountNumber, BigDecimal initialBalance) {
        super(id, "Account");
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        applyChange(new AccountCreatedEvent(id, accountNumber, initialBalance));
    }
    
    // Constructor for reconstruction
    public Account(UUID id) {
        super(id, "Account");
    }
    
    // Business methods
    public void deposit(BigDecimal amount, String reference) {
        if (!active) {
            throw new IllegalStateException("Account is not active");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        applyChange(new MoneyDepositedEvent(getId(), amount, reference));
    }
    
    // Event handlers
    private void on(AccountCreatedEvent event) {
        this.accountNumber = event.accountNumber();
        this.balance = event.initialBalance();
        this.active = true;
    }
    
    private void on(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.amount());
    }
    
    // Getters
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public boolean isActive() {
        return active;
    }
}
```

## Step 6: Create Service

```java
package com.example.service;

import com.firefly.common.eventsourcing.store.EventStore;
import com.example.domain.aggregates.Account;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {
    
    private final EventStore eventStore;
    
    public AccountService(EventStore eventStore) {
        this.eventStore = eventStore;
    }
    
    public Mono<Account> createAccount(String accountNumber, BigDecimal initialBalance) {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, accountNumber, initialBalance);
        
        return eventStore.appendEvents(
                accountId,
                "Account",
                account.getUncommittedEvents(),
                0L
            )
            .doOnSuccess(stream -> account.markEventsAsCommitted())
            .thenReturn(account);
    }
    
    public Mono<Account> loadAccount(UUID accountId) {
        return eventStore.loadEventStream(accountId, "Account")
                .map(stream -> {
                    Account account = new Account(accountId);
                    account.loadFromHistory(stream.getEvents());
                    return account;
                })
                .switchIfEmpty(Mono.error(
                    new RuntimeException("Account not found: " + accountId)
                ));
    }
    
    public Mono<Account> depositMoney(UUID accountId, BigDecimal amount, String reference) {
        return loadAccount(accountId)
                .flatMap(account -> {
                    account.deposit(amount, reference);
                    
                    return eventStore.appendEvents(
                            accountId,
                            "Account",
                            account.getUncommittedEvents(),
                            account.getVersion()
                        )
                        .doOnSuccess(stream -> account.markEventsAsCommitted())
                        .thenReturn(account);
                });
    }
}
```

## Step 7: Create Controller

```java
package com.example.controller;

import com.example.service.AccountService;
import com.example.domain.aggregates.Account;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    
    private final AccountService accountService;
    
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @PostMapping
    public Mono<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request.accountNumber(), request.initialBalance())
                .map(this::toResponse);
    }
    
    @GetMapping("/{accountId}")
    public Mono<AccountResponse> getAccount(@PathVariable UUID accountId) {
        return accountService.loadAccount(accountId)
                .map(this::toResponse);
    }
    
    @PostMapping("/{accountId}/deposit")
    public Mono<AccountResponse> deposit(
            @PathVariable UUID accountId,
            @RequestBody DepositRequest request) {
        return accountService.depositMoney(accountId, request.amount(), request.reference())
                .map(this::toResponse);
    }
    
    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.isActive()
        );
    }
}

// DTOs
record CreateAccountRequest(String accountNumber, BigDecimal initialBalance) {}
record DepositRequest(BigDecimal amount, String reference) {}
record AccountResponse(UUID id, String accountNumber, BigDecimal balance, boolean active) {}
```

## Step 8: Run the Application

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventSourcingDemoApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EventSourcingDemoApplication.class, args);
    }
}
```

## Step 9: Test Your API

```bash
# Create account
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountNumber": "ACC001", "initialBalance": 1000.00}'

# Response: {"id": "550e8400-e29b-41d4-a716-446655440000", ...}

# Deposit money
curl -X POST http://localhost:8080/api/accounts/550e8400-e29b-41d4-a716-446655440000/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 250.00, "reference": "DEPOSIT-001"}'

# Get account
curl http://localhost:8080/api/accounts/550e8400-e29b-41d4-a716-446655440000
```

## Step 10: Run All Tests

Verify everything works correctly:

```bash
# Run all tests including integration tests with PostgreSQL
mvn test

# Run with specific profile
mvn test -Dspring.profiles.active=dev

# Run only PostgreSQL integration tests
mvn test -Dtest=PostgreSqlEventStoreIntegrationTest
```

## Step 11: Production Setup

### Environment Variables

Set these environment variables for production:

```bash
export DB_HOST=your-postgres-host
export DB_PORT=5432
export DB_NAME=your_database
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
export DB_SSL_MODE=require
```

### Database Migration

Run Flyway migrations:

```bash
# Automatic with Spring Boot
spring.flyway.enabled=true

# Or manually
mvn flyway:migrate
```

## What's Next?

- [Testing Guide](./testing.md) - Comprehensive testing with Testcontainers
- [Configuration Reference](./configuration.md) - Explore all configuration options  
- [Architecture Overview](./architecture.md) - Understand the system design
- [Event Store Usage](./event-store.md) - Learn advanced querying features
- [Snapshot Management](./snapshots.md) - Optimize performance with snapshots
- [Best Practices](./best-practices.md) - Production recommendations

## Troubleshooting

### Common Issues

**Events table not found**
- Ensure you've created the database schema
- Check R2DBC connection configuration

**Serialization errors**
- Verify `@JsonTypeName` annotations on events
- Ensure Jackson is properly configured

**Concurrency exceptions**
- Check that you're using the correct aggregate version
- Implement proper retry logic in your services

For more issues, see the [Troubleshooting Guide](./troubleshooting.md).