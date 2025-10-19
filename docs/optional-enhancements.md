# Optional Enhancements

This document describes the optional enhancements that have been added to the Firefly Event Sourcing Library to improve resilience, observability, and multi-tenancy support.

---

## 1. Circuit Breaker Pattern (Resilience4j)

### Overview
Circuit breaker pattern implementation using Resilience4j to prevent cascading failures and improve system resilience.

### Components

#### CircuitBreakerConfiguration
- **Location**: `com.firefly.common.eventsourcing.resilience.CircuitBreakerConfiguration`
- **Purpose**: Auto-configuration for circuit breakers
- **Activation**: Set `firefly.eventsourcing.resilience.circuit-breaker.enabled=true`

#### Three Circuit Breakers

1. **Event Store Circuit Breaker**
   - Failure rate threshold: 50%
   - Wait duration: 60 seconds
   - Sliding window: COUNT_BASED (100 calls)
   - Minimum calls: 10

2. **Outbox Circuit Breaker**
   - Failure rate threshold: 60%
   - Wait duration: 30 seconds
   - Sliding window: TIME_BASED (2 minutes)
   - Minimum calls: 5

3. **Projection Circuit Breaker**
   - Failure rate threshold: 70%
   - Wait duration: 45 seconds
   - Sliding window: TIME_BASED (5 minutes)
   - Minimum calls: 5

### Configuration Example

```yaml
firefly:
  eventsourcing:
    resilience:
      circuit-breaker:
        enabled: true
        event-store:
          failure-rate-threshold: 50
          wait-duration-in-open-state: PT60S
          sliding-window-type: COUNT_BASED
          sliding-window-size: 100
```

### Usage

Circuit breakers are automatically applied when enabled. They monitor operations and open the circuit when failure thresholds are exceeded, preventing further calls until the wait duration expires.

---

## 2. Distributed Tracing (OpenTelemetry)

### Overview
OpenTelemetry instrumentation for distributed tracing of event sourcing operations.

### Components

#### OpenTelemetryConfiguration
- **Location**: `com.firefly.common.eventsourcing.tracing.OpenTelemetryConfiguration`
- **Purpose**: Configures OpenTelemetry tracer for event sourcing
- **Activation**: Set `firefly.eventsourcing.tracing.enabled=true`

### Features

- Automatic span creation for all event store operations
- Context propagation across reactive streams
- Span attributes include:
  - Aggregate ID
  - Aggregate type
  - Event count
  - Event version
  - Global sequence numbers

### Configuration Example

```yaml
firefly:
  eventsourcing:
    tracing:
      enabled: true
      service-name: firefly-eventsourcing
      sampling-rate: 1.0  # 100% sampling
```

### Integration

Requires OpenTelemetry SDK and exporter configuration in your application:

```yaml
otel:
  exporter:
    otlp:
      endpoint: http://localhost:4317
  traces:
    sampler: always_on
```

---

## 3. Event Upcasting

### Overview
Automatic event schema migration framework for handling event versioning and schema evolution.

### Components

#### EventUpcaster Interface
- **Location**: `com.firefly.common.eventsourcing.upcasting.EventUpcaster`
- **Purpose**: Define transformations from old event versions to new versions

```java
public interface EventUpcaster {
    boolean canUpcast(String eventType, int eventVersion);
    Event upcast(Event event);
    int getTargetVersion();
    int getPriority();  // Higher priority runs first
}
```

#### EventUpcastingService
- **Location**: `com.firefly.common.eventsourcing.upcasting.EventUpcastingService`
- **Purpose**: Manages and applies upcasters in sequence
- **Features**:
  - Priority-based ordering
  - Chain execution (V1 → V2 → V3)
  - Automatic detection of applicable upcasters

### Example Implementation

```java
@Component
public class AccountCreatedV1ToV2Upcaster implements EventUpcaster {
    
    @Override
    public boolean canUpcast(String eventType, int eventVersion) {
        return "account.created".equals(eventType) && eventVersion == 1;
    }
    
    @Override
    public Event upcast(Event event) {
        // Transform V1 event to V2
        AccountCreatedEventV2 v2 = new AccountCreatedEventV2();
        // ... map fields ...
        return v2;
    }
    
    @Override
    public int getTargetVersion() {
        return 2;
    }
}
```

### Configuration

```yaml
firefly:
  eventsourcing:
    upcasting:
      enabled: true
      strict-mode: false  # If true, fail on missing upcasters
```

---

## 4. Multi-tenancy Support

### Overview
Tenant isolation framework for multi-tenant event sourcing applications.

### Components

#### TenantContext
- **Location**: `com.firefly.common.eventsourcing.multitenancy.TenantContext`
- **Purpose**: Reactive context holder for tenant information

```java
// Set tenant context
return eventStore.appendEvents(...)
    .contextWrite(TenantContext.withTenantId("tenant-123"));

// Get current tenant
String tenantId = TenantContext.getCurrentTenantId().block();
```

