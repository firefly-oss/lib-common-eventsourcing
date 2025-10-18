# Testing Guide

Comprehensive testing strategies for applications using the Firefly Event Sourcing Library.

## Overview

The library supports multiple testing approaches:

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test with real databases using Testcontainers
- **Contract Tests**: Verify event schemas and backward compatibility
- **Performance Tests**: Load testing and benchmarking

## Unit Testing

### Testing Aggregates

```java
@Test
void shouldCreateAccount() {
    UUID accountId = UUID.randomUUID();
    Account account = new Account(accountId, "ACC-001", BigDecimal.valueOf(1000));
    
    // Verify initial state
    assertEquals("ACC-001", account.getAccountNumber());
    assertEquals(BigDecimal.valueOf(1000), account.getBalance());
    
    // Verify events were generated
    assertEquals(1, account.getUncommittedEventCount());
    assertTrue(account.getUncommittedEvents().get(0) instanceof AccountCreatedEvent);
}

@Test
void shouldHandleBusinessRules() {
    UUID accountId = UUID.randomUUID();
    Account account = new Account(accountId, "ACC-001", BigDecimal.ZERO);
    
    // Test business rule violation
    assertThrows(IllegalArgumentException.class, () -> {
        account.withdraw(BigDecimal.valueOf(100));
    });
}
```

### Testing Event Stores

```java
@ExtendWith(MockitoExtension.class)
class EventStoreTest {
    
    @Mock
    private DatabaseClient databaseClient;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Test
    void shouldAppendEvents() {
        // Setup mocks
        when(databaseClient.sql(any())).thenReturn(mockSpec);
        when(mockSpec.bind(any(), any())).thenReturn(mockSpec);
        
        // Test implementation
        R2dbcEventStore eventStore = new R2dbcEventStore(/* ... */);
        StepVerifier.create(
                eventStore.appendEvents(aggregateId, "Account", events, 0L)
            )
            .assertNext(stream -> {
                assertEquals(3, stream.size());
            })
            .verifyComplete();
    }
}
```

## Integration Testing with Testcontainers

### PostgreSQL Integration Tests

The library includes comprehensive PostgreSQL integration tests using Testcontainers:

```java
@Testcontainers
class EventStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("firefly_eventsourcing_test")
            .withUsername("firefly_test")
            .withPassword("test_password")
            .withReuse(true);

    private R2dbcEventStore eventStore;
    private ConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        connectionFactory = new PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.getHost())
                .port(postgres.getFirstMappedPort())
                .database(postgres.getDatabaseName())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build()
        );

        // Setup event store with real PostgreSQL connection
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory);
        R2dbcEntityTemplate entityTemplate = new R2dbcEntityTemplate(connectionFactory);
        ReactiveTransactionManager transactionManager = new R2dbcTransactionManager(connectionFactory);
        TransactionalOperator transactionalOperator = TransactionalOperator.create(transactionManager);
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        EventSourcingProperties properties = new EventSourcingProperties();
        eventStore = new R2dbcEventStore(
            databaseClient, entityTemplate, objectMapper, properties, 
            transactionManager, transactionalOperator
        );

        createSchema().block();
    }

    @Test
    void shouldPersistAndLoadEvents() {
        UUID aggregateId = UUID.randomUUID();
        List<Event> events = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000))
        );

        // Persist events
        StepVerifier.create(
                eventStore.appendEvents(aggregateId, "Account", events, 0L)
            )
            .assertNext(stream -> {
                assertEquals(1, stream.size());
                assertEquals(aggregateId, stream.getAggregateId());
            })
            .verifyComplete();

        // Load events
        StepVerifier.create(
                eventStore.loadEventStream(aggregateId, "Account")
            )
            .assertNext(stream -> {
                assertEquals(1, stream.size());
                assertEquals("test.account.created", stream.getFirstEvent().getEventType());
            })
            .verifyComplete();
    }
}
```

### Test Configuration

#### application-test.yml

```yaml
spring:
  r2dbc:
    # Will be overridden by Testcontainers
    url: r2dbc:postgresql://localhost:5432/test
    username: test
    password: test

  flyway:
    enabled: false  # Managed by test setup

firefly:
  eventsourcing:
    store:
      batch-size: 10
    snapshot:
      threshold: 5
    publisher:
      enabled: false  # Disable for tests

logging:
  level:
    com.firefly: DEBUG
    org.springframework.r2dbc: INFO
```

#### Test Dependencies

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- Testcontainers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>r2dbc</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Reactor Test -->
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Awaitility for async testing -->
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Database Schema Testing

### Schema Migration Tests

```java
@Test
void shouldRunMigrations() {
    Flyway flyway = Flyway.configure()
        .dataSource(/* JDBC URL */)
        .locations("classpath:db/migration")
        .load();
        
    flyway.migrate();
    
    // Verify schema is correctly created
    // Check tables, indexes, constraints
}
```

### Schema Validation

```java
@Test
void shouldValidateEventSchema() {
    // Test that event table supports all required operations
    String createEventSql = """
        INSERT INTO events (event_id, aggregate_id, aggregate_type, 
                          aggregate_version, event_type, event_data, created_at)
        VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW())
        """;
    
    // Execute and verify no constraints are violated
}
```

## Performance Testing

### Load Testing

