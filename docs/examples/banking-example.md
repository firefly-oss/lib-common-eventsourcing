# Banking Example

> **📚 This example has been superseded by the comprehensive [Account Ledger Tutorial](../tutorial-account-ledger.md).**

The Account Ledger Tutorial provides a complete, production-ready banking example with:
- ✅ All 6 domain events (AccountOpened, MoneyDeposited, MoneyWithdrawn, AccountFrozen, AccountUnfrozen, AccountClosed)
- ✅ Complete aggregate implementation with business rules
- ✅ Snapshot support for performance optimization
- ✅ Service layer with @EventSourcingTransactional
- ✅ Read models and projections for fast queries
- ✅ Repository for fast queries
- ✅ Integration tests demonstrating all features
- ✅ Advanced topics (concurrency, sagas, multi-tenancy)

## Quick Links

- **[Account Ledger Tutorial](../tutorial-account-ledger.md)** - Complete guide with working code
- **[Quick Start Guide](../quick-start.md)** - Get up and running in 5 minutes
- **[Event Sourcing Explained](../event-sourcing-explained.md)** - Understanding the fundamentals

---

## Legacy Content (Deprecated)

The content below is kept for reference but is deprecated. Please use the [Account Ledger Tutorial](../tutorial-account-ledger.md) instead.

<details>
<summary>Click to expand legacy content</summary>

## Overview

This example demonstrates a realistic banking application with accounts, transactions, and event sourcing patterns. All code examples are based on the actual library APIs.

## Domain Events

### Account Events

```java
package com.firefly.banking.domain.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.firefly.common.eventsourcing.domain.Event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonTypeName("account.created")
public record AccountCreatedEvent(
    UUID aggregateId,
    String accountNumber,
    String accountType,
    UUID customerId,
    BigDecimal initialBalance,
    String currency
) implements Event {
    
    @Override
    public String getEventType() {
        return "account.created";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return Instant.now();
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "source", "banking-service",
            "customerId", customerId.toString(),
            "accountType", accountType
        );
    }
}

@JsonTypeName("money.deposited")
public record MoneyDepositedEvent(
    UUID aggregateId,
    BigDecimal amount,
    String currency,
    String reference,
    String channel,
    UUID transactionId
) implements Event {
    
    @Override
    public String getEventType() {
        return "money.deposited";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return Instant.now();
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "source", "banking-service",
            "channel", channel,
            "transactionId", transactionId.toString(),
            "reference", reference
        );
    }
}

@JsonTypeName("money.withdrawn")
public record MoneyWithdrawnEvent(
    UUID aggregateId,
    BigDecimal amount,
    String currency,
    String reference,
    String channel,
    UUID transactionId
) implements Event {
    
    @Override
    public String getEventType() {
        return "money.withdrawn";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return Instant.now();
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "source", "banking-service",
            "channel", channel,
            "transactionId", transactionId.toString(),
            "reference", reference
        );
    }
}

@JsonTypeName("account.frozen")
public record AccountFrozenEvent(
    UUID aggregateId,
    String reason,
    UUID frozenBy,
    Instant frozenAt
) implements Event {
    
    @Override
    public String getEventType() {
        return "account.frozen";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return frozenAt;
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "source", "banking-service",
            "frozenBy", frozenBy.toString(),
            "reason", reason
        );
    }
}

@JsonTypeName("account.unfrozen")
public record AccountUnfrozenEvent(
    UUID aggregateId,
    UUID unfrozenBy,
    Instant unfrozenAt
) implements Event {
    
    @Override
    public String getEventType() {
        return "account.unfrozen";
    }
    
    @Override
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public Instant getEventTimestamp() {
        return unfrozenAt;
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "source", "banking-service",
            "unfrozenBy", unfrozenBy.toString()
        );
    }
}
```

## Aggregate Implementation

### Account Aggregate

