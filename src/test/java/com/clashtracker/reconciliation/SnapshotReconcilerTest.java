package com.clashtracker.reconciliation;

import com.clashtracker.TestFixtures;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotReconcilerTest {
    private final SnapshotReconciler reconciler = new SnapshotReconciler();

    @Test
    void missingIncomingFieldPreservesKnownValue() {
        VillageEntity oldEntity = TestFixtures.entity("Canhão", "Defesas", 1, 10, 12, 0, 0, 0, 0, 0);
        VillageEntity incomingEntity = TestFixtures.entity("Canhão", "Defesas", 1, null, null, 0, 0, 0, 0, 0);

        ReconciliationResult result = reconciler.reconcile(
                TestFixtures.snapshot(List.of(oldEntity), List.of()),
                TestFixtures.snapshot(List.of(incomingEntity), List.of()));

        assertEquals(10, result.snapshot().entities().get(0).currentLevel());
        assertEquals(12, result.snapshot().entities().get(0).maxLevel());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("KNOWN_LEVEL_PRESERVED")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("KNOWN_MAX_PRESERVED")));
    }

    @Test
    void levelRegressionCreatesTraceableConflictAndKeepsKnownLevel() {
        VillageEntity oldEntity = TestFixtures.entity("Rainha", "Heróis", 1, 105, 110, 0, 0, 0, 0, 0);
        VillageEntity incomingEntity = TestFixtures.entity("Rainha", "Heróis", 1, 104, 110, 0, 0, 0, 0, 0);

        ReconciliationResult result = reconciler.reconcile(
                TestFixtures.snapshot(List.of(oldEntity), List.of()),
                TestFixtures.snapshot(List.of(incomingEntity), List.of()));

        assertEquals(105, result.snapshot().entities().get(0).currentLevel());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("LEVEL_REGRESSION_CONFLICT")));
    }

    @Test
    void omittedEntityIsPreservedInsteadOfDeleted() {
        VillageEntity oldEntity = TestFixtures.entity("Canhão", "Defesas", 1, 10, 12, 0, 0, 0, 0, 0);
        ReconciliationResult result = reconciler.reconcile(
                TestFixtures.snapshot(List.of(oldEntity), List.of()),
                TestFixtures.snapshot(List.of(), List.of()));

        assertEquals(1, result.snapshot().entities().size());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("KNOWN_ENTITY_PRESERVED")));
    }

    @Test
    void reconciliationDoesNotMutateEitherInput() {
        VillageEntity oldEntity = TestFixtures.entity("Canhão", "Defesas", 1, 10, 12, 0, 0, 0, 0, 0);
        VillageEntity incomingEntity = TestFixtures.entity("Canhão", "Defesas", 1, null, 13, 0, 0, 0, 0, 0);
        VillageSnapshot previous = TestFixtures.snapshot(List.of(oldEntity), List.of());
        VillageSnapshot incoming = TestFixtures.snapshot(List.of(incomingEntity), List.of());

        ReconciliationResult result = reconciler.reconcile(previous, incoming);

        assertNotSame(incoming, result.snapshot());
        assertEquals(10, previous.entities().get(0).currentLevel());
        assertEquals(12, previous.entities().get(0).maxLevel());
        assertEquals(null, incoming.entities().get(0).currentLevel());
        assertEquals(13, incoming.entities().get(0).maxLevel());
    }
}
