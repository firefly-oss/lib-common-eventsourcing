# Quick Start Guide

Get up and running with the Firefly Event Sourcing Library in 5 minutes.

> **💡 New to Event Sourcing?** Start with our comprehensive [Account Ledger Tutorial](./tutorial-account-ledger.md) that explains all concepts in depth with a complete working example.

This quick start guide shows you how to set up the library and build a simple Account Ledger system. For a deeper understanding of why each component is necessary, see the [full tutorial](./tutorial-account-ledger.md).

## 🗄️ **Important: What Gets a Database Table?**

Before you start, understand this critical concept:

| Component | Has Table? | Why? |
|-----------|------------|------|
| **Events** | ✅ YES (`events` table) | Source of truth, immutable history |
| **Snapshots** | ✅ YES (`snapshots` table) | Performance optimization |
| **Read Models** | ✅ YES (e.g., `account_ledger_read_model`) | Fast queries |
| **Aggregates** | ❌ **NO TABLE** | Reconstructed from events in-memory |

```java
// ❌ WRONG: Do NOT create a table for aggregates
@Table("account_ledger")
public class AccountLedger extends AggregateRoot { }

// ✅ CORRECT: Aggregates have no @Table annotation
public class AccountLedger extends AggregateRoot {
    // Lives in memory only!
}

// ✅ CORRECT: Read models DO have tables
@Table("account_ledger_read_model")
public class AccountLedgerReadModel { }
```

**The Golden Rule:** If you create a table for your aggregate, you're doing traditional CRUD, not event sourcing!

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

Use the `@DomainEvent` annotation for automatic event type registration:

```java
package com.firefly.common.eventsourcing.examples.ledger.events;

import com.firefly.common.eventsourcing.domain.AbstractDomainEvent;
import com.firefly.common.eventsourcing.domain.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@DomainEvent(eventType = "AccountOpened", version = 1)
public class AccountOpenedEvent extends AbstractDomainEvent {
    private final String accountNumber;
    private final String accountType;
    private final UUID customerId;
    private final BigDecimal initialDeposit;
    private final String currency;

    public AccountOpenedEvent(UUID aggregateId, String accountNumber, String accountType,
                             UUID customerId, BigDecimal initialDeposit, String currency) {
        super(aggregateId);
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.customerId = customerId;
        this.initialDeposit = initialDeposit;
        this.currency = currency;
    }
}

@Getter
@DomainEvent(eventType = "MoneyDeposited", version = 1)
public class MoneyDepositedEvent extends AbstractDomainEvent {
    private final BigDecimal amount;
    private final String source;
    private final String reference;
    private final String depositedBy;

    public MoneyDepositedEvent(UUID aggregateId, BigDecimal amount, String source,
                              String reference, String depositedBy) {
        super(aggregateId);
        this.amount = amount;
        this.source = source;
        this.reference = reference;
        this.depositedBy = depositedBy;
    }
}
```

