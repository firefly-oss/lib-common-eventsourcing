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

package com.firefly.common.eventsourcing.aggregate;

import com.firefly.common.eventsourcing.domain.Event;
import com.firefly.common.eventsourcing.domain.EventEnvelope;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for event-sourced aggregates.
 * <p>
 * This class provides the fundamental infrastructure for implementing
 * event sourcing patterns in domain aggregates. It handles:
 * - Event application and state reconstruction
 * - Uncommitted event tracking
 * - Version management for optimistic concurrency control
 * - Event sourcing lifecycle management
 * <p>
 * Subclasses should:
 * 1. Provide event handler methods (named "on" + EventClass)
 * 2. Use {@link #applyChange(Event)} to apply new events
 * 3. Implement business logic that produces events
 * 4. Follow the aggregate design principles
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * public class Account extends AggregateRoot {
 *     private String accountNumber;
 *     private BigDecimal balance;
 *     
 *     public Account(UUID id) {
 *         super(id, "Account");
 *     }
 *     
 *     public void withdraw(BigDecimal amount) {
 *         if (balance.compareTo(amount) < 0) {
 *             throw new InsufficientFundsException();
 *         }
 *         applyChange(new MoneyWithdrawnEvent(getId(), amount));
 *     }
 *     
 *     private void on(MoneyWithdrawnEvent event) {
 *         this.balance = this.balance.subtract(event.getAmount());
 *     }
 * }
 * }
 * </pre>
 */
@Getter
public abstract class AggregateRoot {

    /**
     * The unique identifier of this aggregate.
     */
    private final UUID id;

    /**
     * The type name of this aggregate (e.g., "Account", "Order").
     */
    private final String aggregateType;

    /**
     * Current version of the aggregate for optimistic concurrency control.
     */
    @Getter(AccessLevel.PACKAGE)
    private long version;

    /**
     * List of uncommitted events that have been applied but not yet persisted.
     */
    private final List<Event> uncommittedEvents = new ArrayList<>();

    /**
     * Whether this aggregate has been marked for deletion.
     */
    private boolean deleted = false;

    /**
     * Constructs a new aggregate with the specified ID and type.
     *
     * @param id the unique identifier
     * @param aggregateType the aggregate type name
     */
    protected AggregateRoot(UUID id, String aggregateType) {
        if (id == null) {
            throw new IllegalArgumentException("Aggregate ID cannot be null");
        }
        if (aggregateType == null || aggregateType.trim().isEmpty()) {
            throw new IllegalArgumentException("Aggregate type cannot be null or empty");
        }
        
        this.id = id;
        this.aggregateType = aggregateType.trim();
        this.version = 0L;
    }

    /**
     * Applies a new event to this aggregate.
     * <p>
     * This method:
     * 1. Adds the event to the uncommitted events list
     * 2. Applies the event to update the aggregate state
     * 3. Increments the version number
     *
     * @param event the event to apply
     * @throws IllegalArgumentException if the event is null
     * @throws EventHandlerException if no event handler is found
     */
    protected void applyChange(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        
        if (!id.equals(event.getAggregateId())) {
            throw new IllegalArgumentException(
                "Event aggregate ID (" + event.getAggregateId() + 
                ") does not match aggregate ID (" + id + ")"
            );
        }

        uncommittedEvents.add(event);
        applyEvent(event);
        version++;
    }

    /**
     * Loads the aggregate state from a stream of historical events.
     * <p>
     * This method reconstructs the aggregate state by:
     * 1. Applying each event in sequence
     * 2. Setting the aggregate version to match the last event
     * 3. Clearing any uncommitted events
     *
     * @param events the historical events to apply
     * @throws IllegalArgumentException if events belong to a different aggregate
     */
    public void loadFromHistory(List<EventEnvelope> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        // Validate all events belong to this aggregate
        for (EventEnvelope envelope : events) {
            if (!id.equals(envelope.getAggregateId())) {
                throw new IllegalArgumentException(
                    "Event aggregate ID (" + envelope.getAggregateId() + 
                    ") does not match aggregate ID (" + id + ")"
                );
            }
            if (!aggregateType.equals(envelope.getAggregateType())) {
                throw new IllegalArgumentException(
                    "Event aggregate type (" + envelope.getAggregateType() + 
                    ") does not match aggregate type (" + aggregateType + ")"
                );
            }
        }

        // Apply events in order
        for (EventEnvelope envelope : events) {
            applyEvent(envelope.getEvent());
            version = envelope.getAggregateVersion();
        }

        // Clear uncommitted events after loading from history
        uncommittedEvents.clear();
    }

    /**
     * Gets an immutable copy of the uncommitted events.
     *
     * @return list of uncommitted events
     */
    public List<Event> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    /**
     * Marks all uncommitted events as committed.
     * <p>
     * This should be called after successfully persisting events
     * to the event store.
     */
    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    /**
     * Checks if there are any uncommitted events.
     *
     * @return true if there are uncommitted events
     */
    public boolean hasUncommittedEvents() {
        return !uncommittedEvents.isEmpty();
    }

    /**
     * Gets the number of uncommitted events.
     *
     * @return the count of uncommitted events
     */
    public int getUncommittedEventCount() {
        return uncommittedEvents.size();
    }

    /**
     * Marks this aggregate as deleted.
     * <p>
     * This is a soft delete - the aggregate and its events remain
     * in the event store, but the aggregate is marked as deleted.
     */
    protected void markAsDeleted() {
        this.deleted = true;
    }

    /**
     * Applies an event to update the aggregate state.
     * <p>
     * This method uses reflection to find and invoke the appropriate
     * event handler method. Handler methods should be named "on" + EventClassName
     * and take a single parameter of the event type.
     *
     * @param event the event to apply
     * @throws EventHandlerException if no handler is found or invocation fails
     */
    private void applyEvent(Event event) {
        try {
            String methodName = "on";
            String eventClassName = event.getClass().getSimpleName();
            
            // Try to find a method named "on" + EventClassName
            try {
                var method = this.getClass().getDeclaredMethod(methodName, event.getClass());
                method.setAccessible(true);
                method.invoke(this, event);
                return;
            } catch (NoSuchMethodException e) {
                // Try with event class simple name
                try {
                    String alternativeMethodName = "on" + eventClassName;
                    var method = this.getClass().getDeclaredMethod(alternativeMethodName, event.getClass());
                    method.setAccessible(true);
                    method.invoke(this, event);
                    return;
                } catch (ReflectiveOperationException e2) {
                    // Continue to search in parent classes
                }
            } catch (ReflectiveOperationException e) {
                // Continue to search
            }

            // If no exact match found, look for methods that accept the event type
            var methods = this.getClass().getDeclaredMethods();
            for (var method : methods) {
                if (method.getName().equals(methodName) && 
                    method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0].isAssignableFrom(event.getClass())) {
                    
                    try {
                        method.setAccessible(true);
                        method.invoke(this, event);
                        return;
                    } catch (Exception e2) {
                        // Continue searching
                    }
                }
            }

            throw new EventHandlerException(
                "No event handler found for " + event.getClass().getSimpleName() + 
                " in aggregate " + this.getClass().getSimpleName()
            );
            
        } catch (Exception e) {
            if (e instanceof EventHandlerException) {
                throw e;
            }
            throw new EventHandlerException(
                "Failed to apply event " + event.getClass().getSimpleName() + 
                " to aggregate " + this.getClass().getSimpleName(), e
            );
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AggregateRoot that = (AggregateRoot) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s{id=%s, version=%d, uncommittedEvents=%d}", 
            getClass().getSimpleName(), id, version, uncommittedEvents.size());
    }
}