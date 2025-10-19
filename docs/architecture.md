# Architecture Overview

This document provides a comprehensive overview of the Firefly Event Sourcing Library architecture, design principles, and component interactions.

## System Overview

The Firefly Event Sourcing Library follows the principles of Domain-Driven Design (DDD) and Event Sourcing, providing a production-ready implementation for building event-sourced systems in the Firefly banking platform.

### Core Principles

1. **Event Sourcing**: State is derived from a sequence of events
2. **Reactive Programming**: Non-blocking operations using Project Reactor
3. **Domain-Driven Design**: Rich domain models with clear boundaries
4. **Read/Write Separation**: Separate write models (aggregates) from read models (projections)
5. **Eventual Consistency**: Asynchronous processing and integration

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Layer                            │
│  • Services      • Controllers     • Application Handlers      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Domain Layer                                │
│  • Aggregates    • Events        • Domain Services             │
│  • AggregateRoot • Event         • Business Logic              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                Infrastructure Layer                             │
│  • EventStore    • SnapshotStore  • Publishers                 │
│  • R2DBC        • EDA Integration • Configuration               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   External Systems                              │
│  • Database     • Message Brokers  • Cache                     │
│  • PostgreSQL   • Kafka/RabbitMQ   • Redis                     │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Domain Layer

#### Event Interface
- **Package**: `com.firefly.common.eventsourcing.domain`
- **Purpose**: Defines the contract for domain events
- **Key Methods**: `getEventType()`, `getAggregateId()`, `getEventTimestamp()`, `getMetadata()`

#### AggregateRoot Class
- **Package**: `com.firefly.common.eventsourcing.aggregate`
- **Purpose**: Base class for all event-sourced aggregates
- **Features**:
  - Event application with `applyChange()`
  - State reconstruction with `loadFromHistory()`
  - Uncommitted event tracking
  - Version management for optimistic concurrency
  - Reflection-based event handler invocation

#### EventEnvelope Class
- **Package**: `com.firefly.common.eventsourcing.domain`
- **Purpose**: Wraps domain events with persistence metadata
- **Contains**: Event ID, aggregate info, versioning, timestamps, metadata

#### EventStream Class
- **Package**: `com.firefly.common.eventsourcing.domain`
- **Purpose**: Collection of events for an aggregate
- **Features**: Filtering, querying, statistics

### 2. Persistence Layer

#### EventStore Interface
- **Package**: `com.firefly.common.eventsourcing.store`
- **Purpose**: Main interface for event persistence and retrieval
- **Implementations**: `R2dbcEventStore` (primary)

#### R2dbcEventStore Implementation
- **Package**: `com.firefly.common.eventsourcing.store.r2dbc`
- **Features**:
  - Reactive database operations
  - Transaction management with `TransactionalOperator`
  - Optimistic concurrency control
  - Event streaming capabilities
  - Integration with lib-common-r2dbc

#### SnapshotStore Interface
- **Package**: `com.firefly.common.eventsourcing.snapshot`
- **Purpose**: Snapshot persistence for performance optimization
- **Features**: Save, load, delete snapshots with versioning

### 3. Transaction Management Layer

#### @EventSourcingTransactional Annotation
- **Package**: `com.firefly.common.eventsourcing.annotation`
- **Purpose**: Declarative transaction management for event sourcing operations
- **Features**:
  - Full ACID guarantees (Atomicity, Consistency, Isolation, Durability)
  - Configurable isolation levels (DEFAULT, READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE)
  - Transaction propagation control (REQUIRED, REQUIRES_NEW, MANDATORY, NEVER, SUPPORTS, NOT_SUPPORTED)
  - Automatic rollback on RuntimeException and Error
  - Configurable rollback rules (rollbackFor, noRollbackFor)
  - Automatic retry on concurrency conflicts with exponential backoff
  - Automatic event publishing after successful commit (Transactional Outbox pattern)
  - Timeout support
  - Read-only transaction optimization

#### EventSourcingTransactionalAspect
- **Package**: `com.firefly.common.eventsourcing.transaction`
- **Purpose**: AOP aspect that intercepts @EventSourcingTransactional methods
- **Implementation**:
  - Uses Spring AOP with AspectJ annotations
  - Integrates with Spring's ReactiveTransactionManager
  - Tracks pending events in Reactor Context during transaction
  - Publishes events via EventSourcingPublisher after commit
  - Implements retry logic with exponential backoff for ConcurrencyException
  - Supports both Mono and Flux return types
  - Maps isolation levels to Spring transaction constants
  - Applies transaction propagation behavior

**ACID Properties:**
- **Atomicity**: All events saved or none (all-or-nothing) via database transactions
- **Consistency**: Aggregate version checks prevent conflicts via optimistic locking
- **Isolation**: Configurable isolation levels control visibility between concurrent transactions
- **Durability**: Events persisted to database before method returns

