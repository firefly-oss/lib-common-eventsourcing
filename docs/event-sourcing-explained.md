# Event Sourcing Explained 📚

## What is Event Sourcing? 🤔

Event Sourcing is a powerful architectural pattern that **stores all changes to application state as a sequence of events**. Instead of storing just the current state of data in your database, you store all the events that led to that state.

Think of it like a **bank statement** - instead of just showing your current balance, it shows every transaction that happened to get to that balance.

## The Problem with Traditional CRUD 😰

Let's say you're building a banking system with traditional CRUD:

```sql
-- Traditional approach: Only current state
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    balance DECIMAL,
    last_updated TIMESTAMP
);

-- What we lose:
-- ❌ How did we get to this balance?
-- ❌ What transactions happened?
-- ❌ When did each change occur?
-- ❌ Who made the changes?
-- ❌ Can we recreate the state at any point in time?
```

**Example: Account with $900 balance**
- Current state: `$900`
- Questions we can't answer:
  - Was this account created with $900?
  - Did someone withdraw $100 from $1000?
  - Did someone deposit $400 to $500?
  - When did the last change happen?

## The Event Sourcing Solution ✅

Instead of storing state, we store **events** (facts about what happened):

```sql
-- Event sourcing approach: Store all events
CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    sequence_number BIGINT,
    created_at TIMESTAMP
);
```

**Same account with event sourcing:**
```json
[
  {
    "eventType": "AccountCreated",
    "aggregateId": "acc-123",
    "data": { "initialBalance": 1000, "accountNumber": "12345" },
    "timestamp": "2023-01-15T10:00:00Z"
  },
  {
    "eventType": "MoneyWithdrawn", 
    "aggregateId": "acc-123",
    "data": { "amount": 100, "reason": "ATM Withdrawal", "location": "Main St" },
    "timestamp": "2023-01-15T14:30:00Z"
  }
]

// Current state: $900 (calculated from events)
// We know EVERYTHING that happened! 🎉
```

## Key Benefits of Event Sourcing 🚀

### 1. **Complete Audit Trail** 📋
```
Traditional: "Balance is $900" 
Event Sourcing: "Account created with $1000, then $100 withdrawn at Main St ATM on Jan 15 at 2:30 PM"
```

### 2. **Time Travel** ⏰
```java
// What was the balance on January 1st?
Account account = eventStore.loadAggregateAtTime(accountId, "2023-01-01");
BigDecimal balance = account.getBalance(); // Easy!
```

### 3. **Natural Scalability** 📈
```
Events are immutable → Easy to cache
Events are append-only → Easy to replicate  
Events can be processed in parallel → High performance
```

### 4. **Business Intelligence** 📊
```sql
-- Questions you can easily answer:
-- How much money was withdrawn from ATMs this month?
SELECT SUM(amount) FROM events 
WHERE event_type = 'MoneyWithdrawn' 
AND data->>'location' LIKE '%ATM%'
AND created_at >= '2023-01-01';

-- What are the most common transaction amounts?
-- Which accounts have the highest activity?
-- How do transaction patterns change over time?
```

### 5. **Flexibility for Future Requirements** 🔮
```java
// New requirement: "Show all transactions over $500"
// Traditional: Needs database schema changes
// Event Sourcing: Just query existing events!

@EventHandler
public void handle(MoneyWithdrawnEvent event) {
    if (event.getAmount().compareTo(BigDecimal.valueOf(500)) > 0) {
        largeTransactionProjection.record(event);
    }
}
```

## How Event Sourcing Works 🔧

### 1. **Commands** (What you want to do)
```java
public class WithdrawMoneyCommand {
    private UUID accountId;
    private BigDecimal amount;
    private String reason;
}
```

### 2. **Events** (What actually happened)
```java
@JsonTypeName("money.withdrawn")
public record MoneyWithdrawnEvent(
    UUID aggregateId,
    BigDecimal amount, 
    String reason,
    Instant timestamp
) implements Event {
    @Override
    public String getEventType() { return "money.withdrawn"; }
}
```

### 3. **Aggregates** (Business logic + current state)
```java
public class Account extends AggregateRoot {
    private BigDecimal balance;
    private String accountNumber;
    
    // Command handler
    public void withdraw(BigDecimal amount, String reason) {
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        applyChange(new MoneyWithdrawnEvent(getId(), amount, reason, Instant.now()));
    }
    
    // Event handler  
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.amount());
    }
}
```

### 4. **Event Store** (Persistence)
```java
// Save events
EventStream stream = eventStore.appendEvents(accountId, events, expectedVersion);

// Load events and reconstruct state
Account account = eventStore.loadAggregate(accountId, Account.class);
```

## The Firefly Event Sourcing Architecture 🏗️

### Why We Built It This Way

#### **1. Domain-Driven Design (DDD) Foundation**
```java
// Clear domain concepts
public class Account extends AggregateRoot {
    // Business rules are explicit
    public void withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Cannot withdraw " + amount);
        }
        applyChange(new MoneyWithdrawnEvent(getId(), amount));
    }
}
```

**Why?** Banking business rules are complex. DDD helps us model the real world accurately.

