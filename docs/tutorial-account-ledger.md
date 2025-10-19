# Complete Tutorial: Building an Account Ledger with Event Sourcing

This comprehensive tutorial will guide you through building a production-ready **Account Ledger** system using the Firefly Event Sourcing Library. You'll learn not just *how* to use event sourcing, but *why* each component is necessary and how they work together.

## Table of Contents

1. [What We're Building](#what-were-building)
2. [Why Event Sourcing for Account Ledgers?](#why-event-sourcing-for-account-ledgers)
3. [Architecture Overview](#architecture-overview)
4. [Step 1: Domain Events](#step-1-domain-events)
5. [Step 2: The Aggregate Root](#step-2-the-aggregate-root)
6. [Step 3: Snapshots for Performance](#step-3-snapshots-for-performance)
7. [Step 4: The Service Layer](#step-4-the-service-layer)
8. [Step 5: Read Models](#step-5-read-models)
9. [Step 6: Projections](#step-6-projections)
10. [Step 7: Putting It All Together](#step-7-putting-it-all-together)
11. [Advanced Topics](#advanced-topics)

---

## What We're Building

We're building an **Account Ledger** - a system that tracks all financial transactions for bank accounts. This is a perfect example because:

- **Regulatory Compliance**: Banks must maintain complete audit trails
- **High Volume**: Thousands of transactions per second
- **Critical Accuracy**: Every cent must be accounted for
- **Complex Queries**: "What was the balance on March 15th at 3:47 PM?"

### Key Features

✅ **Complete Transaction History** - Every deposit, withdrawal, freeze, and closure  
✅ **Time Travel** - Reconstruct account state at any point in time  
✅ **Snapshots** - Performance optimization for accounts with millions of transactions  
✅ **Read Models** - Separate read and write models for optimal performance
✅ **Business Rules** - Prevent overdrafts, frozen account transactions, etc.

---

## Why Event Sourcing for Account Ledgers?

### The Problem with Traditional Approaches

**Traditional Database (Current State Only):**
```sql
-- Account table
id          | balance | status  | last_updated
------------|---------|---------|-------------
acc-001     | 1500.00 | ACTIVE  | 2025-10-18

-- ❌ Questions we CAN'T answer:
-- - How did we get to $1,500?
-- - What was the balance yesterday?
-- - Who made the last transaction?
-- - Was the account ever frozen?
```

**Event Sourcing (Complete History):**
```json
[
  {"type": "AccountOpened", "amount": 1000.00, "timestamp": "2025-10-01T10:00:00Z"},
  {"type": "MoneyDeposited", "amount": 500.00, "timestamp": "2025-10-15T14:30:00Z"},
  {"type": "MoneyWithdrawn", "amount": 200.00, "timestamp": "2025-10-17T09:15:00Z"},
  {"type": "MoneyDeposited", "amount": 200.00, "timestamp": "2025-10-18T11:00:00Z"}
]

// ✅ We can answer ANY question about the account's history
// ✅ Current balance: $1,500 (calculated from events)
// ✅ Balance on Oct 16: $1,300
// ✅ Total deposits: $700
// ✅ Complete audit trail for regulators
```

### Real-World Benefits

1. **Regulatory Compliance**: Complete audit trail for SOX, PCI-DSS, GDPR
2. **Fraud Detection**: Analyze transaction patterns over time
3. **Dispute Resolution**: "I didn't make that withdrawal!" - Check the events
4. **Business Intelligence**: "What's our average daily transaction volume?"
5. **Testing**: Replay production events to test new business rules

---

## Architecture Overview

Event sourcing applications follow a specific architecture pattern. Here's how all the pieces fit together:

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT REQUEST                           │
│                    (Deposit $100 to account)                    │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SERVICE LAYER (Write Side)                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AccountLedgerService.deposit(accountId, amount)          │   │
│  │  1. Load aggregate from EventStore                       │   │
│  │  2. Execute business logic (aggregate.deposit())         │   │
│  │  3. Save new events to EventStore                        │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                         EVENT STORE                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ PostgreSQL Table: events                                 │  │
│  │ ┌────────┬──────────┬─────────────┬──────────────────┐   │  │
│  │ │ seq_id │ agg_id   │ event_type  │ event_data       │   │  │
│  │ ├────────┼──────────┼─────────────┼──────────────────┤   │  │
│  │ │ 1      │ acc-001  │ opened      │ {amount: 1000}   │   │  │
│  │ │ 2      │ acc-001  │ deposited   │ {amount: 100}    │   │  │
│  │ └────────┴──────────┴─────────────┴──────────────────┘   │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PROJECTION SERVICE (Read Side)               │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AccountLedgerProjectionService                           │   │
│  │  - Listens to events from EventStore                     │   │
│  │  - Updates read model (AccountLedgerReadModel)           │   │
│  │  - Optimized for queries                                 │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                         READ MODEL                              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ PostgreSQL Table: account_ledger_read_model              │   │
│  │ ┌──────────┬─────────┬────────┬────────┬──────────────┐  │   │
│  │ │ acc_id   │ balance │ status │ frozen │ last_updated │  │   │
│  │ ├──────────┼─────────┼────────┼────────┼──────────────┤  │   │
│  │ │ acc-001  │ 1100.00 │ ACTIVE │ false  │ 2025-10-18   │  │   │
│  │ └──────────┴─────────┴────────┴────────┴──────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    QUERY SERVICE (Read Side)                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AccountLedgerRepository.findByAccountNumber()            │   │
│  │  - Fast queries against read model                       │   │
│  │  - No event replay needed                                │   │
│  │  - Optimized indexes                                     │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 🗄️ **Critical: What Gets a Database Table?**

This is the most important concept to understand in event sourcing:

| Component | Has Table? | Why? |
|-----------|------------|------|
| **Events** | ✅ YES (`events` table) | Source of truth, immutable history |
| **Snapshots** | ✅ YES (`snapshots` table) | Performance optimization |
| **Read Models** | ✅ YES (`account_ledger_read_model` table) | Fast queries |
| **Aggregates** | ❌ **NO TABLE** | Reconstructed from events in-memory |

**The Golden Rule:**
- ✅ **Events are persisted** → They go in the `events` table
- ✅ **Read models are persisted** → They go in their own tables (e.g., `account_ledger_read_model`)
- ❌ **Aggregates are NOT persisted** → They exist only in memory during command execution

```java
// ❌ WRONG: Do NOT create a table for aggregates
@Table("account_ledger")  // ← This defeats event sourcing!
public class AccountLedger extends AggregateRoot { }

// ✅ CORRECT: Aggregates have no @Table annotation
public class AccountLedger extends AggregateRoot {
    // Lives in memory only!
    // Reconstructed from events when needed
    // Discarded after command execution
}

// ✅ CORRECT: Read models DO have tables
@Table("account_ledger_read_model")  // ← For fast queries
public class AccountLedgerReadModel { }
```

**Why This Matters:**
- If you create a table for your aggregate, you're doing **traditional CRUD**, not event sourcing
- The aggregate's state is **derived from events**, not stored directly
- Read models are **projections** built from events for query performance

---

### Why Do We Need All These Components?

Let's understand each component and why it's necessary:

#### 1. **Domain Events** (MoneyDepositedEvent, MoneyWithdrawnEvent, etc.)
- **What**: Immutable records of things that happened
- **Why**: The source of truth for all state changes
- **Storage**: `events` table in PostgreSQL
- **Example**: `MoneyDepositedEvent{amount: 100, depositedBy: "user-123"}`

#### 2. **Aggregate Root** (AccountLedger)
- **What**: Business logic and validation
- **Why**: Enforces business rules and generates events
- **Storage**: ❌ **NO TABLE** - Lives in memory only!
- **Lifecycle**: Loaded from events → Execute command → Save new events → Discarded
- **What**: Business logic that enforces rules and generates events
- **Why**: Ensures business rules are never violated (e.g., no withdrawals from frozen accounts)
- **Example**: `account.withdraw(100)` → validates balance → generates `MoneyWithdrawnEvent`

#### 3. **Event Store** (PostgreSQL events table)
- **What**: Append-only log of all events
- **Why**: Permanent, immutable record of everything that happened
- **Storage**: ✅ `events` table in PostgreSQL
- **Example**: Stores events in order with metadata (timestamp, user, correlation ID)

#### 4. **Snapshots** (AccountLedgerSnapshot)
- **What**: Cached state at a specific version
- **Why**: Performance - avoid replaying millions of events
- **Storage**: ✅ `snapshots` table in PostgreSQL
- **Example**: Instead of replaying 1M events, load snapshot at version 999,000 + replay 1,000 events

#### 5. **Service Layer** (AccountLedgerService)
- **What**: Orchestrates loading aggregates, executing commands, saving events
- **Why**: Handles infrastructure concerns (transactions, retries, logging)
- **Storage**: ❌ No table - Pure business logic
- **Example**: `service.deposit(accountId, 100)` → loads aggregate → executes → saves

#### 6. **Read Model** (AccountLedgerReadModel)
- **What**: Denormalized view optimized for queries
- **Why**: Fast queries without replaying events
- **Storage**: ✅ `account_ledger_read_model` table (traditional table!)
- **Example**: `SELECT * FROM account_ledger_read_model WHERE balance > 10000`
- **Important**: This is a **traditional table** that gets updated by projections

#### 7. **Projection Service** (AccountLedgerProjectionService)
- **What**: Keeps read model in sync with events
- **Why**: Maintains eventual consistency between write and read sides
- **Storage**: ❌ No table - Event handler logic
- **Example**: Listens to `MoneyDepositedEvent` → updates balance in read model table

#### 8. **Repository** (AccountLedgerRepository)
- **What**: Data access layer for read model
- **Why**: Clean abstraction for querying the read model table
- **Storage**: ❌ No table - Queries the `account_ledger_read_model` table
- **Example**: `repository.findByCustomerId(customerId)`

### The Flow: Write vs Read

**WRITE PATH (Commands):**
```
Client → Service → Load Aggregate → Execute Command → Generate Events → Save to EventStore
```

**READ PATH (Queries):**
```
Client → Repository → Query Read Model → Return Results
```

This separation provides:
- **Write Optimization**: Focus on business rules and consistency (aggregates)
- **Read Optimization**: Focus on query performance (read models)
- **Scalability**: Scale reads and writes independently

---

## Step 1: Domain Events

Domain events are the foundation of event sourcing. They represent facts about what happened in the past.

### Principles of Good Domain Events

1. **Past Tense**: Events describe what happened, not what should happen
   - ✅ `MoneyDepositedEvent`
   - ❌ `DepositMoneyEvent`

2. **Immutable**: Once created, events never change
   - Events are facts - you can't change history

3. **Self-Contained**: Include all necessary information
   - Don't require lookups to understand what happened

4. **Business-Focused**: Use domain language
   - ✅ `AccountFrozenEvent{reason: "Suspicious activity"}`
   - ❌ `StatusChangedEvent{newStatus: "FROZEN"}`

### Creating Domain Events

We'll create 6 events for our Account Ledger:

#### 1. AccountOpenedEvent

```java
@DomainEvent("account.opened")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountOpenedEvent extends AbstractDomainEvent {
    private String accountNumber;      // e.g., "ACC-2025-001"
    private String accountType;        // e.g., "CHECKING", "SAVINGS"
    private UUID customerId;           // Who owns this account
    private BigDecimal initialDeposit; // Opening balance
    private String currency;           // e.g., "USD", "EUR"
}
```

**Why these fields?**
- `accountNumber`: Human-readable identifier for customer service
- `accountType`: Different types have different rules (overdraft limits, interest rates)
- `customerId`: Link to customer for queries like "show all accounts for customer X"
- `initialDeposit`: Many accounts require minimum opening deposit
- `currency`: Multi-currency support

#### 2. MoneyDepositedEvent

```java
@DomainEvent("money.deposited")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyDepositedEvent extends AbstractDomainEvent {
    private BigDecimal amount;    // How much was deposited
    private String source;        // e.g., "Wire Transfer", "Check Deposit", "ATM"
    private String reference;     // External reference number
    private String depositedBy;   // User who made the deposit
}
```

**Why these fields?**
- `amount`: The money being added
- `source`: Important for reconciliation and fraud detection
- `reference`: Link to external systems (check number, wire transfer ID)
- `depositedBy`: Audit trail - who performed this action

#### 3. MoneyWithdrawnEvent

```java
@DomainEvent("money.withdrawn")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyWithdrawnEvent extends AbstractDomainEvent {
    private BigDecimal amount;     // How much was withdrawn
    private String destination;    // e.g., "ATM", "Wire Transfer", "Check"
    private String reference;      // External reference
    private String withdrawnBy;    // User who made the withdrawal
}
```

#### 4. AccountFrozenEvent

```java
@DomainEvent("account.frozen")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountFrozenEvent extends AbstractDomainEvent {
    private String reason;      // e.g., "Suspicious activity", "Court order"
    private String frozenBy;    // User who froze the account
}
```

**Why freeze accounts?**
- Fraud prevention
- Legal holds
- Dispute resolution
- Regulatory compliance

#### 5. AccountUnfrozenEvent

```java
@DomainEvent("account.unfrozen")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountUnfrozenEvent extends AbstractDomainEvent {
    private String reason;       // e.g., "Investigation complete"
    private String unfrozenBy;   // User who unfroze the account
}
```

#### 6. AccountClosedEvent

```java
@DomainEvent("account.closed")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosedEvent extends AbstractDomainEvent {
    private String reason;      // e.g., "Customer request", "Dormant account"
    private String closedBy;    // User who closed the account
}
```

### Event Metadata

Notice that all events extend `AbstractDomainEvent`, which provides:

```java
public abstract class AbstractDomainEvent implements Event {
    private UUID aggregateId;              // Which account
    private Map<String, Object> metadata;  // Flexible metadata
    private String userId;                 // Who triggered this
    private String correlationId;          // Request tracking
    private String causationId;            // Event causality
    private Instant eventTimestamp;        // When it happened
    private int eventVersion;              // Schema version
}
```

**Why metadata?**
- **Distributed Tracing**: Track requests across services
- **Debugging**: Understand event causality
- **Auditing**: Who, what, when, where, why
- **Schema Evolution**: Handle event format changes

---

## Step 2: The Aggregate Root

The **Aggregate Root** is where your business logic lives. It:
1. Enforces business rules
2. Generates events when state changes
3. Reconstructs state from events

### Why Do We Need an Aggregate?

**Without Aggregate (Anemic Domain Model):**
```java
// ❌ Business logic scattered everywhere
public class AccountService {
    public void withdraw(UUID accountId, BigDecimal amount) {
        Account account = repository.findById(accountId);
        if (account.isFrozen()) throw new Exception("Frozen!");
        if (account.getBalance().compareTo(amount) < 0) throw new Exception("Insufficient funds!");
        account.setBalance(account.getBalance().subtract(amount));
        repository.save(account);
    }
}
```

**Problems:**
- Business rules in service layer (hard to test)
- Easy to forget validation
- No encapsulation
- Can't replay events to rebuild state

**With Aggregate (Rich Domain Model):**
```java
// ✅ Business logic in the domain
public class AccountLedger extends AggregateRoot {
    public void withdraw(BigDecimal amount, String destination, String withdrawnBy) {
        // Business rules enforced here
        if (frozen) {
            throw new AccountFrozenException("Cannot withdraw from frozen account");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        // Generate event
        MoneyWithdrawnEvent event = MoneyWithdrawnEvent.builder()
            .aggregateId(id)
            .amount(amount)
            .destination(destination)
            .withdrawnBy(withdrawnBy)
            .build();

        applyChange(event);  // Apply and record
    }
}
```

**Benefits:**
- Business rules in one place
- Impossible to bypass validation
- Testable without infrastructure
- Event replay for free

### Creating the AccountLedger Aggregate

```java
@Slf4j
public class AccountLedger extends AggregateRoot {
    // Current state (rebuilt from events)
    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean frozen;
    private boolean closed;
    private Instant openedAt;
    private Instant lastTransactionAt;

    // Constructor for creating NEW accounts
    public AccountLedger(UUID id, String accountNumber, String accountType,
                         UUID customerId, BigDecimal initialDeposit, String currency) {
        super(id, "AccountLedger");

        // Validate
        if (initialDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative");
        }

        // Generate event
        AccountOpenedEvent event = AccountOpenedEvent.builder()
            .aggregateId(id)
            .accountNumber(accountNumber)
            .accountType(accountType)
            .customerId(customerId)
            .initialDeposit(initialDeposit)
            .currency(currency)
            .build();

        applyChange(event);  // This will call apply(AccountOpenedEvent)
    }

    // Constructor for LOADING from events
    public AccountLedger(UUID id) {
        super(id, "AccountLedger");
    }

    // Event handler - updates state
    @EventHandler
    public void apply(AccountOpenedEvent event) {
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
}
```

### Understanding the Flow

**Creating a new account:**
```
1. new AccountLedger(id, "ACC-001", "CHECKING", customerId, 1000, "USD")
2. → Validates initial deposit
3. → Creates AccountOpenedEvent
4. → Calls applyChange(event)
5. → applyChange() calls apply(AccountOpenedEvent)
6. → State is updated (balance = 1000, frozen = false, etc.)
7. → Event is added to uncommittedEvents list
8. → Service saves uncommittedEvents to EventStore
```

**Loading an existing account:**
```
1. new AccountLedger(id)  // Empty aggregate
2. → Service loads events from EventStore
3. → Calls aggregate.loadFromHistory(events)
4. → For each event, calls appropriate apply() method
5. → State is rebuilt (balance, frozen, etc.)
6. → Aggregate is ready for commands
```

### Implementing Commands

Commands are methods that change state by generating events:

```java
public void deposit(BigDecimal amount, String source, String reference, String depositedBy) {
    // Validation
    if (closed) {
        throw new AccountClosedException("Cannot deposit to closed account");
    }
    if (frozen) {
        throw new AccountFrozenException("Cannot deposit to frozen account");
    }
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Deposit amount must be positive");
    }

    // Generate event
    MoneyDepositedEvent event = MoneyDepositedEvent.builder()
        .aggregateId(id)
        .amount(amount)
        .source(source)
        .reference(reference)
        .depositedBy(depositedBy)
        .build();

    applyChange(event);
}

@EventHandler
public void apply(MoneyDepositedEvent event) {
    this.balance = this.balance.add(event.getAmount());
    this.lastTransactionAt = event.getEventTimestamp();
}
```

**Key Pattern:**
1. **Command method** (`deposit()`) - Validates and generates event
2. **Event handler** (`apply()`) - Updates state

**Why separate them?**
- Command: Only called for NEW events (validation needed)
- Handler: Called for NEW and HISTORICAL events (no validation, just state update)

---

## Step 3: Snapshots for Performance

### The Performance Problem

Imagine an account with 1 million transactions:

```
Load account → Fetch 1,000,000 events → Replay all events → Ready
                     ⏱️ 30 seconds!
```

**This is too slow for production!**

### The Solution: Snapshots

A snapshot is a cached copy of aggregate state at a specific version:

```
Load account → Fetch snapshot at version 999,000 → Fetch 1,000 new events → Replay 1,000 events → Ready
                     ⏱️ 0.3 seconds (100x faster!)
```

### Creating the Snapshot Class

```java
@Getter
public class AccountLedgerSnapshot extends AbstractSnapshot {
    private final String accountNumber;
    private final String accountType;
    private final UUID customerId;
    private final BigDecimal balance;
    private final String currency;
    private final boolean frozen;
    private final boolean closed;
    private final Instant openedAt;
    private final Instant lastTransactionAt;

    public AccountLedgerSnapshot(UUID aggregateId, long version, Instant createdAt,
                                 String accountNumber, String accountType, UUID customerId,
                                 BigDecimal balance, String currency, boolean frozen,
                                 boolean closed, Instant openedAt, Instant lastTransactionAt) {
        super(aggregateId, version, createdAt);  // AbstractSnapshot handles common fields
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
        return "AccountLedgerSnapshot";
    }
}
```

### Why Extend AbstractSnapshot?

`AbstractSnapshot` provides common functionality:
- `aggregateId` - Which aggregate this snapshot belongs to
- `version` - Which version of the aggregate (event count)
- `createdAt` - When the snapshot was created
- `reason` - Why it was created (e.g., "Scheduled", "Manual")
- `sizeBytes` - Size for monitoring
- Validation (null checks, version >= 0)
- Standard `equals()`, `hashCode()`, `toString()`

**Without AbstractSnapshot:**
```java
// ❌ 40+ lines of boilerplate per snapshot class
private final UUID aggregateId;
private final long version;
private final Instant createdAt;
// ... validation, equals, hashCode, toString
```

**With AbstractSnapshot:**
```java
// ✅ Just extend and focus on domain fields
public class AccountLedgerSnapshot extends AbstractSnapshot {
    private final String accountNumber;
    private final BigDecimal balance;
    // ... only domain-specific fields
}
```

### Adding Snapshot Support to Aggregate

```java
public class AccountLedger extends AggregateRoot {
    // ... existing code ...

    // Create snapshot from current state
    public AccountLedgerSnapshot createSnapshot(String reason) {
        return new AccountLedgerSnapshot(
            id,
            getCurrentVersion(),  // Current event count
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

    // Restore state from snapshot
    public static AccountLedger fromSnapshot(AccountLedgerSnapshot snapshot) {
        AccountLedger ledger = new AccountLedger(snapshot.getAggregateId());
        ledger.accountNumber = snapshot.getAccountNumber();
        ledger.accountType = snapshot.getAccountType();
        ledger.customerId = snapshot.getCustomerId();
        ledger.balance = snapshot.getBalance();
        ledger.currency = snapshot.getCurrency();
        ledger.frozen = snapshot.isFrozen();
        ledger.closed = snapshot.isClosed();
        ledger.openedAt = snapshot.getOpenedAt();
        ledger.lastTransactionAt = snapshot.getLastTransactionAt();
        ledger.setCurrentVersion(snapshot.getVersion());  // Important!
        return ledger;
    }
}
```

### Snapshot Strategy

**When to create snapshots?**

1. **Event Count Threshold**: Every N events (e.g., every 100 events)
2. **Time-Based**: Daily snapshots for active accounts
3. **On-Demand**: Manual snapshots for important accounts
4. **Adaptive**: More frequent for high-activity accounts

**Configuration:**
```yaml
firefly:
  eventsourcing:
    snapshot:
      enabled: true
      threshold: 100  # Create snapshot every 100 events
```

---

## Step 4: The Service Layer

The service layer orchestrates the event sourcing infrastructure. It handles:
- Loading aggregates from the EventStore
- Executing business logic
- Saving events transactionally
- Creating snapshots
- Error handling and retries

### Why Do We Need a Service Layer?

**Without Service Layer:**
```java
// ❌ Infrastructure concerns mixed with business logic
EventStore eventStore = ...;
AccountLedger account = new AccountLedger(accountId);
List<EventEnvelope> events = eventStore.loadEvents(accountId).collectList().block();
account.loadFromHistory(events);
account.deposit(100, "ATM", "REF-123", "user-123");
eventStore.saveEvents(account.getUncommittedEvents()).block();
account.markEventsAsCommitted();
```

**With Service Layer:**
```java
// ✅ Clean, simple, transactional
accountLedgerService.deposit(accountId, 100, "ATM", "REF-123", "user-123").block();
```

### Creating AccountLedgerService

```java
@Service
@Slf4j
public class AccountLedgerService {
    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;

    public AccountLedgerService(EventStore eventStore, SnapshotStore snapshotStore) {
        this.eventStore = eventStore;
        this.snapshotStore = snapshotStore;
    }

    // Create new account
    @EventSourcingTransactional
    public Mono<AccountLedger> openAccount(String accountNumber, String accountType,
                                           UUID customerId, BigDecimal initialDeposit,
                                           String currency) {
        return Mono.fromCallable(() -> {
            UUID accountId = UUID.randomUUID();
            AccountLedger ledger = new AccountLedger(
                accountId, accountNumber, accountType, customerId, initialDeposit, currency
            );

            // Save events (handled by @EventSourcingTransactional)
            return ledger;
        });
    }

    // Deposit money
    @EventSourcingTransactional
    public Mono<AccountLedger> deposit(UUID accountId, BigDecimal amount, String source,
                                       String reference, String depositedBy) {
        return loadAggregate(accountId)
            .map(ledger -> {
                ledger.deposit(amount, source, reference, depositedBy);
                return ledger;
            });
    }

    // Withdraw money
    @EventSourcingTransactional
    public Mono<AccountLedger> withdraw(UUID accountId, BigDecimal amount, String destination,
                                        String reference, String withdrawnBy) {
        return loadAggregate(accountId)
            .map(ledger -> {
                ledger.withdraw(amount, destination, reference, withdrawnBy);
                return ledger;
            });
    }

    // Load aggregate (with snapshot optimization)
    private Mono<AccountLedger> loadAggregate(UUID accountId) {
        return snapshotStore.loadLatestSnapshot(accountId, AccountLedgerSnapshot.class)
            .flatMap(snapshot -> {
                // Load from snapshot + new events
                AccountLedger ledger = AccountLedger.fromSnapshot(snapshot);
                long fromVersion = snapshot.getVersion() + 1;

                return eventStore.loadEvents(accountId, fromVersion, Long.MAX_VALUE)
                    .collectList()
                    .map(events -> {
                        if (!events.isEmpty()) {
                            ledger.loadFromHistory(events);
                        }
                        return ledger;
                    });
            })
            .switchIfEmpty(
                // No snapshot - load all events
                eventStore.loadEvents(accountId)
                    .collectList()
                    .map(events -> {
                        AccountLedger ledger = new AccountLedger(accountId);
                        ledger.loadFromHistory(events);
                        return ledger;
                    })
            );
    }
}
```

### Understanding @EventSourcingTransactional

This annotation provides:
1. **Automatic Event Saving**: Saves uncommitted events to EventStore
2. **Transaction Management**: ACID guarantees
3. **Event Publishing**: Publishes events to message broker
4. **Error Handling**: Rollback on failure
5. **Logging**: Automatic MDC context

**How it works:**
```java
@EventSourcingTransactional
public Mono<AccountLedger> deposit(...) {
    // 1. Load aggregate
    // 2. Execute business logic (generates events)
    // 3. Return aggregate
    // 4. @EventSourcingTransactional intercepts
    // 5. Saves uncommitted events to EventStore
    // 6. Publishes events to Kafka/RabbitMQ
    // 7. Marks events as committed
    // 8. Returns result
}
```

### Time Travel Queries

One of the most powerful features of event sourcing:

```java
public Mono<AccountLedger> getAccountAtTime(UUID accountId, Instant pointInTime) {
    return eventStore.loadEvents(accountId)
        .filter(envelope -> envelope.getCreatedAt().isBefore(pointInTime))
        .collectList()
        .map(events -> {
            AccountLedger ledger = new AccountLedger(accountId);
            ledger.loadFromHistory(events);
            return ledger;
        });
}
```

**Use cases:**
- "What was the balance on March 15th at 3:47 PM?"
- "Show me the account state before the disputed transaction"
- "Replay events to test new business rules"

### Snapshot Management

```java
public Mono<Void> createSnapshot(UUID accountId, String reason) {
    return loadAggregate(accountId)
        .flatMap(ledger -> {
            AccountLedgerSnapshot snapshot = ledger.createSnapshot(reason);
            return snapshotStore.saveSnapshot(snapshot);
        });
}
```

**Automatic snapshot creation:**
```java
@Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
public void createDailySnapshots() {
    // Find accounts with > 100 events since last snapshot
    // Create snapshots for them
}
```

---

## Step 5: Read Models

### ⚠️ **CRITICAL: When to Use Traditional Tables vs Event Sourcing**

Before we dive into read models, let's be crystal clear about what gets a table and what doesn't:

#### ❌ **DO NOT Create Tables For Aggregates**

```java
// ❌ WRONG: Do NOT create a table for the aggregate
@Table("account_ledger")  // ← NO! This defeats event sourcing!
public class AccountLedger extends AggregateRoot {
    private BigDecimal balance;
    // ...
}
```

**Why NOT?**
- ❌ Aggregates are **reconstructed from events** - they live in memory only
- ❌ Creating an aggregate table means you're doing traditional CRUD, not event sourcing
- ❌ You lose the complete history and audit trail
- ❌ You can't time-travel or replay events

#### ✅ **DO Create Tables For Read Models**

```java
// ✅ CORRECT: Read models get tables for fast queries
@Table("account_ledger_read_model")  // ← YES! This is for queries
public class AccountLedgerReadModel {
    private BigDecimal balance;
    // ...
}
```

**Why YES?**
- ✅ Read models are **projections** built from events
- ✅ Optimized for queries (indexed, denormalized)
- ✅ Can be rebuilt from events if corrupted
- ✅ Eventual consistency is acceptable for reads

---

### The Query Performance Problem

**Problem**: Loading aggregates from events is slow for queries:

```java
// ❌ Slow: Load 1000 accounts to find high balances
List<AccountLedger> accounts = loadAll1000Accounts();  // Replay events for each!
List<AccountLedger> highBalance = accounts.stream()
    .filter(a -> a.getBalance().compareTo(new BigDecimal("10000")) > 0)
    .collect(Collectors.toList());
```

**Solution**: Maintain a separate read model optimized for queries:

```java
// ✅ Fast: Query read model directly
SELECT * FROM account_ledger_read_model WHERE balance > 10000;
```

### Read/Write Separation Pattern

Event sourcing naturally separates writes (commands) from reads (queries):

```
WRITE SIDE (Commands)          READ SIDE (Queries)
┌──────────────────┐          ┌──────────────────┐
│  AccountLedger   │          │ ReadModel Table  │
│   (Aggregate)    │          │  (Denormalized)  │
│                  │          │                  │
│                  │          │                  │
│ - Business Rules │          │ - Fast Queries   │
│ - Event Sourced  │          │ - Traditional DB │
│ - In-Memory Only │          │ - Eventually     │
│ - Consistency    │          │   Consistent     │
└────────┬─────────┘          └────────▲─────────┘
         │                             │
         │ Events                      │
         └─────────────────────────────┘
           Projection Service
```

### 🗄️ **Database Tables in Event Sourcing**

| Table | Purpose | Contains | Mutable? |
|-------|---------|----------|----------|
| `events` | Source of truth | All domain events | ❌ Append-only |
| `snapshots` | Performance optimization | Aggregate state at specific versions | ❌ Append-only |
| `account_ledger_read_model` | Fast queries | Current account state | ✅ Updated by projections |
| ~~`account_ledger`~~ | ❌ **DOES NOT EXIST** | N/A | N/A |

**Key Point:** The `AccountLedger` aggregate **has NO table**. It's reconstructed from the `events` table when needed!

### Creating the Read Model

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("account_ledger_read_model")
public class AccountLedgerReadModel {
    @Id
    private UUID accountId;
    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean frozen;
    private boolean closed;
    private Instant openedAt;
    private Instant lastTransactionAt;
    private Instant closedAt;
    private long version;
    private Instant lastUpdated;
    private String lastUpdatedBy;
    private String status;  // "ACTIVE", "FROZEN", "CLOSED"

    // Computed fields for queries
    public String getStatus() {
        if (closed) return "CLOSED";
        if (frozen) return "FROZEN";
        return "ACTIVE";
    }
}
```

### Why a Separate Table?

**Event Store Table:**
```sql
-- Optimized for appending events
CREATE TABLE events (
    global_sequence BIGSERIAL PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    version BIGINT NOT NULL
);
-- Index on (aggregate_id, version) for loading events
```

**Read Model Table:**
```sql
-- Optimized for queries
CREATE TABLE account_ledger_read_model (
    account_id UUID PRIMARY KEY,
    account_number VARCHAR(50) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    balance DECIMAL(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    frozen BOOLEAN NOT NULL,
    last_transaction_at TIMESTAMP
);
-- Indexes for common queries
CREATE INDEX idx_customer_id ON account_ledger_read_model(customer_id);
CREATE INDEX idx_balance ON account_ledger_read_model(balance);
CREATE INDEX idx_status ON account_ledger_read_model(status);
```

**Benefits:**
- **Fast Queries**: Indexed for common access patterns
- **Denormalized**: No joins needed
- **Flexible**: Can have multiple read models for different use cases
- **Scalable**: Can use different database (MongoDB, Elasticsearch, etc.)

### Creating the Repository

```java
@Repository
public interface AccountLedgerRepository extends R2dbcRepository<AccountLedgerReadModel, UUID> {

    // Find by account number
    Mono<AccountLedgerReadModel> findByAccountNumber(String accountNumber);

    // Find all accounts for a customer
    Flux<AccountLedgerReadModel> findByCustomerId(UUID customerId);

    // Find active accounts
    @Query("SELECT * FROM account_ledger_read_model WHERE closed = false AND frozen = false")
    Flux<AccountLedgerReadModel> findAllActive();

    // Find frozen accounts
    @Query("SELECT * FROM account_ledger_read_model WHERE frozen = true")
    Flux<AccountLedgerReadModel> findAllFrozen();

    // Find high-balance accounts
    @Query("SELECT * FROM account_ledger_read_model WHERE balance > :minBalance ORDER BY balance DESC")
    Flux<AccountLedgerReadModel> findByBalanceGreaterThan(BigDecimal minBalance);

    // Find by account type
    Flux<AccountLedgerReadModel> findByAccountType(String accountType);
}
```

**Usage:**
```java
// Fast queries without loading aggregates
repository.findByCustomerId(customerId)  // All accounts for customer
repository.findAllFrozen()               // All frozen accounts
repository.findByBalanceGreaterThan(new BigDecimal("10000"))  // High balances
```

---

## Step 6: Projections

Projections keep the read model in sync with events. They listen to events and update the read model accordingly.

### Why Do We Need Projections?

**The Problem:**
```
1. User deposits $100
2. MoneyDepositedEvent saved to EventStore
3. Read model still shows old balance!
4. User queries balance → sees old value → confused!
```

**The Solution:**
```
1. User deposits $100
2. MoneyDepositedEvent saved to EventStore
3. ProjectionService listens to event
4. Updates read model: balance += 100
5. User queries balance → sees new value → happy!
```

### Creating AccountLedgerProjectionService

```java
@Service
@Slf4j
public class AccountLedgerProjectionService extends ProjectionService<AccountLedgerReadModel> {

    private final AccountLedgerRepository repository;
    private final EventStore eventStore;

    public AccountLedgerProjectionService(AccountLedgerRepository repository,
                                          EventStore eventStore) {
        super("AccountLedgerProjection");
        this.repository = repository;
        this.eventStore = eventStore;
    }

    @Override
    protected Mono<Void> handleEvent(EventEnvelope envelope) {
        Event event = envelope.getEvent();

        // Route to appropriate handler
        if (event instanceof AccountOpenedEvent) {
            return handleAccountOpened(envelope, (AccountOpenedEvent) event);
        } else if (event instanceof MoneyDepositedEvent) {
            return handleMoneyDeposited(envelope, (MoneyDepositedEvent) event);
        } else if (event instanceof MoneyWithdrawnEvent) {
            return handleMoneyWithdrawn(envelope, (MoneyWithdrawnEvent) event);
        } else if (event instanceof AccountFrozenEvent) {
            return handleAccountFrozen(envelope, (AccountFrozenEvent) event);
        } else if (event instanceof AccountUnfrozenEvent) {
            return handleAccountUnfrozen(envelope, (AccountUnfrozenEvent) event);
        } else if (event instanceof AccountClosedEvent) {
            return handleAccountClosed(envelope, (AccountClosedEvent) event);
        }

        return Mono.empty();
    }

    private Mono<Void> handleAccountOpened(EventEnvelope envelope, AccountOpenedEvent event) {
        AccountLedgerReadModel readModel = AccountLedgerReadModel.builder()
            .accountId(event.getAggregateId())
            .accountNumber(event.getAccountNumber())
            .accountType(event.getAccountType())
            .customerId(event.getCustomerId())
            .balance(event.getInitialDeposit())
            .currency(event.getCurrency())
            .frozen(false)
            .closed(false)
            .openedAt(envelope.getCreatedAt())
            .lastTransactionAt(envelope.getCreatedAt())
            .version(envelope.getVersion())
            .lastUpdated(Instant.now())
            .build();

        return repository.save(readModel).then();
    }

    private Mono<Void> handleMoneyDeposited(EventEnvelope envelope, MoneyDepositedEvent event) {
        return repository.findById(event.getAggregateId())
            .flatMap(readModel -> {
                readModel.setBalance(readModel.getBalance().add(event.getAmount()));
                readModel.setLastTransactionAt(envelope.getCreatedAt());
                readModel.setVersion(envelope.getVersion());
                readModel.setLastUpdated(Instant.now());
                readModel.setLastUpdatedBy(event.getDepositedBy());
                return repository.save(readModel);
            })
            .then();
    }

    // Similar handlers for other events...
}
```

### Understanding Eventual Consistency

**Strong Consistency (Traditional):**
```
Write → Database → Read
        (same transaction)
Result: Read always sees latest write
```

**Eventual Consistency:**
```
Write → EventStore → Event → Projection → ReadModel → Read
        (async)
Result: Read might see slightly stale data (milliseconds)
```

**Is this a problem?**

Usually NO! Consider:
- Bank ATM shows balance from 5 minutes ago
- Stock prices update every second
- Social media likes update eventually

**When it matters:**
- Use aggregate for critical reads: `loadAggregate(id).getBalance()`
- Most queries can tolerate milliseconds of lag

### Projection Position Tracking

Projections need to track which events they've processed:

```java
@Override
protected Mono<Long> getCurrentPosition() {
    // Load from database: "AccountLedgerProjection processed up to event 12345"
    return projectionPositionRepository.findById(getProjectionName())
        .map(ProjectionPosition::getPosition)
        .defaultIfEmpty(0L);
}

@Override
protected Mono<Void> updatePosition(long position) {
    // Save to database: "AccountLedgerProjection now at event 12346"
    return projectionPositionRepository.save(
        new ProjectionPosition(getProjectionName(), position)
    ).then();
}
```

**Why track position?**
- **Restart**: If projection crashes, resume from last position
- **Rebuild**: Reset position to 0 to rebuild read model from scratch
- **Monitoring**: Track projection lag (current event - processed event)

---

## Step 7: Putting It All Together

Now let's see how all the components work together in a complete example.

### Complete Flow: Opening an Account and Making Transactions

```java
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountLedgerService service;
    private final AccountLedgerRepository repository;

    // 1. Open new account
    @PostMapping
    public Mono<AccountResponse> openAccount(@RequestBody OpenAccountRequest request) {
        return service.openAccount(
            request.getAccountNumber(),
            request.getAccountType(),
            request.getCustomerId(),
            request.getInitialDeposit(),
            request.getCurrency()
        ).map(this::toResponse);
    }

    // 2. Deposit money (WRITE - uses aggregate)
    @PostMapping("/{accountId}/deposit")
    public Mono<AccountResponse> deposit(@PathVariable UUID accountId,
                                         @RequestBody DepositRequest request) {
        return service.deposit(
            accountId,
            request.getAmount(),
            request.getSource(),
            request.getReference(),
            request.getDepositedBy()
        ).map(this::toResponse);
    }

    // 3. Query account (READ - uses read model)
    @GetMapping("/{accountId}")
    public Mono<AccountLedgerReadModel> getAccount(@PathVariable UUID accountId) {
        return repository.findById(accountId);
    }

    // 4. Query by customer (READ - uses read model)
    @GetMapping("/customer/{customerId}")
    public Flux<AccountLedgerReadModel> getCustomerAccounts(@PathVariable UUID customerId) {
        return repository.findByCustomerId(customerId);
    }

    // 5. Time travel query (uses aggregate + events)
    @GetMapping("/{accountId}/at/{timestamp}")
    public Mono<AccountResponse> getAccountAtTime(@PathVariable UUID accountId,
                                                   @PathVariable Instant timestamp) {
        return service.getAccountAtTime(accountId, timestamp)
            .map(this::toResponse);
    }
}
```

### Example Scenario

Let's trace a complete user journey:

#### Step 1: User Opens Account

```http
POST /api/accounts
{
  "accountNumber": "ACC-2025-001",
  "accountType": "CHECKING",
  "customerId": "cust-123",
  "initialDeposit": 1000.00,
  "currency": "USD"
}
```

**What happens:**
1. `AccountLedgerService.openAccount()` called
2. Creates new `AccountLedger` aggregate
3. Aggregate generates `AccountOpenedEvent`
4. `@EventSourcingTransactional` saves event to EventStore
5. Event published to Kafka
6. `AccountLedgerProjectionService` receives event
7. Creates `AccountLedgerReadModel` in database
8. Response returned to user

**Database state:**
```sql
-- events table
global_sequence | aggregate_id | event_type      | event_data
1               | acc-001      | account.opened  | {"accountNumber": "ACC-2025-001", ...}

-- account_ledger_read_model table
account_id | account_number | balance | status
acc-001    | ACC-2025-001   | 1000.00 | ACTIVE
```

#### Step 2: User Deposits Money

```http
POST /api/accounts/acc-001/deposit
{
  "amount": 500.00,
  "source": "Wire Transfer",
  "reference": "WIRE-456",
  "depositedBy": "user-123"
}
```

**What happens:**
1. `AccountLedgerService.deposit()` called
2. Loads aggregate from EventStore (replays `AccountOpenedEvent`)
3. Calls `aggregate.deposit(500)`
4. Aggregate validates (not frozen, not closed)
5. Generates `MoneyDepositedEvent`
6. Event saved to EventStore
7. Projection updates read model: `balance = 1000 + 500 = 1500`

**Database state:**
```sql
-- events table
global_sequence | aggregate_id | event_type       | event_data
1               | acc-001      | account.opened   | {...}
2               | acc-001      | money.deposited  | {"amount": 500, ...}

-- account_ledger_read_model table
account_id | account_number | balance | status
acc-001    | ACC-2025-001   | 1500.00 | ACTIVE
```

#### Step 3: User Queries Balance

```http
GET /api/accounts/acc-001
```

**What happens:**
1. `AccountLedgerRepository.findById()` called
2. Queries read model table (fast!)
3. Returns current state

**No event replay needed!**

#### Step 4: Compliance Officer Asks "What was the balance yesterday?"

```http
GET /api/accounts/acc-001/at/2025-10-17T23:59:59Z
```

**What happens:**
1. `AccountLedgerService.getAccountAtTime()` called
2. Loads all events before timestamp
3. Replays events to rebuild state
4. Returns historical balance

**This is the power of event sourcing!**

---

## Advanced Topics

### 1. Handling Concurrent Modifications

**Problem**: Two users try to withdraw from the same account simultaneously:

```
User A: Withdraw $100 (balance: $1000 → $900)
User B: Withdraw $200 (balance: $1000 → $800)  // Should be $900 → $700!
```

**Solution**: Optimistic Locking with Version Numbers

```java
@EventSourcingTransactional
public Mono<AccountLedger> withdraw(UUID accountId, BigDecimal amount, ...) {
    return loadAggregate(accountId)
        .flatMap(ledger -> {
            long expectedVersion = ledger.getCurrentVersion();
            ledger.withdraw(amount, ...);

            // EventStore checks version when saving
            return eventStore.saveEvents(
                ledger.getUncommittedEvents(),
                expectedVersion  // Fails if version changed
            ).thenReturn(ledger);
        })
        .retry(3);  // Retry on conflict
}
```

### 2. Event Versioning

**Problem**: Event schema changes over time:

```java
// Version 1
class MoneyDepositedEvent {
    BigDecimal amount;
}

// Version 2 (added source field)
class MoneyDepositedEvent {
    BigDecimal amount;
    String source;  // NEW!
}
```

**Solution**: Event Upcasting

```java
public class MoneyDepositedEventUpcaster implements EventUpcaster {
    public Event upcast(Event event) {
        if (event.getEventVersion() == 1) {
            // Convert v1 to v2
            return MoneyDepositedEvent.builder()
                .amount(event.getAmount())
                .source("UNKNOWN")  // Default for old events
                .eventVersion(2)
                .build();
        }
        return event;
    }
}
```

### 3. Saga Pattern for Distributed Transactions

**Problem**: Transfer money between accounts (2 aggregates):

```java
// ❌ Can't do this atomically
account1.withdraw(100);
account2.deposit(100);
```

**Solution**: Saga with compensating actions:

```java
public Mono<Void> transferMoney(UUID fromId, UUID toId, BigDecimal amount) {
    return service.withdraw(fromId, amount, "Transfer", ...)
        .flatMap(from -> service.deposit(toId, amount, "Transfer", ...)
            .onErrorResume(error -> {
                // Compensate: refund the withdrawal
                return service.deposit(fromId, amount, "Refund", ...)
                    .then(Mono.error(error));
            })
        );
}
```

### 4. Multi-Tenancy

**Problem**: Isolate data for different tenants (banks, companies):

```java
@DomainEvent("account.opened")
public class AccountOpenedEvent extends AbstractDomainEvent {
    private String tenantId;  // Add tenant ID
    // ... other fields
}
```

**Query with tenant isolation:**
```java
@Query("SELECT * FROM account_ledger_read_model WHERE tenant_id = :tenantId AND customer_id = :customerId")
Flux<AccountLedgerReadModel> findByTenantAndCustomer(String tenantId, UUID customerId);
```

### 5. Performance Monitoring

**Track projection lag:**
```java
@Scheduled(fixedRate = 60000)  // Every minute
public void monitorProjectionLag() {
    long latestEvent = eventStore.getLatestGlobalSequence().block();
    long processedEvent = getCurrentPosition().block();
    long lag = latestEvent - processedEvent;

    if (lag > 1000) {
        log.warn("Projection lag: {} events behind", lag);
        metrics.gauge("projection.lag", lag);
    }
}
```

---

## Summary

Congratulations! You've learned how to build a complete event-sourced system. Let's recap:

### Components and Their Roles

| Component | Purpose | When to Use |
|-----------|---------|-------------|
| **Domain Events** | Immutable facts about what happened | Always - the foundation |
| **Aggregate Root** | Business logic and rules | Write operations (commands) |
| **Event Store** | Permanent event log | Always - the source of truth |
| **Snapshots** | Performance optimization | Aggregates with many events |
| **Service Layer** | Orchestration and infrastructure | Always - clean separation |
| **Read Model** | Optimized for queries | Read operations (queries) |
| **Projections** | Keep read model in sync | When using read models |
| **Repository** | Data access for read model | Query operations |

### Key Patterns

1. **Event Sourcing**: Store events, not state
2. **Read/Write Separation**: Separate write models (aggregates) from read models (projections)
3. **Aggregate Pattern**: Encapsulate business rules
4. **Snapshot Pattern**: Cache state for performance
5. **Projection Pattern**: Build read models from events
6. **Saga Pattern**: Coordinate distributed transactions

### Best Practices

✅ **DO:**
- Use past tense for event names (`MoneyDeposited`, not `DepositMoney`)
- Make events immutable and self-contained
- Enforce business rules in aggregates
- Use snapshots for high-volume aggregates
- Separate read and write models (read models for queries, aggregates for writes)
- Track projection positions for reliability
- Version your events for schema evolution

❌ **DON'T:**
- Modify or delete events (they're history!)
- Put business logic in services
- Query aggregates for reporting (use read models)
- Forget to handle concurrent modifications
- Ignore projection lag monitoring

### Next Steps

1. **Run the Example**: See `AccountLedgerIntegrationTest` for complete examples
2. **Explore Advanced Features**: Sagas, event versioning, multi-tenancy
3. **Production Deployment**: Monitoring, scaling, backup strategies
4. **Read More**: Check out the other documentation files

---

## Additional Resources

- **[Event Sourcing Explained](./event-sourcing-explained.md)** - Deep dive into concepts
- **[API Reference](./api-reference.md)** - Complete API documentation
- **[Architecture Guide](./architecture.md)** - System architecture details
- **[Database Schema](./database-schema.md)** - Database design and migrations
- **[Testing Guide](./testing.md)** - Testing strategies and examples

---

**Happy Event Sourcing! 🚀**