```java
package com.firefly.banking.domain.aggregates;

import com.firefly.common.eventsourcing.aggregate.AggregateRoot;
import com.firefly.banking.domain.events.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Account extends AggregateRoot {
    
    // State
    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean active;
    private boolean frozen;
    private Instant createdAt;
    private Instant lastTransactionAt;
    
    // Constructor for new accounts
    public Account(UUID id, String accountNumber, String accountType, 
                   UUID customerId, BigDecimal initialBalance, String currency) {
        super(id, "Account");
        
        validateAccountCreation(accountNumber, accountType, customerId, initialBalance, currency);
        
        applyChange(new AccountCreatedEvent(
            id, accountNumber, accountType, customerId, initialBalance, currency
        ));
    }
    
    // Constructor for reconstruction
    public Account(UUID id) {
        super(id, "Account");
    }
    
    // Business methods
    public void deposit(BigDecimal amount, String reference, String channel, UUID transactionId) {
        validateDeposit(amount, reference, channel, transactionId);
        
        applyChange(new MoneyDepositedEvent(
            getId(), amount, currency, reference, channel, transactionId
        ));
    }
    
    public void withdraw(BigDecimal amount, String reference, String channel, UUID transactionId) {
        validateWithdrawal(amount, reference, channel, transactionId);
        
        applyChange(new MoneyWithdrawnEvent(
            getId(), amount, currency, reference, channel, transactionId
        ));
    }
    
    public void freeze(String reason, UUID frozenBy) {
        if (frozen) {
            throw new IllegalStateException("Account is already frozen");
        }
        if (!active) {
            throw new IllegalStateException("Cannot freeze inactive account");
        }
        
        applyChange(new AccountFrozenEvent(getId(), reason, frozenBy, Instant.now()));
    }
    
    public void unfreeze(UUID unfrozenBy) {
        if (!frozen) {
            throw new IllegalStateException("Account is not frozen");
        }
        
        applyChange(new AccountUnfrozenEvent(getId(), unfrozenBy, Instant.now()));
    }
    
    // Event handlers
    private void on(AccountCreatedEvent event) {
        this.accountNumber = event.accountNumber();
        this.accountType = event.accountType();
        this.customerId = event.customerId();
        this.balance = event.initialBalance();
        this.currency = event.currency();
        this.active = true;
        this.frozen = false;
        this.createdAt = event.getEventTimestamp();
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.amount());
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.amount());
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(AccountFrozenEvent event) {
        this.frozen = true;
    }
    
    private void on(AccountUnfrozenEvent event) {
        this.frozen = false;
    }
    
    // Validation methods
    private void validateAccountCreation(String accountNumber, String accountType, 
                                       UUID customerId, BigDecimal initialBalance, String currency) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }
        if (accountType == null || accountType.trim().isEmpty()) {
            throw new IllegalArgumentException("Account type cannot be empty");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
    }
    
    private void validateDeposit(BigDecimal amount, String reference, String channel, UUID transactionId) {
        if (!active) {
            throw new IllegalStateException("Account is not active");
        }
        if (frozen) {
            throw new IllegalStateException("Account is frozen");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        if (reference == null || reference.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction reference cannot be empty");
        }
        if (channel == null || channel.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction channel cannot be empty");
        }
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
    }
    
    private void validateWithdrawal(BigDecimal amount, String reference, String channel, UUID transactionId) {
        if (!active) {
            throw new IllegalStateException("Account is not active");
        }
        if (frozen) {
            throw new IllegalStateException("Account is frozen");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        if (reference == null || reference.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction reference cannot be empty");
        }
        if (channel == null || channel.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction channel cannot be empty");
        }
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
    }
    
    // Getters
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public String getAccountType() {
        return accountType;
    }
    
    public UUID getCustomerId() {
        return customerId;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public boolean isFrozen() {
        return frozen;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getLastTransactionAt() {
        return lastTransactionAt;
    }
}
```

## Application Services

### Account Service

