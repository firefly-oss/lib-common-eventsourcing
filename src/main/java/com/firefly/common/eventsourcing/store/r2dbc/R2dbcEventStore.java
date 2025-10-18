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

package com.firefly.common.eventsourcing.store.r2dbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.core.queries.PaginationRequest;
import com.firefly.common.eventsourcing.config.EventSourcingProperties;
import com.firefly.common.eventsourcing.domain.Event;
import com.firefly.common.eventsourcing.domain.EventEnvelope;
import com.firefly.common.eventsourcing.domain.EventStream;
import com.firefly.common.eventsourcing.store.*;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R2DBC-based implementation of EventStore.
 * <p>
 * This implementation uses R2DBC for reactive database access, providing
 * non-blocking event persistence and retrieval. It supports:
 * - PostgreSQL, MySQL, H2, and other R2DBC-compatible databases
 * - Atomic event appending with optimistic concurrency control
 * - Efficient event streaming and querying
 * - JSON serialization of events and metadata
 * <p>
 * Database schema requirements:
 * <pre>
 * CREATE TABLE events (
 *     event_id UUID PRIMARY KEY,
 *     aggregate_id UUID NOT NULL,
 *     aggregate_type VARCHAR(255) NOT NULL,
 *     aggregate_version BIGINT NOT NULL,
 *     global_sequence BIGSERIAL UNIQUE,
 *     event_type VARCHAR(255) NOT NULL,
 *     event_data JSONB NOT NULL,
 *     metadata JSONB,
 *     created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
 *     UNIQUE(aggregate_id, aggregate_version)
 * );
 * 
 * CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);
 * CREATE INDEX idx_events_global_sequence ON events(global_sequence);
 * CREATE INDEX idx_events_type ON events(event_type);
 * CREATE INDEX idx_events_created_at ON events(created_at);
 * </pre>
 */
@RequiredArgsConstructor
@Slf4j
public class R2dbcEventStore implements EventStore {

    private final DatabaseClient databaseClient;
    private final R2dbcEntityTemplate entityTemplate;
    private final ObjectMapper objectMapper;
    private final EventSourcingProperties properties;
    private final ReactiveTransactionManager transactionManager;
    private final TransactionalOperator transactionalOperator;
    private final ConnectionFactory connectionFactory;
    private final AtomicLong globalSequenceCounter = new AtomicLong(0);

    @Override
    public Mono<EventStream> appendEvents(UUID aggregateId, 
                                         String aggregateType, 
                                         List<Event> events, 
                                         long expectedVersion,
                                         Map<String, Object> metadata) {
        
        if (events == null || events.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Events list cannot be null or empty"));
        }

        log.debug("Appending {} events to aggregate {}, expected version: {}", 
                 events.size(), aggregateId, expectedVersion);

        return transactionalOperator.transactional(
                checkConcurrency(aggregateId, aggregateType, expectedVersion)
                        .flatMap(currentVersion -> {
                            List<EventEnvelope> envelopes = createEventEnvelopes(
                                    events, aggregateType, currentVersion, metadata);
                            return insertEvents(envelopes)
                                    .then(Mono.fromSupplier(() -> EventStream.of(aggregateId, aggregateType, envelopes)));
                        })
        )
        .doOnSuccess(stream -> log.debug("Successfully appended {} events to aggregate {}", 
                                       events.size(), aggregateId))
        .doOnError(error -> log.error("Failed to append events to aggregate {}: {}", 
                                    aggregateId, error.getMessage()));
    }

