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

package com.firefly.common.eventsourcing.monitoring;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer.Sample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics and monitoring for Event Store operations.
 * 
 * This component provides comprehensive monitoring of event store performance,
 * including operation timers, counters, and health indicators.
 * 
 * Metrics exposed:
 * - event.store.operations.duration (Timer)
 * - event.store.events.appended (Counter)
 * - event.store.events.loaded (Counter)
 * - event.store.errors (Counter)
 * - event.store.concurrency.conflicts (Counter)
 * - event.store.connection.pool.active (Gauge)
 * - event.store.batch.size (DistributionSummary)
 */
@Component
@Slf4j
public class EventStoreMetrics implements HealthIndicator {

    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter eventsAppendedCounter;
    private final Counter eventsLoadedCounter;
    private final Counter errorsCounter;
    private final Counter concurrencyConflictsCounter;
    
    // Timers
    private final Timer appendOperationTimer;
    private final Timer loadOperationTimer;
    private final Timer queryOperationTimer;
    
    // Gauges
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalAggregates = new AtomicLong(0);
    private final AtomicLong totalEvents = new AtomicLong(0);
    
    // Distribution Summary
    private final DistributionSummary batchSizeSummary;
    
    // Health tracking
    private volatile boolean isHealthy = true;
    private volatile Instant lastHealthCheck = Instant.now();
    private volatile String lastError;
    
    public EventStoreMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.eventsAppendedCounter = Counter.builder("event.store.events.appended")
                .description("Total number of events appended to the event store")
                .register(meterRegistry);
                
        this.eventsLoadedCounter = Counter.builder("event.store.events.loaded")
                .description("Total number of events loaded from the event store")
                .register(meterRegistry);
                
        this.errorsCounter = Counter.builder("event.store.errors")
                .description("Total number of event store errors")
                .tag("type", "general")
                .register(meterRegistry);
                
        this.concurrencyConflictsCounter = Counter.builder("event.store.concurrency.conflicts")
                .description("Total number of concurrency conflicts")
                .register(meterRegistry);
        
        // Initialize timers
        this.appendOperationTimer = Timer.builder("event.store.operations.duration")
                .description("Event store append operation duration")
                .tag("operation", "append")
                .register(meterRegistry);
                
        this.loadOperationTimer = Timer.builder("event.store.operations.duration")
                .description("Event store load operation duration")
                .tag("operation", "load")
                .register(meterRegistry);
                
        this.queryOperationTimer = Timer.builder("event.store.operations.duration")
                .description("Event store query operation duration")
                .tag("operation", "query")
                .register(meterRegistry);
        
        // Initialize distribution summary
        this.batchSizeSummary = DistributionSummary.builder("event.store.batch.size")
                .description("Distribution of event batch sizes")
                .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("event.store.connection.pool.active", this, EventStoreMetrics::getActiveConnections)
                .description("Number of active database connections")
                .register(meterRegistry);
                
        Gauge.builder("event.store.aggregates.total", this, EventStoreMetrics::getTotalAggregates)
                .description("Total number of aggregates in the event store")
                .register(meterRegistry);
                
