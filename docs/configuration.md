# Configuration Reference

Complete configuration guide for the Firefly Event Sourcing Library.

## Overview

The library uses Spring Boot's configuration properties with the prefix `firefly.eventsourcing`. All properties are defined in the `EventSourcingProperties` class.

## Configuration Hierarchy

```yaml
firefly:
  eventsourcing:
    enabled: true              # Enable/disable event sourcing
    store: {}                  # Event store configuration
    snapshot: {}               # Snapshot configuration
    publisher: {}              # Event publishing configuration
    performance: {}            # Performance tuning
```

## Core Configuration

### Root Properties

```yaml
firefly:
  eventsourcing:
    enabled: true              # Default: true
```

- **`enabled`**: Master switch to enable/disable the entire event sourcing system

## Event Store Configuration

```yaml
firefly:
  eventsourcing:
    store:
      type: r2dbc                       # Default: "r2dbc"
      batch-size: 100                   # Default: 100
      connection-timeout: 30s           # Default: 30s
      query-timeout: 30s                # Default: 30s
      validate-schemas: true            # Default: true
      max-events-per-load: 1000        # Default: 1000
      properties: {}                    # Default: empty map
```

### Event Store Properties Details

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | String | `"r2dbc"` | Event store implementation type |
| `batch-size` | int | `100` | Batch size for event operations |
| `connection-timeout` | Duration | `30s` | Database connection timeout |
| `query-timeout` | Duration | `30s` | Query execution timeout |
| `validate-schemas` | boolean | `true` | Enable event schema validation |
| `max-events-per-load` | int | `1000` | Maximum events to load in one operation |
| `properties` | Map<String,Object> | `{}` | Store-specific properties |

### Supported Event Store Types

- **`r2dbc`**: R2DBC-based event store (primary implementation)
- **`mongodb`**: MongoDB-based event store (future implementation)
- **`memory`**: In-memory event store (testing only)

## Snapshot Configuration

```yaml
firefly:
  eventsourcing:
    snapshot:
      enabled: true                     # Default: true
      threshold: 50                     # Default: 50
      check-interval: 5m               # Default: 5 minutes
      keep-count: 3                    # Default: 3
      max-age: 30d                     # Default: 30 days
      compression: true                # Default: true
      store-type: same                 # Default: "same"
      caching: true                    # Default: true
      cache-ttl: 1h                    # Default: 1 hour
```

### Snapshot Properties Details

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable snapshot functionality |
| `threshold` | int | `50` | Event count threshold for snapshot creation |
| `check-interval` | Duration | `5m` | Interval for snapshot opportunity checks |
| `keep-count` | int | `3` | Number of snapshots to keep per aggregate |
| `max-age` | Duration | `30d` | Maximum age before snapshot cleanup |
| `compression` | boolean | `true` | Enable snapshot compression |
| `store-type` | String | `"same"` | Snapshot storage type |
| `caching` | boolean | `true` | Enable snapshot caching |
| `cache-ttl` | Duration | `1h` | Cache time-to-live for snapshots |

### Snapshot Store Types

- **`same`**: Use the same storage as the event store
- **`cache`**: Store snapshots in cache only
- **`redis`**: Redis-based snapshot storage
- **`memory`**: In-memory snapshot storage

## Publisher Configuration

```yaml
firefly:
  eventsourcing:
    publisher:
      enabled: true                     # Default: true
      type: AUTO                        # Default: AUTO
      destination-prefix: events        # Default: "events"
      destination-mappings: {}          # Default: empty map
      async: true                       # Default: true
      batch-size: 10                    # Default: 10
      publish-timeout: 10s              # Default: 10s
      continue-on-failure: true         # Default: true
      retry:
        enabled: true                   # Default: true
        max-attempts: 3                 # Default: 3
        initial-delay: 1s               # Default: 1s
        max-delay: 10s                  # Default: 10s
        backoff-multiplier: 2.0         # Default: 2.0
```

### Publisher Properties Details

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable event publishing |
| `type` | PublisherType | `AUTO` | Publisher type selection |
| `destination-prefix` | String | `"events"` | Prefix for event destinations |
| `destination-mappings` | Map<String,String> | `{}` | Custom event type to destination mappings |
| `async` | boolean | `true` | Enable asynchronous publishing |
| `batch-size` | int | `10` | Batch size for event publishing |
| `publish-timeout` | Duration | `10s` | Timeout for publish operations |
| `continue-on-failure` | boolean | `true` | Continue processing on publish failures |

### Publisher Types

- **`AUTO`**: Auto-detect available publishers
- **`KAFKA`**: Apache Kafka publisher
- **`RABBITMQ`**: RabbitMQ publisher
- **`AZURE_SERVICE_BUS`**: Azure Service Bus publisher
- **`MEMORY`**: In-memory publisher (testing)
- **`NONE`**: Disable publishing

### Retry Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `retry.enabled` | boolean | `true` | Enable retry mechanism |
| `retry.max-attempts` | int | `3` | Maximum retry attempts |
| `retry.initial-delay` | Duration | `1s` | Initial delay between retries |
| `retry.max-delay` | Duration | `10s` | Maximum delay between retries |
| `retry.backoff-multiplier` | double | `2.0` | Exponential backoff multiplier |

