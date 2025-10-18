# API Reference

Complete API documentation for the Firefly Event Sourcing Library.

## Core Interfaces

### Event

Interface for domain events in the event sourcing system.

**Package**: `com.firefly.common.eventsourcing.domain`

```java
public interface Event {
    String getEventType();
    UUID getAggregateId();
    Instant getEventTimestamp();
    Map<String, Object> getMetadata();
}
```

#### Methods

| Method | Return Type | Description |
|--------|------------|-------------|
| `getEventType()` | `String` | Returns the type identifier of the event |
| `getAggregateId()` | `UUID` | Returns the ID of the aggregate this event belongs to |
| `getEventTimestamp()` | `Instant` | Returns when the event occurred |
| `getMetadata()` | `Map<String, Object>` | Returns additional metadata for the event |

#### Default Implementation

Events typically provide default implementations for `getEventTimestamp()` and `getMetadata()`:

```java
@Override
public Instant getEventTimestamp() {
    return Instant.now();
}

@Override  
public Map<String, Object> getMetadata() {
    return Map.of();
}
```

#### Example Implementation

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
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
}
```

### EventStore

Main interface for event persistence and retrieval.

**Package**: `com.firefly.common.eventsourcing.store`

```java
public interface EventStore {
    // Event persistence
    Mono<EventStream> appendEvents(UUID aggregateId, String aggregateType, 
                                  List<Event> events, long expectedVersion, 
                                  Map<String, Object> metadata);
    
    Mono<EventStream> appendEvents(UUID aggregateId, String aggregateType,
                                  List<Event> events, long expectedVersion);
    
    // Event retrieval
    Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType);
    
    Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, 
                                     long fromVersion);
    
    Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType,
                                     long fromVersion, long toVersion);
    
    // Aggregate information
    Mono<Long> getAggregateVersion(UUID aggregateId, String aggregateType);
    
    Mono<Boolean> aggregateExists(UUID aggregateId, String aggregateType);
    
    // Event streaming
    Flux<EventEnvelope> streamAllEvents();
    
    Flux<EventEnvelope> streamAllEvents(long fromSequence);
    
    Flux<EventEnvelope> streamEventsByType(List<String> eventTypes);
    
    Flux<EventEnvelope> streamEventsByAggregateType(List<String> aggregateTypes);
    
    Flux<EventEnvelope> streamEventsByTimeRange(Instant from, Instant to);
    
    Flux<EventEnvelope> streamEventsByMetadata(Map<String, Object> metadataCriteria);
    
    // Utility methods
    Mono<Long> getCurrentGlobalSequence();
    
    Mono<Boolean> isHealthy();
    
    Mono<EventStoreStatistics> getStatistics();
}
```

#### Method Details

##### appendEvents

```java
Mono<EventStream> appendEvents(UUID aggregateId, String aggregateType, 
                              List<Event> events, long expectedVersion,
                              Map<String, Object> metadata)
```

Appends new events to an aggregate's event stream with metadata.

**Parameters:**
- `aggregateId`: Unique identifier of the aggregate
- `aggregateType`: Type name of the aggregate (e.g., "Account")  
- `events`: List of events to append
- `expectedVersion`: Expected current version for optimistic concurrency control
- `metadata`: Additional metadata to attach to all events

**Returns:** `Mono<EventStream>` - The updated event stream

**Throws:**
- `ConcurrencyException` - If expectedVersion doesn't match current version
- `EventStoreException` - If the operation fails

##### loadEventStream

```java
Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType)
```

Loads the complete event stream for an aggregate.

**Parameters:**
- `aggregateId`: Unique identifier of the aggregate
- `aggregateType`: Type name of the aggregate

**Returns:** `Mono<EventStream>` - The event stream, or empty if aggregate doesn't exist

##### streamAllEvents

```java
Flux<EventEnvelope> streamAllEvents(long fromSequence)
```

Streams events from a specific global sequence number.

**Parameters:**
- `fromSequence`: Global sequence number to start from (inclusive)

**Returns:** `Flux<EventEnvelope>` - Stream of event envelopes

### AggregateRoot

Base class for event-sourced aggregates.

**Package**: `com.firefly.common.eventsourcing.aggregate`

```java
public abstract class AggregateRoot {
    // Constructor
    protected AggregateRoot(UUID id, String aggregateType);
    
    // Event application
    protected void applyChange(Event event);
    
    public void loadFromHistory(List<EventEnvelope> events);
    
    // Event management
    public List<Event> getUncommittedEvents();
    
    public void markEventsAsCommitted();
    
    public boolean hasUncommittedEvents();
    
    public int getUncommittedEventCount();
    
    // Aggregate information
    public UUID getId();
    
    public String getAggregateType();
    
    public long getVersion();
    
    // State management
    protected void markAsDeleted();
    