> **📚 See the [Account Ledger Tutorial](./tutorial-account-ledger.md#step-1-domain-events)** for all 6 domain events with detailed explanations.

## Step 5: Create Aggregate Root

The aggregate enforces business rules and generates events:

```java
package com.firefly.common.eventsourcing.examples.ledger;

import com.firefly.common.eventsourcing.aggregate.AggregateRoot;
import com.firefly.common.eventsourcing.aggregate.EventHandler;
import com.firefly.common.eventsourcing.examples.ledger.events.*;
import com.firefly.common.eventsourcing.examples.ledger.exceptions.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class AccountLedger extends AggregateRoot {

    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean frozen;
    private boolean closed;
    private Instant openedAt;

    // Constructor for creating new accounts
    public AccountLedger(UUID id, String accountNumber, String accountType,
                        UUID customerId, BigDecimal initialDeposit, String currency) {
        super(id, "AccountLedger");

        // Validation
        if (initialDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative");
        }

        // Generate event
        applyChange(new AccountOpenedEvent(id, accountNumber, accountType,
                                          customerId, initialDeposit, currency));
    }

    // Constructor for loading from events
    public AccountLedger(UUID id) {
        super(id, "AccountLedger");
    }

    // Business method: Deposit money
    public void deposit(BigDecimal amount, String source, String reference, String depositedBy) {
        validateAccountIsActive();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        applyChange(new MoneyDepositedEvent(getId(), amount, source, reference, depositedBy));
    }

    // Event handler
    @EventHandler
    public void apply(AccountOpenedEvent event) {
        this.accountNumber = event.getAccountNumber();
        this.accountType = event.getAccountType();
        this.customerId = event.getCustomerId();
        this.balance = event.getInitialDeposit();
        this.currency = event.getCurrency();
        this.frozen = false;
        this.closed = false;
        this.openedAt = Instant.now();
    }

    @EventHandler
    public void apply(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.getAmount());
    }

    private void validateAccountIsActive() {
        if (closed) throw new AccountClosedException(getId());
        if (frozen) throw new AccountFrozenException(getId());
    }
}
```

> **📚 See the [Account Ledger Tutorial](./tutorial-account-ledger.md#step-2-the-aggregate-root)** for the complete implementation with all business methods and event handlers.

## Step 6: Create Service Layer

Use `@EventSourcingTransactional` for automatic event persistence:

```java
package com.firefly.common.eventsourcing.examples.ledger;

import com.firefly.common.eventsourcing.annotation.EventSourcingTransactional;
import com.firefly.common.eventsourcing.snapshot.SnapshotStore;
import com.firefly.common.eventsourcing.store.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountLedgerService {

    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;

    @EventSourcingTransactional
    public Mono<AccountLedger> openAccount(String accountNumber, String accountType,
                                          UUID customerId, BigDecimal initialDeposit,
                                          String currency) {
        UUID accountId = UUID.randomUUID();
        AccountLedger account = new AccountLedger(accountId, accountNumber, accountType,
                                                  customerId, initialDeposit, currency);

        return eventStore.appendEvents(
                accountId,
                "AccountLedger",
                account.getUncommittedEvents(),
                0L
            )
            .doOnSuccess(stream -> account.markEventsAsCommitted())
            .thenReturn(account);
    }

    @EventSourcingTransactional
    public Mono<AccountLedger> deposit(UUID accountId, BigDecimal amount, String source,
                                      String reference, String depositedBy) {
        return loadAggregate(accountId)
                .flatMap(account -> {
                    account.deposit(amount, source, reference, depositedBy);

                    return eventStore.appendEvents(
                            accountId,
                            "AccountLedger",
                            account.getUncommittedEvents(),
                            account.getCurrentVersion()
                        )
                        .doOnSuccess(stream -> account.markEventsAsCommitted())
                        .thenReturn(account);
                });
    }

    private Mono<AccountLedger> loadAggregate(UUID accountId) {
        // Try to load from snapshot first for performance
        return snapshotStore.loadLatestSnapshot(accountId, "AccountLedger")
                .flatMap(snapshot -> {
                    AccountLedger account = AccountLedger.fromSnapshot(
                        (AccountLedgerSnapshot) snapshot);

                    // Load events after snapshot
                    return eventStore.loadEventStream(accountId, "AccountLedger",
                                                     snapshot.getVersion() + 1)
                            .map(stream -> {
                                account.loadFromHistory(stream.getEvents());
                                return account;
                            });
                })
                .switchIfEmpty(
                    // No snapshot, load all events
                    eventStore.loadEventStream(accountId, "AccountLedger")
                            .map(stream -> {
                                AccountLedger account = new AccountLedger(accountId);
                                account.loadFromHistory(stream.getEvents());
                                return account;
                            })
                );
    }
}
```

> **📚 See the [Account Ledger Tutorial](./tutorial-account-ledger.md#step-4-the-service-layer)** for the complete service with all operations and snapshot management.

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

### 📚 **Deep Dive into Event Sourcing**
- **[Account Ledger Tutorial](./tutorial-account-ledger.md)** - Complete guide with all concepts explained
- **[Event Sourcing Explained](./event-sourcing-explained.md)** - Understanding the fundamentals
- **[Architecture Overview](./architecture.md)** - System design and patterns

### 🔧 **Advanced Topics**
- **[Testing Guide](./testing.md)** - Comprehensive testing with Testcontainers
- **[Configuration Reference](./configuration.md)** - All configuration options
- **[Database Schema](./database-schema.md)** - Understanding the data model
- **[API Reference](./api-reference.md)** - Complete API documentation

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