```java
package com.firefly.banking.application.services;

import com.firefly.common.eventsourcing.store.EventStore;
import com.firefly.banking.domain.aggregates.Account;
import com.firefly.banking.application.dto.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {
    
    private final EventStore eventStore;
    
    public AccountService(EventStore eventStore) {
        this.eventStore = eventStore;
    }
    
    public Mono<AccountDto> createAccount(CreateAccountRequest request) {
        UUID accountId = UUID.randomUUID();
        
        return Mono.fromCallable(() -> new Account(
                accountId,
                request.accountNumber(),
                request.accountType(),
                request.customerId(),
                request.initialBalance(),
                request.currency()
            ))
            .flatMap(account -> 
                eventStore.appendEvents(
                    accountId,
                    "Account",
                    account.getUncommittedEvents(),
                    0L
                )
                .doOnSuccess(stream -> account.markEventsAsCommitted())
                .thenReturn(account)
            )
            .map(this::mapToDto);
    }
    
    public Mono<Account> loadAccount(UUID accountId) {
        return eventStore.loadEventStream(accountId, "Account")
            .switchIfEmpty(Mono.error(new AccountNotFoundException(accountId)))
            .map(stream -> {
                Account account = new Account(accountId);
                account.loadFromHistory(stream.getEvents());
                return account;
            });
    }
    
    public Mono<AccountDto> getAccount(UUID accountId) {
        return loadAccount(accountId)
            .map(this::mapToDto);
    }
    
    public Mono<AccountDto> deposit(UUID accountId, DepositRequest request) {
        return loadAccount(accountId)
            .flatMap(account -> {
                UUID transactionId = UUID.randomUUID();
                account.deposit(request.amount(), request.reference(), request.channel(), transactionId);
                
                return eventStore.appendEvents(
                        accountId,
                        "Account",
                        account.getUncommittedEvents(),
                        account.getVersion()
                    )
                    .doOnSuccess(stream -> account.markEventsAsCommitted())
                    .thenReturn(account);
            })
            .map(this::mapToDto);
    }
    
    public Mono<AccountDto> withdraw(UUID accountId, WithdrawalRequest request) {
        return loadAccount(accountId)
            .flatMap(account -> {
                UUID transactionId = UUID.randomUUID();
                account.withdraw(request.amount(), request.reference(), request.channel(), transactionId);
                
                return eventStore.appendEvents(
                        accountId,
                        "Account",
                        account.getUncommittedEvents(),
                        account.getVersion()
                    )
                    .doOnSuccess(stream -> account.markEventsAsCommitted())
                    .thenReturn(account);
            })
            .map(this::mapToDto);
    }
    
    public Mono<AccountDto> freezeAccount(UUID accountId, FreezeAccountRequest request) {
        return loadAccount(accountId)
            .flatMap(account -> {
                account.freeze(request.reason(), request.frozenBy());
                
                return eventStore.appendEvents(
                        accountId,
                        "Account",
                        account.getUncommittedEvents(),
                        account.getVersion()
                    )
                    .doOnSuccess(stream -> account.markEventsAsCommitted())
                    .thenReturn(account);
            })
            .map(this::mapToDto);
    }
    
    public Mono<AccountDto> unfreezeAccount(UUID accountId, UnfreezeAccountRequest request) {
        return loadAccount(accountId)
            .flatMap(account -> {
                account.unfreeze(request.unfrozenBy());
                
                return eventStore.appendEvents(
                        accountId,
                        "Account",
                        account.getUncommittedEvents(),
                        account.getVersion()
                    )
                    .doOnSuccess(stream -> account.markEventsAsCommitted())
                    .thenReturn(account);
            })
            .map(this::mapToDto);
    }
    
    private AccountDto mapToDto(Account account) {
        return new AccountDto(
            account.getId(),
            account.getAccountNumber(),
            account.getAccountType(),
            account.getCustomerId(),
            account.getBalance(),
            account.getCurrency(),
            account.isActive(),
            account.isFrozen(),
            account.getCreatedAt(),
            account.getLastTransactionAt(),
            account.getVersion()
        );
    }
}
```

## Data Transfer Objects

### DTOs and Requests

```java
package com.firefly.banking.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Response DTOs
public record AccountDto(
    UUID id,
    String accountNumber,
    String accountType,
    UUID customerId,
    BigDecimal balance,
    String currency,
    boolean active,
    boolean frozen,
    Instant createdAt,
    Instant lastTransactionAt,
    long version
) {}

// Request DTOs
public record CreateAccountRequest(
    String accountNumber,
    String accountType,
    UUID customerId,
    BigDecimal initialBalance,
    String currency
) {}

public record DepositRequest(
    BigDecimal amount,
    String reference,
    String channel
) {}

public record WithdrawalRequest(
    BigDecimal amount,
    String reference,
    String channel
) {}

public record FreezeAccountRequest(
    String reason,
    UUID frozenBy
) {}

public record UnfreezeAccountRequest(
    UUID unfrozenBy
) {}
```

