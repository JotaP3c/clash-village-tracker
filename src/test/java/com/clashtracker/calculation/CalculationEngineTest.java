package com.clashtracker.calculation;

import com.clashtracker.TestFixtures;
import com.clashtracker.domain.ActiveUpgrade;
import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.CatalogStep;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.QueueKind;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculationEngineTest {
    private final CalculationEngine engine = new CalculationEngine();

    @Test
    void enumeratesEveryExistingLevelTransition() {
        List<VillageEntity> entities = List.of(
                TestFixtures.entity("Bebê Dragão", "Tropas", 1, 9, 12, 0, 0, 0, 0, 0),
                TestFixtures.entity("Torre do Mago", "Defesas", 1, 13, 17, 0, 0, 0, 0, 0),
                TestFixtures.entity("Rainha Arqueira", "Heróis", 1, 105, 110, 0, 0, 0, 0, 0));
        List<CatalogStep> steps = new ArrayList<>();
        addRange(steps, "Bebê Dragão", "Tropas", 9, 12);
        addRange(steps, "Torre do Mago", "Defesas", 13, 17);
        addRange(steps, "Rainha Arqueira", "Heróis", 105, 110);

        CalculationResult result = engine.calculate(TestFixtures.snapshot(entities, List.of()),
                TestFixtures.catalog(steps), BigDecimal.ZERO, List.of());

        assertEquals(3, count(result, "Bebê Dragão"));
        assertEquals(4, count(result, "Torre do Mago"));
        assertEquals(5, count(result, "Rainha Arqueira"));
        assertEquals(12, result.steps().size());
    }

    @Test
    void activeThreeToFourKeepsCompletedLevelAndDoesNotDiscountObservedBalance() {
        VillageEntity smasher = TestFixtures.entity("Smasher", "Guardiões", 1, 3, 5, 0, 0, 0, 0, 0);
        ActiveUpgrade active = TestFixtures.job("Smasher", QueueKind.BUILDER, 1, 3, 4, 800L, "Guardião");
        CatalogStep step = TestFixtures.step("Smasher", "Guardiões", UpgradeType.PERMANENT,
                3, 4, 1, 1_000L);

        CalculationResult result = engine.calculate(TestFixtures.snapshot(List.of(smasher), List.of(active)),
                TestFixtures.catalog(List.of(step)), new BigDecimal("0.20"), List.of());

        assertEquals(3, smasher.currentLevel());
        assertEquals(800L, result.steps().get(0).scenarioKnownSeconds());
        assertEquals(800L, result.durations().observedRemainingSeconds());
        assertEquals(0L, result.durations().discountedFutureKnownSeconds());
    }

    @Test
    void appliesZeroFifteenAndTwentyPercentOnlyToFutureBaseDuration() {
        VillageEntity entity = TestFixtures.entity("Canhão", "Defesas", 1, 1, 2, 0, 0, 0, 0, 0);
        GameCatalog catalog = TestFixtures.catalog(List.of(
                TestFixtures.step("Canhão", "Defesas", UpgradeType.PERMANENT, 1, 2, 1, 1_000L)));
        VillageSnapshot snapshot = TestFixtures.snapshot(List.of(entity), List.of());

        assertEquals(1_000L, calculate(snapshot, catalog, "0").durations().scenarioKnownSeconds());
        assertEquals(850L, calculate(snapshot, catalog, "0.15").durations().scenarioKnownSeconds());
        assertEquals(800L, calculate(snapshot, catalog, "0.20").durations().scenarioKnownSeconds());
    }

    @Test
    void nullDurationProducesKnownSubtotalButNoExactTotal() {
        VillageEntity entity = TestFixtures.entity("Torre", "Defesas", 1, 1, 3, 0, 0, 0, 0, 0);
        List<CatalogStep> steps = List.of(
                TestFixtures.step("Torre", "Defesas", UpgradeType.PERMANENT, 1, 2, 1, 1_000L),
                TestFixtures.step("Torre", "Defesas", UpgradeType.PERMANENT, 2, 3, 1, null));

        CalculationResult result = engine.calculate(TestFixtures.snapshot(List.of(entity), List.of()),
                TestFixtures.catalog(steps), BigDecimal.ZERO, List.of());

        assertEquals(1_000L, result.durations().baseKnownSeconds());
        assertFalse(result.durations().exactTotalAvailable());
        assertEquals(1, result.durations().missingStepKeys().size());
        assertEquals(1, result.progress().unknownUpgradeStepCount());
    }

    @Test
    void nullBaseRemainsNonExactEvenWhenObservedTimerExists() {
        VillageEntity smasher = TestFixtures.entity("Smasher", "Guardiões", 1, 3, 4, 0, 0, 0, 0, 0);
        ActiveUpgrade active = TestFixtures.job("Smasher", QueueKind.BUILDER, 1, 3, 4, 849_600L, "Guardião");
        CatalogStep step = TestFixtures.step("Smasher", "Guardiões", UpgradeType.PERMANENT,
                3, 4, 1, null);

        CalculationResult result = engine.calculate(TestFixtures.snapshot(List.of(smasher), List.of(active)),
                TestFixtures.catalog(List.of(step)), new BigDecimal("0.20"), List.of());

        assertEquals(849_600L, result.durations().observedRemainingSeconds());
        assertEquals(849_600L, result.durations().scenarioKnownSeconds());
        assertFalse(result.durations().exactTotalAvailable());
    }

    @Test
    void keepsBuilderLaboratoryAndPetWorkIndependentAndGoblinTemporary() {
        VillageEntity defense = TestFixtures.entity("Canhão", "Defesas", 6, 1, 2, 0, 0, 0, 0, 0);
        VillageEntity spell = TestFixtures.entity("Veneno", "Feitiços", 1, 1, 2, 0, 0, 0, 0, 0);
        VillageEntity pet = TestFixtures.entity("Diggy", "Pets", 1, 1, 2, 0, 0, 0, 0, 0);
        List<CatalogStep> steps = List.of(
                TestFixtures.step("Canhão", "Defesas", UpgradeType.PERMANENT, 1, 2, 6, 1_000L),
                TestFixtures.step("Veneno", "Feitiços", UpgradeType.PERMANENT, 1, 2, 1, 3_000L),
                TestFixtures.step("Diggy", "Pets", UpgradeType.PERMANENT, 1, 2, 1, 2_000L));

        CalculationResult result = engine.calculate(TestFixtures.snapshot(List.of(defense, spell, pet), List.of()),
                TestFixtures.catalog(steps), BigDecimal.ZERO, List.of());

        assertEquals(6_000L, result.workloads().builderSeconds());
        assertEquals(3_000L, result.workloads().laboratorySeconds());
        assertEquals(2_000L, result.workloads().petSeconds());
        assertEquals(6, result.workloads().permanentBuilderCapacity());
        assertTrue(result.workloads().goblinBuilderTemporary());
    }

    @Test
    void plansAggregatedSuperchargesOnlyForEligibleEntity() {
        VillageEntity eligible = TestFixtures.entity("Defesa Aérea", "Defesas", 4, 16, 16, 4, 2, 1, 0, 0);
        List<CatalogStep> steps = List.of(
                TestFixtures.step("Defesa Aérea", "Sobrecargas", UpgradeType.SUPERCHARGE, 0, 1, 3, 100L),
                TestFixtures.step("Defesa Aérea", "Sobrecargas", UpgradeType.SUPERCHARGE, 1, 2, 4, 200L));
        CalculationResult eligibleResult = engine.calculate(TestFixtures.snapshot(List.of(eligible), List.of()),
                TestFixtures.catalog(steps), BigDecimal.ZERO, List.of());
        assertEquals(2, eligibleResult.steps().size());
        assertEquals(3, eligibleResult.steps().get(0).count());
        assertEquals(4, eligibleResult.steps().get(1).count());

        VillageEntity ineligible = TestFixtures.entity("Defesa Aérea", "Defesas", 4, 16, 16, 4, 0, 0, 0, 0);
        CalculationResult ineligibleResult = engine.calculate(TestFixtures.snapshot(List.of(ineligible), List.of()),
                TestFixtures.catalog(steps), BigDecimal.ZERO, List.of());
        assertTrue(ineligibleResult.steps().isEmpty());
    }

    @Test
    void newMaximumRemovesFullWithoutRewritingHistoricalSnapshot() {
        VillageEntity historicalEntity = TestFixtures.entity("Canhão", "Defesas", 1, 10, 10, 1, 0, 0, 0, 0);
        VillageSnapshot historical = TestFixtures.snapshot(List.of(historicalEntity), List.of());
        GameCatalog historicalCatalog = TestFixtures.catalog(List.of(
                TestFixtures.step("Canhão", "Defesas", UpgradeType.PERMANENT, 9, 10, 0, 100L)));
        assertTrue(engine.calculate(historical, historicalCatalog, BigDecimal.ZERO, List.of()).progress().fullCertified());

        VillageEntity currentEntity = TestFixtures.entity("Canhão", "Defesas", 1, 10, 11, 0, 0, 0, 0, 0);
        GameCatalog newCatalog = TestFixtures.catalog(List.of(
                TestFixtures.step("Canhão", "Defesas", UpgradeType.PERMANENT, 10, 11, 1, 100L)));
        assertFalse(engine.calculate(TestFixtures.snapshot(List.of(currentEntity), List.of()), newCatalog,
                BigDecimal.ZERO, List.of()).progress().fullCertified());
        assertEquals(10, historical.entities().get(0).maxLevel());
        assertEquals(10, historical.entities().get(0).currentLevel());
    }

    @Test
    void hundredPercentKnownProgressWithIncompleteCoverageIsNotFull() {
        VillageEntity known = TestFixtures.entity("Canhão", "Defesas", 1, 10, 10, 1, 0, 0, 0, 0);
        VillageEntity unknown = TestFixtures.entity("Mistério", "Defesas Artesanais", 1, null, null, 0, 0, 0, 0, 0);
        GameCatalog catalog = TestFixtures.catalog(List.of(
                TestFixtures.step("Canhão", "Defesas", UpgradeType.PERMANENT, 9, 10, 0, 100L)));
        CalculationResult result = engine.calculate(TestFixtures.snapshot(List.of(known, unknown), List.of()),
                catalog, BigDecimal.ZERO, List.of());

        assertEquals(new BigDecimal("1.000000"), result.progress().overallCataloguedProgress());
        assertEquals(new BigDecimal("0.500000"), result.progress().coverage());
        assertFalse(result.progress().fullCertified());
    }

    private CalculationResult calculate(VillageSnapshot snapshot, GameCatalog catalog, String boost) {
        return engine.calculate(snapshot, catalog, new BigDecimal(boost), List.of());
    }

    private static long count(CalculationResult result, String name) {
        return result.steps().stream().filter(step -> step.rawItemName().equals(name)).count();
    }

    private static void addRange(List<CatalogStep> steps, String name, String category, int from, int max) {
        for (int level = from; level < max; level++) {
            steps.add(TestFixtures.step(name, category, UpgradeType.PERMANENT, level, level + 1, 1, 100L));
        }
    }
}
