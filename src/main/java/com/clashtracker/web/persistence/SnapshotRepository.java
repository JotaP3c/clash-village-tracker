package com.clashtracker.web.persistence;

import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Persists and reads village snapshots in SQL Server using {@link JdbcTemplate}. Writes are
 * transactional and idempotent: re-importing the same source (same player tag, observed date and
 * SHA-256) does not create a duplicate. History is append-only; an import never rewrites a row.
 */
public class SnapshotRepository {

    private final JdbcTemplate jdbc;

    public SnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Imports a normalized snapshot. Returns the stored snapshot id. If an identical snapshot
     * already exists (same identity), returns the existing id without inserting again.
     */
    @Transactional
    public long importSnapshot(VillageSnapshot snapshot) {
        Optional<Long> existing = findIdByIdentity(
                snapshot.playerTag(), snapshot.observedAt(), snapshot.sourceSha256());
        if (existing.isPresent()) {
            return existing.get();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO village_snapshot
                        (player_tag, player_name, town_hall_level, observed_date, observed_precision,
                         source_type, source_path, source_sha256, gold_pass_boost,
                         permanent_builders, goblin_temporary)
                    VALUES (?, ?, ?, ?, 'DATE', ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, snapshot.playerTag());
            ps.setString(2, snapshot.playerName());
            ps.setInt(3, snapshot.townHallLevel());
            ps.setDate(4, java.sql.Date.valueOf(snapshot.observedAt()));
            ps.setString(5, "MANUAL");
            ps.setString(6, snapshot.sourcePath());
            ps.setString(7, snapshot.sourceSha256());
            ps.setBigDecimal(8, snapshot.snapshotGoldPassBoost());
            ps.setInt(9, snapshot.permanentBuilderCapacity());
            ps.setBoolean(10, snapshot.goblinBuilderTemporary());
            return ps;
        }, keyHolder);

        long snapshotId = keyHolder.getKey().longValue();
        insertEntities(snapshotId, snapshot.entities());
        return snapshotId;
    }

    private void insertEntities(long snapshotId, List<VillageEntity> entities) {
        String sql = """
                INSERT INTO village_entity_observation
                    (snapshot_id, entity_key, display_name, category, quantity, current_level,
                     max_level, count_at_max, max_supercharge, sc1_completed, sc2_completed,
                     sc3_completed, representation, confidence, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(sql, entities, entities.size(), (ps, entity) -> {
            ps.setLong(1, snapshotId);
            ps.setString(2, entity.entityKey());
            ps.setString(3, entity.displayNamePtBr() != null && !entity.displayNamePtBr().isBlank()
                    ? entity.displayNamePtBr() : entity.rawName());
            ps.setString(4, entity.rawCategory());
            ps.setInt(5, entity.quantity());
            setNullableInt(ps, 6, entity.currentLevel());
            setNullableInt(ps, 7, entity.maxLevel());
            ps.setInt(8, entity.countAtMax());
            setNullableInt(ps, 9, entity.maxSupercharge());
            setNullableInt(ps, 10, entity.sc1Completed());
            setNullableInt(ps, 11, entity.sc2Completed());
            setNullableInt(ps, 12, entity.sc3Completed());
            ps.setString(13, entity.representation() == null ? null : entity.representation().name());
            ps.setString(14, entity.confidence());
            ps.setString(15, entity.notes());
        });
    }

    public Optional<Long> findIdByIdentity(String playerTag, java.time.LocalDate observedDate, String sha256) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM village_snapshot
                WHERE player_tag = ? AND observed_date = ? AND source_sha256 = ?
                """,
                (rs, rowNum) -> rs.getLong("id"),
                playerTag, java.sql.Date.valueOf(observedDate), sha256);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public List<PersistedSnapshotSummary> listSnapshots(String playerTag) {
        return jdbc.query("""
                SELECT s.id, s.player_tag, s.player_name, s.town_hall_level, s.observed_date,
                       s.observed_precision, s.source_type, s.source_sha256, s.imported_at,
                       (SELECT COUNT(*) FROM village_entity_observation e WHERE e.snapshot_id = s.id) AS entity_count
                FROM village_snapshot s
                WHERE s.player_tag = ?
                ORDER BY s.observed_date DESC, s.imported_at DESC
                """,
                (rs, rowNum) -> new PersistedSnapshotSummary(
                        rs.getLong("id"),
                        rs.getString("player_tag"),
                        rs.getString("player_name"),
                        rs.getInt("town_hall_level"),
                        rs.getDate("observed_date").toLocalDate(),
                        rs.getString("observed_precision"),
                        rs.getString("source_type"),
                        rs.getString("source_sha256"),
                        rs.getInt("entity_count"),
                        rs.getTimestamp("imported_at").toInstant().atOffset(ZoneOffset.UTC)),
                playerTag);
    }

    public int countSnapshots() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM village_snapshot", Integer.class);
        return count == null ? 0 : count;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
