# Improved Developer Experience Guide

This guide demonstrates the enhanced developer experience when using the Firefly Event Sourcing Library with Spring-aligned annotations: `@DomainEvent` and `@EventSourcingTransactional`.

## Table of Contents
- [Creating Events with @DomainEvent](#creating-events-with-domainevent)
- [Transactional Operations with @EventSourcingTransactional](#transactional-operations-with-eventsourcingtransactional)
- [Building Aggregates](#building-aggregates)
- [Complete Working Example](#complete-working-example)
- [Best Practices](#best-practices)

## Creating Events with @DomainEvent

### Before: Manual Implementation

```java
@JsonTypeName("account.created")
public record AccountCreatedEvent(
    UUID aggregateId,
    String accountNumber,
    BigDecimal initialBalance,
    Instant eventTimestamp,
    Map<String, Object> metadata
) implements Event {

    @Override
    public String getEventType() {
        return "account.created";  // ❌ Repetitive - already in @JsonTypeName
    }

    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }

    @Override
    public Instant getEventTimestamp() {
        return eventTimestamp != null ? eventTimestamp : Instant.now();
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata != null ? metadata : Map.of();
    }
}

// Creating the event - verbose and error-prone
Map<String, Object> metadata = new HashMap<>();
metadata.put("userId", "user-123");
metadata.put("source", "mobile-app");
metadata.put("correlationId", "corr-456");

AccountCreatedEvent event = new AccountCreatedEvent(
    accountId,
    "12345",
    BigDecimal.valueOf(1000),
    Instant.now(),
    metadata
);
```

### After: Using @DomainEvent + AbstractDomainEvent

```java
@DomainEvent("account.created")  // ✅ Spring-aligned annotation
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent extends AbstractDomainEvent {
    private String accountNumber;
    private BigDecimal initialBalance;
    // No need to override getEventType() - it's read from @DomainEvent!
}

// Creating the event - clean and fluent
AccountCreatedEvent event = AccountCreatedEvent.builder()
    .aggregateId(accountId)
    .accountNumber("12345")
    .initialBalance(BigDecimal.valueOf(1000))
    .userId("user-123")
    .source("mobile-app")
    .correlationId("corr-456")
    .build();
```

### Benefits

✅ **Spring-Aligned** - Declarative annotation-based approach like `@Transactional`
✅ **No Method Override** - Event type automatically read from `@DomainEvent`
✅ **Less Boilerplate** - No need to implement `getAggregateId()`, `getEventTimestamp()`, etc.
✅ **Builder Pattern** - Clean, readable event construction
✅ **Fluent Metadata API** - Built-in methods for common metadata fields
✅ **Type Safety** - Compile-time checking for required fields
✅ **Automatic Defaults** - Timestamp and metadata initialized automatically

## Transactional Operations with @EventSourcingTransactional

### Before: Manual Transaction Management

```java
@Service
public class BankAccountService {

    private final EventStore eventStore;
    private final TransactionalOperator transactionalOperator;
    private final EventPublisher eventPublisher;

    public Mono<BankAccount> withdraw(UUID accountId, BigDecimal amount) {
        return transactionalOperator.transactional(
            loadAccount(accountId)
                .flatMap(account -> {
                    account.withdraw(amount, "ATM Withdrawal");
                    return saveAccount(account);
                })
                .flatMap(account ->
                    eventPublisher.publish(account.getUncommittedEvents())
                        .thenReturn(account)
                )
        )
        .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
            .filter(e -> e instanceof ConcurrencyException)
        );
        // ❌ Lots of boilerplate for transaction + retry + publishing
    }
}
```

### After: Using @EventSourcingTransactional

```java
@Service
public class BankAccountService {

    private final EventStore eventStore;

    @EventSourcingTransactional(
        retryOnConcurrencyConflict = true,
        maxRetries = 3
    )
    public Mono<BankAccount> withdraw(UUID accountId, BigDecimal amount) {
        return loadAccount(accountId)
            .flatMap(account -> {
                account.withdraw(amount, "ATM Withdrawal");
                return saveAccount(account);
            });
        // ✅ Transaction, retry, and event publishing handled automatically!
    }
}
```

### Benefits

✅ **Spring-Aligned** - Similar to `@Transactional` but event-sourcing aware
✅ **ACID Guarantees** - Automatic transaction management with rollback on failure
✅ **Optimistic Locking** - Built-in concurrency conflict detection
✅ **Automatic Retry** - Configurable retry on version conflicts with exponential backoff
✅ **Transactional Outbox** - Events published only after successful commit
✅ **Less Code** - No manual transaction or retry logic needed

### Advanced Usage

```java
@Service
public class BankAccountService {

    // Simple transaction
    @EventSourcingTransactional
    public Mono<BankAccount> createAccount(String accountNumber, BigDecimal initialBalance) {
        // Default: REQUIRED propagation, publish events, no retry
    }

    // With retry on conflicts
    @EventSourcingTransactional(
        retryOnConcurrencyConflict = true,
        maxRetries = 5,
        retryDelay = 200
    )
    public Mono<BankAccount> withdraw(UUID accountId, BigDecimal amount) {
        // Retries up to 5 times with exponential backoff starting at 200ms
    }

    // New transaction (suspend existing)
    @EventSourcingTransactional(propagation = Propagation.REQUIRES_NEW)
    public Mono<Void> auditLog(String action) {
        // Always creates new transaction, even if called within another transaction
    }

    // Read-only (no events saved)
    @EventSourcingTransactional(
        readOnly = true,
        publishEvents = false
    )
    public Mono<BankAccount> getAccountBalance(UUID accountId) {
        // Optimized for read operations
    }

    // With timeout
    @EventSourcingTransactional(timeout = 5)
    public Mono<Void> complexOperation() {
        // Transaction will rollback if it takes more than 5 seconds
    }

    // With custom isolation level for high-concurrency scenarios
    @EventSourcingTransactional(
        isolation = Isolation.SERIALIZABLE,
        timeout = 10
    )
    public Mono<BankAccount> criticalFinancialOperation(UUID accountId) {
        // Highest isolation level prevents phantom reads and ensures consistency
        // Use for critical operations like end-of-day reconciliation
    }

    // With custom rollback rules
    @EventSourcingTransactional(
        rollbackFor = {BusinessException.class, ValidationException.class},
        noRollbackFor = {LoggingException.class}
    )
    public Mono<Void> transfer(UUID fromId, UUID toId, BigDecimal amount) {
        // Rolls back on business/validation errors
        // Commits even if logging fails
    }

    // Combining multiple features
    @EventSourcingTransactional(
        isolation = Isolation.REPEATABLE_READ,
        retryOnConcurrencyConflict = true,
        maxRetries = 3,
        timeout = 5,
        rollbackFor = {InsufficientFundsException.class}
    )
    public Mono<BankAccount> complexWithdrawal(UUID accountId, BigDecimal amount) {
        // Production-ready configuration:
        // - REPEATABLE_READ isolation for consistency
        // - Automatic retry on version conflicts
        // - 5 second timeout
        // - Explicit rollback on business rule violations
    }
}
```

**Available Isolation Levels:**
- `DEFAULT` - Use database default (usually READ_COMMITTED)
- `READ_UNCOMMITTED` - Lowest isolation, allows dirty reads
- `READ_COMMITTED` - Prevents dirty reads
- `REPEATABLE_READ` - Prevents dirty and non-repeatable reads
- `SERIALIZABLE` - Highest isolation, prevents phantom reads

**Rollback Configuration:**
- `rollbackFor` - Exception classes that trigger rollback
- `rollbackForClassName` - Exception class names that trigger rollback
- `noRollbackFor` - Exception classes that don't trigger rollback
- `noRollbackForClassName` - Exception class names that don't trigger rollback
- Default: Rollback on `RuntimeException` and `Error`

## Building Aggregates

### Complete Bank Account Example

```java
package com.example.banking.domain;

import com.firefly.common.eventsourcing.aggregate.AggregateRoot;
import com.firefly.common.eventsourcing.domain.AbstractDomainEvent;
import com.firefly.common.eventsourcing.annotation.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bank Account Aggregate - manages account lifecycle and transactions.
 */
public class BankAccount extends AggregateRoot {
    
    // State (derived from events)
    private String accountNumber;
    private String accountHolder;
    private BigDecimal balance;
    private AccountStatus status;
    
    // Constructor for creating new accounts
    public BankAccount(UUID id, String accountNumber, String accountHolder, BigDecimal initialBalance) {
        super(id, "BankAccount");
        
        // Validate business rules
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number is required");
        }
        if (accountHolder == null || accountHolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Account holder is required");
        }
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        
        // Generate creation event
        applyChange(AccountCreatedEvent.builder()
            .aggregateId(id)
            .accountNumber(accountNumber)
            .accountHolder(accountHolder)
            .initialBalance(initialBalance)
            .build());
    }
    
    // Constructor for loading from event store
    public BankAccount(UUID id) {
        super(id, "BankAccount");
    }
    
    // Command: Deposit money
    public void deposit(BigDecimal amount, String description) {
        validateAccountActive();
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        
        applyChange(MoneyDepositedEvent.builder()
            .aggregateId(getId())
            .amount(amount)
            .description(description)
            .build());
    }
    
    // Command: Withdraw money
    public void withdraw(BigDecimal amount, String description) {
        validateAccountActive();
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                String.format("Insufficient funds. Balance: %s, Requested: %s", balance, amount)
            );
        }
        
        applyChange(MoneyWithdrawnEvent.builder()
            .aggregateId(getId())
            .amount(amount)
            .description(description)
            .build());
    }
    
    // Command: Close account
    public void close(String reason) {
        validateAccountActive();
        
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot close account with non-zero balance");
        }
        
        applyChange(AccountClosedEvent.builder()
            .aggregateId(getId())
            .reason(reason)
            .build());
    }
    
    // Event Handlers (private - only events can change state)
    
    private void on(AccountCreatedEvent event) {
        this.accountNumber = event.getAccountNumber();
        this.accountHolder = event.getAccountHolder();
        this.balance = event.getInitialBalance();
        this.status = AccountStatus.ACTIVE;
    }
    
    private void on(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.getAmount());
    }
    
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
    }
    
    private void on(AccountClosedEvent event) {
        this.status = AccountStatus.CLOSED;
    }
    
    // Helper methods
    
    private void validateAccountActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }
    
    // Getters (read-only access to state)
    
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public String getAccountHolder() {
        return accountHolder;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public AccountStatus getStatus() {
        return status;
    }
    
    // Domain Events

    @DomainEvent("account.created")
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountCreatedEvent extends AbstractDomainEvent {
        private String accountNumber;
        private String accountHolder;
        private BigDecimal initialBalance;
    }

    @DomainEvent("money.deposited")
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoneyDepositedEvent extends AbstractDomainEvent {
        private BigDecimal amount;
        private String description;
    }

    @DomainEvent("money.withdrawn")
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoneyWithdrawnEvent extends AbstractDomainEvent {
        private BigDecimal amount;
        private String description;
    }

    @DomainEvent("account.closed")
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountClosedEvent extends AbstractDomainEvent {
        private String reason;
    }
    
    // Domain Enums
    
    public enum AccountStatus {
        ACTIVE, CLOSED, SUSPENDED
    }
    
    // Domain Exceptions
    
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}
```

## Complete Working Example

### Service Layer

```java
package com.example.banking.service;

import com.example.banking.domain.BankAccount;
import com.firefly.common.eventsourcing.store.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankAccountService {
    
    private final EventStore eventStore;
    
    /**
     * Creates a new bank account.
     */
    public Mono<BankAccount> createAccount(String accountNumber, String accountHolder, BigDecimal initialBalance) {
        UUID accountId = UUID.randomUUID();
        BankAccount account = new BankAccount(accountId, accountNumber, accountHolder, initialBalance);
        
        return eventStore.appendEvents(
                accountId,
                "BankAccount",
                account.getUncommittedEvents(),
                0L
            )
            .doOnSuccess(stream -> account.markEventsAsCommitted())
            .thenReturn(account);
    }
    
    /**
     * Deposits money into an account.
     */
    public Mono<BankAccount> deposit(UUID accountId, BigDecimal amount, String description) {
        return loadAccount(accountId)
            .doOnNext(account -> account.deposit(amount, description))
            .flatMap(this::saveAccount);
    }
    
    /**
     * Withdraws money from an account.
     */
    public Mono<BankAccount> withdraw(UUID accountId, BigDecimal amount, String description) {
        return loadAccount(accountId)
            .doOnNext(account -> account.withdraw(amount, description))
            .flatMap(this::saveAccount);
    }
    
    /**
     * Closes an account.
     */
    public Mono<BankAccount> closeAccount(UUID accountId, String reason) {
        return loadAccount(accountId)
            .doOnNext(account -> account.close(reason))
            .flatMap(this::saveAccount);
    }
    
    /**
     * Gets the current account balance.
     */
    public Mono<BigDecimal> getBalance(UUID accountId) {
        return loadAccount(accountId)
            .map(BankAccount::getBalance);
    }
    
    // Helper methods
    
    private Mono<BankAccount> loadAccount(UUID accountId) {
        return eventStore.loadEventStream(accountId, "BankAccount")
            .map(stream -> {
                BankAccount account = new BankAccount(accountId);
                account.loadFromHistory(stream.getEvents());
                return account;
            });
    }
    
    private Mono<BankAccount> saveAccount(BankAccount account) {
        return eventStore.appendEvents(
                account.getId(),
                "BankAccount",
                account.getUncommittedEvents(),
                account.getVersion()
            )
            .doOnSuccess(stream -> account.markEventsAsCommitted())
            .thenReturn(account);
    }
}
```

### Controller Layer

```java
package com.example.banking.controller;

import com.example.banking.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class BankAccountController {
    
    private final BankAccountService accountService;
    
    @PostMapping
    public Mono<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        return accountService.createAccount(
                request.accountNumber(),
                request.accountHolder(),
                request.initialBalance()
            )
            .map(account -> new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountHolder(),
                account.getBalance(),
                account.getStatus().name()
            ));
    }
    
    @PostMapping("/{accountId}/deposit")
    public Mono<AccountResponse> deposit(
            @PathVariable UUID accountId,
            @RequestBody TransactionRequest request) {
        return accountService.deposit(accountId, request.amount(), request.description())
            .map(this::toResponse);
    }
    
    @PostMapping("/{accountId}/withdraw")
    public Mono<AccountResponse> withdraw(
            @PathVariable UUID accountId,
            @RequestBody TransactionRequest request) {
        return accountService.withdraw(accountId, request.amount(), request.description())
            .map(this::toResponse);
    }
    
    @GetMapping("/{accountId}/balance")
    public Mono<BalanceResponse> getBalance(@PathVariable UUID accountId) {
        return accountService.getBalance(accountId)
            .map(balance -> new BalanceResponse(accountId, balance));
    }
    
    private AccountResponse toResponse(BankAccount account) {
        return new AccountResponse(
            account.getId(),
            account.getAccountNumber(),
            account.getAccountHolder(),
            account.getBalance(),
            account.getStatus().name()
        );
    }
    
    // DTOs
    record CreateAccountRequest(String accountNumber, String accountHolder, BigDecimal initialBalance) {}
    record TransactionRequest(BigDecimal amount, String description) {}
    record AccountResponse(UUID id, String accountNumber, String accountHolder, BigDecimal balance, String status) {}
    record BalanceResponse(UUID accountId, BigDecimal balance) {}
}
```

## Best Practices

### 1. Event Design

✅ **DO:**
```java
@JsonTypeName("account.created")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent extends AbstractDomainEvent {
    private String accountNumber;
    private BigDecimal initialBalance;
    
    @Override
    public String getEventType() {
        return "account.created";
    }
}
```

❌ **DON'T:**
```java
// Don't use mutable events
public class AccountCreatedEvent extends AbstractDomainEvent {
    private String accountNumber;
    
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber; // Events should be immutable!
    }
}
```

### 2. Aggregate Design

✅ **DO:**
```java
public class BankAccount extends AggregateRoot {
    // Command validates, then generates event
    public void withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        applyChange(new MoneyWithdrawnEvent(getId(), amount));
    }
    
    // Event handler just updates state
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
    }
}
```

❌ **DON'T:**
```java
public class BankAccount extends AggregateRoot {
    // Don't modify state directly!
    public void withdraw(BigDecimal amount) {
        this.balance = this.balance.subtract(amount); // Wrong!
    }
}
```

### 3. Metadata Usage

✅ **DO:**
```java
MoneyWithdrawnEvent event = MoneyWithdrawnEvent.builder()
    .aggregateId(accountId)
    .amount(amount)
    .userId(currentUser.getId())
    .correlationId(requestContext.getCorrelationId())
    .source("mobile-app")
    .build();
```

This enables:
- Distributed tracing
- Audit trails
- Debugging
- Analytics

## Summary

The improved developer experience provides:

1. **Less Boilerplate** - AbstractDomainEvent handles common event functionality
2. **Better Documentation** - Enhanced AggregateRoot with comprehensive examples
3. **Type Safety** - Builder pattern with compile-time checking
4. **Fluent APIs** - Clean, readable code
5. **Best Practices** - Built-in patterns for metadata, versioning, and timestamps

Start building event-sourced systems faster and with fewer errors! 🚀

