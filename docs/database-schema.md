# Database Schema

Complete database schema reference for the Firefly Event Sourcing Library.

## Overview

The event sourcing library uses a simple but effective database schema designed for high performance, data integrity, and scalability. The schema is optimized for the event sourcing pattern with proper indexing and constraints.

## Events Table

The primary table for storing all events in the system.

### PostgreSQL Schema

```sql
CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    global_sequence BIGSERIAL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(aggregate_id, aggregate_version)
);
```

### Column Details

| Column | Type | Null | Description |
|--------|------|------|-------------|
| `event_id` | UUID | NO | Unique identifier for each event envelope |
| `aggregate_id` | UUID | NO | Identifier of the aggregate this event belongs to |
| `aggregate_type` | VARCHAR(255) | NO | Type of the aggregate (e.g., "Account", "Order") |
| `aggregate_version` | BIGINT | NO | Version number within the aggregate's event stream |
| `global_sequence` | BIGSERIAL | NO | Global ordering sequence across all events |
| `event_type` | VARCHAR(255) | NO | Type identifier of the domain event |
| `event_data` | JSONB | NO | Serialized event data in JSON format |
| `metadata` | JSONB | YES | Additional metadata (correlation IDs, etc.) |
| `created_at` | TIMESTAMP WITH TIME ZONE | NO | When the event was persisted to the store |

### Constraints

1. **Primary Key**: `event_id` ensures each event envelope is unique
2. **Unique Constraint**: `(aggregate_id, aggregate_version)` ensures version uniqueness per aggregate
3. **Unique Constraint**: `global_sequence` ensures global ordering
4. **Not Null**: Critical fields cannot be null for data integrity

### Indexes

```sql
-- Primary key index (automatically created)
-- CREATE UNIQUE INDEX events_pkey ON events(event_id);

-- Aggregate lookup (most common query pattern)
CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);

-- Global sequence ordering (for event streaming)
CREATE INDEX idx_events_global_sequence ON events(global_sequence);

-- Event type filtering (for projections)
CREATE INDEX idx_events_type ON events(event_type);

-- Time-based queries (for temporal filtering)
CREATE INDEX idx_events_created_at ON events(created_at);

-- Composite index for aggregate version range queries
CREATE INDEX idx_events_aggregate_version ON events(aggregate_id, aggregate_version);

-- JSONB indexes for metadata queries (optional, based on usage)
CREATE INDEX idx_events_metadata_correlation ON events USING GIN ((metadata->>'correlationId'));
CREATE INDEX idx_events_metadata_user ON events USING GIN ((metadata->>'userId'));
```

## Database Variations

### MySQL Schema

```sql
CREATE TABLE events (
    event_id CHAR(36) PRIMARY KEY,
    aggregate_id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    global_sequence BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_data JSON NOT NULL,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_aggregate_version (aggregate_id, aggregate_version),
    KEY idx_aggregate (aggregate_id, aggregate_type),
    KEY idx_global_sequence (global_sequence),
    KEY idx_event_type (event_type),
    KEY idx_created_at (created_at)
);
```

### H2 Schema (for testing)

```sql
CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    global_sequence BIGINT AUTO_INCREMENT UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_data CLOB NOT NULL,
    metadata CLOB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(aggregate_id, aggregate_version)
);

CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);
CREATE INDEX idx_events_global_sequence ON events(global_sequence);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_created_at ON events(created_at);
```

## Snapshots Table (Optional)

For performance optimization, snapshots can be stored in a separate table.

### PostgreSQL Schema

```sql
CREATE TABLE snapshots (
    snapshot_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    snapshot_type VARCHAR(255) NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(aggregate_id, version)
);

-- Indexes
CREATE INDEX idx_snapshots_aggregate ON snapshots(aggregate_id, aggregate_type);
CREATE INDEX idx_snapshots_version ON snapshots(aggregate_id, version DESC);
CREATE INDEX idx_snapshots_created_at ON snapshots(created_at);
```

### Snapshot Columns

| Column | Type | Null | Description |
|--------|------|------|-------------|
| `snapshot_id` | UUID | NO | Unique identifier for the snapshot |
| `aggregate_id` | UUID | NO | Identifier of the aggregate |
| `aggregate_type` | VARCHAR(255) | NO | Type of the aggregate |
| `version` | BIGINT | NO | Version when the snapshot was taken |
| `snapshot_type` | VARCHAR(255) | NO | Type of the snapshot |
| `snapshot_data` | JSONB | NO | Serialized aggregate state |
| `created_at` | TIMESTAMP WITH TIME ZONE | NO | When the snapshot was created |

## Sample Data

### Events Table Sample

```sql
INSERT INTO events VALUES 
(
    '550e8400-e29b-41d4-a716-446655440000',
    'acc-123e4567-e89b-12d3-a456-426614174000',
    'Account',
    1,
    1001,
    'account.created',
    '{"aggregateId":"acc-123e4567-e89b-12d3-a456-426614174000","accountNumber":"ACC001","initialBalance":1000.00}',
    '{"correlationId":"corr-001","userId":"user-123","source":"banking-service"}',
    '2025-01-15 10:30:00+00'
),
(
    '550e8400-e29b-41d4-a716-446655440001',
    'acc-123e4567-e89b-12d3-a456-426614174000',
    'Account',
    2,
    1002,
    'money.deposited',
    '{"aggregateId":"acc-123e4567-e89b-12d3-a456-426614174000","amount":250.00,"reference":"DEP-001"}',
    '{"correlationId":"corr-002","userId":"user-123","source":"banking-service"}',
    '2025-01-15 10:35:00+00'
);
```

