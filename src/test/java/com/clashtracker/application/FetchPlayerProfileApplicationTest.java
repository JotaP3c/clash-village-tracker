package com.clashtracker.application;

import com.clashtracker.integration.supercell.SupercellPlayerProfile;
import com.clashtracker.util.JsonSupport;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchPlayerProfileApplicationTest {
    @TempDir
    Path temp;

    @Test
    void persistsTimestampedOfficialObservationWithoutInventingVillageFields() throws Exception {
        Instant observedAt = Instant.parse("2026-10-20T12:34:56Z");
        SupercellPlayerProfile profile = new SupercellPlayerProfile(
                "#U8J89QP2", "jaoo", 18, 250,
                JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.arrayNode());
        FetchPlayerProfileApplication application = new FetchPlayerProfileApplication(
                tag -> profile, JsonSupport.createMapper(), Clock.fixed(observedAt, ZoneOffset.UTC));

        FetchPlayerProfileResult result = application.fetch("#U8J89QP2", temp);

        assertEquals(observedAt, result.observation().observedAt());
        assertTrue(Files.isRegularFile(result.artifact()));
        assertTrue(result.artifact().getFileName().toString().startsWith("player-U8J89QP2-"));
        String json = Files.readString(result.artifact());
        assertTrue(json.contains("OFFICIAL_SUPERCELL_API"));
        assertTrue(json.contains("townHallLevel"));
        assertFalse(json.contains("buildings"));
        assertFalse(json.contains("supercharges"));
        assertFalse(json.contains("queues"));
    }
}
