# Firefly Common Event Sourcing Library

A comprehensive Spring Boot library that provides Event Sourcing pattern implementation with reactive programming support, featuring event stores, aggregates, snapshots, and integration with EDA messaging.

## Features

- **🚀 Reactive Architecture**: Built on Project Reactor for non-blocking operations
- **📦 Event Store Abstraction**: Pluggable event store implementations (R2DBC, MongoDB, etc.)
- **🏗️ Aggregate Framework**: Base classes for implementing event-sourced aggregates
- **📸 Snapshot Support**: Automatic snapshotting for performance optimization
- **🔄 EDA Integration**: Seamless integration with Firefly's EDA library for event publishing
- **🔧 Auto-Configuration**: Spring Boot auto-configuration for minimal setup
- **🗄️ R2DBC Integration**: Leverages lib-common-r2dbc for advanced database utilities and transaction management
- **📊 Metrics & Health**: Built-in metrics and health indicators
- **🔍 Distributed Tracing**: Automatic tracing integration
- **⚡ Performance Optimized**: Batching, caching, and circuit breaker support

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-eventsourcing</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configuration

```yaml
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
      type: KAFKA
      destination-prefix: events
```

### 3. Create Domain Events

```java
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
}
```

### 4. Implement Aggregates

```java
public class Account extends AggregateRoot {
    private String accountNumber;
    private BigDecimal balance;
    
    public Account(UUID id, String accountNumber, BigDecimal initialBalance) {
        super(id, "Account");
        applyChange(new AccountCreatedEvent(id, accountNumber, initialBalance));
    }
    
    public void withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        applyChange(new MoneyWithdrawnEvent(getId(), amount));
    }
    
    private void on(AccountCreatedEvent event) {
        this.accountNumber = event.getAccountNumber();
        this.balance = event.getInitialBalance();
    }
    
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
    }
}
```

### 5. Use Event Store

```java
@Service
public class AccountService {
    
    private final EventStore eventStore;
    
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
                });
    }
}
```

## Core Concepts

### Events
Events are immutable facts that represent state changes in your domain:

- Implement the `Event` interface
- Use `@JsonTypeName` for serialization
- Should be serializable and self-contained
- Include aggregate ID and event type

### Aggregates
Aggregates are the consistency boundaries in your domain:

- Extend `AggregateRoot`
- Apply events using `applyChange()`
- Implement event handlers (`on` methods)
- Maintain business invariants

### Event Store
The event store persists and retrieves events:

- Atomic event appending with optimistic concurrency control
- Event streaming capabilities
- Global ordering guarantees
- Support for multiple storage backends

### Snapshots
Snapshots optimize aggregate reconstruction:

- Automatic snapshotting based on event count
- Configurable retention policies
- Optional compression and caching
- Pluggable storage backends

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Application   │    │   Domain Layer  │    │  Infrastructure │
│    Services     │    │   (Aggregates)  │    │     Layer       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Event Store    │    │     Events      │    │   EDA Publisher │
│   Interface     │    │   & Streams     │    │   Integration   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                                             │
         ▼                                             ▼
┌─────────────────┐                          ┌─────────────────┐
│  Persistence    │                          │   Message       │
│   Adapters      │                          │   Brokers       │
│  (R2DBC, etc.)  │                          │ (Kafka, etc.)   │
└─────────────────┘                          └─────────────────┘
```

## Configuration Properties

### Event Store
```yaml
firefly:
  eventsourcing:
    store:
      type: r2dbc                    # Event store type
      batch-size: 100                # Batch size for operations
      connection-timeout: 30s        # Connection timeout
      query-timeout: 30s             # Query timeout
      validate-schemas: true         # Validate event schemas
      max-events-per-load: 1000      # Max events per load
```

### Snapshots
```yaml
firefly:
  eventsourcing:
    snapshot:
      enabled: true                  # Enable snapshots
      threshold: 50                  # Events before snapshot
      check-interval: 5m             # Snapshot check frequency
      keep-count: 3                  # Snapshots to keep
      max-age: 30d                   # Maximum snapshot age
      compression: true              # Compress snapshots
      caching: true                  # Enable snapshot caching
```

### Publisher
```yaml
firefly:
  eventsourcing:
    publisher:
      enabled: true                  # Enable event publishing
      type: KAFKA                    # Publisher type
      destination-prefix: events     # Topic/queue prefix
      async: true                    # Async publishing
      batch-size: 10                # Publishing batch size
      continue-on-failure: true     # Continue on publish failures
```

## Database Schema

### PostgreSQL (R2DBC)
```sql
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

CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);
CREATE INDEX idx_events_global_sequence ON events(global_sequence);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_created_at ON events(created_at);
```

### Snapshots Table
```sql
CREATE TABLE snapshots (
    snapshot_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    snapshot_type VARCHAR(255) NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(aggregate_id, version)
);

CREATE INDEX idx_snapshots_aggregate ON snapshots(aggregate_id, aggregate_type);
CREATE INDEX idx_snapshots_version ON snapshots(version);
```

## Best Practices

1. **Event Design**
   - Keep events small and focused
   - Make events immutable
   - Use meaningful event names
   - Include all necessary data

2. **Aggregate Design**
   - Keep aggregates small
   - Maintain consistency boundaries
   - Avoid loading multiple aggregates in a single transaction
   - Use eventual consistency between aggregates

3. **Performance**
   - Use snapshots for aggregates with many events
   - Configure appropriate batch sizes
   - Monitor event store performance
   - Consider read model projections

4. **Error Handling**
   - Handle concurrency exceptions gracefully
   - Implement proper retry mechanisms
   - Monitor failed event publishing
   - Use circuit breakers for external dependencies

## Documentation

For comprehensive documentation, see the [docs](./docs/) directory:

- [📚 Documentation Overview](./docs/README.md) - Start here for complete documentation
- [⚡ Quick Start Guide](./docs/quick-start.md) - Get running in 5 minutes
- [🏗️ Architecture Overview](./docs/architecture.md) - System design and components
- [⚙️ Configuration Reference](./docs/configuration.md) - Complete configuration guide
- [📖 API Reference](./docs/api-reference.md) - Detailed API documentation
- [🗃️ Database Schema](./docs/database-schema.md) - Schema definitions and queries
- [💼 Banking Example](./docs/examples/banking-example.md) - Complete working example

## Integration with Other Firefly Libraries

- **lib-common-r2dbc**: Reactive database access, filtering, pagination utilities, and transaction management
- **lib-common-eda**: Event publishing to message brokers
- **lib-common-cache**: Snapshot caching
- **lib-common-domain**: Domain abstractions
- **lib-common-cqrs**: Command/query separation

## Monitoring

The library provides several monitoring capabilities:

- **Health Indicators**: Event store and snapshot store health
- **Metrics**: Event throughput, latencies, errors
- **Distributed Tracing**: Automatic trace propagation
- **Statistics**: Event counts, storage usage

## Testing

Use the provided test utilities for testing event-sourced aggregates:

```java
@Test
void testAccountWithdrawal() {
    UUID accountId = UUID.randomUUID();
    Account account = new Account(accountId, "12345", BigDecimal.valueOf(1000));
    
    account.withdraw(BigDecimal.valueOf(100));
    
    assertEquals(BigDecimal.valueOf(900), account.getBalance());
    assertEquals(1, account.getUncommittedEventCount());
}
```

## License

Licensed under the Apache License, Version 2.0.