    public boolean isDeleted();
}
```

#### Method Details

##### Constructor

```java
protected AggregateRoot(UUID id, String aggregateType)
```

Creates a new aggregate with the specified ID and type.

**Parameters:**
- `id`: Unique identifier for the aggregate (cannot be null)
- `aggregateType`: Type name for the aggregate (cannot be null or empty)

**Throws:**
- `IllegalArgumentException` - If id is null or aggregateType is null/empty

##### applyChange

```java
protected void applyChange(Event event)
```

Applies a new event to the aggregate.

**Process:**
1. Adds event to uncommitted events list
2. Calls the appropriate event handler method
3. Increments the version number

**Parameters:**
- `event`: The event to apply (cannot be null)

**Throws:**
- `IllegalArgumentException` - If event is null or has wrong aggregate ID
- `EventHandlerException` - If no event handler is found

##### loadFromHistory

```java
public void loadFromHistory(List<EventEnvelope> events)
```

Reconstructs aggregate state from historical events.

**Process:**
1. Validates all events belong to this aggregate
2. Applies each event in sequence
3. Sets version to match the last event
4. Clears uncommitted events

**Parameters:**
- `events`: List of historical event envelopes

**Throws:**
- `IllegalArgumentException` - If events belong to different aggregate

#### Event Handlers

Event handler methods must follow this pattern:

```java
private void on(EventType event) {
    // Update aggregate state based on event
}
```

**Rules:**
- Method name must be "on"
- Must be private
- Must take single parameter of the event type
- Should only update aggregate state (no side effects)

**Example:**

```java
public class Account extends AggregateRoot {
    private BigDecimal balance;
    
    private void on(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.getAmount());
    }
}
```

### EventEnvelope

Wrapper for events with persistence metadata.

**Package**: `com.firefly.common.eventsourcing.domain`

```java
@Data
@Builder
public class EventEnvelope {
    private final UUID eventId;
    private final Event event;
    private final UUID aggregateId;
    private final String aggregateType;
    private final long aggregateVersion;
    private final long globalSequence;
    private final String eventType;
    private final Instant createdAt;
    private final Map<String, Object> metadata;
    
    // Factory methods
    public static EventEnvelope of(Event event, String aggregateType, 
                                  long aggregateVersion, long globalSequence,
                                  Map<String, Object> metadata);
    
    public static EventEnvelope of(Event event, String aggregateType,
                                  long aggregateVersion, long globalSequence);
    
    // Utility methods
    public Object getMetadataValue(String key);
    
    public <T> T getMetadataValue(String key, Class<T> type);
    
    public String getCorrelationId();
    
    public String getCausationId();
    
    public String getUserId();
}
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `eventId` | `UUID` | Unique identifier for this event envelope |
| `event` | `Event` | The wrapped domain event |
| `aggregateId` | `UUID` | ID of the aggregate this event belongs to |
| `aggregateType` | `String` | Type of the aggregate |
| `aggregateVersion` | `long` | Version number within the aggregate stream |
| `globalSequence` | `long` | Global sequence number across all events |
| `eventType` | `String` | Type of the domain event |
| `createdAt` | `Instant` | When the event was persisted |
| `metadata` | `Map<String, Object>` | Additional metadata |

### EventStream

Collection of events for an aggregate.

**Package**: `com.firefly.common.eventsourcing.domain`

```java
@Data
public class EventStream {
    private final UUID aggregateId;
    private final String aggregateType;
    private final List<EventEnvelope> events;
    private final long currentVersion;
    private final long fromVersion;
    
    // Factory methods
    public static EventStream of(UUID aggregateId, String aggregateType, 
                                List<EventEnvelope> events);
    
    public static EventStream empty(UUID aggregateId, String aggregateType);
    
    // Query methods
    public boolean isEmpty();
    
    public int size();
    
    public EventEnvelope getFirstEvent();
    
    public EventEnvelope getLastEvent();
    
    public EventEnvelope getEventAt(int index);
    
    // Filtering
    public EventStream filterByType(String eventType);
    
    public EventStream filterByVersion(long fromVersion, long toVersion);
    
    public EventStream filterByTimeRange(Instant from, Instant to);
    
    // Statistics
    public Map<String, Long> getEventTypeCounts();
}
```

## Configuration Classes

### EventSourcingProperties

Main configuration properties class.

**Package**: `com.firefly.common.eventsourcing.config`

```java
@Data
@ConfigurationProperties(prefix = "firefly.eventsourcing")
public class EventSourcingProperties {
    private boolean enabled = true;
    private EventStore store = new EventStore();
    private Snapshot snapshot = new Snapshot();
    private Publisher publisher = new Publisher();
    private Performance performance = new Performance();
    
    // Nested configuration classes
    public static class EventStore { /* ... */ }
    public static class Snapshot { /* ... */ }
    public static class Publisher { /* ... */ }
    public static class Performance { /* ... */ }
}
```