        Gauge.builder("event.store.events.total", this, EventStoreMetrics::getTotalEvents)
                .description("Total number of events in the event store")
                .register(meterRegistry);
    }
    
    /**
     * Record an append operation with timing and event count.
     *
     * @param duration the duration of the append operation
     * @param eventCount the number of events appended
     */
    public void recordAppendOperation(Duration duration, int eventCount) {
        appendOperationTimer.record(duration);
        eventsAppendedCounter.increment(eventCount);
        batchSizeSummary.record(eventCount);
        
        log.debug("Recorded append operation: {} events in {}", eventCount, duration);
    }
    
    /**
     * Record a load operation with timing and event count.
     *
     * @param duration the duration of the load operation
     * @param eventCount the number of events loaded
     */
    public void recordLoadOperation(Duration duration, int eventCount) {
        loadOperationTimer.record(duration);
        eventsLoadedCounter.increment(eventCount);
        
        log.debug("Recorded load operation: {} events in {}", eventCount, duration);
    }
    
    /**
     * Record a query operation with timing.
     *
     * @param duration the duration of the query operation
     */
    public void recordQueryOperation(Duration duration) {
        queryOperationTimer.record(duration);
        
        log.debug("Recorded query operation in {}", duration);
    }
    
    /**
     * Record an error by type.
     *
     * @param errorType the type/category of the error
     * @param error the throwable that occurred
     */
    public void recordError(String errorType, Throwable error) {
        Counter.builder("event.store.errors")
                .description("Event store errors by type")
                .tag("type", errorType)
                .register(meterRegistry)
                .increment();
                
        this.lastError = error.getMessage();
        this.isHealthy = false;
        
        log.warn("Recorded event store error of type {}: {}", errorType, error.getMessage());
    }
    
    /**
     * Record a concurrency conflict.
     */
    public void recordConcurrencyConflict() {
        concurrencyConflictsCounter.increment();
        log.debug("Recorded concurrency conflict");
    }
    
    /**
     * Start timing an operation.
     *
     * @return a timer sample to stop later
     */
    public Sample startTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Update active connection count.
     *
     * @param count the number of active connections
     */
    public void setActiveConnections(long count) {
        activeConnections.set(count);
    }
    
    /**
     * Update total aggregate count.
     *
     * @param count the total number of aggregates
     */
    public void setTotalAggregates(long count) {
        totalAggregates.set(count);
    }
    
    /**
     * Update total event count.
     *
     * @param count the total number of events
     */
    public void setTotalEvents(long count) {
        totalEvents.set(count);
    }
    
    /**
     * Mark the event store as healthy.
     */
    public void markHealthy() {
        this.isHealthy = true;
        this.lastHealthCheck = Instant.now();
        this.lastError = null;
    }
    
    /**
     * Mark the event store as unhealthy.
     */
    public void markUnhealthy(String reason) {
        this.isHealthy = false;
        this.lastError = reason;
        this.lastHealthCheck = Instant.now();
    }
    
    // Gauge value providers
    private double getActiveConnections() {
        return activeConnections.get();
    }
    
    private double getTotalAggregates() {
        return totalAggregates.get();
    }
    
    private double getTotalEvents() {
        return totalEvents.get();
    }
    
    /**
     * Health indicator implementation.
     */
    @Override
    public Health health() {
        Health.Builder builder = isHealthy ? Health.up() : Health.down();
        
        return builder
                .withDetail("lastHealthCheck", lastHealthCheck)
                .withDetail("totalEvents", totalEvents.get())
                .withDetail("totalAggregates", totalAggregates.get())
                .withDetail("activeConnections", activeConnections.get())
                .withDetail("lastError", lastError)
                .build();
    }
    
    /**
     * Get performance summary for monitoring dashboards.
     *
     * @return performance summary with all metrics
     */
    public PerformanceSummary getPerformanceSummary() {
        return PerformanceSummary.builder()
                .eventsAppended(eventsAppendedCounter.count())
                .eventsLoaded(eventsLoadedCounter.count())
                .totalErrors(errorsCounter.count())
                .concurrencyConflicts(concurrencyConflictsCounter.count())
                .averageAppendTime(appendOperationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .averageLoadTime(loadOperationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .averageBatchSize(batchSizeSummary.mean())
                .activeConnections(activeConnections.get())
                .totalEvents(totalEvents.get())
                .totalAggregates(totalAggregates.get())
                .isHealthy(isHealthy)
                .lastHealthCheck(lastHealthCheck)
                .build();
    }
    
    /**
     * Performance summary data transfer object.
     */
    public static class PerformanceSummary {
        private final double eventsAppended;
        private final double eventsLoaded;
        private final double totalErrors;
        private final double concurrencyConflicts;
        private final double averageAppendTime;
        private final double averageLoadTime;
        private final double averageBatchSize;
        private final long activeConnections;
        private final long totalEvents;
        private final long totalAggregates;
        private final boolean isHealthy;
        private final Instant lastHealthCheck;
        
        private PerformanceSummary(Builder builder) {
            this.eventsAppended = builder.eventsAppended;
            this.eventsLoaded = builder.eventsLoaded;
            this.totalErrors = builder.totalErrors;
            this.concurrencyConflicts = builder.concurrencyConflicts;
            this.averageAppendTime = builder.averageAppendTime;
            this.averageLoadTime = builder.averageLoadTime;
            this.averageBatchSize = builder.averageBatchSize;
            this.activeConnections = builder.activeConnections;
            this.totalEvents = builder.totalEvents;
            this.totalAggregates = builder.totalAggregates;
            this.isHealthy = builder.isHealthy;
            this.lastHealthCheck = builder.lastHealthCheck;
        }
        
        /**
         * Creates a new builder for PerformanceSummary.
         *
         * @return a new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public double getEventsAppended() { return eventsAppended; }
        public double getEventsLoaded() { return eventsLoaded; }
        public double getTotalErrors() { return totalErrors; }
        public double getConcurrencyConflicts() { return concurrencyConflicts; }
        public double getAverageAppendTime() { return averageAppendTime; }
        public double getAverageLoadTime() { return averageLoadTime; }
        public double getAverageBatchSize() { return averageBatchSize; }
        public long getActiveConnections() { return activeConnections; }
        public long getTotalEvents() { return totalEvents; }
        public long getTotalAggregates() { return totalAggregates; }
        public boolean isHealthy() { return isHealthy; }
        public Instant getLastHealthCheck() { return lastHealthCheck; }
        
        public static class Builder {
            private double eventsAppended;
            private double eventsLoaded;
            private double totalErrors;
            private double concurrencyConflicts;
            private double averageAppendTime;
            private double averageLoadTime;
            private double averageBatchSize;
            private long activeConnections;
            private long totalEvents;
            private long totalAggregates;
            private boolean isHealthy;
            private Instant lastHealthCheck;
            
            /** Sets events appended count. @param eventsAppended the count @return this builder */
            public Builder eventsAppended(double eventsAppended) { this.eventsAppended = eventsAppended; return this; }
            /** Sets events loaded count. @param eventsLoaded the count @return this builder */
            public Builder eventsLoaded(double eventsLoaded) { this.eventsLoaded = eventsLoaded; return this; }
            /** Sets total errors count. @param totalErrors the count @return this builder */
            public Builder totalErrors(double totalErrors) { this.totalErrors = totalErrors; return this; }
            /** Sets concurrency conflicts count. @param concurrencyConflicts the count @return this builder */
            public Builder concurrencyConflicts(double concurrencyConflicts) { this.concurrencyConflicts = concurrencyConflicts; return this; }
            /** Sets average append time. @param averageAppendTime the time @return this builder */
            public Builder averageAppendTime(double averageAppendTime) { this.averageAppendTime = averageAppendTime; return this; }
            /** Sets average load time. @param averageLoadTime the time @return this builder */
            public Builder averageLoadTime(double averageLoadTime) { this.averageLoadTime = averageLoadTime; return this; }
            /** Sets average batch size. @param averageBatchSize the size @return this builder */
            public Builder averageBatchSize(double averageBatchSize) { this.averageBatchSize = averageBatchSize; return this; }
            /** Sets active connections count. @param activeConnections the count @return this builder */
            public Builder activeConnections(long activeConnections) { this.activeConnections = activeConnections; return this; }
            /** Sets total events count. @param totalEvents the count @return this builder */
            public Builder totalEvents(long totalEvents) { this.totalEvents = totalEvents; return this; }
            /** Sets total aggregates count. @param totalAggregates the count @return this builder */
            public Builder totalAggregates(long totalAggregates) { this.totalAggregates = totalAggregates; return this; }
            /** Sets healthy status. @param isHealthy the status @return this builder */
            public Builder isHealthy(boolean isHealthy) { this.isHealthy = isHealthy; return this; }
            /** Sets last health check time. @param lastHealthCheck the time @return this builder */
            public Builder lastHealthCheck(Instant lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; return this; }
            
            /**
             * Builds the PerformanceSummary instance.
             *
             * @return the built PerformanceSummary
             */
            public PerformanceSummary build() {
                return new PerformanceSummary(this);
            }
        }
    }
}