    @Override
    public Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType) {
        return loadEventStream(aggregateId, aggregateType, 0L);
    }

    @Override
    public Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, long fromVersion) {
        log.debug("Loading event stream for aggregate {}, from version: {}", aggregateId, fromVersion);

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE aggregate_id = :aggregateId 
                  AND aggregate_type = :aggregateType 
                  AND aggregate_version >= :fromVersion
                ORDER BY aggregate_version ASC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", aggregateType)
                .bind("fromVersion", fromVersion)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all()
                .collectList()
                .map(envelopes -> EventStream.of(aggregateId, aggregateType, envelopes))
                .doOnSuccess(stream -> log.debug("Loaded {} events for aggregate {}", 
                                               stream.size(), aggregateId));
    }

    @Override
    public Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, 
                                           long fromVersion, long toVersion) {
        log.debug("Loading event stream for aggregate {}, versions {} to {}", 
                 aggregateId, fromVersion, toVersion);

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE aggregate_id = :aggregateId 
                  AND aggregate_type = :aggregateType 
                  AND aggregate_version >= :fromVersion 
                  AND aggregate_version <= :toVersion
                ORDER BY aggregate_version ASC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", aggregateType)
                .bind("fromVersion", fromVersion)
                .bind("toVersion", toVersion)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all()
                .collectList()
                .map(envelopes -> EventStream.of(aggregateId, aggregateType, envelopes));
    }

    @Override
    public Mono<Long> getAggregateVersion(UUID aggregateId, String aggregateType) {
        String sql = """
                SELECT COALESCE(MAX(aggregate_version), 0) as version
                FROM events 
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", aggregateType)
                .map(row -> row.get("version", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> aggregateExists(UUID aggregateId, String aggregateType) {
        return getAggregateVersion(aggregateId, aggregateType)
                .map(version -> version > 0);
    }

    @Override
    public Flux<EventEnvelope> streamAllEvents() {
        return streamAllEvents(0L);
    }

    @Override
    public Flux<EventEnvelope> streamAllEvents(long fromSequence) {
        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE global_sequence >= :fromSequence
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("fromSequence", fromSequence)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<EventEnvelope> streamEventsByType(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE event_type = ANY(:eventTypes)
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("eventTypes", eventTypes.toArray(new String[0]))
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<EventEnvelope> streamEventsByAggregateType(List<String> aggregateTypes) {
        if (aggregateTypes == null || aggregateTypes.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE aggregate_type = ANY(:aggregateTypes)
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateTypes", aggregateTypes.toArray(new String[0]))
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<EventEnvelope> streamEventsByTimeRange(Instant from, Instant to) {
        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE created_at >= :from AND created_at <= :to
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<EventEnvelope> streamEventsByMetadata(Map<String, Object> metadataCriteria) {
        // This is a simplified implementation - in practice, you'd want more sophisticated JSON querying
        log.warn("streamEventsByMetadata is not fully implemented in this version");
        return Flux.empty();
    }

    @Override
    public Mono<Long> getCurrentGlobalSequence() {
        String sql = "SELECT COALESCE(MAX(global_sequence), 0) as max_sequence FROM events";
        
        return databaseClient.sql(sql)
                .map(row -> row.get("max_sequence", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return databaseClient.sql("SELECT 1")
                .fetch()
                .first()
                .map(row -> true)
                .onErrorReturn(false);
    }

    @Override
    public Mono<EventStoreStatistics> getStatistics() {
        String sql = """
                SELECT 
                    COUNT(*) as total_events,
                    COUNT(DISTINCT aggregate_id) as total_aggregates,
                    MAX(global_sequence) as current_global_sequence
                FROM events
                """;

        return databaseClient.sql(sql)
                .map(row -> EventStoreStatistics.builder()
                        .totalEvents(row.get("total_events", Long.class))
                        .totalAggregates(row.get("total_aggregates", Long.class))
                        .currentGlobalSequence(row.get("current_global_sequence", Long.class))
                        .build())
                .one();
    }

    // Private helper methods

    private Mono<Long> checkConcurrency(UUID aggregateId, String aggregateType, long expectedVersion) {
        return getAggregateVersion(aggregateId, aggregateType)
                .flatMap(currentVersion -> {
                    if (currentVersion != expectedVersion) {
                        return Mono.error(new ConcurrencyException(
                                aggregateId, aggregateType, expectedVersion, currentVersion));
                    }
                    return Mono.just(currentVersion);
                });
    }

    private List<EventEnvelope> createEventEnvelopes(List<Event> events, String aggregateType, 
                                                    long baseVersion, Map<String, Object> metadata) {
        long globalSeq = globalSequenceCounter.incrementAndGet(); // Simplified - should be atomic in DB
        
        return events.stream()
                .map(event -> {
                    long version = baseVersion + events.indexOf(event) + 1;
                    return EventEnvelope.of(event, aggregateType, version, globalSeq + events.indexOf(event), metadata);
                })
                .toList();
    }
    
    /**
     * Converts EventEnvelope to EventEntity for database persistence using R2DBC.
     */
    private EventEntity toEventEntity(EventEnvelope envelope) {
        try {
            return new EventEntity(
                envelope.getEventId(),
                envelope.getAggregateId(),
                envelope.getAggregateType(),
                envelope.getAggregateVersion(),
                envelope.getGlobalSequence(),
                envelope.getEventType(),
                serializeEvent(envelope.getEvent()),
                serializeMetadata(envelope.getMetadata()),
                envelope.getCreatedAt()
            );
        } catch (JsonProcessingException e) {
            throw new EventStoreException("Failed to serialize event for database persistence", e);
        }
    }
    
    /**
     * Alternative method to save events using R2dbcEntityTemplate for better type safety.
     */
    private Mono<Void> saveEventsWithTemplate(List<EventEnvelope> envelopes) {
        List<EventEntity> entities = envelopes.stream()
                .map(this::toEventEntity)
                .toList();
                
        return Flux.fromIterable(entities)
                .flatMap(entity -> entityTemplate.insert(entity))
                .then();
    }

    private Mono<Void> insertEvents(List<EventEnvelope> envelopes) {
        String sql = """
                INSERT INTO events (event_id, aggregate_id, aggregate_type, aggregate_version, 
                                  global_sequence, event_type, event_data, metadata, created_at)
                VALUES (:eventId, :aggregateId, :aggregateType, :aggregateVersion, 
                       :globalSequence, :eventType, :eventData, :metadata, :createdAt)
                """;

        return Flux.fromIterable(envelopes)
                .flatMap(envelope -> {
                    try {
                        String serializedMetadata = serializeMetadata(envelope.getMetadata());
                        String eventDataJson = serializeEvent(envelope.getEvent());
                        
                        var spec = databaseClient.sql(sql)
                                .bind("eventId", envelope.getEventId())
                                .bind("aggregateId", envelope.getAggregateId())
                                .bind("aggregateType", envelope.getAggregateType())
                                .bind("aggregateVersion", envelope.getAggregateVersion())
                                .bind("globalSequence", envelope.getGlobalSequence())
                                .bind("eventType", envelope.getEventType())
                                .bind("createdAt", envelope.getCreatedAt());
                        
                        // Use helper method to bind JSON data
                        spec = bindJsonData(spec, "eventData", eventDataJson);
                        spec = bindJsonData(spec, "metadata", serializedMetadata);
                        
                        return spec.fetch().rowsUpdated();
                    } catch (Exception e) {
                        return Mono.error(new EventStoreException("Failed to serialize event", e));
                    }
                })
                .then();
    }

    private EventEnvelope mapToEventEnvelope(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        try {
            UUID eventId = row.get("event_id", UUID.class);
            UUID aggregateId = row.get("aggregate_id", UUID.class);
            String aggregateType = row.get("aggregate_type", String.class);
            Long aggregateVersion = row.get("aggregate_version", Long.class);
            Long globalSequence = row.get("global_sequence", Long.class);
            String eventType = row.get("event_type", String.class);
            // Handle JSONB columns - try Json first, fallback to String for H2 compatibility
            String eventData;
            String metadataJson;
            
            try {
                Json eventDataJson = row.get("event_data", Json.class);
                eventData = eventDataJson != null ? eventDataJson.asString() : null;
                Json metadataJsonObj = row.get("metadata", Json.class);
                metadataJson = metadataJsonObj != null ? metadataJsonObj.asString() : null;
            } catch (Exception e) {
                // Fallback for databases that don't support Json type (like H2)
                eventData = row.get("event_data", String.class);
                metadataJson = row.get("metadata", String.class);
            }
            Instant createdAt = row.get("created_at", Instant.class);

            Event event = deserializeEvent(eventData, eventType);
            Map<String, Object> eventMetadata = deserializeMetadata(metadataJson);

            return EventEnvelope.builder()
                    .eventId(eventId)
                    .event(event)
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .aggregateVersion(aggregateVersion)
                    .globalSequence(globalSequence)
                    .eventType(eventType)
                    .createdAt(createdAt)
                    .metadata(eventMetadata)
                    .build();

        } catch (Exception e) {
            throw new EventStoreException("Failed to map database row to EventEnvelope", e);
        }
    }

    private String serializeEvent(Event event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }

    private String serializeMetadata(Map<String, Object> metadata) throws JsonProcessingException {
        return metadata != null && !metadata.isEmpty() ? 
               objectMapper.writeValueAsString(metadata) : null;
    }

    private Event deserializeEvent(String eventData, String eventType) throws JsonProcessingException {
        try {
            // First try to deserialize as is - this works if the event type is properly registered
            return objectMapper.readValue(eventData, Event.class);
        } catch (Exception e) {
            // If that fails, try to create a generic event wrapper
            log.warn("Failed to deserialize event of type '{}', using generic wrapper: {}", eventType, e.getMessage());
            return new GenericEvent(eventType, eventData);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeMetadata(String metadataJson) throws JsonProcessingException {
        if (metadataJson == null || metadataJson.trim().isEmpty()) {
            return Map.of();
        }
        return objectMapper.readValue(metadataJson, Map.class);
    }
    
    /**
     * Binds JSON data to the database client spec, handling different database types.
     * Uses PostgreSQL Json type for PostgreSQL, falls back to String for other databases.
     */
    private DatabaseClient.GenericExecuteSpec bindJsonData(DatabaseClient.GenericExecuteSpec spec, 
                                                          String paramName, String jsonData) {
        // Detect database type by checking connection factory
        boolean isPostgreSQL = connectionFactory.getClass().getSimpleName().contains("Postgresql");
        
        if (jsonData == null) {
            if (isPostgreSQL) {
                return spec.bindNull(paramName, Json.class);
            } else {
                return spec.bindNull(paramName, String.class);
            }
        }
        
        if (isPostgreSQL) {
            // Use PostgreSQL Json type
            return spec.bind(paramName, Json.of(jsonData));
        } else {
            // Use String for H2 and other databases
            return spec.bind(paramName, jsonData);
        }
    }
}
