package com.clashtracker.web.persistence;

import com.clashtracker.application.SupercellCaptureMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.sql.Types;

/**
 * Auditable index of official-API collections. The raw response bytes remain on disk
 * ({@code response.bin}); this table records the metadata so collections can be listed and
 * compared by date. Registration is idempotent on the collection id: re-reading the same capture
 * does not duplicate it, and two identical payloads collected at different times stay distinct
 * (their collection ids differ).
 *
 * <p>Created only when persistence is enabled ({@code tracker.db.enabled=true}).
 */
public class OfficialCollectionRepository {

    private final JdbcTemplate jdbc;

    public OfficialCollectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void registerIfAbsent(SupercellCaptureMetadata metadata, String artifactPath) {
        String collectionId = metadata.collectionId() == null ? null : metadata.collectionId().toString();
        if (collectionId == null) {
            return;
        }
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM official_api_collection WHERE collection_id = ?",
                Integer.class, collectionId);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO official_api_collection
                        (collection_id, requested_tag, returned_tag, player_name, town_hall_level,
                         http_status, outcome, body_sha256, collected_at, artifact_path)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setString(1, collectionId);
            ps.setString(2, metadata.requestedPlayerTag());
            ps.setString(3, metadata.returnedPlayerTag());
            ps.setString(4, null);
            ps.setNull(5, Types.INTEGER);
            ps.setInt(6, metadata.httpStatus());
            ps.setString(7, metadata.outcome() == null ? "UNKNOWN" : metadata.outcome());
            ps.setString(8, metadata.bodySha256());
            ps.setTimestamp(9, metadata.receivedAt() == null
                    ? new Timestamp(System.currentTimeMillis())
                    : Timestamp.from(metadata.receivedAt()));
            ps.setString(10, artifactPath);
            return ps;
        });
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM official_api_collection", Integer.class);
        return count == null ? 0 : count;
    }
}