```java
@Test
void shouldHandleHighThroughput() {
    int numberOfAggregates = 1000;
    int eventsPerAggregate = 100;
    
    StepVerifier.create(
            Flux.range(0, numberOfAggregates)
                .flatMap(i -> {
                    UUID aggregateId = UUID.randomUUID();
                    List<Event> events = generateEvents(aggregateId, eventsPerAggregate);
                    return eventStore.appendEvents(aggregateId, "Account", events, 0L);
                })
                .collectList()
        )
        .assertNext(results -> {
            assertEquals(numberOfAggregates, results.size());
        })
        .verifyTimeout(Duration.ofMinutes(5));
}
```

### Memory Usage Testing

```java
@Test
void shouldNotLeakMemory() {
    Runtime runtime = Runtime.getRuntime();
    long initialMemory = runtime.totalMemory() - runtime.freeMemory();
    
    // Perform operations
    for (int i = 0; i < 10000; i++) {
        // Create and process events
    }
    
    System.gc();
    long finalMemory = runtime.totalMemory() - runtime.freeMemory();
    
    // Assert memory usage is reasonable
    assertTrue(finalMemory - initialMemory < 50 * 1024 * 1024); // 50MB threshold
}
```

## Event Contract Testing

### Schema Validation

```java
@Test
void shouldMaintainEventSchema() {
    AccountCreatedEvent event = new AccountCreatedEvent(
        UUID.randomUUID(), "ACC-001", BigDecimal.valueOf(1000)
    );
    
    // Serialize and deserialize
    String json = objectMapper.writeValueAsString(event);
    Event deserialized = objectMapper.readValue(json, Event.class);
    
    // Verify no data loss
    assertTrue(deserialized instanceof AccountCreatedEvent);
    AccountCreatedEvent deserializedEvent = (AccountCreatedEvent) deserialized;
    assertEquals(event.getAccountNumber(), deserializedEvent.getAccountNumber());
}
```

### Backward Compatibility

```java
@Test
void shouldSupportLegacyEventFormats() {
    // Test with old event format JSON
    String legacyEventJson = """
        {
            "eventType": "account.created",
            "aggregateId": "12345678-1234-1234-1234-123456789012",
            "accountNumber": "ACC-001",
            "balance": 1000.00
        }
        """;
    
    // Should still deserialize correctly
    Event event = objectMapper.readValue(legacyEventJson, Event.class);
    assertNotNull(event);
}
```

## Test Utilities

### Event Builder

```java
public class EventTestBuilder {
    public static AccountCreatedEvent accountCreated(UUID aggregateId) {
        return new AccountCreatedEvent(
            aggregateId,
            "ACC-" + System.currentTimeMillis(),
            BigDecimal.valueOf(1000)
        );
    }
    
    public static List<Event> eventSequence(UUID aggregateId, int count) {
        List<Event> events = new ArrayList<>();
        events.add(accountCreated(aggregateId));
        
        for (int i = 1; i < count; i++) {
            events.add(new MoneyDepositedEvent(
                aggregateId, BigDecimal.valueOf(100), "REF-" + i
            ));
        }
        
        return events;
    }
}
```

### Aggregate Test Base

```java
public abstract class AggregateTestBase<T extends AggregateRoot> {
    
    protected abstract T createAggregate(UUID id);
    
    @Test
    void shouldHaveCorrectInitialState() {
        UUID id = UUID.randomUUID();
        T aggregate = createAggregate(id);
        
        assertEquals(id, aggregate.getId());
        assertTrue(aggregate.hasUncommittedEvents());
    }
    
    protected void given(T aggregate, Event... events) {
        aggregate.loadFromHistory(Arrays.asList(events));
    }
    
    protected void when(T aggregate, Runnable action) {
        action.run();
    }
    
    protected void then(T aggregate, Class<? extends Event> expectedEventType) {
        assertTrue(aggregate.hasUncommittedEvents());
        assertTrue(expectedEventType.isInstance(aggregate.getLastUncommittedEvent()));
    }
}
```

## Continuous Integration

### GitHub Actions Example

```yaml
name: Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: firefly_eventsourcing_test
          POSTGRES_USER: firefly_test
          POSTGRES_PASSWORD: test_password
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Run tests
      run: mvn clean test
      env:
        DB_HOST: localhost
        DB_PORT: 5432
        DB_NAME: firefly_eventsourcing_test
        DB_USERNAME: firefly_test
        DB_PASSWORD: test_password
```

## Best Practices

### Test Organization

1. **Arrange-Act-Assert**: Structure tests clearly
2. **Single Responsibility**: One concept per test
3. **Descriptive Names**: Test names should describe behavior
4. **Fast Feedback**: Keep tests fast and focused

### Data Management

1. **Clean State**: Reset database between tests
2. **Test Isolation**: Each test should be independent
3. **Realistic Data**: Use production-like test data
4. **Schema Evolution**: Test migration paths

### Assertions

1. **Specific Checks**: Assert exact expected values
2. **Error Scenarios**: Test failure paths
3. **State Verification**: Check both events and final state
4. **Performance Bounds**: Set acceptable performance limits

## Troubleshooting

### Common Issues

**Testcontainers not starting**
- Ensure Docker is running
- Check Docker permissions
- Verify image availability

**Database schema errors**
- Run migrations manually to verify
- Check SQL syntax for your database
- Verify index creation

**Serialization failures**
- Register event types with Jackson
- Check @JsonTypeName annotations
- Verify ObjectMapper configuration

**Performance issues**
- Monitor connection pool settings
- Check database query plans
- Profile memory usage
- Verify transaction boundaries