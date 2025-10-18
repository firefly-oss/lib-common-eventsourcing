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

package com.firefly.common.eventsourcing.projection;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer-based metrics tracking for event sourcing projections.
 * Provides counters, timers, and gauges for monitoring projection performance and health.
 */
@Slf4j
public class ProjectionMetrics {
    
    private final String projectionName;
    private final MeterRegistry meterRegistry;
    
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter projectionsResetCounter;
    private final Timer eventProcessingTimer;
    
    private final AtomicLong currentPosition = new AtomicLong(0L);
    private final AtomicLong lagAmount = new AtomicLong(0L);
    private final AtomicLong lastProcessedAt = new AtomicLong(System.currentTimeMillis());
    
    public ProjectionMetrics(String projectionName, MeterRegistry meterRegistry) {
        this.projectionName = projectionName;
        this.meterRegistry = meterRegistry;
        
        this.eventsProcessedCounter = Counter.builder("projection.events.processed")
                .description("Total number of events processed by the projection")
                .tag("projection", projectionName)
                .register(meterRegistry);
                
        this.eventsFailedCounter = Counter.builder("projection.events.failed")
                .description("Total number of events that failed processing")
                .tag("projection", projectionName)
                .register(meterRegistry);
                
        this.projectionsResetCounter = Counter.builder("projection.resets")
                .description("Total number of times projection was reset")
                .tag("projection", projectionName)
                .register(meterRegistry);
                
        this.eventProcessingTimer = Timer.builder("projection.event.processing.duration")
                .description("Time taken to process individual events")
                .tag("projection", projectionName)
                .register(meterRegistry);
                
        // Register gauges
        Gauge.builder("projection.position.current", this, metrics -> (double) metrics.currentPosition.get())
                .description("Current position (global sequence) of the projection")
                .tag("projection", projectionName)
                .register(meterRegistry);
                
        Gauge.builder("projection.lag", this, metrics -> (double) metrics.lagAmount.get())
                .description("Number of events the projection is lagging behind")
                .tag("projection", projectionName)
                .register(meterRegistry);
                
        Gauge.builder("projection.last.processed.seconds.ago", this, 
                    metrics -> (System.currentTimeMillis() - metrics.lastProcessedAt.get()) / 1000.0)
                .description("Seconds since the projection last processed an event")
                .tag("projection", projectionName)
                .register(meterRegistry);
    }
    
    /**
     * Records successful event processing.
     */
    public void recordEventProcessed() {
        eventsProcessedCounter.increment();
        lastProcessedAt.set(System.currentTimeMillis());
    }
    
    /**
     * Records failed event processing.
     */
    public void recordEventFailed() {
        eventsFailedCounter.increment();
    }
    
    /**
     * Records projection reset.
     */
    public void recordProjectionReset() {
        projectionsResetCounter.increment();
        currentPosition.set(0L);
        lagAmount.set(0L);
    }
    
    /**
     * Records time taken to process an event.
     */
    public Timer.Sample startEventProcessingTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Updates current position and lag metrics.
     */
    public void updatePosition(long position, long globalSequence) {
        currentPosition.set(position);
        lagAmount.set(Math.max(0, globalSequence - position));
    }
    
    /**
     * Gets a timer for recording event processing duration.
     */
    public Timer getEventProcessingTimer() {
        return eventProcessingTimer;
    }
    
    /**
     * Gets current processing rate (events per second) over the last minute.
     */
    public double getProcessingRate() {
        return eventsProcessedCounter.count() / 60.0; // Simple approximation
    }
    
    /**
     * Gets current position.
     */
    public long getCurrentPosition() {
        return currentPosition.get();
    }
    
    /**
     * Gets current lag amount.
     */
    public long getLagAmount() {
        return lagAmount.get();
    }
    
    /**
     * Gets the projection name.
     */
    public String getProjectionName() {
        return projectionName;
    }
}