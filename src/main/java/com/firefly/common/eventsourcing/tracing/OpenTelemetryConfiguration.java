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

package com.firefly.common.eventsourcing.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenTelemetry distributed tracing.
 * Provides instrumentation for event sourcing operations.
 */
@Configuration
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(prefix = "firefly.eventsourcing.tracing", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class OpenTelemetryConfiguration {

    /**
     * Creates a tracer for event sourcing operations.
     */
    @Bean
    public Tracer eventSourcingTracer(OpenTelemetry openTelemetry) {
        log.info("Initializing OpenTelemetry Tracer for Event Sourcing");
        return openTelemetry.getTracer("com.firefly.eventsourcing", "1.0.0");
    }
}