#### MultiTenancyConfiguration
- **Location**: `com.firefly.common.eventsourcing.multitenancy.MultiTenancyConfiguration`
- **Purpose**: Auto-configuration for multi-tenancy
- **Activation**: Set `firefly.eventsourcing.multitenancy.enabled=true`

### Features

- **Tenant Context Propagation**: Automatic propagation through reactive streams
- **Strict Mode**: Enforce tenant filtering (reject events without tenant ID)
- **Non-Strict Mode**: Allow events without tenant ID
- **Metadata-based**: Stores tenant ID in event metadata

### Configuration

```yaml
firefly:
  eventsourcing:
    multitenancy:
      enabled: true
      strict-mode: true  # Enforce strict tenant filtering
      default-tenant: default
```

### Usage Example

```java
@Service
public class TenantAwareService {
    
    private final EventStore eventStore;
    
    public Mono<Void> createAccount(String tenantId, AccountCreatedEvent event) {
        return eventStore.appendEvents(...)
                .contextWrite(TenantContext.withTenantId(tenantId));
    }
    
    public Mono<EventStream> loadAccount(String tenantId, UUID accountId) {
        return eventStore.loadEventStream(accountId, "Account")
                .contextWrite(TenantContext.withTenantId(tenantId));
    }
}
```

---

## Dependencies

### Required Dependencies

All optional enhancements use optional dependencies that are only loaded when enabled:

```xml
<!-- Circuit Breaker -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <optional>true</optional>
</dependency>

<!-- Distributed Tracing -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.32.0</version>
    <optional>true</optional>
</dependency>
```

---

## Testing

All enhancements include comprehensive tests:

- Circuit breaker state transitions
- Event upcasting chains
- Multi-tenancy isolation

Run tests with:
```bash
mvn clean test
```

---

## Performance Impact

### Circuit Breaker
- **Overhead**: Minimal (~1-2% latency increase)
- **Benefit**: Prevents cascading failures, improves overall system stability

### Distributed Tracing
- **Overhead**: Low (~2-5% latency increase with 100% sampling)
- **Recommendation**: Use sampling in production (e.g., 10% sampling rate)

### Event Upcasting
- **Overhead**: Only applies when loading old events
- **Impact**: Proportional to number of upcasters in chain

### Multi-tenancy
- **Overhead**: Minimal (context lookup and metadata filtering)
- **Benefit**: Strong tenant isolation without database-level separation

---

## Best Practices

1. **Circuit Breaker**:
   - Monitor circuit breaker metrics
   - Adjust thresholds based on your SLAs
   - Set up alerts for circuit open events

2. **Distributed Tracing**:
   - Use sampling in production (10-20%)
   - Include business context in span attributes
   - Integrate with APM tools (Jaeger, Zipkin, etc.)

3. **Event Upcasting**:
   - Keep upcaster chains short (max 2-3 versions)
   - Test upcasters thoroughly
   - Consider snapshot-based migration for large version jumps

4. **Multi-tenancy**:
   - Always use strict mode in production
   - Set tenant context at API gateway/controller level
   - Include tenant ID in all logs and metrics

---

## Migration Guide

### Enabling Circuit Breaker

1. Add configuration:
```yaml
firefly.eventsourcing.resilience.circuit-breaker.enabled=true
```

2. Monitor metrics:
```java
@Autowired
private CircuitBreakerRegistry circuitBreakerRegistry;

public void checkCircuitBreakerState() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("eventStore");
    log.info("Circuit breaker state: {}", cb.getState());
}
```

### Enabling Distributed Tracing

1. Add OpenTelemetry dependencies
2. Configure exporter
3. Enable tracing:
```yaml
firefly.eventsourcing.tracing.enabled=true
```

### Implementing Event Upcasting

1. Create upcaster implementations
2. Register as Spring beans
3. Enable upcasting:
```yaml
firefly.eventsourcing.upcasting.enabled=true
```

### Enabling Multi-tenancy

1. Enable multi-tenancy:
```yaml
firefly.eventsourcing.multitenancy.enabled=true
```

2. Set tenant context in controllers:
```java
@PostMapping("/accounts")
public Mono<Account> createAccount(@RequestHeader("X-Tenant-ID") String tenantId, ...) {
    return accountService.createAccount(...)
            .contextWrite(TenantContext.withTenantId(tenantId));
}
```

---

## Troubleshooting

### Circuit Breaker Not Working
- Check if `resilience4j-spring-boot3` is on classpath
- Verify configuration is enabled
- Check logs for circuit breaker initialization

### Tracing Not Appearing
- Verify OpenTelemetry exporter is configured
- Check sampling rate (set to 1.0 for testing)
- Ensure OTLP endpoint is reachable

### Upcasting Not Applied
- Verify upcasters are registered as Spring beans
- Check `canUpcast()` method logic
- Enable debug logging for `EventUpcastingService`

### Tenant Isolation Issues
- Verify tenant context is set before event store operations
- Check strict mode configuration
- Review event metadata for tenant ID presence

