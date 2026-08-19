package com.github.fmaiassistent.football;

/** Transport-free wage-health summary. */
public record SquadWageHealth(long wageBillWeekly, Long payrollBudget, Double usedFraction) {
    public boolean overBudget() {
        return payrollBudget != null && payrollBudget > 0 && wageBillWeekly > payrollBudget;
    }
}
