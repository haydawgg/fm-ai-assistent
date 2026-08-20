package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Stable player-workspace column schema shared by grid construction and sorting. */
final class PlayerWorkspaceColumns {
    static final Set<String> NUMERIC_SORT_COLUMNS = Set.of(
            "ID", "CLUB_ID", "PLAYING_CLUB_ID", "CURRENT_REPUTATION", "HOME_REPUTATION", "WORLD_REPUTATION",
            "CA", "PA", "ASKING_PRICE", "ASKING_PRICE_RAW", "SALARY_PA", "SALARY_WEEKLY_RAW", "AGE", "HEIGHT_CM",
            "APPEARANCES", "STARTS", "MINUTES", "GOALS", "ASSISTS", "AVERAGE_RATING",
            "REPUTATION", "TRANSFER_BUDGET", "PAYROLL_BUDGET",
            "GOALKEEPER", "DEFENDER_LEFT", "DEFENDER_CENTRAL", "DEFENDER_RIGHT", "WING_BACK_LEFT",
            "DEFENSIVE_MIDFIELDER", "WING_BACK_RIGHT", "MIDFIELDER_LEFT", "MIDFIELDER_CENTRAL",
            "MIDFIELDER_RIGHT", "ATTACKING_MIDFIELDER_LEFT", "ATTACKING_MIDFIELDER_CENTRAL",
            "ATTACKING_MIDFIELDER_RIGHT", "STRIKER",
            "CROSSING", "DRIBBLING", "FINISHING", "HEADING", "LONG_SHOTS", "MARKING", "OFF_THE_BALL",
            "PASSING", "PENALTIES", "TACKLING", "VISION", "HANDLING", "AERIAL_ABILITY", "COMMAND_OF_AREA",
            "COMMUNICATION", "KICKING", "THROWING", "ANTICIPATION", "DECISIONS", "ONE_ON_ONES",
            "POSITIONING", "REFLEXES", "FIRST_TOUCH", "TECHNIQUE", "LEFT_FOOT", "RIGHT_FOOT", "FLAIR",
            "CORNERS", "TEAMWORK", "WORK_RATE", "LONG_THROWS", "ECCENTRICITY", "RUSHING_OUT",
            "TENDENCY_TO_PUNCH", "ACCELERATION", "FREE_KICKS", "STRENGTH", "STAMINA", "PACE",
            "JUMPING_REACH", "LEADERSHIP", "DIRTINESS", "BALANCE", "BRAVERY", "CONSISTENCY",
            "AGGRESSION", "AGILITY", "IMPORTANT_MATCHES", "INJURY_PRONENESS", "VERSATILITY",
            "NATURAL_FITNESS", "DETERMINATION", "COMPOSURE", "CONCENTRATION");
    static final Set<String> MONEY_COLUMNS = Set.of(
            "ASKING_PRICE", "ASKING_PRICE_RAW", "SALARY_PA", "SALARY_WEEKLY_RAW",
            "BALANCE", "TRANSFER_BUDGET", "PAYROLL_BUDGET");
    private static final Set<String> DEFAULT_KEYS = Set.of(
            "NAME", "AGE", "CLUB", "POSITION", "CA", "PA", "APPEARANCES", "GOALS", "ASSISTS", "AVERAGE_RATING",
            "SALARY_WEEKLY_RAW", "ASKING_PRICE", "CONTRACT_END_DATE");

    private static final Set<String> SQUAD_KEYS = Set.of(
            "NAME", "AGE", "CLUB", "PLAYING_CLUB", "POSITION", "CA", "PA", "INJURED",
            "APPEARANCES", "STARTS", "MINUTES", "GOALS", "ASSISTS", "AVERAGE_RATING");
    private static final Set<String> RECRUITMENT_KEYS = Set.of(
            "NAME", "AGE", "CLUB", "POSITION", "CA", "PA", "ASKING_PRICE", "SALARY_WEEKLY_RAW",
            "TRANSFER_LISTED", "LISTED_FOR_LOAN", "APPEARANCES", "GOALS", "AVERAGE_RATING");
    private static final Set<String> CONTRACT_KEYS = Set.of(
            "NAME", "AGE", "CLUB", "POSITION", "CA", "SALARY_WEEKLY_RAW", "ASKING_PRICE",
            "CONTRACT_END_DATE", "TRANSFER_AGREED", "FUTURE_TRANSFER_CLUB", "FUTURE_TRANSFER_DATE");
    private static final Set<String> PERFORMANCE_KEYS = Set.of(
            "NAME", "AGE", "CLUB", "POSITION", "CA", "PA", "APPEARANCES", "STARTS", "MINUTES",
            "GOALS", "ASSISTS", "AVERAGE_RATING", "INJURED");

