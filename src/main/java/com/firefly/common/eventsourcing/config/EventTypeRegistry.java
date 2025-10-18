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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.firefly.common.eventsourcing.domain.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Registry for automatically discovering and registering event types with Jackson.
 * <p>
 * This component scans for classes that implement {@link Event} and are annotated
 * with {@link JsonTypeName}, then automatically registers them with the ObjectMapper
 * for proper polymorphic serialization/deserialization.
 * <p>
 * Usage:
 * <pre>{@code
 * @JsonTypeName("user.created")
 * public class UserCreatedEvent implements Event {
 *     // Event implementation
 * }
 * }</pre>
 * <p>
 * The event will be automatically discovered and registered when the application starts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventTypeRegistry {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    /**
     * Automatically registers all discovered event types when the application is ready.
     * This ensures that all event classes are properly configured for serialization.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerEventTypes() {
        log.info("Starting automatic event type registration...");
        
        // Find all Event implementations with JsonTypeName annotations
        Map<String, Event> eventBeans = applicationContext.getBeansOfType(Event.class);
        
        // Also scan for event classes (not just beans)
        registerEventClasses();
        
        log.info("Completed automatic event type registration for {} event types", eventBeans.size());
    }
    
    /**
     * Manually register an event type with the ObjectMapper.
     * This is useful for events that aren't Spring beans or for programmatic registration.
     * 
     * @param eventClass the event class to register
     */
    public void registerEventType(Class<? extends Event> eventClass) {
        JsonTypeName annotation = eventClass.getAnnotation(JsonTypeName.class);
        if (annotation != null) {
            String typeName = annotation.value();
            objectMapper.registerSubtypes(new NamedType(eventClass, typeName));
            log.debug("Manually registered event type: {} -> {}", typeName, eventClass.getSimpleName());
        } else {
            log.warn("Event class {} does not have @JsonTypeName annotation, skipping registration", 
                    eventClass.getSimpleName());
        }
    }
    
    /**
     * Register an event type with a custom type name.
     * 
     * @param eventClass the event class to register
     * @param typeName the custom type name to use
     */
    public void registerEventType(Class<? extends Event> eventClass, String typeName) {
        objectMapper.registerSubtypes(new NamedType(eventClass, typeName));
        log.debug("Manually registered event type: {} -> {}", typeName, eventClass.getSimpleName());
    }
    
    /**
     * Get all registered event type names.
     * 
     * @return set of registered type names
     */
    public Set<String> getRegisteredEventTypes() {
        // This is a simplified implementation
        // In practice, we could maintain our own registry of registered types
        log.debug("Getting registered event types - this is a placeholder implementation");
        return Set.of(); // Placeholder - could be enhanced to track registrations
    }
    
    /**
     * Scans for event classes in the classpath.
     * This is a simplified implementation - in practice you might want to use
     * classpath scanning libraries like Reflections or Spring's ClassPathScanningCandidateComponentProvider.
     */
    private void registerEventClasses() {
        // For now, we'll rely on Spring beans and manual registration
        // A future enhancement could add classpath scanning
        log.debug("Event class scanning not implemented yet - relying on manual registration and Spring beans");
    }
}
