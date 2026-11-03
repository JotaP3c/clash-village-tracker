package com.clashtracker.web.service;

import com.clashtracker.util.JsonSupport;
import com.clashtracker.web.config.TrackerProperties;
import com.clashtracker.web.dto.OfficialProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Opção A reader: it reads the most recent on-disk collection written by fetch-player and
 * never calls the Supercell API nor fabricates data. The database index is optional, so these tests
 * run with no SQL Server.
 */
class OfficialProfileServiceTest {

    @TempDir
    Path collectionsRoot;

    private OfficialProfileService serviceFor(Path collections) {
        TrackerProperties properties = new TrackerProperties();
        properties.setCollectionsPath(collections.toString());
        return new OfficialProfileService(properties, JsonSupport.createMapper(), Optional.empty());
    }

    @Test
    void reportsUnavailableWhenThereIsNoCollectionDirectory() {
        OfficialProfileResponse response =
                serviceFor(collectionsRoot.resolve("does-not-exist")).latest();

        assertFalse(response.available());
        assertNotNull(response.message());
        assertNull(response.profile());
    }

    @Test
    void reportsUnavailableWhenCollectionHasNoMetadata() throws Exception {
        Files.createDirectories(collectionsRoot.resolve("incomplete-collection"));
        OfficialProfileResponse response = serviceFor(collectionsRoot).latest();

        assertFalse(response.available());
        assertTrue(response.message().toLowerCase().contains("metadata"));
    }

    @Test
    void readsTheMostRecentValidCollectionWithoutFabricatingArrays() throws Exception {
        // An older collection and a newer one; the service must pick the newer by modification time.
        writeCollection(collectionsRoot.resolve("older"), "SUCCESS", true);
        Thread.sleep(20);
        Path newer = collectionsRoot.resolve("newer");
        writeCollection(newer, "SUCCESS", true);

        OfficialProfileResponse response = serviceFor(collectionsRoot).latest();

        assertTrue(response.available());
        assertEquals("SUCCESS", response.outcome());
        assertEquals("#U8J89QP2", response.requestedTag());
        assertNotNull(response.profile());
        assertEquals("jaoo", response.profile().name());
        assertEquals(18, response.profile().townHallLevel());
        // Array sizes are counted from the real payload (2 heroes written below), never invented.
        assertEquals(2, response.profile().heroCount());
        assertEquals(0, response.profile().spellCount());
    }

    @Test
    void keepsEvidenceWithoutProfileWhenResponseWasRejected() throws Exception {
        writeCollection(collectionsRoot.resolve("rejected"), "FAILURE", false);

        OfficialProfileResponse response = serviceFor(collectionsRoot).latest();

        assertTrue(response.available());
        assertEquals("FAILURE", response.outcome());
        assertNull(response.profile(), "a rejected response keeps evidence but has no valid profile");
    }

    private static void writeCollection(Path directory, String outcome, boolean withProfile) throws Exception {
        Files.createDirectories(directory);
        String metadata = """
                {
                  "schemaVersion": "1",
                  "collectionId": "%s",
                  "requestedPlayerTag": "#U8J89QP2",
                  "returnedPlayerTag": "#U8J89QP2",
                  "source": "official-api",
                  "origin": "https://api.clashofclans.com",
                  "httpStatus": 200,
                  "receivedAt": "2026-11-03T12:00:00Z",
                  "persistedAt": "2026-11-03T12:00:01Z",
                  "contentType": "application/json",
                  "charset": "utf-8",
                  "contentEncoding": "identity",
                  "bodyLengthBytes": 10,
                  "bodySha256": "abc123",
                  "responseFile": "response.bin",
                  "profileFile": "player-profile.json",
                  "outcome": "%s",
                  "failureCode": null,
                  "failureMessage": null
                }
                """.formatted(java.util.UUID.randomUUID(), outcome);
        Files.writeString(directory.resolve("metadata.json"), metadata, StandardCharsets.UTF_8);
        if (withProfile) {
            String profile = """
                    {
                      "observedAt": "2026-11-03T12:00:00Z",
                      "source": "official-api",
                      "player": {
                        "tag": "#U8J89QP2",
                        "name": "jaoo",
                        "townHallLevel": 18,
                        "expLevel": 300,
                        "heroes": [ {"name":"Barbarian King"}, {"name":"Archer Queen"} ],
                        "troops": [ {"name":"Barbarian"} ],
                        "spells": []
                      }
                    }
                    """;
            Files.writeString(directory.resolve("player-profile.json"), profile, StandardCharsets.UTF_8);
        }
    }
}