## REST Controller

### Account Controller

```java
package com.firefly.banking.presentation.controllers;

import com.firefly.banking.application.services.AccountService;
import com.firefly.banking.application.dto.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    
    private final AccountService accountService;
    
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @PostMapping
    public Mono<AccountDto> createAccount(@RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }
    
    @GetMapping("/{accountId}")
    public Mono<AccountDto> getAccount(@PathVariable UUID accountId) {
        return accountService.getAccount(accountId);
    }
    
    @PostMapping("/{accountId}/deposit")
    public Mono<AccountDto> deposit(
            @PathVariable UUID accountId,
            @RequestBody DepositRequest request) {
        return accountService.deposit(accountId, request);
    }
    
    @PostMapping("/{accountId}/withdraw")
    public Mono<AccountDto> withdraw(
            @PathVariable UUID accountId,
            @RequestBody WithdrawalRequest request) {
        return accountService.withdraw(accountId, request);
    }
    
    @PostMapping("/{accountId}/freeze")
    public Mono<AccountDto> freezeAccount(
            @PathVariable UUID accountId,
            @RequestBody FreezeAccountRequest request) {
        return accountService.freezeAccount(accountId, request);
    }
    
    @PostMapping("/{accountId}/unfreeze")
    public Mono<AccountDto> unfreezeAccount(
            @PathVariable UUID accountId,
            @RequestBody UnfreezeAccountRequest request) {
        return accountService.unfreezeAccount(accountId, request);
    }
}
```

## Exception Handling

### Custom Exceptions

```java
package com.firefly.banking.domain.exceptions;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {
    private final UUID accountId;
    
    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
        this.accountId = accountId;
    }
    
    public UUID getAccountId() {
        return accountId;
    }
}

public class InsufficientFundsException extends RuntimeException {
    private final UUID accountId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;
    
    public InsufficientFundsException(UUID accountId, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super(String.format("Insufficient funds in account %s. Requested: %s, Available: %s", 
                          accountId, requestedAmount, availableBalance));
        this.accountId = accountId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }
    
    public UUID getAccountId() { return accountId; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
}
```

## Configuration

### Application Configuration

```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/banking
    username: banking_user
    password: banking_password
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m

firefly:
  eventsourcing:
    enabled: true
    store:
      type: r2dbc
      batch-size: 100
      validate-schemas: true
    snapshot:
      enabled: true
      threshold: 50
      compression: true
    publisher:
      enabled: true
      type: KAFKA
      destination-prefix: banking-events
      destination-mappings:
        "account.created": "account-lifecycle"
        "money.deposited": "account-transactions"
        "money.withdrawn": "account-transactions"
        "account.frozen": "account-compliance"
        "account.unfrozen": "account-compliance"
    performance:
      metrics-enabled: true
      health-checks-enabled: true
      tracing-enabled: true
```

## Testing

### Integration Tests