## Query Patterns

### 1. Load Event Stream for Aggregate

```sql
SELECT event_id, aggregate_id, aggregate_type, aggregate_version, 
       global_sequence, event_type, event_data, metadata, created_at
FROM events 
WHERE aggregate_id = ? 
  AND aggregate_type = ?
ORDER BY aggregate_version ASC;
```

**Index Used**: `idx_events_aggregate`

### 2. Load Events from Specific Version

```sql
SELECT event_id, aggregate_id, aggregate_type, aggregate_version, 
       global_sequence, event_type, event_data, metadata, created_at
FROM events 
WHERE aggregate_id = ? 
  AND aggregate_type = ? 
  AND aggregate_version >= ?
ORDER BY aggregate_version ASC;
```

**Index Used**: `idx_events_aggregate_version`

### 3. Stream All Events from Sequence

```sql
SELECT event_id, aggregate_id, aggregate_type, aggregate_version, 
       global_sequence, event_type, event_data, metadata, created_at
FROM events 
WHERE global_sequence >= ?
ORDER BY global_sequence ASC;
```

**Index Used**: `idx_events_global_sequence`

### 4. Get Aggregate Version

```sql
SELECT COALESCE(MAX(aggregate_version), 0) as version
FROM events 
WHERE aggregate_id = ? 
  AND aggregate_type = ?;
```

**Index Used**: `idx_events_aggregate`

### 5. Stream Events by Type

```sql
SELECT event_id, aggregate_id, aggregate_type, aggregate_version, 
       global_sequence, event_type, event_data, metadata, created_at
FROM events 
WHERE event_type IN (?, ?, ?)
ORDER BY global_sequence ASC;
```

**Index Used**: `idx_events_type` + `idx_events_global_sequence`

### 6. Stream Events by Time Range

```sql
SELECT event_id, aggregate_id, aggregate_type, aggregate_version, 
       global_sequence, event_type, event_data, metadata, created_at
FROM events 
WHERE created_at BETWEEN ? AND ?
ORDER BY created_at ASC;
```

**Index Used**: `idx_events_created_at`

## Performance Considerations

### 1. Index Usage

- **Primary Queries**: Always use indexed columns in WHERE clauses
- **Composite Indexes**: Order columns by selectivity (most selective first)
- **Covering Indexes**: Consider including frequently selected columns

### 2. Partitioning Strategy

For high-volume systems, consider table partitioning:

```sql
-- Partition by aggregate_type
CREATE TABLE events_account PARTITION OF events 
FOR VALUES IN ('Account');

CREATE TABLE events_order PARTITION OF events 
FOR VALUES IN ('Order');

-- Or partition by time
CREATE TABLE events_2025_01 PARTITION OF events 
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

### 3. Maintenance

```sql
-- Regular statistics update
ANALYZE events;

-- Vacuum for PostgreSQL
VACUUM ANALYZE events;

-- Monitor index usage
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch 
FROM pg_stat_user_indexes 
WHERE tablename = 'events';
```

## Migration Scripts

### Version 1.0.0 - Initial Schema

```sql
-- Initial events table
CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    global_sequence BIGSERIAL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(aggregate_id, aggregate_version)
);

-- Essential indexes
CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);
CREATE INDEX idx_events_global_sequence ON events(global_sequence);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_created_at ON events(created_at);
```

### Future Migrations

```sql
-- Version 1.1.0 - Add snapshot support
CREATE TABLE snapshots (
    snapshot_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    snapshot_type VARCHAR(255) NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(aggregate_id, version)
);

-- Version 1.2.0 - Add metadata indexes
CREATE INDEX idx_events_metadata_correlation 
ON events USING GIN ((metadata->>'correlationId'));
```

## Backup and Recovery

### 1. Backup Strategy

```sql
-- Full backup
pg_dump -h localhost -U firefly -d firefly_eventstore > backup_full.sql

-- Events table only
pg_dump -h localhost -U firefly -d firefly_eventstore -t events > backup_events.sql

-- Incremental backup (events after specific sequence)
COPY (
    SELECT * FROM events 
    WHERE global_sequence > 1000000
) TO '/backup/events_incremental.csv' WITH CSV HEADER;
```

### 2. Point-in-Time Recovery

```sql
-- Restore to specific global sequence
CREATE TABLE events_restored AS 
SELECT * FROM events 
WHERE global_sequence <= 1000000;
```

## Monitoring Queries

### 1. Event Store Statistics

```sql
SELECT 
    COUNT(*) as total_events,
    COUNT(DISTINCT aggregate_id) as total_aggregates,
    MAX(global_sequence) as current_global_sequence,
    COUNT(DISTINCT event_type) as unique_event_types,
    COUNT(DISTINCT aggregate_type) as unique_aggregate_types
FROM events;
```

### 2. Event Type Distribution

```sql
SELECT 
    event_type,
    COUNT(*) as event_count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM events), 2) as percentage
FROM events 
GROUP BY event_type 
ORDER BY event_count DESC;
```

### 3. Aggregate Activity

```sql
SELECT 
    aggregate_type,
    COUNT(DISTINCT aggregate_id) as aggregate_count,
    AVG(aggregate_version) as avg_version,
    MAX(aggregate_version) as max_version
FROM events 
GROUP BY aggregate_type;
```

This schema provides a solid foundation for event sourcing with excellent performance characteristics and scalability options.