## Performance Configuration

```yaml
firefly:
  eventsourcing:
    performance:
      thread-pool-size: 8               # Default: CPU cores
      buffer-size: 1000                 # Default: 1000
      metrics-enabled: true             # Default: true
      health-checks-enabled: true       # Default: true
      statistics-interval: 1m           # Default: 1m
      tracing-enabled: true             # Default: true
      circuit-breaker:
        enabled: false                  # Default: false
        failure-rate-threshold: 50.0    # Default: 50.0%
        minimum-number-of-calls: 10     # Default: 10
        sliding-window-size: 60s        # Default: 60s
        wait-duration-in-open-state: 30s # Default: 30s
```

### Performance Properties Details

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `thread-pool-size` | int | CPU cores | Thread pool size for parallel operations |
| `buffer-size` | int | `1000` | Buffer size for reactive streams |
| `metrics-enabled` | boolean | `true` | Enable metrics collection |
| `health-checks-enabled` | boolean | `true` | Enable health checks |
| `statistics-interval` | Duration | `1m` | Statistics collection interval |
| `tracing-enabled` | boolean | `true` | Enable distributed tracing |

### Circuit Breaker Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `circuit-breaker.enabled` | boolean | `false` | Enable circuit breaker |
| `circuit-breaker.failure-rate-threshold` | float | `50.0` | Failure rate threshold (%) |
| `circuit-breaker.minimum-number-of-calls` | int | `10` | Minimum calls before evaluation |
| `circuit-breaker.sliding-window-size` | Duration | `60s` | Time window for failure rate |
| `circuit-breaker.wait-duration-in-open-state` | Duration | `30s` | Wait time in open state |

## Database Configuration

The event sourcing library integrates with Spring Boot's R2DBC configuration:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/firefly
    username: firefly
    password: password
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
```

## Complete Configuration Example

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
      batch-size: 200
      connection-timeout: 60s
      query-timeout: 30s
      validate-schemas: true
      max-events-per-load: 2000
      
    snapshot:
      enabled: true
      threshold: 100
      check-interval: 10m
      keep-count: 5
      max-age: 90d
      compression: true
      store-type: same
      caching: true
      cache-ttl: 2h
      
    publisher:
      enabled: true
      type: KAFKA
      destination-prefix: banking-events
      destination-mappings:
        "account.created": "account-lifecycle"
        "money.transferred": "payment-events"
      async: true
      batch-size: 50
      publish-timeout: 30s
      continue-on-failure: true
      retry:
        enabled: true
        max-attempts: 5
        initial-delay: 2s
        max-delay: 30s
        backoff-multiplier: 1.5
        
    performance:
      thread-pool-size: 16
      buffer-size: 2000
      metrics-enabled: true
      health-checks-enabled: true
      statistics-interval: 30s
      tracing-enabled: true
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 60.0
        minimum-number-of-calls: 20
        sliding-window-size: 120s
        wait-duration-in-open-state: 60s
```

## Environment-Specific Configuration

### Development

```yaml
firefly:
  eventsourcing:
    store:
      batch-size: 10
      validate-schemas: true
    snapshot:
      threshold: 5  # More frequent snapshots for testing
    publisher:
      type: MEMORY  # Use in-memory publisher
    performance:
      metrics-enabled: true
      tracing-enabled: true
```

### Production

```yaml
firefly:
  eventsourcing:
    store:
      batch-size: 500
      connection-timeout: 90s
      query-timeout: 60s
    snapshot:
      threshold: 200
      check-interval: 5m
      compression: true
    publisher:
      type: KAFKA
      batch-size: 100
      retry:
        max-attempts: 5
    performance:
      thread-pool-size: 32
      circuit-breaker:
        enabled: true
```

## Configuration Validation

The library validates configuration at startup:

- Required properties are checked
- Value ranges are validated
- Dependencies between properties are verified
- Warning messages for deprecated configurations

## Auto-Configuration

The library provides Spring Boot auto-configuration that:

1. Loads configuration properties
2. Creates required beans based on configuration
3. Sets up conditional components
4. Validates configuration consistency

### Conditional Configuration

- **EventStore**: Created only when database connection is available
- **SnapshotStore**: Created only when snapshots are enabled
- **EventPublisher**: Created only when EDA libraries are present
- **Health Indicators**: Created only when Spring Actuator is present

## Configuration Testing

Test your configuration with Spring Boot's configuration validation:

```java
@ConfigurationPropertiesTest
class EventSourcingPropertiesTest {
    
    @Test
    void testDefaultProperties() {
        EventSourcingProperties properties = new EventSourcingProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getStore().getType()).isEqualTo("r2dbc");
        assertThat(properties.getSnapshot().getThreshold()).isEqualTo(50);
    }
}
```

## Migration Notes

When upgrading versions, check for:

- Deprecated configuration properties
- Changed default values
- New required properties
- Breaking changes in property structure

See the [Migration Guide](./migration.md) for version-specific changes.