```java
package com.firefly.banking.integration;

import com.firefly.banking.application.services.AccountService;
import com.firefly.banking.application.dto.*;
import com.firefly.common.eventsourcing.store.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.r2dbc.url=r2dbc:h2:mem:///testdb",
    "firefly.eventsourcing.publisher.enabled=false"
})
class AccountServiceIntegrationTest {
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private EventStore eventStore;
    
    @Test
    void shouldCreateAccountSuccessfully() {
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest(
            "ACC001",
            "CHECKING",
            customerId,
            BigDecimal.valueOf(1000.00),
            "USD"
        );
        
        StepVerifier.create(accountService.createAccount(request))
            .assertNext(account -> {
                assertThat(account.accountNumber()).isEqualTo("ACC001");
                assertThat(account.accountType()).isEqualTo("CHECKING");
                assertThat(account.customerId()).isEqualTo(customerId);
                assertThat(account.balance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
                assertThat(account.currency()).isEqualTo("USD");
                assertThat(account.active()).isTrue();
                assertThat(account.frozen()).isFalse();
                assertThat(account.version()).isEqualTo(1L);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldProcessDepositCorrectly() {
        // Create account first
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest createRequest = new CreateAccountRequest(
            "ACC002",
            "SAVINGS",
            customerId,
            BigDecimal.valueOf(500.00),
            "USD"
        );
        
        UUID accountId = accountService.createAccount(createRequest)
            .map(AccountDto::id)
            .block();
        
        // Deposit money
        DepositRequest depositRequest = new DepositRequest(
            BigDecimal.valueOf(250.00),
            "DEPOSIT-001",
            "ATM"
        );
        
        StepVerifier.create(accountService.deposit(accountId, depositRequest))
            .assertNext(account -> {
                assertThat(account.balance()).isEqualByComparingTo(BigDecimal.valueOf(750.00));
                assertThat(account.version()).isEqualTo(2L);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldHandleInsufficientFunds() {
        // Create account with small balance
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest createRequest = new CreateAccountRequest(
            "ACC003",
            "CHECKING",
            customerId,
            BigDecimal.valueOf(100.00),
            "USD"
        );
        
        UUID accountId = accountService.createAccount(createRequest)
            .map(AccountDto::id)
            .block();
        
        // Try to withdraw more than balance
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest(
            BigDecimal.valueOf(200.00),
            "WITHDRAW-001",
            "WEB"
        );
        
        StepVerifier.create(accountService.withdraw(accountId, withdrawalRequest))
            .expectError(IllegalStateException.class)
            .verify();
    }
    
    @Test
    void shouldFreezeAndUnfreezeAccount() {
        // Create account
        UUID customerId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        
        CreateAccountRequest createRequest = new CreateAccountRequest(
            "ACC004",
            "CHECKING",
            customerId,
            BigDecimal.valueOf(1000.00),
            "USD"
        );
        
        UUID accountId = accountService.createAccount(createRequest)
            .map(AccountDto::id)
            .block();
        
        // Freeze account
        FreezeAccountRequest freezeRequest = new FreezeAccountRequest(
            "Suspicious activity",
            adminUserId
        );
        
        StepVerifier.create(accountService.freezeAccount(accountId, freezeRequest))
            .assertNext(account -> {
                assertThat(account.frozen()).isTrue();
                assertThat(account.version()).isEqualTo(2L);
            })
            .verifyComplete();
        
        // Unfreeze account
        UnfreezeAccountRequest unfreezeRequest = new UnfreezeAccountRequest(adminUserId);
        
        StepVerifier.create(accountService.unfreezeAccount(accountId, unfreezeRequest))
            .assertNext(account -> {
                assertThat(account.frozen()).isFalse();
                assertThat(account.version()).isEqualTo(3L);
            })
            .verifyComplete();
    }
}
```

## Usage Example

### Complete Usage Flow

```java
// 1. Create account
CreateAccountRequest createRequest = new CreateAccountRequest(
    "ACC12345",
    "CHECKING",
    UUID.fromString("customer-uuid"),
    BigDecimal.valueOf(1000.00),
    "USD"
);

AccountDto account = accountService.createAccount(createRequest).block();

// 2. Make deposit
DepositRequest depositRequest = new DepositRequest(
    BigDecimal.valueOf(500.00),
    "SALARY-DEPOSIT-2025-01",
    "DIRECT_DEPOSIT"
);

account = accountService.deposit(account.id(), depositRequest).block();

// 3. Make withdrawal
WithdrawalRequest withdrawalRequest = new WithdrawalRequest(
    BigDecimal.valueOf(200.00),
    "ATM-WITHDRAWAL-001",
    "ATM"
);

account = accountService.withdraw(account.id(), withdrawalRequest).block();

// 4. Check final balance
System.out.println("Final balance: " + account.balance()); // Should be 1300.00
System.out.println("Version: " + account.version()); // Should be 3
```

This example demonstrates a complete banking application with proper event sourcing patterns, domain-driven design, and reactive programming using the Firefly Event Sourcing Library.

</details>

---

## Recommended Next Steps

For the most up-to-date and comprehensive banking example, please refer to:

👉 **[Account Ledger Tutorial](../tutorial-account-ledger.md)** - The official, maintained banking example for this library.