**Transaction Lifecycle:**
1. Begin transaction with configured isolation level and propagation
2. Execute business logic (load aggregate, apply changes)
3. Append events to event store
4. Track pending events in Reactor Context
5. Commit transaction (or rollback on error)
6. Publish events to message bus (only after successful commit)
7. Return result to caller

#### @DomainEvent Annotation
- **Package**: `com.firefly.common.eventsourcing.annotation`
- **Purpose**: Declarative event type definition aligned with Spring conventions
- **Features**:
  - Combines @JsonTypeName for Jackson polymorphic serialization
  - Metadata: description, version, publishable flag, tags
  - Eliminates need to override getEventType() method
  - Compile-time validation of event type uniqueness

### 4. Integration Layer

#### EventSourcingPublisher
- **Package**: `com.firefly.common.eventsourcing.publisher`
- **Purpose**: Publishes events to external message systems
- **Integration**: Uses lib-common-eda for message bus abstraction

#### Configuration System
- **Package**: `com.firefly.common.eventsourcing.config`
- **Components**:
  - `EventSourcingProperties`: Configuration properties
  - `EventSourcingAutoConfiguration`: Spring Boot auto-configuration
  - `EventStoreAutoConfiguration`: Event store setup
  - `SnapshotAutoConfiguration`: Snapshot system setup

## Data Flow

### 1. Command Processing Flow

```
Client Request
      │
      ▼
Application Service
      │
      ▼ 1. Load Aggregate
EventStore.loadEventStream()
      │
      ▼ 2. Reconstruct State
AggregateRoot.loadFromHistory()
      │
      ▼ 3. Execute Business Logic
Aggregate.businessMethod()
      │
      ▼ 4. Apply Events
AggregateRoot.applyChange()
      │
      ▼ 5. Persist Events
EventStore.appendEvents()
      │
      ▼ 6. Publish Events (async)
EventSourcingPublisher.publishEvents()
      │
      ▼
Response to Client
```

### 2. Event Persistence Flow

```
Events from Aggregate
      │
      ▼ 1. Create Envelopes
EventEnvelope.of()
      │
      ▼ 2. Begin Transaction
TransactionalOperator.transactional()
      │
      ▼ 3. Check Concurrency
R2dbcEventStore.checkConcurrency()
      │
      ▼ 4. Insert Events
R2dbcEventStore.insertEvents()
      │
      ▼ 5. Update Global Sequence
GlobalSequenceCounter.increment()
      │
      ▼ 6. Commit Transaction
Transaction.commit()
      │
      ▼
EventStream Response
```

### 3. Event Reconstruction Flow

```
LoadEventStream Request
      │
      ▼ 1. Query Database
R2dbcEntityTemplate.select()
      │
      ▼ 2. Map Rows to Envelopes
mapToEventEnvelope()
      │
      ▼ 3. Deserialize Events
deserializeEvent()
      │
      ▼ 4. Create EventStream
EventStream.of()
      │
      ▼ 5. Load into Aggregate
AggregateRoot.loadFromHistory()
      │
      ▼ 6. Apply Events Sequentially
AggregateRoot.applyEvent()
      │
      ▼
Reconstructed Aggregate
```

## Integration with lib-common-r2dbc

The event sourcing library leverages the existing lib-common-r2dbc infrastructure:

### Database Operations
- **DatabaseClient**: For custom SQL queries
- **R2dbcEntityTemplate**: For type-safe ORM operations
- **ReactiveTransactionManager**: For transaction management
- **TransactionalOperator**: For reactive transaction wrapping

### Advanced Features
- **FilterUtils**: For complex event querying (future enhancement)
- **PaginationUtils**: For paginated event streaming (future enhancement)
- **Connection Management**: Shared connection pooling

## Concurrency Control

### Optimistic Locking Strategy

```java
// 1. Load current version
Mono<Long> currentVersion = eventStore.getAggregateVersion(aggregateId, aggregateType);

// 2. Check expected vs actual version
if (currentVersion != expectedVersion) {
    throw new ConcurrencyException(aggregateId, aggregateType, expectedVersion, currentVersion);
}

// 3. Append events with version increment
eventStore.appendEvents(aggregateId, aggregateType, events, currentVersion);
```

### Database Constraints
```sql
-- Ensure unique version per aggregate
UNIQUE(aggregate_id, aggregate_version)

-- Global ordering
global_sequence BIGSERIAL UNIQUE
```

## Event Processing Patterns

### 1. Command Handler Pattern

