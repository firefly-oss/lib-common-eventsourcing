# Firefly Event Sourcing Library Documentation

Welcome to the comprehensive documentation for the Firefly Event Sourcing Library. This library provides a production-ready event sourcing implementation with reactive programming support, designed specifically for the Firefly banking platform.

## Documentation Structure

### 📚 Core Documentation
- [**Quick Start Guide**](./quick-start.md) - Get up and running in 5 minutes
- [**Architecture Overview**](./architecture.md) - System design and components
- [**Configuration Reference**](./configuration.md) - Complete configuration guide
- [**API Reference**](./api-reference.md) - Detailed API documentation

### 🏗️ Implementation Guides
- [**Implementing Aggregates**](./implementing-aggregates.md) - Build event-sourced aggregates
- [**Working with Events**](./working-with-events.md) - Event design and implementation
- [**Event Store Usage**](./event-store.md) - Persistence and querying
- [**Snapshot Management**](./snapshots.md) - Performance optimization with snapshots

### 🔧 Integration & Operations
- [**lib-common-r2dbc Integration**](./r2dbc-integration.md) - Database integration details
- [**EDA Integration**](./eda-integration.md) - Event publishing and messaging
- [**Monitoring & Health**](./monitoring.md) - Production monitoring setup
- [**Performance Tuning**](./performance.md) - Optimization strategies

### 🧪 Development & Testing
- [**Testing Guide**](./testing.md) - Testing strategies and examples
- [**Migration Guide**](./migration.md) - Upgrading from previous versions
- [**Troubleshooting**](./troubleshooting.md) - Common issues and solutions

### 📋 Reference Materials
- [**Database Schema**](./database-schema.md) - Complete schema definitions
- [**Examples**](./examples/) - Working code examples
- [**Best Practices**](./best-practices.md) - Production recommendations
- [**FAQ**](./faq.md) - Frequently asked questions

## Library Overview

The Firefly Event Sourcing Library provides:

- **🚀 Reactive Architecture**: Built on Project Reactor for non-blocking operations
- **📦 Event Store Abstraction**: Pluggable implementations (R2DBC primary)
- **🏗️ Aggregate Framework**: Base classes for domain-driven design
- **📸 Snapshot Support**: Automatic performance optimization
- **🔄 EDA Integration**: Seamless message publishing
- **🗄️ R2DBC Integration**: Leverages lib-common-r2dbc utilities
- **🔧 Auto-Configuration**: Spring Boot ready

## Getting Started

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-eventsourcing</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Minimal Configuration

```yaml
firefly:
  eventsourcing:
    enabled: true
    store:
      type: r2dbc
```

### 3. Create Your First Aggregate

```java
public class Account extends AggregateRoot {
    private String accountNumber;
    private BigDecimal balance;
    
    public Account(UUID id, String accountNumber, BigDecimal initialBalance) {
        super(id, "Account");
        applyChange(new AccountCreatedEvent(id, accountNumber, initialBalance));
    }
    
    private void on(AccountCreatedEvent event) {
        this.accountNumber = event.accountNumber();
        this.balance = event.initialBalance();
    }
}
```

## Key Components

### Core Packages

- `com.firefly.common.eventsourcing.domain` - Core domain abstractions
- `com.firefly.common.eventsourcing.aggregate` - Aggregate root implementation
- `com.firefly.common.eventsourcing.store` - Event persistence layer
- `com.firefly.common.eventsourcing.snapshot` - Snapshot management
- `com.firefly.common.eventsourcing.publisher` - Event publishing
- `com.firefly.common.eventsourcing.config` - Configuration and auto-setup

### Primary Interfaces

- **EventStore** - Event persistence and retrieval
- **Event** - Domain event abstraction  
- **AggregateRoot** - Base class for aggregates
- **SnapshotStore** - Snapshot persistence
- **EventSourcingPublisher** - Event publishing to message buses

## System Requirements

- Java 21+
- Spring Boot 3.2+
- Project Reactor
- R2DBC compatible database (PostgreSQL, MySQL, H2)
- lib-common-r2dbc for database operations

## Production Readiness

This library has been designed for production use in the Firefly banking platform:

- ✅ **Transaction Safety** - ACID compliance for event persistence
- ✅ **Optimistic Locking** - Concurrency control with version checking
- ✅ **Reactive Streams** - Backpressure-aware processing
- ✅ **Health Monitoring** - Built-in health checks and metrics
- ✅ **Error Handling** - Comprehensive exception hierarchy
- ✅ **Performance** - Batching, caching, and optimization features

## Support & Contributing

For questions, issues, or contributions, please refer to the Firefly development team.

## License

Copyright 2025 Firefly Software Solutions Inc. Licensed under the Apache License, Version 2.0.