## Exception Classes

### EventStoreException

Base exception for event store operations.

**Package**: `com.firefly.common.eventsourcing.store`

```java
public class EventStoreException extends RuntimeException {
    public EventStoreException(String message);
    public EventStoreException(String message, Throwable cause);
}
```

### ConcurrencyException

Exception thrown when optimistic concurrency control fails.

```java
public class ConcurrencyException extends EventStoreException {
    private final UUID aggregateId;
    private final String aggregateType;
    private final long expectedVersion;
    private final long actualVersion;
    
    public ConcurrencyException(UUID aggregateId, String aggregateType,
                               long expectedVersion, long actualVersion);
    
    // Getters for exception details
    public UUID getAggregateId();
    public String getAggregateType();
    public long getExpectedVersion();
    public long getActualVersion();
}
```

### EventHandlerException

Exception thrown when event handler invocation fails.

**Package**: `com.firefly.common.eventsourcing.aggregate`

```java
public class EventHandlerException extends RuntimeException {
    public EventHandlerException(String message);
    public EventHandlerException(String message, Throwable cause);
}
```

## Statistics and Monitoring

### EventStoreStatistics

Statistics about the event store.

**Package**: `com.firefly.common.eventsourcing.store`

```java
@Data
@Builder
public class EventStoreStatistics {
    private final long totalEvents;
    private final long totalAggregates;
    private final long currentGlobalSequence;
    private final Map<String, Long> eventTypeCounts;
    private final Map<String, Long> aggregateTypeCounts;
}
```

## Publisher Integration

### EventSourcingPublisher

Publishes events to external message systems.

**Package**: `com.firefly.common.eventsourcing.publisher`

```java
@Component
public class EventSourcingPublisher {
    public Mono<Void> publishEvent(EventEnvelope envelope);
    
    public Mono<Void> publishEvents(List<EventEnvelope> envelopes);
    
    public Flux<Void> publishEventStream(Flux<EventEnvelope> eventStream);
    
    // Configuration methods
    public boolean isEnabled();
    
    public PublisherType getPublisherType();
}
```

## Snapshot Support

### Snapshot

Interface for aggregate snapshots.

**Package**: `com.firefly.common.eventsourcing.snapshot`

```java
public interface Snapshot {
    UUID getAggregateId();
    String getAggregateType();
    long getVersion();
    String getSnapshotType();
    Instant getCreatedAt();
    <T> T getData(Class<T> type);
}
```

### SnapshotStore

Interface for snapshot persistence.

```java
public interface SnapshotStore {
    Mono<Void> saveSnapshot(Snapshot snapshot);
    
    Mono<Snapshot> loadSnapshot(UUID aggregateId, String aggregateType);
    
    Mono<Snapshot> loadSnapshotAtVersion(UUID aggregateId, String aggregateType, 
                                        long version);
    
    Mono<Void> deleteSnapshots(UUID aggregateId, String aggregateType);
    
    Flux<Snapshot> listSnapshots(UUID aggregateId, String aggregateType);
}
```

## Usage Examples

### Basic Event Store Usage

```java
@Service
public class AccountService {
    private final EventStore eventStore;
    
    public Mono<Account> createAccount(String accountNumber, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        Account account = new Account(id, accountNumber, balance);
        
        return eventStore.appendEvents(
                id, "Account", 
                account.getUncommittedEvents(), 
                0L
            )
            .doOnSuccess(stream -> account.markEventsAsCommitted())
            .thenReturn(account);
    }
    
    public Mono<Account> loadAccount(UUID id) {
        return eventStore.loadEventStream(id, "Account")
                .map(stream -> {
                    Account account = new Account(id);
                    account.loadFromHistory(stream.getEvents());
                    return account;
                });
    }
}
```

### Event Streaming

```java
@Component
public class EventProjector {
    private final EventStore eventStore;
    
    public Flux<ProjectionUpdate> projectEvents(long fromSequence) {
        return eventStore.streamAllEvents(fromSequence)
                .filter(envelope -> isRelevantEvent(envelope))
                .map(this::createProjectionUpdate)
                .onErrorContinue(this::handleProjectionError);
    }
}
```

### Custom Event Implementation

```java
@JsonTypeName("money.transferred")
public record MoneyTransferredEvent(
    UUID aggregateId,
    UUID toAccountId,
    BigDecimal amount,
    String reference,
    Instant timestamp
) implements Event {
    
    @Override
    public String getEventType() {
        return "money.transferred";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return timestamp;
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "targetAccount", toAccountId.toString(),
            "source", "transfer-service"
        );
    }
}
```

This API reference covers all public interfaces and classes in the Firefly Event Sourcing Library. For implementation examples, see the [Examples](./examples/) directory.