package com.wedelivery.service;

import com.wedelivery.dto.PlanOptionDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Ranks dispatch plans by how well they match what THIS user cares about.
 *
 * PROPOSAL (P1) - not yet wired into RecommendationService.
 * Why this exists: the current contract score is
 * {@code (isFastest ? 50 : 30) + (isCheapest ? 50 : 30)}, which is a label
 * computed by comparing candidates with each other. It says nothing about the
 * user, collapses to only three values (60/80/100), and ties constantly.
 * This class replaces it with a continuous, user-conditioned utility score.
 *
 * Design boundary (deliberate, do not break):
 *   Prices, distances and ETA stay inside RecommendationService - this class
 *   never computes money or time, it only decides how much each dimension
 *   matters. That keeps the contract rule "pricing belongs to the backend"
 *   intact and lets AI influence ranking without ever quoting a number.
 *
 * Pure function: no Spring context, no repository, no clock. Unit-testable.
 */
public final class PlanScoringService {

    private PlanScoringService() {
    }

    /** How much each dimension matters. Weights are normalised to sum to 1. */
    public static final class Intent {
        public final double wTime;
        public final double wCost;
        public final double wRisk;
        /** Minutes the user can wait. null = no constraint. */
        public final Integer deadlineMinutes;

        public Intent(double wTime, double wCost, double wRisk, Integer deadlineMinutes) {
            double sum = wTime + wCost + wRisk;
            if (sum <= 0) {
                throw new IllegalArgumentException("intent weights must sum to a positive value");
            }
            this.wTime = wTime / sum;
            this.wCost = wCost / sum;
            this.wRisk = wRisk / sum;
            this.deadlineMinutes = deadlineMinutes;
        }
    }

    /** A plan plus its utility score and a human-readable reason. */
    public static final class Scored {
        private final PlanOptionDto plan;
        private final double utility;
        private final String reason;

        public Scored(PlanOptionDto plan, double utility, String reason) {
            this.plan = plan;
            this.utility = utility;
            this.reason = reason;
        }

        public PlanOptionDto getPlan() {
            return plan;
        }

        /** 0.0 (worst) .. 1.0 (best). Continuous, not one of three buckets. */
        public double getUtility() {
            return utility;
        }

        /** Contract-compatible 0..100 score. */
        public int getScore() {
            return (int) Math.round(Math.max(0.0, Math.min(1.0, utility)) * 100.0);
        }

        public String getReason() {
            return reason;
        }
    }

    /** Neutral intent: no preference expressed. */
    public static Intent balanced() {
        return new Intent(0.45, 0.45, 0.10, null);
    }

    public static Intent fastest() {
        return new Intent(0.75, 0.15, 0.10, null);
    }

    public static Intent cheapest() {
        return new Intent(0.15, 0.75, 0.10, null);
    }

    /**
     * Maps the existing contract {@code priority} field (STANDARD | EXPRESS)
     * onto an intent. This is the no-AI fallback and is already shippable:
     * the field reaches the backend today but is never read.
     */
    public static Intent fromPriority(String priority) {
        if (priority == null) {
            return balanced();
        }
        switch (priority.trim().toUpperCase()) {
            case "EXPRESS":
                return fastest();
            case "CHEAP":
            case "CHEAPEST":
            case "ECONOMY":
                return cheapest();
            default:
                return balanced();
        }
    }

    /** How heavily a plan is punished for missing the user's deadline. */
    private static final double MAX_DEADLINE_PENALTY = 0.6;

    /**
     * Ranks plans best-first. Empty or single-element input is handled
     * without division by zero (normalisation degenerates to 0).
     */
    public static List<Scored> rank(List<PlanOptionDto> plans, Intent intent) {
        List<Scored> out = new ArrayList<>();
        if (plans == null || plans.isEmpty()) {
            return out;
        }

        int minMinutes = plans.stream().mapToInt(PlanOptionDto::getEstimatedMinutes).min().orElse(0);
        int maxMinutes = plans.stream().mapToInt(PlanOptionDto::getEstimatedMinutes).max().orElse(0);
        BigDecimal minCost = plans.stream()
                .map(p -> nz(p.getFinalPrice()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxCost = plans.stream()
                .map(p -> nz(p.getFinalPrice()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        for (PlanOptionDto p : plans) {
            double normTime = norm(p.getEstimatedMinutes(), minMinutes, maxMinutes);
            double normCost = norm(nz(p.getFinalPrice()), minCost, maxCost);
            double risk = riskOf(p);

            double penalty = 0.0;
            boolean missesDeadline = false;
            if (intent.deadlineMinutes != null && p.getEstimatedMinutes() > intent.deadlineMinutes) {
                missesDeadline = true;
                double over = (p.getEstimatedMinutes() - intent.deadlineMinutes)
                        / (double) Math.max(1, intent.deadlineMinutes);
                penalty = Math.min(MAX_DEADLINE_PENALTY, MAX_DEADLINE_PENALTY * over);
            }

            double utility = 1.0
                    - intent.wTime * normTime
                    - intent.wCost * normCost
                    - intent.wRisk * risk
                    - penalty;

            out.add(new Scored(p, utility, reason(p, intent, minMinutes, minCost, missesDeadline)));
        }

        out.sort((a, b) -> Double.compare(b.getUtility(), a.getUtility()));
        return out;
    }

    /** Single best plan, or empty when nothing is feasible. */
    public static java.util.Optional<Scored> best(List<PlanOptionDto> plans, Intent intent) {
        List<Scored> ranked = rank(plans, intent);
        return ranked.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(ranked.get(0));
    }

    private static double norm(double value, double min, double max) {
        if (max - min < 1e-9) {
            return 0.0;
        }
        return (value - min) / (max - min);
    }

    private static double norm(BigDecimal value, BigDecimal min, BigDecimal max) {
        return norm(value.doubleValue(), min.doubleValue(), max.doubleValue());
    }

    /**
     * Scarcity risk: a plan backed by a single idle vehicle can disappear
     * between quote and order, which is exactly the 409 users hit today.
     */
    private static double riskOf(PlanOptionDto p) {
        Integer units = p.getAvailableUnits();
        if (units == null || units <= 1) {
            return 1.0;
        }
        if (units == 2) {
            return 0.5;
        }
        return 0.0;
    }

    private static String reason(PlanOptionDto p, Intent intent,
                                 int minMinutes, BigDecimal minCost, boolean missesDeadline) {
        boolean isFastest = p.getEstimatedMinutes() == minMinutes;
        boolean isCheapest = nz(p.getFinalPrice()).compareTo(minCost) == 0;

        StringBuilder sb = new StringBuilder();
        if (isFastest && isCheapest) {
            sb.append("Fastest and cheapest for this route");
        } else if (isCheapest) {
            sb.append("Cheapest option, about ").append(p.getEstimatedMinutes() - minMinutes).append(" min slower");
        } else if (isFastest) {
            sb.append("Fastest option, arrives in ").append(p.getEstimatedMinutes()).append(" min");
        } else {
            sb.append("Balanced price and speed");
        }

        if (intent.deadlineMinutes != null) {
            if (missesDeadline) {
                sb.append("; misses your ").append(intent.deadlineMinutes).append("-min deadline");
            } else {
                sb.append("; arrives ").append(intent.deadlineMinutes - p.getEstimatedMinutes())
                        .append(" min before your deadline");
            }
        }
        if (riskOf(p) >= 1.0) {
            sb.append("; only 1 vehicle left");
        }
        return sb.toString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
