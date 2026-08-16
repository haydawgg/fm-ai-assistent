package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SquadAdvice {
    /** Depth, surplus, and best-at-position use a natural rating (typically 15+ in FM). */
    private static final int NATURAL_POSITION = 15;
    /** First XI may fill with an accomplished player (10+); this is looser than {@link #NATURAL_POSITION}. */
    private static final int XI_MIN_POSITION = 10;
    private static final Set<String> COMPARE_ATTRIBUTES = Set.of(
            "PASSING", "TACKLING", "MARKING", "POSITIONING", "PACE", "ACCELERATION",
            "STRENGTH", "STAMINA", "FIRST_TOUCH", "TECHNIQUE", "VISION", "COMPOSURE",
            "DECISIONS", "WORK_RATE", "TEAMWORK", "FINISHING", "DRIBBLING", "HEADING");

    private SquadAdvice() {
    }

    public record SellRow(
            int rank,
            String name,
            Integer age,
            String position,
            int ca,
            int pa,
            int salaryWeekly,
            Long askingPrice,
            String contractEnd,
            int depthAtPosition,
            int caVsFirstTeam,
            String recommendation,
            double sellScore,
            List<String> reasons) {
    }

    public record SquadCompareRow(
            String position,
            String leftName,
            Integer leftCa,
            String rightName,
            Integer rightCa,
            int caGap) {
    }

    public record PlayerCompareMetric(String label, String left, String right, Integer winner) {
    }

    public record XiSlot(String position, String inPossessionRole, String outOfPossessionRole) {
    }

    public record XiPick(
            String position,
            String inPossessionRole,
            String outOfPossessionRole,
            String playerName,
            int positionScore,
            Double roleFit,
            int ca,
            int pa,
            boolean hole) {
    }

    public record WageHealth(long wageBillWeekly, Long payrollBudget, Double usedFraction) {
        public boolean overBudget() {
            return payrollBudget != null && payrollBudget > 0 && wageBillWeekly > payrollBudget;
        }
    }

    public record AcademyRow(
            String name,
            String position,
            Integer age,
            int ca,
            int pa,
            int upside,
            int vsFirstTeam,
            int dualPositions,
            int salaryWeekly,
            String contractEnd) {
    }

    public record ContractRow(
            String name,
            String position,
            Integer age,
            int ca,
            int salaryWeekly,
            String contractEnd,
            Long daysUntilExpiry,
            String action,
            List<String> reasons) {
    }

    public static List<SellRow> sellShortlist(List<PlayerEntity> squad, ClubEntity club) {
        List<PlayerEntity> playable = squad.stream().filter(MarketValuation::hasPlayablePosition).toList();
        int firstTeamCa = firstTeamAverageCa(playable);
        Map<String, Long> depth = depthByPosition(playable);
        List<SellRow> rows = new ArrayList<>();
        for (PlayerEntity player : playable) {
            String position = Positions.bestCode(player);
            int depthAtPosition = depth.getOrDefault(position, 0L).intValue();
            int ca = nz(player.getCa());
            int caGap = ca - firstTeamCa;
            List<String> reasons = new ArrayList<>();
            double score = 0;
            if (depthAtPosition >= 4 && rankInPosition(playable, player, position) >= 3) {
                score += 30;
                reasons.add("surplus_at_" + position);
            }
            if (caGap < -10) {
                score += 20;
                reasons.add("below_first_team_ca");
            }
            int wage = nz(player.getSalaryWeeklyRaw());
            long payroll = club == null || club.getPayrollBudget() == null ? 0L : club.getPayrollBudget();
            if (playable.size() > 0 && payroll > 0 && wage > payroll / playable.size() * 1.5) {
                score += 15;
                reasons.add("high_wage");
            }
            if (Boolean.TRUE.equals(player.getTransferListed())) {
                score += 20;
                reasons.add("already_listed");
            }
            if (Boolean.TRUE.equals(player.getListedForLoan())) {
                score += 10;
                reasons.add("loan_listed");
            }
            Integer age = parseAge(player.getAge());
            if (age != null && age >= 32) {
                score += 10;
                reasons.add("veteran");
            }
            if (contractExpiringSoon(player)) {
                score += 15;
                reasons.add("contract_expiring");
            }
            if (player.getAskingPrice() != null && player.getAskingPrice() > 0 && caGap < 0) {
                score += 8;
                reasons.add("saleable_asset");
            }
            String recommendation;
            if (score >= 45 && (age == null || age >= 23)) {
                recommendation = "sell";
            } else if (score >= 25 && age != null && age <= 22) {
                recommendation = "loan";
            } else if (score >= 35) {
                recommendation = "sell";
            } else {
                recommendation = "keep";
            }
            rows.add(new SellRow(
                    0,
                    player.getName(),
                    age,
                    position,
                    ca,
                    nz(player.getPa()),
                    wage,
                    player.getAskingPrice(),
                    player.getContractEndDate(),
                    depthAtPosition,
                    caGap,
                    recommendation,
                    score,
                    reasons));
        }
        rows.sort(Comparator.comparingDouble(SellRow::sellScore).reversed()
                .thenComparing(SellRow::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        List<SellRow> ranked = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            SellRow row = rows.get(index);
            ranked.add(new SellRow(
                    index + 1, row.name(), row.age(), row.position(), row.ca(), row.pa(),
                    row.salaryWeekly(), row.askingPrice(), row.contractEnd(), row.depthAtPosition(),
                    row.caVsFirstTeam(), row.recommendation(), row.sellScore(), row.reasons()));
        }
        return ranked;
    }

    public static WageHealth wageHealth(List<PlayerEntity> squad, ClubEntity club) {
        List<PlayerEntity> playable = squad.stream().filter(MarketValuation::hasPlayablePosition).toList();
        long bill = playable.stream()
                .map(PlayerEntity::getSalaryWeeklyRaw)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        Long payroll = club == null ? null : club.getPayrollBudget();
        Double used = payroll == null || payroll <= 0 ? null : bill / (double) payroll;
        return new WageHealth(bill, payroll, used);
    }

    public static Long daysUntilExpiry(PlayerEntity player) {
        LocalDate end = parseDate(player.getContractEndDate());
        LocalDate game = parseDate(player.getAgeAsOf());
        if (end == null || game == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(game, end);
    }

    public static List<ContractRow> contractQueue(List<PlayerEntity> squad, ClubEntity club) {
        Map<String, SellRow> byName = new HashMap<>();
        for (SellRow row : sellShortlist(squad, club)) {
            SellRow existing = byName.get(row.name());
            if (existing == null || row.sellScore() > existing.sellScore()) {
                byName.put(row.name(), row);
            }
        }
        List<ContractRow> rows = new ArrayList<>();
        for (PlayerEntity player : squad.stream().filter(MarketValuation::hasPlayablePosition).toList()) {
            Long days = daysUntilExpiry(player);
            if (days == null || days < 0 || days > 180) {
                continue;
            }
            SellRow sell = byName.get(player.getName());
            String recommendation = sell == null ? "keep" : sell.recommendation();
            String action = switch (recommendation) {
                case "sell" -> "sell";
                case "loan" -> "loan";
                default -> "renew";
            };
            List<String> reasons = new ArrayList<>();
            reasons.add("contract_expiring");
            if (sell != null) {
                for (String reason : sell.reasons()) {
                    if (!"contract_expiring".equals(reason)) {
                        reasons.add(reason);
                    }
                }
            }
            rows.add(new ContractRow(
                    player.getName(),
                    Positions.bestCode(player),
                    parseAge(player.getAge()),
                    nz(player.getCa()),
                    nz(player.getSalaryWeeklyRaw()),
                    player.getContractEndDate(),
                    days,
                    action,
                    List.copyOf(reasons)));
        }
        rows.sort(Comparator
                .comparingLong((ContractRow row) -> row.daysUntilExpiry() == null ? Long.MAX_VALUE : row.daysUntilExpiry())
                .thenComparing(Comparator.comparingInt(ContractRow::salaryWeekly).reversed())
                .thenComparing(ContractRow::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return rows;
    }

    public static List<AcademyRow> academy(List<PlayerEntity> squad, int maxAge) {
        List<PlayerEntity> playable = squad.stream().filter(MarketValuation::hasPlayablePosition).toList();
        int firstTeamCa = firstTeamAverageCa(playable);
        List<AcademyRow> rows = new ArrayList<>();
        for (PlayerEntity player : playable) {
            Integer age = parseAge(player.getAge());
            if (age == null || age > maxAge) {
                continue;
            }
            int ca = nz(player.getCa());
            int pa = nz(player.getPa());
            int dual = 0;
            for (String code : PositionCodes.CODES) {
                if (Positions.score(player, code) >= NATURAL_POSITION) {
                    dual++;
                }
            }
            rows.add(new AcademyRow(
                    player.getName(),
                    Positions.bestCode(player),
                    age,
                    ca,
                    pa,
                    Math.max(0, pa - ca),
                    ca - firstTeamCa,
                    dual,
                    nz(player.getSalaryWeeklyRaw()),
                    player.getContractEndDate()));
        }
        rows.sort(Comparator.comparingInt(AcademyRow::pa).reversed()
                .thenComparing(Comparator.comparingInt(AcademyRow::upside).reversed())
                .thenComparing(AcademyRow::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return rows;
    }

    public static Map<String, Object> compareSquads(
            String leftName,
            String rightName,
            List<PlayerEntity> leftSquad,
            List<PlayerEntity> rightSquad) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", squadCard(leftName, leftSquad));
        out.put("right", squadCard(rightName, rightSquad));
        List<SquadCompareRow> positions = new ArrayList<>();
        for (String code : PositionCodes.CODES) {
            PlayerEntity leftBest = bestAt(leftSquad, code);
            PlayerEntity rightBest = bestAt(rightSquad, code);
            int leftCa = leftBest == null ? 0 : nz(leftBest.getCa());
            int rightCa = rightBest == null ? 0 : nz(rightBest.getCa());
            positions.add(new SquadCompareRow(
                    code,
                    leftBest == null ? null : leftBest.getName(),
                    leftBest == null ? null : leftBest.getCa(),
                    rightBest == null ? null : rightBest.getName(),
                    rightBest == null ? null : rightBest.getCa(),
                    leftCa - rightCa));
        }
        out.put("positions", positions);
        return out;
    }

    public static List<PlayerCompareMetric> comparePlayers(PlayerEntity left, PlayerEntity right) {
        List<PlayerCompareMetric> rows = new ArrayList<>();
        rows.add(metric("Name", display(left.getName()), display(right.getName()), null));
        rows.add(metric("Age", display(left.getAge()), display(right.getAge()),
                compareLongs(parseLong(left.getAge()), parseLong(right.getAge()), false)));
        rows.add(metric("Club", display(left.getClub()), display(right.getClub()), null));
        rows.add(metric("Position", Positions.bestCode(left), Positions.bestCode(right), null));
        rows.add(metric("CA", display(left.getCa()), display(right.getCa()),
                compareLongs(asLong(left.getCa()), asLong(right.getCa()), true)));
        rows.add(metric("PA", display(left.getPa()), display(right.getPa()),
                compareLongs(asLong(left.getPa()), asLong(right.getPa()), true)));
        rows.add(metric("Wage", display(left.getSalaryWeeklyRaw()), display(right.getSalaryWeeklyRaw()),
                compareLongs(asLong(left.getSalaryWeeklyRaw()), asLong(right.getSalaryWeeklyRaw()), false)));
        rows.add(metric("Asking", display(left.getAskingPrice()), display(right.getAskingPrice()),
                compareLongs(left.getAskingPrice(), right.getAskingPrice(), false)));
        rows.add(metric("Contract", display(left.getContractEndDate()), display(right.getContractEndDate()), null));
        for (FieldDef field : AttributeDefinitions.VISIBLE_FIELDS) {
            String column = FmAiAssistentTools.columnName(field);
            if (!COMPARE_ATTRIBUTES.contains(column)) {
                continue;
            }
            Object leftValue = left.getColumnValue(column);
            Object rightValue = right.getColumnValue(column);
            rows.add(metric(field.name(), display(leftValue), display(rightValue),
                    compareLongs(asNumber(leftValue), asNumber(rightValue), true)));
        }
        return rows;
    }

    public static List<XiPick> bestXi(
            List<PlayerEntity> squad,
            List<XiSlot> slots,
            java.util.function.BiFunction<PlayerEntity, XiSlot, Double> roleFit) {
        List<PlayerEntity> remaining = new ArrayList<>(squad.stream()
                .filter(MarketValuation::hasPlayablePosition)
                .filter(player -> !Boolean.TRUE.equals(player.getInjured()))
                .toList());
        record IndexedSlot(int index, XiSlot slot) {
        }
        List<IndexedSlot> assignmentOrder = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            assignmentOrder.add(new IndexedSlot(index, slots.get(index)));
        }
        assignmentOrder.sort(Comparator
                .comparingInt((IndexedSlot item) -> slotPositionScarcity(item.slot().position()))
                .thenComparingInt(IndexedSlot::index));

        XiPick[] picks = new XiPick[slots.size()];
        for (IndexedSlot item : assignmentOrder) {
            XiSlot slot = item.slot();
            PlayerEntity best = null;
            double bestScore = -1;
            Double bestFit = null;
            int bestPosition = 0;
            for (PlayerEntity player : remaining) {
                int positionScore = Positions.score(player, slot.position());
                if (positionScore < XI_MIN_POSITION) {
                    continue;
                }
                Double fit = roleFit == null ? null : roleFit.apply(player, slot);
                double score = positionScore * 2 + nz(player.getCa()) + (fit == null ? 0 : fit * 4);
                if (score > bestScore) {
                    bestScore = score;
                    best = player;
                    bestFit = fit;
                    bestPosition = positionScore;
                }
            }
            if (best == null) {
                picks[item.index()] = new XiPick(slot.position(), slot.inPossessionRole(), slot.outOfPossessionRole(),
                        null, 0, null, 0, 0, true);
            } else {
                remaining.remove(best);
                picks[item.index()] = new XiPick(slot.position(), slot.inPossessionRole(), slot.outOfPossessionRole(),
                        best.getName(), bestPosition, bestFit, nz(best.getCa()), nz(best.getPa()), false);
            }
        }
        return List.of(picks);
    }

    private static int slotPositionScarcity(String position) {
        String code;
        try {
            code = Positions.canonicalCode(position);
        } catch (IllegalArgumentException ignored) {
            return 99;
        }
        if (code == null) {
            return 99;
        }
        return switch (code) {
            case "GK" -> 1;
            case "ST" -> 2;
            case "AML", "AMR" -> 3;
            case "DL", "DR" -> 4;
            case "WBL", "WBR" -> 5;
            case "AMC" -> 6;
            case "DMC" -> 7;
            case "ML", "MR" -> 8;
            case "DC" -> 9;
            case "MC" -> 10;
            default -> 99;
        };
    }

    private static Map<String, Object> squadCard(String name, List<PlayerEntity> squad) {
        List<PlayerEntity> playable = squad.stream().filter(MarketValuation::hasPlayablePosition).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", name);
        out.put("players", playable.size());
        out.put("average_ca", average(playable, PlayerEntity::getCa));
        out.put("average_pa", average(playable, PlayerEntity::getPa));
        out.put("wage_bill_weekly", playable.stream().map(PlayerEntity::getSalaryWeeklyRaw).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum());
        out.put("average_age", playable.stream().map(player -> parseAge(player.getAge())).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().orElse(0));
        return out;
    }

    private static PlayerEntity bestAt(List<PlayerEntity> squad, String code) {
        return squad.stream()
                .filter(MarketValuation::hasPlayablePosition)
                .filter(player -> Positions.score(player, code) >= NATURAL_POSITION)
                .max(Comparator.comparingInt(player -> nz(player.getCa())))
                .orElse(null);
    }

    private static Map<String, Long> depthByPosition(List<PlayerEntity> squad) {
        Map<PlayerEntity, String> playerPrimary = new HashMap<>();
        for (PlayerEntity player : squad) {
            String best = Positions.bestCode(player);
            if (best != null) {
                playerPrimary.put(player, best);
            }
        }
        Map<String, Long> depth = new HashMap<>();
        for (String code : PositionCodes.CODES) {
            depth.put(code, playerPrimary.entrySet().stream()
                    .filter(e -> e.getValue().equals(code))
                    .count());
        }
        return depth;
    }

    private static int rankInPosition(List<PlayerEntity> squad, PlayerEntity player, String position) {
        List<PlayerEntity> ranked = squad.stream()
                .filter(candidate -> Positions.score(candidate, position) >= NATURAL_POSITION)
                .sorted(Comparator.comparingInt((PlayerEntity candidate) -> nz(candidate.getCa())).reversed())
                .collect(Collectors.toList());
        return ranked.indexOf(player);
    }

    public static int firstTeamAverageCa(List<PlayerEntity> squad) {
        return (int) Math.round(squad.stream()
                .map(PlayerEntity::getCa)
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .limit(11)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));
    }

    private static boolean contractExpiringSoon(PlayerEntity player) {
        Long days = daysUntilExpiry(player);
        return days != null && days >= 0 && days <= 180;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Integer parseAge(String age) {
        if (age == null || age.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(age);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static PlayerCompareMetric metric(String label, String left, String right, Integer winner) {
        return new PlayerCompareMetric(label, left, right, winner);
    }

    private static Integer compareLongs(Long left, Long right, boolean higherIsBetter) {
        if (left == null || right == null || left.equals(right)) {
            return null;
        }
        boolean leftWins = higherIsBetter ? left > right : left < right;
        return leftWins ? -1 : 1;
    }

    private static String display(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long asLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    static Long asNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int average(List<PlayerEntity> players, java.util.function.Function<PlayerEntity, Integer> getter) {
        return (int) Math.round(players.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue)
                .average().orElse(0));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
