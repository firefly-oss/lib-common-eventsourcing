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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import com.firefly.common.eventsourcing.publisher.EventSourcingPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for the Event Sourcing library.
 * <p>
 * This configuration class automatically sets up the event sourcing library
 * when included in a Spring Boot application. It enables configuration properties,
 * sets up component scanning, and provides default beans where needed.
 * <p>
 * Components automatically discovered and configured:
 * <ul>
 *   <li>Event stores (R2DBC, MongoDB, etc.)</li>
 *   <li>Snapshot stores with optional caching</li>
 *   <li>Event publishers integrated with EDA</li>
 *   <li>Health indicators for Spring Boot Actuator</li>
 *   <li>Metrics collection via Micrometer</li>
 *   <li>Distributed tracing integration</li>
 * </ul>
 * <p>
 * The auto-configuration is enabled by default but can be disabled using:
 * <pre>
 * firefly:
 *   eventsourcing:
 *     enabled: false
 * </pre>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.eventsourcing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EventSourcingProperties.class)
@ComponentScan(
        basePackages = "com.firefly.common.eventsourcing",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.firefly\\.common\\.eventsourcing\\.examples\\..*"
        )
)
@EnableAsync
@EnableScheduling
@Import({
    EventStoreAutoConfiguration.class,
    SnapshotAutoConfiguration.class,
    EventSourcingHealthConfiguration.class,
    EventSourcingMetricsConfiguration.class,
    com.firefly.common.core.config.R2dbcConfig.class,
    com.firefly.common.core.config.R2dbcTransactionConfig.class
})
@Slf4j
public class EventSourcingAutoConfiguration {

    public EventSourcingAutoConfiguration() {
        log.info("Firefly Event Sourcing Auto-Configuration - Starting initialization");
        log.info("Event sourcing components will be auto-discovered: stores, snapshots, publishers, health, metrics");
    }

    /**
     * Creates the main event sourcing publisher if EDA is available.
     * This integrates event sourcing with the EDA messaging infrastructure.
     */
    @Bean
    @ConditionalOnBean(EventPublisherFactory.class)
    @ConditionalOnProperty(prefix = "firefly.eventsourcing.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public EventSourcingPublisher eventSourcingPublisher(
            EventPublisherFactory publisherFactory,
            EventSourcingProperties properties) {
        log.info("Creating EventSourcingPublisher with type: {}", properties.getPublisher().getType());
        return new EventSourcingPublisher(publisherFactory, properties);
    }

    /**
     * Provides a default ObjectMapper for event serialization if none exists.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper eventSourcingObjectMapper() {
        log.debug("Creating default ObjectMapper for event sourcing");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.findAndRegisterModules();
        return mapper;
    }
}