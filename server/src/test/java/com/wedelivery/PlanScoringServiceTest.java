package com.wedelivery;

import com.wedelivery.dto.PlanOptionDto;
import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.service.PlanScoringService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanScoringServiceTest {

    private static PlanOptionDto plan(String code, int minutes, String price, int units, VehicleType type) {
        return PlanOptionDto.builder()
                .vehicleCode(code)
                .estimatedMinutes(minutes)
                .finalPrice(new BigDecimal(price))
                .availableUnits(units)
                .vehicleType(type)
                .build();
    }

    /** Fixture: fast but pricey drone, mid robot, slow but cheap off-peak. */
    private static List<PlanOptionDto> threePlans() {
        return Arrays.asList(
                plan("DRN-001", 20, "30.00", 2, VehicleType.DRONE),
                plan("RBT-004", 45, "15.00", 3, VehicleType.ROBOT),
                plan("RBT-004", 105, "7.00", 3, VehicleType.ROBOT)
        );
    }

    @Test
    void fastestIntentPutsTheDroneFirst() {
        List<PlanScoringService.Scored> ranked =
                PlanScoringService.rank(threePlans(), PlanScoringService.fastest());
        assertEquals("DRN-001", ranked.get(0).getPlan().getVehicleCode());
    }

    @Test
    void cheapestIntentPutsTheOffPeakPlanFirst() {
        List<PlanScoringService.Scored> ranked =
                PlanScoringService.rank(threePlans(), PlanScoringService.cheapest());
        assertEquals(105, ranked.get(0).getPlan().getEstimatedMinutes());
    }

    @Test
    void balancedIntentPrefersTheMiddlePlan() {
        List<PlanScoringService.Scored> ranked =
                PlanScoringService.rank(threePlans(), PlanScoringService.balanced());
        assertEquals(45, ranked.get(0).getPlan().getEstimatedMinutes());
    }

    @Test
    void aDeadlineFlipsTheWinnerToTheFastestPlan() {
        PlanScoringService.Intent wantsItIn30Min =
                new PlanScoringService.Intent(0.45, 0.45, 0.10, 30);

        List<PlanScoringService.Scored> ranked =
                PlanScoringService.rank(threePlans(), wantsItIn30Min);

        assertEquals("DRN-001", ranked.get(0).getPlan().getVehicleCode());
        assertTrue(ranked.get(0).getReason().contains("before your deadline"));
        assertTrue(ranked.get(2).getReason().contains("misses your"));
    }

    @Test
    void scarcePlanLosesWhenReliabilityMatters() {
        // Same price, one minute apart: the only difference is fleet depth.
        List<PlanOptionDto> oneLeft = Arrays.asList(
                plan("DRN-001", 20, "30.00", 1, VehicleType.DRONE),
                plan("RBT-004", 21, "30.00", 5, VehicleType.ROBOT)
        );

        PlanScoringService.Intent reliabilityFirst =
                new PlanScoringService.Intent(0.2, 0.2, 0.6, null);

        List<PlanScoringService.Scored> ranked =
                PlanScoringService.rank(oneLeft, reliabilityFirst);

        assertEquals("RBT-004", ranked.get(0).getPlan().getVehicleCode());
        assertTrue(ranked.get(1).getReason().contains("only 1 vehicle left"));
    }

    @Test
    void aScarcePlanStillWinsWhenItIsMuchFasterAndCheaper() {
        // Risk weight is only 0.10 by default: a big time or price gap should
        // outweigh "only one vehicle left". This is a deliberate trade-off.
        List<PlanOptionDto> oneLeft = Arrays.asList(
                plan("DRN-001", 20, "30.00", 1, VehicleType.DRONE),
                plan("RBT-004", 25, "31.00", 5, VehicleType.ROBOT)
        );

        List<PlanScoringService.Scored> ranked =
                PlanScoringService.rank(oneLeft, PlanScoringService.fastest());

        assertEquals("DRN-001", ranked.get(0).getPlan().getVehicleCode());
    }

    @Test
    void scoresAreContinuousNotJustThreeBuckets() {
        List<PlanScoringService.Scored> ranked =
                PlanScoringService.rank(threePlans(), PlanScoringService.balanced());

        int a = ranked.get(0).getScore();
        int b = ranked.get(1).getScore();
        int c = ranked.get(2).getScore();
        assertTrue(a >= b && b >= c, "scores must be ordered");
        assertTrue(a != b && b != c, "scores must not collapse into ties");
    }

    @Test
    void emptyAndSingleInputsAreSafe() {
        assertTrue(PlanScoringService.rank(Collections.emptyList(), PlanScoringService.balanced()).isEmpty());
        assertEquals(1, PlanScoringService.rank(
                Collections.singletonList(threePlans().get(0)),
                PlanScoringService.balanced()).size());
    }

    @Test
    void priorityFieldMapsOntoAnIntent() {
        assertTrue(PlanScoringService.fromPriority("EXPRESS").wTime
                > PlanScoringService.fromPriority("STANDARD").wTime);
        assertEquals(PlanScoringService.balanced().wTime,
                PlanScoringService.fromPriority(null).wTime, 1e-9);
    }
}
