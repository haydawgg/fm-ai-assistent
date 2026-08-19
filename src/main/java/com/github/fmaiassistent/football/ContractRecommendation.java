package com.github.fmaiassistent.football;

import java.util.List;

/** Transport-free contract decision. */
public record ContractRecommendation(
        String name,
        String position,
        Integer age,
        int ca,
        int salaryWeekly,
        String contractEnd,
        Long daysUntilExpiry,
        String action,
        List<String> reasons) {
    public ContractRecommendation {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
