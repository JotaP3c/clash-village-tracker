-- Clash Village Tracker - initial persistence schema (SQL Server).
-- Scope of E2: store imported village snapshots and their per-entity observations with provenance
-- and temporal precision, plus a registry of official-API collections (fetch-player evidence).
-- History is append-only: an import never rewrites a previous snapshot.

-- A village snapshot that was imported into the database. The same source file can be imported
-- again idempotently (guarded by source_sha256 + observed_date), while the import instant is kept
-- separate from the observation date so a historical date is never presented as "now".
CREATE TABLE village_snapshot (
    id                 BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_village_snapshot PRIMARY KEY,
    player_tag         NVARCHAR(20)  NOT NULL,
    player_name        NVARCHAR(100) NULL,
    town_hall_level    INT           NOT NULL,
    observed_date      DATE          NOT NULL,          -- DATE precision; time of day is unknown
    observed_precision NVARCHAR(10)  NOT NULL CONSTRAINT df_snapshot_precision DEFAULT 'DATE',
    source_type        NVARCHAR(20)  NOT NULL,          -- MANUAL, GAME_EXPORT, OFFICIAL_API, ...
    source_path        NVARCHAR(400) NULL,
    source_sha256      CHAR(64)      NOT NULL,
    gold_pass_boost    DECIMAL(5,4)  NOT NULL CONSTRAINT df_snapshot_boost DEFAULT 0,
    permanent_builders INT           NOT NULL,
    goblin_temporary   BIT           NOT NULL CONSTRAINT df_snapshot_goblin DEFAULT 1,
    imported_at        DATETIME2(3)  NOT NULL CONSTRAINT df_snapshot_imported DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_village_snapshot_identity UNIQUE (player_tag, observed_date, source_sha256)
);

CREATE INDEX ix_village_snapshot_player_date
    ON village_snapshot (player_tag, observed_date DESC);

-- One observed entity row inside a snapshot (aggregated when the source does not expose instances).
-- NULL level columns mean UNKNOWN and must never be treated as zero.
CREATE TABLE village_entity_observation (
    id                 BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_entity_observation PRIMARY KEY,
    snapshot_id        BIGINT        NOT NULL,
    entity_key         NVARCHAR(100) NOT NULL,
    display_name       NVARCHAR(150) NULL,
    category           NVARCHAR(60)  NOT NULL,
    quantity           INT           NOT NULL,
    current_level      INT           NULL,
    max_level          INT           NULL,
    count_at_max       INT           NOT NULL CONSTRAINT df_entity_count_at_max DEFAULT 0,
    max_supercharge    INT           NULL,
    sc1_completed      INT           NULL,
    sc2_completed      INT           NULL,
    sc3_completed      INT           NULL,
    representation     NVARCHAR(12)  NULL,              -- AGGREGATED / INDIVIDUAL
    confidence         NVARCHAR(30)  NULL,
    notes              NVARCHAR(MAX) NULL,
    CONSTRAINT fk_entity_observation_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES village_snapshot (id) ON DELETE CASCADE
);

CREATE INDEX ix_entity_observation_snapshot
    ON village_entity_observation (snapshot_id);

CREATE INDEX ix_entity_observation_category
    ON village_entity_observation (snapshot_id, category);

-- Registry of official Supercell API collections produced by the fetch-player command. The raw
-- bytes stay on disk (response.bin); here we keep an auditable index with the hash. Two identical
-- payloads collected on different dates are distinct rows (identity is the collection_id, not hash).
CREATE TABLE official_api_collection (
    id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_official_collection PRIMARY KEY,
    collection_id    NVARCHAR(80)  NOT NULL,            -- UUID-based directory name of the capture
    requested_tag    NVARCHAR(20)  NOT NULL,
    returned_tag     NVARCHAR(20)  NULL,
    player_name      NVARCHAR(100) NULL,
    town_hall_level  INT           NULL,
    http_status      INT           NULL,
    outcome          NVARCHAR(20)  NOT NULL,            -- SUCCESS / FAILURE classification
    body_sha256      CHAR(64)      NULL,
    collected_at     DATETIME2(3)  NOT NULL,
    artifact_path    NVARCHAR(400) NULL,                -- path to player-profile.json when valid
    registered_at    DATETIME2(3)  NOT NULL CONSTRAINT df_collection_registered DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_official_collection_id UNIQUE (collection_id)
);

CREATE INDEX ix_official_collection_tag_date
    ON official_api_collection (requested_tag, collected_at DESC);
