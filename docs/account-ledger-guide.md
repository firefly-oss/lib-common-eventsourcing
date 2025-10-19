# Account Ledger: Complete Event Sourcing Guide

This comprehensive guide uses an **Account Ledger** as a consistent example to explain all event sourcing concepts, from basic events to advanced features like snapshots, time travel, and the transactional outbox pattern.

## Table of Contents

1. [What is an Account Ledger?](#what-is-an-account-ledger)
2. [Domain Events](#domain-events)
3. [The Account Aggregate](#the-account-aggregate)
4. [Event Store Integration](#event-store-integration)
5. [Snapshots for Performance](#snapshots-for-performance)
6. [Transactional Outbox Pattern](#transactional-outbox-pattern)
7. [Time Travel & Temporal Queries](#time-travel--temporal-queries)
8. [Multi-Tenancy & Enterprise Features](#multi-tenancy--enterprise-features)
9. [Complete Working Example](#complete-working-example)
10. [Testing Strategies](#testing-strategies)

---

## What is an Account Ledger?

An **Account Ledger** is a financial record that tracks all transactions (debits and credits) for a bank account. It's a perfect example for event sourcing because:

- **Immutable History**: Financial regulations require complete audit trails
- **Temporal Queries**: "What was the balance on December 31st?"
- **Compliance**: Every transaction must be traceable
- **Reconciliation**: Balance = sum of all transactions

### Traditional vs Event Sourcing Approach

**Traditional Database (Current State Only):**
```sql
-- accounts table
id          | account_number | balance  | updated_at
------------|----------------|----------|------------
acc-123     | 1234567890     | 1250.00  | 2025-10-18

-- ❌ Lost information:
-- - How did we get to $1250?
-- - What transactions occurred?
-- - Who made the changes?
-- - When exactly did each change happen?
```

**Event Sourcing (Complete History):**
```json
[
  {
    "eventType": "AccountOpened",
    "accountNumber": "1234567890",
    "initialDeposit": 1000.00,
    "timestamp": "2025-01-15T10:00:00Z",
    "customerId": "cust-456"
  },
  {
    "eventType": "MoneyDeposited",
    "amount": 500.00,
    "source": "Wire Transfer",
    "timestamp": "2025-02-20T14:30:00Z",
    "reference": "REF-789"
  },
  {
    "eventType": "MoneyWithdrawn",
    "amount": 250.00,
    "destination": "ATM Withdrawal",
    "timestamp": "2025-03-10T09:15:00Z",
    "atmId": "ATM-001"
  }
]
// ✅ Current balance: $1250 = $1000 + $500 - $250
// ✅ Complete audit trail
// ✅ Can answer: "What was balance on Feb 1st?" → $1000
```

---

## Domain Events

Events are **immutable facts** about what happened in the account's lifecycle. They use **past tense** names because they represent things that already occurred.

### Core Account Events

```java
package com.firefly.banking.ledger.events;

import com.firefly.common.eventsourcing.annotation.DomainEvent;
import com.firefly.common.eventsourcing.domain.AbstractDomainEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event: Account was opened with initial deposit
 */
@DomainEvent("account.opened")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountOpenedEvent extends AbstractDomainEvent {
    private String accountNumber;
    private String accountType;      // CHECKING, SAVINGS, etc.
    private UUID customerId;
    private BigDecimal initialDeposit;
    private String currency;
}

/**
 * Event: Money was deposited into the account
 */
@DomainEvent("money.deposited")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyDepositedEvent extends AbstractDomainEvent {
    private BigDecimal amount;
    private String source;           // "Wire Transfer", "Check Deposit", etc.
    private String reference;        // External reference number
    private String depositedBy;      // User who made the deposit
}

/**
 * Event: Money was withdrawn from the account
 */
@DomainEvent("money.withdrawn")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyWithdrawnEvent extends AbstractDomainEvent {
    private BigDecimal amount;
    private String destination;      // "ATM", "Wire Transfer", etc.
    private String reference;
    private String withdrawnBy;
}

/**
 * Event: Account was frozen (e.g., due to suspicious activity)
 */
@DomainEvent("account.frozen")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountFrozenEvent extends AbstractDomainEvent {
    private String reason;
    private String frozenBy;
}

/**
 * Event: Account was unfrozen
 */
@DomainEvent("account.unfrozen")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountUnfrozenEvent extends AbstractDomainEvent {
    private String reason;
    private String unfrozenBy;
}

/**
 * Event: Account was closed
 */
@DomainEvent("account.closed")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosedEvent extends AbstractDomainEvent {
    private String reason;
    private BigDecimal finalBalance;
    private String closedBy;
}
```

### Event Design Principles

1. **Past Tense Names**: `AccountOpened`, not `OpenAccount`
2. **Immutable**: Once created, events never change
3. **Self-Contained**: Include all necessary data
4. **Business Language**: Use domain terminology
5. **Rich Metadata**: Track who, when, why, where

---

## The Account Aggregate

The **Account** aggregate is the consistency boundary that:
- Enforces business rules
- Generates events
- Maintains current state (derived from events)

```java
package com.firefly.banking.ledger;

import com.firefly.common.eventsourcing.aggregate.AggregateRoot;
import com.firefly.banking.ledger.events.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class AccountLedger extends AggregateRoot {
    
    // Current state (derived from events)
    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean frozen;
    private boolean closed;
    private Instant openedAt;
    private Instant lastTransactionAt;
    
    // For reconstruction from events
    public AccountLedger(UUID id) {
        super(id, "AccountLedger");
    }
    
    // Constructor for new accounts (command)
    public AccountLedger(UUID id, String accountNumber, String accountType, 
                        UUID customerId, BigDecimal initialDeposit, String currency) {
        super(id, "AccountLedger");
        
        // Validate business rules
        validateAccountOpening(accountNumber, accountType, customerId, initialDeposit, currency);
        
        // Generate event
        applyChange(AccountOpenedEvent.builder()
                .aggregateId(id)
                .accountNumber(accountNumber)
                .accountType(accountType)
                .customerId(customerId)
                .initialDeposit(initialDeposit)
                .currency(currency)
                .build());
    }
    
    // Business logic: Deposit money
    public void deposit(BigDecimal amount, String source, String reference, String depositedBy) {
        // Validate business rules
        if (closed) {
            throw new AccountClosedException("Cannot deposit to closed account");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Deposit amount must be positive");
        }
        
        // Generate event
        applyChange(MoneyDepositedEvent.builder()
                .aggregateId(getId())
                .amount(amount)
                .source(source)
                .reference(reference)
                .depositedBy(depositedBy)
                .build());
    }
    
    // Business logic: Withdraw money
    public void withdraw(BigDecimal amount, String destination, String reference, String withdrawnBy) {
        // Validate business rules
        if (closed) {
            throw new AccountClosedException("Cannot withdraw from closed account");
        }
        if (frozen) {
            throw new AccountFrozenException("Cannot withdraw from frozen account");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be positive");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds: balance=" + balance + ", requested=" + amount);
        }
        
        // Generate event
        applyChange(MoneyWithdrawnEvent.builder()
                .aggregateId(getId())
                .amount(amount)
                .destination(destination)
                .reference(reference)
                .withdrawnBy(withdrawnBy)
                .build());
    }
    
    // Event handlers (how state changes)
    private void on(AccountOpenedEvent event) {
        this.accountNumber = event.getAccountNumber();
        this.accountType = event.getAccountType();
        this.customerId = event.getCustomerId();
        this.balance = event.getInitialDeposit();
        this.currency = event.getCurrency();
        this.frozen = false;
        this.closed = false;
        this.openedAt = event.getEventTimestamp();
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.getAmount());
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(AccountFrozenEvent event) {
        this.frozen = true;
    }
    
    private void on(AccountUnfrozenEvent event) {
        this.frozen = false;
    }
    
    private void on(AccountClosedEvent event) {
        this.closed = true;
    }
    
    // Validation methods
    private void validateAccountOpening(String accountNumber, String accountType, 
                                       UUID customerId, BigDecimal initialDeposit, String currency) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        if (accountType == null || accountType.isBlank()) {
            throw new IllegalArgumentException("Account type is required");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (initialDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }
}
```

---

## Event Store Integration

The Event Store persists events and reconstructs aggregates from their event history.

### Saving Events (Commands)

```java
package com.firefly.banking.ledger.service;

import com.firefly.common.eventsourcing.annotation.EventSourcingTransactional;
import com.firefly.common.eventsourcing.store.EventStore;
import com.firefly.banking.ledger.AccountLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLedgerService {

    private final EventStore eventStore;

    /**
     * Opens a new account with initial deposit.
     * Uses @EventSourcingTransactional for ACID guarantees.
     */
    @EventSourcingTransactional
    public Mono<AccountLedger> openAccount(String accountNumber, String accountType,
                                          UUID customerId, BigDecimal initialDeposit, String currency) {
        UUID accountId = UUID.randomUUID();

        // Create new aggregate (generates AccountOpenedEvent)
        AccountLedger account = new AccountLedger(accountId, accountNumber, accountType,
                                                  customerId, initialDeposit, currency);

        // Persist events to event store
        return eventStore.appendEvents(
                accountId,
                "AccountLedger",
                account.getUncommittedEvents(),
                0L  // Expected version for new aggregate
            )
            .doOnSuccess(stream -> {
                account.markEventsAsCommitted();
                log.info("Account opened: accountId={}, accountNumber={}, balance={}",
                        accountId, accountNumber, initialDeposit);
            })
            .thenReturn(account);
    }

    /**
     * Deposits money into an account.
     * Loads current state, applies business logic, saves new events.
     */
    @EventSourcingTransactional(retryOnConcurrencyConflict = true, maxRetries = 3)
    public Mono<AccountLedger> deposit(UUID accountId, BigDecimal amount,
                                      String source, String reference, String depositedBy) {
        return loadAccount(accountId)
            .flatMap(account -> {
                // Apply business logic (generates MoneyDepositedEvent)
                account.deposit(amount, source, reference, depositedBy);

                // Save new events
                return eventStore.appendEvents(
                        accountId,
                        "AccountLedger",
                        account.getUncommittedEvents(),
                        account.getVersion()  // Optimistic locking
                    )
                    .doOnSuccess(stream -> {
                        account.markEventsAsCommitted();
                        log.info("Money deposited: accountId={}, amount={}, newBalance={}",
                                accountId, amount, account.getBalance());
                    })
                    .thenReturn(account);
            });
    }

    /**
     * Withdraws money from an account.
     */
    @EventSourcingTransactional(retryOnConcurrencyConflict = true, maxRetries = 3)
    public Mono<AccountLedger> withdraw(UUID accountId, BigDecimal amount,
                                       String destination, String reference, String withdrawnBy) {
        return loadAccount(accountId)
            .flatMap(account -> {
                // Apply business logic (generates MoneyWithdrawnEvent)
                account.withdraw(amount, destination, reference, withdrawnBy);

                // Save new events
                return eventStore.appendEvents(
                        accountId,
                        "AccountLedger",
                        account.getUncommittedEvents(),
                        account.getVersion()
                    )
                    .doOnSuccess(stream -> {
                        account.markEventsAsCommitted();
                        log.info("Money withdrawn: accountId={}, amount={}, newBalance={}",
                                accountId, amount, account.getBalance());
                    })
                    .thenReturn(account);
            });
    }

    /**
     * Loads an account by replaying all its events.
     */
    private Mono<AccountLedger> loadAccount(UUID accountId) {
        return eventStore.loadEventStream(accountId, "AccountLedger")
            .map(stream -> {
                AccountLedger account = new AccountLedger(accountId);
                account.loadFromHistory(stream.getEvents());
                log.debug("Loaded account: accountId={}, version={}, balance={}",
                         accountId, account.getVersion(), account.getBalance());
                return account;
            });
    }
}
```

### How Event Replay Works

```java
// Step 1: Load events from database
List<Event> events = [
    AccountOpenedEvent(initialDeposit=1000),
    MoneyDepositedEvent(amount=500),
    MoneyWithdrawnEvent(amount=250)
];

// Step 2: Create empty aggregate
AccountLedger account = new AccountLedger(accountId);

// Step 3: Replay events to rebuild state
account.loadFromHistory(events);
// Internally calls:
//   on(AccountOpenedEvent)   → balance = 1000
//   on(MoneyDepositedEvent)  → balance = 1500
//   on(MoneyWithdrawnEvent)  → balance = 1250

// Step 4: Current state is ready
assert account.getBalance().equals(BigDecimal.valueOf(1250));
assert account.getVersion() == 3;
```

---

## Snapshots for Performance

As accounts accumulate thousands of transactions, replaying all events becomes slow. **Snapshots** solve this by saving the state at specific points.

### Why Use AbstractSnapshot?

The library provides `AbstractSnapshot` base class that handles common snapshot functionality:

**Benefits:**
- ✅ **Reduces Boilerplate**: No need to implement `aggregateId`, `version`, `createdAt` getters
- ✅ **Built-in Validation**: Ensures required fields are not null and version is valid
- ✅ **Utility Methods**: Provides `isOlderThan()`, `isNewerThan()`, `isForVersion()` out of the box
- ✅ **Consistent Behavior**: Standard `equals()`, `hashCode()`, and `toString()` implementations
- ✅ **Schema Evolution**: Support for snapshot versioning when your aggregate structure changes

**What You Need to Implement:**
1. Define your aggregate-specific state fields
2. Implement `getSnapshotType()` to return the aggregate type
3. Optionally override `getSnapshotVersion()` for schema versioning

### Account Snapshot

```java
package com.firefly.banking.ledger.snapshot;

import com.firefly.common.eventsourcing.snapshot.AbstractSnapshot;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of AccountLedger state at a specific version.
 *
 * Extends AbstractSnapshot which provides:
 * - Common metadata management (aggregateId, version, createdAt)
 * - Built-in validation
 * - Utility methods (isOlderThan, isNewerThan, etc.)
 * - Consistent equals/hashCode/toString
 */
@Getter
public class AccountLedgerSnapshot extends AbstractSnapshot {

    // Account identification
    private final String accountNumber;
    private final String accountType;
    private final UUID customerId;

    // Financial state
    private final BigDecimal balance;
    private final String currency;

    // Account status
    private final boolean frozen;
    private final boolean closed;

    // Timestamps
    private final Instant openedAt;
    private final Instant lastTransactionAt;

    public AccountLedgerSnapshot(UUID aggregateId, long version, Instant createdAt,
                                String accountNumber, String accountType, UUID customerId,
                                BigDecimal balance, String currency,
                                boolean frozen, boolean closed,
                                Instant openedAt, Instant lastTransactionAt) {
        super(aggregateId, version, createdAt);
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.frozen = frozen;
        this.closed = closed;
        this.openedAt = openedAt;
        this.lastTransactionAt = lastTransactionAt;
    }

    @Override
    public String getSnapshotType() {
        return "AccountLedger";
    }
}
```

### Creating Snapshots

```java
// In AccountLedger class, add method to create snapshot
public AccountLedgerSnapshot createSnapshot() {
    return new AccountLedgerSnapshot(
            getId(),
            getCurrentVersion(),
            Instant.now(),
            accountNumber,
            accountType,
            customerId,
            balance,
            currency,
            frozen,
            closed,
            openedAt,
            lastTransactionAt
    );
}

// Method to restore from snapshot
public static AccountLedger fromSnapshot(AccountLedgerSnapshot snapshot) {
    AccountLedger account = new AccountLedger(snapshot.getAggregateId());
    account.accountNumber = snapshot.getAccountNumber();
    account.accountType = snapshot.getAccountType();
    account.customerId = snapshot.getCustomerId();
    account.balance = snapshot.getBalance();
    account.currency = snapshot.getCurrency();
    account.frozen = snapshot.isFrozen();
    account.closed = snapshot.isClosed();
    account.openedAt = snapshot.getOpenedAt();
    account.lastTransactionAt = snapshot.getLastTransactionAt();
    account.setCurrentVersion(snapshot.getVersion());
    return account;
}
```

### Snapshot-Optimized Loading

```java
@Service
@RequiredArgsConstructor
public class AccountLedgerService {

    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;

    /**
     * Loads account using snapshot + recent events (much faster!)
     */
    private Mono<AccountLedger> loadAccountWithSnapshot(UUID accountId) {
        return snapshotStore.loadLatestSnapshot(accountId, "AccountLedger")
            .flatMap(snapshot -> {
                // Restore from snapshot
                AccountLedgerSnapshot accountSnapshot = snapshot.getData(AccountLedgerSnapshot.class);
                AccountLedger account = AccountLedger.fromSnapshot(accountSnapshot);

                // Load only events AFTER snapshot
                return eventStore.loadEventStream(accountId, "AccountLedger", snapshot.getVersion())
                    .map(stream -> {
                        account.loadFromHistory(stream.getEvents());
                        log.debug("Loaded account from snapshot: accountId={}, snapshotVersion={}, currentVersion={}",
                                 accountId, snapshot.getVersion(), account.getVersion());
                        return account;
                    });
            })
            .switchIfEmpty(
                // No snapshot exists, load all events
                loadAccount(accountId)
            );
    }
}
```

### Performance Comparison

```
Account with 10,000 transactions:

WITHOUT Snapshots:
- Load all 10,000 events from database
- Replay all 10,000 events
- Time: ~5000ms

WITH Snapshots (threshold=100):
- Load latest snapshot (at version 9,900)
- Load only 100 recent events
- Replay only 100 events
- Time: ~50ms

🚀 100x faster!
```

---

## Transactional Outbox Pattern

The **Transactional Outbox Pattern** ensures reliable event publishing to external systems (Kafka, RabbitMQ, etc.) by:
1. Saving events to the outbox table in the **same transaction** as the event store
2. Processing outbox entries asynchronously
3. Retrying failed publications with exponential backoff
4. Guaranteeing **at-least-once delivery**

### How It Works

```
┌─────────────────────────────────────────────────────────────┐
│ Transaction (ACID)                                          │
│                                                             │
│  1. Save events to events table                            │
│  2. Save events to event_outbox table                      │
│                                                             │
│  ✅ Both succeed or both fail (atomicity)                  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ Background Processor (Async)                                │
│                                                             │
│  1. Poll outbox for PENDING entries                        │
│  2. Publish to Kafka/RabbitMQ                              │
│  3. Mark as COMPLETED or retry on failure                  │
│                                                             │
│  🔄 Retry with exponential backoff                         │
└─────────────────────────────────────────────────────────────┘
```

### Integration with Account Ledger

The outbox is automatically integrated when you use `@EventSourcingTransactional`:

```java
@Service
@RequiredArgsConstructor
public class AccountLedgerService {

    private final EventStore eventStore;

    @EventSourcingTransactional  // ← Automatically uses outbox!
    public Mono<AccountLedger> deposit(UUID accountId, BigDecimal amount,
                                      String source, String reference, String depositedBy) {
        return loadAccount(accountId)
            .flatMap(account -> {
                account.deposit(amount, source, reference, depositedBy);

                return eventStore.appendEvents(
                        accountId,
                        "AccountLedger",
                        account.getUncommittedEvents(),
                        account.getVersion()
                    )
                    .thenReturn(account);
                    // ✅ Events saved to events table
                    // ✅ Events saved to event_outbox table (same transaction)
                    // ✅ Background processor will publish to Kafka
            });
    }
}
```

### Outbox Entry Example

When you deposit $500, this entry is created in the `event_outbox` table:

```json
{
  "outbox_id": "550e8400-e29b-41d4-a716-446655440000",
  "aggregate_id": "acc-123",
  "aggregate_type": "AccountLedger",
  "event_type": "money.deposited",
  "event_data": {
    "amount": 500.00,
    "source": "Wire Transfer",
    "reference": "REF-789",
    "depositedBy": "user-456"
  },
  "status": "PENDING",
  "priority": 5,
  "retry_count": 0,
  "max_retries": 3,
  "created_at": "2025-10-18T10:00:00Z",
  "tenant_id": "tenant-1",
  "correlation_id": "corr-123",
  "partition_key": "acc-123"
}
```

### Outbox Processing Flow

```java
// Background processor runs every 5 seconds
@Scheduled(fixedDelay = 5000)
public void processPendingEntries() {
    // 1. Find PENDING entries (ordered by priority)
    List<OutboxEntry> pending = outboxRepository.findPendingEntries(100);

    // 2. For each entry:
    for (OutboxEntry entry : pending) {
        // Mark as PROCESSING
        entry.setStatus("PROCESSING");

        try {
            // Publish to Kafka
            kafkaTemplate.send("account-events", entry.getEventData());

            // Mark as COMPLETED
            entry.setStatus("COMPLETED");
            entry.setProcessedAt(Instant.now());
        } catch (Exception e) {
            // Mark as FAILED, schedule retry
            entry.setStatus("FAILED");
            entry.setRetryCount(entry.getRetryCount() + 1);
            entry.setNextRetryAt(calculateNextRetry(entry.getRetryCount()));
            entry.setLastError(e.getMessage());
        }
    }
}

// Exponential backoff: 2^retry_count minutes
private Instant calculateNextRetry(int retryCount) {
    long delayMinutes = (long) Math.pow(2, retryCount);
    return Instant.now().plusSeconds(delayMinutes * 60);
}
```

### Dead Letter Queue

Entries that exceed `max_retries` are moved to a **dead letter queue** for manual investigation:

```sql
-- Find permanently failed entries
SELECT * FROM event_outbox
WHERE status = 'FAILED'
  AND retry_count >= max_retries
ORDER BY created_at ASC;
```

---

## Time Travel & Temporal Queries

One of the most powerful features of event sourcing is the ability to reconstruct state at any point in time.

### Reconstruct Account at Specific Date

```java
@Service
@RequiredArgsConstructor
public class AccountLedgerService {

    private final EventStore eventStore;

    /**
     * Gets the account balance as it was on a specific date.
     * Perfect for:
     * - End-of-month statements
     * - Tax reporting
     * - Audits
     * - Dispute resolution
     */
    public Mono<BigDecimal> getBalanceAtDate(UUID accountId, Instant targetDate) {
        return eventStore.loadEventStream(accountId, "AccountLedger")
            .map(stream -> {
                AccountLedger account = new AccountLedger(accountId);

                // Replay only events up to target date
                List<Event> eventsUpToDate = stream.getEvents().stream()
                    .filter(event -> event.getEventTimestamp().isBefore(targetDate)
                                  || event.getEventTimestamp().equals(targetDate))
                    .toList();

                account.loadFromHistory(eventsUpToDate);

                log.info("Balance at {}: {}", targetDate, account.getBalance());
                return account.getBalance();
            });
    }

    /**
     * Gets the account state at a specific version.
     */
    public Mono<AccountLedger> getAccountAtVersion(UUID accountId, long targetVersion) {
        return eventStore.loadEventStream(accountId, "AccountLedger", 0L, targetVersion)
            .map(stream -> {
                AccountLedger account = new AccountLedger(accountId);
                account.loadFromHistory(stream.getEvents());
                return account;
            });
    }
}
```

### Example: Monthly Statement

```java
// Generate statement for January 2025
Instant startOfJanuary = Instant.parse("2025-01-01T00:00:00Z");
Instant endOfJanuary = Instant.parse("2025-01-31T23:59:59Z");

// Get opening balance
BigDecimal openingBalance = accountService.getBalanceAtDate(accountId, startOfJanuary).block();

// Get closing balance
BigDecimal closingBalance = accountService.getBalanceAtDate(accountId, endOfJanuary).block();

// Get all transactions in January
List<Event> januaryTransactions = eventStore
    .streamEventsByTimeRange(startOfJanuary, endOfJanuary)
    .filter(envelope -> envelope.getAggregateId().equals(accountId))
    .map(EventEnvelope::getEvent)
    .collectList()
    .block();

// Generate statement
System.out.println("Account Statement - January 2025");
System.out.println("Opening Balance: $" + openingBalance);
januaryTransactions.forEach(event -> {
    if (event instanceof MoneyDepositedEvent deposit) {
        System.out.println("  + $" + deposit.getAmount() + " - " + deposit.getSource());
    } else if (event instanceof MoneyWithdrawnEvent withdrawal) {
        System.out.println("  - $" + withdrawal.getAmount() + " - " + withdrawal.getDestination());
    }
});
System.out.println("Closing Balance: $" + closingBalance);
```

### Audit Trail Query

```java
/**
 * Gets complete audit trail for an account.
 * Shows who did what, when, and why.
 */
public Mono<List<AuditEntry>> getAuditTrail(UUID accountId) {
    return eventStore.loadEventStream(accountId, "AccountLedger")
        .map(stream -> stream.getEvents().stream()
            .map(event -> {
                String action = event.getEventType();
                String user = event.getMetadata().get("userId");
                Instant timestamp = event.getEventTimestamp();
                String details = formatEventDetails(event);

                return new AuditEntry(action, user, timestamp, details);
            })
            .toList()
        );
}

// Example output:
// 2025-01-15 10:00:00 | user-123 | AccountOpened      | Initial deposit: $1000
// 2025-02-20 14:30:00 | user-456 | MoneyDeposited     | Wire Transfer: $500 (REF-789)
// 2025-03-10 09:15:00 | user-123 | MoneyWithdrawn     | ATM Withdrawal: $250 (ATM-001)
```

---

## Multi-Tenancy & Enterprise Features

The library includes production-ready features for enterprise applications.

### Multi-Tenancy

Isolate accounts by tenant (e.g., different banks, branches, or organizations):

```java
@Service
@RequiredArgsConstructor
public class AccountLedgerService {

    private final EventStore eventStore;

    @EventSourcingTransactional
    public Mono<AccountLedger> openAccount(String tenantId, String accountNumber,
                                          String accountType, UUID customerId,
                                          BigDecimal initialDeposit, String currency) {
        // Set tenant context in MDC
        EventSourcingLoggingContext.setTenantId(tenantId);

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
            .doFinally(signal -> EventSourcingLoggingContext.clearTenantId())
            .thenReturn(account);
            // ✅ All events tagged with tenant_id
            // ✅ Can query: "Show all accounts for tenant-1"
    }

    /**
     * Get all accounts for a specific tenant.
     */
    public Flux<UUID> getAccountsForTenant(String tenantId) {
        return eventStore.streamEventsByMetadata(Map.of("tenantId", tenantId))
            .filter(envelope -> envelope.getEventType().equals("account.opened"))
            .map(EventEnvelope::getAggregateId)
            .distinct();
    }
}
```

### Distributed Tracing

Track requests across microservices using correlation IDs:

```java
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountLedgerService accountService;

    @PostMapping("/{accountId}/deposit")
    public Mono<AccountResponse> deposit(
            @PathVariable UUID accountId,
            @RequestBody DepositRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId) {

        // Set correlation ID in MDC
        EventSourcingLoggingContext.setCorrelationId(correlationId);

        return accountService.deposit(
                accountId,
                request.amount(),
                request.source(),
                request.reference(),
                request.depositedBy()
            )
            .map(account -> new AccountResponse(account))
            .doFinally(signal -> EventSourcingLoggingContext.clearCorrelationId());
            // ✅ All events tagged with correlation_id
            // ✅ Can trace: "Show all events for request corr-123"
    }
}
```

### Data Integrity with Checksums

Events are automatically checksummed to detect tampering:

```sql
-- Events table includes checksum
SELECT event_id, event_type, checksum
FROM events
WHERE aggregate_id = 'acc-123';

-- Verify integrity
SELECT event_id,
       checksum,
       encode(digest(event_data::text, 'sha256'), 'hex') as calculated_checksum,
       checksum = encode(digest(event_data::text, 'sha256'), 'hex') as is_valid
FROM events
WHERE aggregate_id = 'acc-123';
```

### Audit Fields

All events include audit information:

```sql
SELECT
    event_type,
    created_at,
    created_by,
    tenant_id,
    correlation_id,
    causation_id
FROM events
WHERE aggregate_id = 'acc-123'
ORDER BY aggregate_version;
```

---

## Complete Working Example

Here's a complete end-to-end example showing all features:

```java
package com.firefly.banking.ledger.example;

import com.firefly.banking.ledger.AccountLedger;
import com.firefly.banking.ledger.service.AccountLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountLedgerExample implements CommandLineRunner {

    private final AccountLedgerService accountService;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Account Ledger Example ===");

        // 1. Open a new account
        UUID customerId = UUID.randomUUID();
        AccountLedger account = accountService.openAccount(
                "ACC-2025-001",
                "CHECKING",
                customerId,
                BigDecimal.valueOf(1000.00),
                "USD"
            ).block();

        UUID accountId = account.getId();
        log.info("✅ Account opened: {} with balance ${}",
                account.getAccountNumber(), account.getBalance());

        // 2. Make some deposits
        account = accountService.deposit(
                accountId,
                BigDecimal.valueOf(500.00),
                "Wire Transfer",
                "REF-001",
                "user-123"
            ).block();
        log.info("✅ Deposited $500, new balance: ${}", account.getBalance());

        account = accountService.deposit(
                accountId,
                BigDecimal.valueOf(250.00),
                "Check Deposit",
                "CHK-456",
                "user-123"
            ).block();
        log.info("✅ Deposited $250, new balance: ${}", account.getBalance());

        // 3. Make a withdrawal
        account = accountService.withdraw(
                accountId,
                BigDecimal.valueOf(300.00),
                "ATM Withdrawal",
                "ATM-789",
                "user-123"
            ).block();
        log.info("✅ Withdrew $300, new balance: ${}", account.getBalance());

        // 4. Check balance at specific point in time
        Instant yesterday = Instant.now().minusSeconds(86400);
        BigDecimal balanceYesterday = accountService.getBalanceAtDate(accountId, yesterday).block();
        log.info("📅 Balance yesterday: ${}", balanceYesterday);

        // 5. Get audit trail
        var auditTrail = accountService.getAuditTrail(accountId).block();
        log.info("📋 Audit Trail:");
        auditTrail.forEach(entry ->
            log.info("  {} | {} | {}", entry.timestamp(), entry.user(), entry.action())
        );

        // 6. Demonstrate snapshot
        // (Snapshot automatically created after 50 events)
        for (int i = 0; i < 60; i++) {
            accountService.deposit(
                    accountId,
                    BigDecimal.valueOf(10.00),
                    "Automated Deposit",
                    "AUTO-" + i,
                    "system"
                ).block();
        }
        log.info("✅ Made 60 more deposits, snapshot should be created");

        // 7. Load account (will use snapshot + recent events)
        account = accountService.loadAccountWithSnapshot(accountId).block();
        log.info("✅ Loaded account from snapshot, version: {}, balance: ${}",
                account.getVersion(), account.getBalance());

        log.info("=== Example Complete ===");
    }
}
```

### Expected Output

```
=== Account Ledger Example ===
✅ Account opened: ACC-2025-001 with balance $1000.00
✅ Deposited $500, new balance: $1500.00
✅ Deposited $250, new balance: $1750.00
✅ Withdrew $300, new balance: $1450.00
📅 Balance yesterday: $0.00
📋 Audit Trail:
  2025-10-18T10:00:00Z | user-123 | AccountOpened
  2025-10-18T10:01:00Z | user-123 | MoneyDeposited
  2025-10-18T10:02:00Z | user-123 | MoneyDeposited
  2025-10-18T10:03:00Z | user-123 | MoneyWithdrawn
✅ Made 60 more deposits, snapshot should be created
✅ Loaded account from snapshot, version: 64, balance: $2050.00
=== Example Complete ===
```

---

## Testing Strategies

### Unit Testing Aggregates

```java
@Test
void shouldCalculateBalanceCorrectly() {
    // Given: New account
    UUID accountId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    AccountLedger account = new AccountLedger(
            accountId, "ACC-001", "CHECKING", customerId,
            BigDecimal.valueOf(1000), "USD"
    );

    // When: Deposit and withdraw
    account.deposit(BigDecimal.valueOf(500), "Wire", "REF-1", "user-1");
    account.withdraw(BigDecimal.valueOf(200), "ATM", "REF-2", "user-1");

    // Then: Balance is correct
    assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1300));
    assertThat(account.getVersion()).isEqualTo(3);
    assertThat(account.getUncommittedEventCount()).isEqualTo(3);
}

@Test
void shouldPreventOverdraft() {
    // Given: Account with $100
    AccountLedger account = new AccountLedger(
            UUID.randomUUID(), "ACC-001", "CHECKING", UUID.randomUUID(),
            BigDecimal.valueOf(100), "USD"
    );

    // When/Then: Cannot withdraw $200
    assertThatThrownBy(() ->
        account.withdraw(BigDecimal.valueOf(200), "ATM", "REF-1", "user-1")
    ).isInstanceOf(InsufficientFundsException.class);
}
```

### Integration Testing with Event Store

```java
@SpringBootTest
@Testcontainers
class AccountLedgerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private AccountLedgerService accountService;

    @Autowired
    private EventStore eventStore;

    @Test
    void shouldPersistAndReloadAccount() {
        // Given: Create and save account
        UUID customerId = UUID.randomUUID();
        AccountLedger account = accountService.openAccount(
                "ACC-TEST-001", "CHECKING", customerId,
                BigDecimal.valueOf(1000), "USD"
            ).block();

        UUID accountId = account.getId();

        // Make some transactions
        accountService.deposit(accountId, BigDecimal.valueOf(500), "Wire", "REF-1", "user-1").block();
        accountService.withdraw(accountId, BigDecimal.valueOf(200), "ATM", "REF-2", "user-1").block();

        // When: Reload from event store
        AccountLedger reloaded = eventStore.loadEventStream(accountId, "AccountLedger")
            .map(stream -> {
                AccountLedger acc = new AccountLedger(accountId);
                acc.loadFromHistory(stream.getEvents());
                return acc;
            })
            .block();

        // Then: State is correct
        assertThat(reloaded.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1300));
        assertThat(reloaded.getVersion()).isEqualTo(3);
        assertThat(reloaded.getAccountNumber()).isEqualTo("ACC-TEST-001");
    }
}
```

---

## Summary

The **Account Ledger** example demonstrates all key event sourcing concepts:

✅ **Domain Events** - Immutable facts about account transactions
✅ **Aggregates** - Business logic and state management
✅ **Event Store** - Persistence and event replay
✅ **Snapshots** - Performance optimization for high-volume accounts
✅ **Transactional Outbox** - Reliable event publishing
✅ **Time Travel** - Historical state reconstruction
✅ **Multi-Tenancy** - Tenant isolation
✅ **Audit Trail** - Complete transaction history
✅ **Data Integrity** - Checksums and validation

### Next Steps

1. **Implement Your Domain** - Use this pattern for your business domain
2. **Add Projections** - Create read models for queries
3. **Configure Snapshots** - Tune threshold for your event volume
4. **Set Up Monitoring** - Track event throughput and latencies
5. **Test Thoroughly** - Use the testing strategies shown above

For more information, see:
- [Quick Start Guide](./quick-start.md)
- [API Reference](./api-reference.md)
- [Configuration Guide](./configuration.md)
- [Testing Guide](./testing.md)