```java
@Component
public class CreateAccountHandler {
    private final EventStore eventStore;
    
    public Mono<AccountId> handle(CreateAccountCommand command) {
        return Mono.fromCallable(() -> {
            Account account = new Account(
                command.getAccountId(),
                command.getAccountNumber(),
                command.getInitialBalance()
            );
            return account;
        })
        .flatMap(account -> 
            eventStore.appendEvents(
                account.getId(),
                "Account",
                account.getUncommittedEvents(),
                0L
            )
        )
        .map(stream -> command.getAccountId());
    }
}
```

### 2. Event Handler Pattern

```java
@Component
public class AccountProjectionHandler {
    
    @EventHandler
    public void on(AccountCreatedEvent event) {
        // Update read model
        accountReadModelRepository.save(
            new AccountReadModel(
                event.getAggregateId(),
                event.getAccountNumber(),
                event.getInitialBalance()
            )
        );
    }
}
```

### 3. Saga Pattern (Process Manager)

```java
@Component
public class MoneyTransferSaga {
    
    @SagaOrchestrationStart
    public void handle(TransferMoneyCommand command) {
        // Step 1: Debit source account
        commandGateway.send(
            new DebitAccountCommand(
                command.getFromAccountId(),
                command.getAmount()
            )
        );
    }
    
    @SagaOrchestrationContinue
    public void on(AccountDebitedEvent event) {
        // Step 2: Credit target account
        commandGateway.send(
            new CreditAccountCommand(
                sagaData.getToAccountId(),
                event.getAmount()
            )
        );
    }
}
```

## Error Handling Strategy

### Exception Hierarchy

```
RuntimeException
  └── EventStoreException
      ├── ConcurrencyException
      ├── EventSerializationException
      └── EventStoreConnectionException
      
  └── EventHandlerException
      ├── EventHandlerNotFoundException  
      └── EventHandlerInvocationException
      
  └── EventPublishingException
      ├── PublisherNotAvailableException
      └── PublishTimeoutException
```

### Error Recovery Patterns

1. **Retry with Exponential Backoff**
   ```java
   return operation()
       .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
           .filter(throwable -> throwable instanceof TransientException));
   ```

2. **Circuit Breaker Pattern**
   ```yaml
   firefly:
     eventsourcing:
       performance:
         circuit-breaker:
           enabled: true
           failure-rate-threshold: 50.0
   ```

3. **Graceful Degradation**
   ```java
   return primaryOperation()
       .onErrorResume(ex -> fallbackOperation())
       .onErrorReturn(defaultValue);
   ```

## Performance Considerations

### 1. Batching Strategy
- Events are batched for persistence efficiency
- Configurable batch sizes per environment
- Parallel processing for large event streams

### 2. Snapshot Strategy
- Automatic snapshot creation based on event count threshold
- Configurable snapshot frequency and retention
- Compression and caching support

### 3. Connection Management
- R2DBC connection pooling through lib-common-r2dbc
- Configurable pool sizes and timeouts
- Connection health monitoring

### 4. Memory Management
- Streaming APIs for large result sets
- Backpressure handling in reactive streams
- Configurable buffer sizes

## Security Considerations

### 1. Event Data Protection
- Sensitive data encryption at rest
- PII data masking in events
- Audit trail for event access

### 2. Access Control
- Role-based access to event stores
- Aggregate-level permissions
- Event type restrictions

### 3. Data Privacy
- Right to be forgotten implementation
- Data retention policies
- GDPR compliance features

## Monitoring and Observability

### 1. Metrics Collection
- Event throughput and latency
- Aggregate reconstruction times
- Error rates and types
- Database connection pool stats

### 2. Distributed Tracing
- Request tracing across services
- Event processing correlation
- Performance bottleneck identification

### 3. Health Checks
- Database connectivity
- Event store availability
- Publisher health status

## Deployment Architecture

### 1. Microservice Deployment
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Service   │    │   Service   │    │   Service   │
│      A      │    │      B      │    │      C      │
│             │    │             │    │             │
│ Event Store │    │ Event Store │    │ Event Store │
└─────────────┘    └─────────────┘    └─────────────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                  ┌─────────────┐
                  │  Shared     │
                  │  Database   │
                  │ (PostgreSQL)│
                  └─────────────┘
```

### 2. Event Streaming Architecture
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Command   │    │    Event    │    │    Query    │
│   Services  │───▶│    Store    │───▶│   Services  │
│             │    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘
                           │
                           ▼
                  ┌─────────────┐
                  │  Message    │
                  │   Broker    │
                  │  (Kafka)    │
                  └─────────────┘
                           │
                           ▼
                  ┌─────────────┐
                  │  External   │
                  │  Services   │
                  │             │
                  └─────────────┘
```

This architecture provides a robust foundation for building event-sourced systems within the Firefly banking platform, ensuring scalability, reliability, and maintainability.