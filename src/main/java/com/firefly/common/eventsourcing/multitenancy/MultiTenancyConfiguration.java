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

package com.firefly.common.eventsourcing.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for multi-tenancy support in event sourcing.
 * Enables tenant isolation for events, aggregates, and projections.
 */
@Configuration
@ConditionalOnProperty(prefix = "firefly.eventsourcing.multitenancy", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class MultiTenancyConfiguration {

    public MultiTenancyConfiguration() {
        log.info("Multi-tenancy support enabled for Event Sourcing");
    }
}