    private PlayerWorkspaceColumns() {
    }

    static List<Column> visible(boolean showAll) {
        return showAll ? all() : all().stream().filter(column -> DEFAULT_KEYS.contains(column.key())).toList();
    }

    static List<Column> visible(PlayerViewPreset preset) {
        if (preset == null || preset == PlayerViewPreset.FULL_DATA) {
            return all();
        }
        Set<String> keys = switch (preset) {
            case SQUAD -> SQUAD_KEYS;
            case RECRUITMENT -> RECRUITMENT_KEYS;
            case CONTRACTS -> CONTRACT_KEYS;
            case PERFORMANCE -> PERFORMANCE_KEYS;
            case FULL_DATA -> Set.of();
        };
        return all().stream().filter(column -> keys.contains(column.key())).toList();
    }

    static List<Column> all() {
        return List.of(
                new Column("NAME", "Name", PlayerEntity::getName),
                new Column("AGE", "Age", PlayerEntity::getAge),
                new Column("HEIGHT_CM", "Height (cm)", PlayerEntity::getHeightCm),
                new Column("NATIONALITY", "Nationality", PlayerEntity::getNationality),
                new Column("CLUB", "Club", PlayerEntity::getClub),
                new Column("PLAYING_CLUB", "Playing Club", PlayerEntity::getPlayingClub),
                new Column("POSITION", "Position", PositionTextFormatter::format),
                new Column("CA", "CA", PlayerEntity::getCa),
                new Column("PA", "PA", PlayerEntity::getPa),
                new Column("APPEARANCES", "Apps", PlayerEntity::getAppearances),
                new Column("STARTS", "Starts", PlayerEntity::getStarts),
                new Column("MINUTES", "Minutes", PlayerEntity::getMinutes),
                new Column("GOALS", "Goals", PlayerEntity::getGoals),
                new Column("ASSISTS", "Assists", PlayerEntity::getAssists),
                new Column("AVERAGE_RATING", "Rating", PlayerEntity::getAverageRating),
                new Column("SALARY_WEEKLY_RAW", "Wage", PlayerEntity::getSalaryWeeklyRaw),
                new Column("ASKING_PRICE", "Asking", PlayerEntity::getAskingPrice),
                new Column("CONTRACT_END_DATE", "Contract", PlayerEntity::getContractEndDate),
                new Column("TRANSFER_LISTED", "Transfer Listed", PlayerEntity::getTransferListed),
                new Column("LISTED_FOR_LOAN", "Listed For Loan", PlayerEntity::getListedForLoan),
                new Column("TRANSFER_AGREED", "Transfer Agreed", PlayerEntity::getTransferAgreed),
                new Column("FUTURE_TRANSFER_CLUB", "Future Transfer Club", PlayerEntity::getFutureTransferClub),
                new Column("FUTURE_TRANSFER_DATE", "Future Transfer Date", PlayerEntity::getFutureTransferDate),
                new Column("FUTURE_TRANSFER_CONTRACT_END_DATE", "Future Contract End", PlayerEntity::getFutureTransferContractEndDate),
                new Column("INJURED", "Injured", PlayerEntity::getInjured),
                new Column("INJURY", "Injury", PlayerEntity::getInjury),
                new Column("INJURY_LIGHT_TRAINING_DAYS_REMAINING", "Light Training Days", PlayerEntity::getInjuryLightTrainingDaysRemaining),
                new Column("INJURY_FULL_TRAINING_DAYS_REMAINING", "Full Training Days", PlayerEntity::getInjuryFullTrainingDaysRemaining),
                new Column("INJURY_EXPECTED_RETURN", "Expected Return", PlayerEntity::getInjuryExpectedReturn),
                new Column("TRAITS", "Traits", PlayerEntity::getTraits),
                new Column("CURRENT_REPUTATION", "Current Reputation", PlayerEntity::getCurrentReputation),
                new Column("HOME_REPUTATION", "Home Reputation", PlayerEntity::getHomeReputation),
                new Column("WORLD_REPUTATION", "World Reputation", PlayerEntity::getWorldReputation));
    }

    record Column(String key, String header, Function<PlayerEntity, Object> valueProvider) {
        Object value(PlayerEntity player) {
            return valueProvider.apply(player);
        }
    }
}
