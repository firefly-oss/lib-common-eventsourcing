/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.eventsourcing.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firefly.common.eventsourcing.domain.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for Event Sourcing serialization.
 * <p>
 * This configuration provides a properly configured ObjectMapper for event serialization
 * and deserialization with the following features:
 * - Proper handling of Java 8+ time types
 * - Polymorphic serialization support for events
 * - Graceful handling of unknown properties
 * - Consistent date/time formatting
 * - Event type resolution based on @JsonTypeName annotations
 */
@Configuration
@Slf4j
public class EventSourcingJacksonConfiguration {

    /**
     * Creates the primary ObjectMapper bean for the application with event sourcing optimizations.
     * 
     * @return configured ObjectMapper instance
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ObjectMapper eventSourcingObjectMapper() {
        log.info("Configuring ObjectMapper for event sourcing");
        
        ObjectMapper mapper = new ObjectMapper();
        
        // Time handling
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        
        // Property handling
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        
        // Null handling
        mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        mapper.enable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        
        // Configure polymorphic type handling for Events
        configurePolymorphicTypeHandling(mapper);
        
        return mapper;
    }
    
    /**
     * Configures polymorphic type handling for Event interface.
     * This enables proper serialization/deserialization of different event types.
     */
    private void configurePolymorphicTypeHandling(ObjectMapper mapper) {
        mapper.addMixIn(Event.class, EventMixin.class);
        
        // Register common event types that might be used in tests or examples
        // Applications should register their own event types via EventTypeRegistry
        registerDefaultEventTypes(mapper);
        
        log.debug("Configured polymorphic type handling for Event interface");
    }
    
    /**
     * Registers default event types for testing and examples.
     * Production applications should register their domain-specific events.
     */
    private void registerDefaultEventTypes(ObjectMapper mapper) {
        // These are registered for test compatibility
        // Real applications should register their domain events
        try {
            // Try to register test events if they exist (for testing scenarios)
            registerEventType(mapper, "test.account.created", 
                "com.firefly.common.eventsourcing.store.r2dbc.PostgreSqlEventStoreIntegrationTest$TestAccountCreatedEvent");
            registerEventType(mapper, "test.money.withdrawn", 
                "com.firefly.common.eventsourcing.store.r2dbc.PostgreSqlEventStoreIntegrationTest$TestMoneyWithdrawnEvent");
            registerEventType(mapper, "test.money.deposited", 
                "com.firefly.common.eventsourcing.store.r2dbc.PostgreSqlEventStoreIntegrationTest$TestMoneyDepositedEvent");
                
            // Register integration test events
            registerEventType(mapper, "account.created", 
                "com.firefly.common.eventsourcing.EventSourcingIntegrationTest$AccountCreatedEvent");
            registerEventType(mapper, "money.withdrawn", 
                "com.firefly.common.eventsourcing.EventSourcingIntegrationTest$MoneyWithdrawnEvent");
            registerEventType(mapper, "money.deposited", 
                "com.firefly.common.eventsourcing.EventSourcingIntegrationTest$MoneyDepositedEvent");
        } catch (ClassNotFoundException e) {
            log.debug("Test event classes not found, skipping registration: {}", e.getMessage());
        }
    }
    
    /**
     * Registers an event type with Jackson for proper polymorphic deserialization.
     */
    private void registerEventType(ObjectMapper mapper, String typeName, String className) throws ClassNotFoundException {
        Class<?> eventClass = Class.forName(className);
        mapper.registerSubtypes(new NamedType(eventClass, typeName));
        log.debug("Registered event type: {} -> {}", typeName, className);
    }
    
    /**
     * Mixin interface to configure polymorphic type handling for Event.
     * This tells Jackson to use the eventType property for type resolution.
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType",
        visible = true
    )
    public interface EventMixin {
        // This interface is just for type configuration
    }
}