#### **2. Reactive Programming with R2DBC**
```java
// Non-blocking database operations
public Mono<EventStream> appendEvents(UUID aggregateId, List<Event> events) {
    return transactionalOperator.transactional(
        checkConcurrency(aggregateId, expectedVersion)
            .flatMap(currentVersion -> insertEvents(events))
            .map(result -> EventStream.of(aggregateId, events))
    );
}
```

**Why?** Banking systems need high throughput and low latency. Reactive programming handles thousands of concurrent operations efficiently.

#### **3. PostgreSQL with JSONB Optimization**
```sql
-- Efficient JSON querying
CREATE TABLE events (
    event_data JSONB NOT NULL,  -- Native JSON support
    ...
);

-- Fast queries on event data
SELECT * FROM events 
WHERE event_data->>'amount' > '1000'  -- Direct JSON querying
AND event_type = 'money.withdrawn';
```

**Why?** Events have flexible structure. JSONB gives us JSON flexibility with SQL performance.

#### **4. Optimistic Concurrency Control**
```java
public Mono<EventStream> appendEvents(UUID aggregateId, List<Event> events, 
                                    long expectedVersion) {
    return checkConcurrency(aggregateId, expectedVersion)  // Prevent conflicts
        .flatMap(currentVersion -> persistEvents(events));
}
```

**Why?** Multiple users might modify the same account simultaneously. Optimistic locking prevents conflicts without blocking.

#### **5. Comprehensive Monitoring**
```java
@Component 
public class EventStoreMetrics {
    private final Timer appendTimer = Timer.builder("eventstore.append.duration").register(registry);
    private final Counter eventsCounter = Counter.builder("eventstore.events.appended").register(registry);
    
    public void recordAppend(Duration duration, int eventCount) {
        appendTimer.record(duration);
        eventsCounter.increment(eventCount);
    }
}
```

**Why?** Production banking systems need observability. We built-in metrics, health checks, and monitoring.

## Common Patterns We Implemented 🎯

### **1. Aggregate Pattern**
- Encapsulates business logic
- Maintains consistency boundaries
- Generates events from commands

### **2. Repository Pattern** 
- `EventStore` abstracts persistence
- Clean separation of domain and infrastructure
- Easy to test and switch implementations

### **3. CQRS (Command Query Responsibility Segregation)**
- Commands modify state (write side)
- Queries read projections (read side)  
- Optimized for different access patterns

### **4. Transactional Outbox Pattern**
```sql
CREATE TABLE event_outbox (
    outbox_id UUID PRIMARY KEY,
    event_data JSONB NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    ...
);
```
- Reliable event publishing
- Consistency between database and message bus
- Handles network failures gracefully

### **5. Snapshot Pattern**
```java
// For aggregates with many events
if (eventCount > snapshotThreshold) {
    Snapshot snapshot = createSnapshot(aggregate);
    snapshotStore.save(snapshot);
}
```
- Performance optimization
- Faster aggregate loading
- Configurable thresholds

## When to Use Event Sourcing 🤷‍♂️

### ✅ **Great for:**
- **Audit Requirements**: Financial, medical, legal systems
- **Complex Business Logic**: Banking, trading, insurance
- **Analytics Heavy**: Need to answer "how did we get here?"
- **Temporal Queries**: "What was the state on X date?"
- **High Scalability**: Read-heavy systems with complex reporting

### ❌ **Consider Alternatives for:**
- **Simple CRUD**: Basic user profiles, settings
- **Low Audit Requirements**: Internal tools, caches
- **Small Teams**: Additional complexity might not be worth it
- **Rapid Prototyping**: Adds development overhead

## Real-World Banking Example 🏦

```java
// Traditional approach - we lose information
@Entity
public class Account {
    private BigDecimal balance; // Only current state
    // Lost: When? How? Who? Why?
}

// Event sourcing approach - complete story
public class Account extends AggregateRoot {
    private BigDecimal balance;
    
    public void withdraw(WithdrawMoneyCommand cmd) {
        // Business rule validation
        if (balance.compareTo(cmd.getAmount()) < 0) {
            throw new InsufficientFundsException();
        }
        
        // Record what happened
        applyChange(new MoneyWithdrawnEvent(
            getId(), 
            cmd.getAmount(), 
            cmd.getReason(),
            cmd.getAtmLocation(),
            cmd.getUserId(),
            Instant.now()
        ));
    }
    
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
        // State is derived from events
    }
}
```

**Benefits in banking:**
- Complete transaction history for compliance
- Fraud detection through pattern analysis  
- Customer service can see exact transaction details
- Regulatory reporting is straightforward
- Can replay events to test new business rules

## Next Steps 📖

Now that you understand the concepts, explore:

1. **[Quick Start Guide](./quick-start.md)** - Build your first event-sourced application
2. **[API Reference](./api-reference.md)** - Detailed interface documentation  
3. **[Configuration Guide](./configuration.md)** - Production setup options
4. **[Testing Guide](./testing.md)** - How to test event-sourced systems
5. **[Banking Example](./examples/banking-example.md)** - Complete real-world example

Event sourcing might seem complex at first, but it provides powerful capabilities that traditional CRUD cannot match. The Firefly Event Sourcing Library handles the complexity so you can focus on your business logic! 🚀