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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for event sourcing metrics collection.
 * <p>
 * This configuration sets up Micrometer metrics for event sourcing
 * operations like event appends, reads, snapshot operations, etc.
 */
@Configuration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = "firefly.eventsourcing.performance", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EventSourcingMetricsConfiguration {

    public EventSourcingMetricsConfiguration() {
        log.debug("Event Sourcing Metrics Configuration initialized");
    }

    // TODO: Add metrics bean configurations
    // @Bean
    // public EventStoreMetrics eventStoreMetrics(MeterRegistry meterRegistry) { ... }
}