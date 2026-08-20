package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.football.AcademyCandidate;
import com.github.fmaiassistent.football.FirstXiPick;
import com.github.fmaiassistent.football.FirstXiSlot;
import com.github.fmaiassistent.football.FirstXiSuggestionQuery;
import com.github.fmaiassistent.football.PlayerAnalysisPort;
import com.github.fmaiassistent.football.PlayerAnalysisRules;
import com.github.fmaiassistent.football.TransferShortlistCandidate;
import com.github.fmaiassistent.football.TransferShortlistPort;
import com.github.fmaiassistent.football.TransferShortlistQuery;
import com.github.fmaiassistent.football.MoneyballAnalysisResult;
import com.github.fmaiassistent.football.MoneyballCandidate;
import com.github.fmaiassistent.football.MoneyballDeal;
import com.github.fmaiassistent.football.MoneyballPort;
import com.github.fmaiassistent.football.MoneyballQuery;
import com.github.fmaiassistent.football.SquadAdvicePort;
import com.github.fmaiassistent.football.SquadSellCandidate;
import com.github.fmaiassistent.football.SquadWageHealth;
import com.github.fmaiassistent.football.ContractRecommendation;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.service.CompetitionDatabaseService;
import com.github.fmaiassistent.service.DatabaseLoadAllService;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import com.github.fmaiassistent.service.PlayerSearchService;
import com.github.fmaiassistent.service.PlayerStatsQueryService;
import com.github.fmaiassistent.service.RamLoadCoordinator;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;
import com.github.fmaiassistent.web.ui.PositionTextFormatter;
import com.github.fmaiassistent.web.mapper.PlayerMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Period;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class FmAiAssistentTools implements PlayerAnalysisPort, TransferShortlistPort, MoneyballPort, SquadAdvicePort {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 250;
    private static final int DEFAULT_SHORTLIST_LIMIT = 8;
    private static final int MAX_SHORTLIST_LIMIT = 30;
    private static final int DEFAULT_REPUTATION_MARGIN = 750;
    private static final int DEFAULT_MIN_POSITION_SCORE = 15;
    private static final int DEFAULT_MONEYBALL_QUALITY_GAP = 15;
    private static final int DEFAULT_MONEYBALL_MAX_AGE = 40;
    private static final int DEFAULT_WONDERKID_MAX_AGE = 21;
    /** Youth often joined recently; a 1-year tenure filter empties U19 pools. */
    static final String WONDERKID_MIN_TIME_AT_CLUB = "P0D";
    static final List<String> FIELDS_NOT_IN_RAM = List.of("morale", "form");
    private static final int SOURCE_CLUB_REPUTATION_MARGIN = 1000;
    private static final List<String> RAM_DECODED_TABLES = List.of("PeopleOffset", "TeamOffset", "CompetitionOffset");
    private static final List<String> RAM_NOT_DECODED_TABLES = List.of("NationOffset", "StadiumOffset", "AgreementOffset", "ClubOffset",
            "CityOffset", "ContinentOffset", "RegionOffset", "CurrencyOffset");

    private final PlayerDatabaseService players;
    private final PlayerSearchService playerSearch;
    private final ClubDatabaseService clubs;
    private final CompetitionDatabaseService competitions;
    private final PlayerMapper playerMapper;
    private final PlayerStatsQueryService statsQuery;
    private final JdbcTemplate jdbc;
    private final RamLoadCoordinator ramLoad;
    private final DatabaseLoadAllService loadAll;
    private volatile List<RoleAttributeRow> roleAttributeRowsCache;

    @Override
    public Map<String, Double> importedPlayerStats(PlayerEntity player) {
        return statsQuery.importedStats(player);
    }

    @Override
    public List<Map<String, Object>> recentPlayerMatchStats(PlayerEntity player, int limit) {
        return statsQuery.recentMatches(player, limit).stream().map(match -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("date", match.date());
            out.put("competition", match.competition());
            out.put("opponent", match.opponent());
            out.put("stats", match.stats());
            return out;
        }).toList();
    }

    public FmAiAssistentTools(
            PlayerDatabaseService players,
            PlayerSearchService playerSearch,
            ClubDatabaseService clubs,
            CompetitionDatabaseService competitions,
            PlayerMapper playerMapper,
            PlayerStatsQueryService statsQuery,
            JdbcTemplate jdbc,
            RamLoadCoordinator ramLoad,
            DatabaseLoadAllService loadAll) {
        this.players = players;
        this.playerSearch = playerSearch;
        this.clubs = clubs;
        this.competitions = competitions;
        this.playerMapper = playerMapper;
        this.statsQuery = statsQuery;
        this.jdbc = jdbc;
        this.ramLoad = ramLoad;
        this.loadAll = loadAll;
    }

    @Tool(name = "fm26_find_clubs", description = "Find FM26 clubs by name, nation, competition, reputation and finances. Money values are raw pounds.")
    @Transactional(readOnly = true)
    public Map<String, Object> findClubs(
            @ToolParam(required = false, description = "Club name contains filter, for example Feyenoord") String name,
            @ToolParam(required = false, description = "Nation exact filter, for example Netherlands") String nation,
            @ToolParam(required = false, description = "Competition exact filter, for example Eredivisie") String competition,
            @ToolParam(required = false, description = "Minimum club reputation") Integer reputationMin,
            @ToolParam(required = false, description = "Maximum number of clubs to return") Integer limit) {
        int safeLimit = safeLimit(limit);
        List<Map<String, Object>> rows = clubs.searchClubs(name, nation, competition, reputationMin, safeLimit)
                .stream()
                .map(this::clubMap)
                .toList();
        return result("clubs", rows, safeLimit);
    }

    @Tool(name = "fm26_get_club_context", description = "Get a club profile, finances and squad snapshot for transfer advice. Money values are raw pounds.")
    @Transactional(readOnly = true)
    public Map<String, Object> getClubContext(
            @ToolParam(description = "Club name, for example Feyenoord") String clubName,
            @ToolParam(required = false, description = "Maximum squad players to return") Integer squadLimit) {
        ClubEntity club = requireClub(clubName);
        List<PlayerEntity> squad = squadPlayers(club.getName()).stream()
                .filter(MarketValuation::hasPlayablePosition)
                .sorted(Comparator
                        .comparing((PlayerEntity player) -> value(player.getCa())).reversed()
                        .thenComparing(PlayerEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", clubMap(club));
        out.put("squad_summary", squadSummary(squad, club));
        out.put("squad", squad.stream()
                .limit(safeLimit(squadLimit))
                .map(this::playerSummaryMap)
                .toList());
        return out;
    }

    @Tool(name = "fm26_find_players", description = "Search FM26 players using the same data available in the UI, including current-season all-competition statistics and availability/contract filters. Null statistics are unknown and do not match performance ranges. Money values are raw pounds.")
    @Transactional(readOnly = true)
    public Map<String, Object> findPlayers(
            @ToolParam(required = false, description = "Player name contains filter") String name,
            @ToolParam(required = false, description = "Gender exact filter: male or female") String gender,
            @ToolParam(required = false, description = "Nationality exact filter") String nationality,
            @ToolParam(required = false, description = "Playing nation exact filter") String playingNation,
            @ToolParam(required = false, description = "Playing competition exact filter") String playingCompetition,
            @ToolParam(required = false, description = "Club or playing club exact filter") String club,
            @ToolParam(required = false, description = "Minimum age") Integer ageMin,
            @ToolParam(required = false, description = "Maximum age") Integer ageMax,
            @ToolParam(required = false, description = "Minimum current ability") Integer caMin,
            @ToolParam(required = false, description = "Maximum current ability") Integer caMax,
            @ToolParam(required = false, description = "Minimum potential ability") Integer paMin,
            @ToolParam(required = false, description = "Maximum potential ability") Integer paMax,
            @ToolParam(required = false, description = "Maximum asking price in pounds") Long askingPriceMax,
            @ToolParam(required = false, description = "Maximum weekly salary in pounds") Integer salaryWeeklyMax,
            @ToolParam(required = false, description = "Contract end date from, ISO-8601 YYYY-MM-DD") String contractEndDateFrom,
            @ToolParam(required = false, description = "Contract end date to, ISO-8601 YYYY-MM-DD") String contractEndDateTo,
            @ToolParam(required = false, description = "Minimum world reputation") Integer worldReputationMin,
            @ToolParam(required = false, description = "Maximum world reputation") Integer worldReputationMax,
            @ToolParam(required = false, description = "Transfer-listed filter. Use true for only transfer-listed players, false for only players not transfer-listed.") Boolean transferListed,
            @ToolParam(required = false, description = "Listed-for-loan filter. Use true for only loan-listed players, false for only players not listed for loan.") Boolean listedForLoan,
            @ToolParam(required = false, description = "Transfer-agreed filter. Use true for players who already agreed a future move, false to exclude them.") Boolean transferAgreed,
            @ToolParam(required = false, description = "Future transfer destination club exact filter.") String futureTransferClub,
             @ToolParam(required = false, description = "Injury filter. Use true for only injured players, false for only currently fit players.") Boolean injured,
             @ToolParam(required = false, description = "Free-agent filter. Use true for players without a contracted club.") Boolean freeAgent,
             @ToolParam(required = false, description = "Loan filter: LOANED or NOT_LOANED.") String loanStatus,
             @ToolParam(required = false, description = "Club scope: EITHER, CONTRACTED or PLAYING.") String clubScope,
             @ToolParam(required = false, description = "Minimum current-season appearances.") Integer appearancesMin,
             @ToolParam(required = false, description = "Maximum current-season appearances.") Integer appearancesMax,
             @ToolParam(required = false, description = "Minimum current-season starts.") Integer startsMin,
             @ToolParam(required = false, description = "Maximum current-season starts.") Integer startsMax,
             @ToolParam(required = false, description = "Minimum current-season minutes.") Integer minutesMin,
             @ToolParam(required = false, description = "Maximum current-season minutes.") Integer minutesMax,
             @ToolParam(required = false, description = "Minimum current-season goals.") Integer goalsMin,
             @ToolParam(required = false, description = "Maximum current-season goals.") Integer goalsMax,
             @ToolParam(required = false, description = "Minimum current-season assists.") Integer assistsMin,
             @ToolParam(required = false, description = "Maximum current-season assists.") Integer assistsMax,
             @ToolParam(required = false, description = "Minimum current-season average rating.") Double averageRatingMin,
             @ToolParam(required = false, description = "Maximum current-season average rating.") Double averageRatingMax,
             @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST.") String position,
            @ToolParam(required = false, description = "Minimum position ability 1-20 when position is supplied. Defaults to 15.") Integer minimumPositionScore,
            @ToolParam(required = false, description = "If true, return full attributes. Defaults to compact summaries; use fm26_get_player_details for finalists.") Boolean details,
            @ToolParam(required = false, description = "Maximum players to return") Integer limit) {
        int safeLimit = safeLimit(limit);
        PositionSpec positionSpec;
        try {
            positionSpec = resolvePosition(position);
        } catch (UnsupportedPositionException ex) {
            return positionError(position);
        }
        int positionMinimum = positionSpec == null
                ? 1
                : Math.max(1, Math.min(20, minimumPositionScore == null ? DEFAULT_MIN_POSITION_SCORE : minimumPositionScore));
        boolean fullDetails = Boolean.TRUE.equals(details);
        PlayerFilterCriteria.Advanced advanced = new PlayerFilterCriteria.Advanced(
                injured, transferListed, listedForLoan, transferAgreed, freeAgent,
                parseLoanStatus(loanStatus), parseClubScope(clubScope),
                appearancesMin, appearancesMax, startsMin, startsMax, minutesMin, minutesMax,
                goalsMin, goalsMax, assistsMin, assistsMax, averageRatingMin, averageRatingMax);
        Predicate<PlayerEntity> filter = player ->
                        askingPriceWithinMax(player.getAskingPrice(), player.getClub(), askingPriceMax)
                        && salaryWithinMax(player.getSalaryWeeklyRaw(), salaryWeeklyMax)
                        && (blank(futureTransferClub) || equalsIgnoreCase(player.getFutureTransferClub(), futureTransferClub))
                        && MarketValuation.hasPlayablePosition(player)
                        && (positionSpec == null || positionScore(player, positionSpec) >= positionMinimum);
        LocalDate contractFrom = parseDate(contractEndDateFrom);
        LocalDate contractTo = parseDate(contractEndDateTo);
        PlayerFilterCriteria databaseFilter = new PlayerFilterCriteria(
                name, gender, playingNation, playingCompetition, club,
                ageMin, ageMax, null, null, nationality,
                null, null, null, null, worldReputationMin, worldReputationMax,
                caMin, caMax, paMin, paMax,
                contractFrom, contractTo, null, askingPriceMax, salaryWeeklyMax == null ? null : salaryWeeklyMax.longValue(),
                Map.of(), Map.of(), advanced);
        Map<String, Object> snapshotMetadata = players.metadata();
        List<Map<String, Object>> rows = playerSearch.find(databaseFilter).stream()
                .filter(filter)
                .sorted(Comparator
                        .comparing((PlayerEntity player) -> value(player.getPa())).reversed()
                        .thenComparing(Comparator.comparing((PlayerEntity player) -> value(player.getCa())).reversed())
                        .thenComparing(PlayerEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(safeLimit)
                .map(player -> fullDetails
                        ? playerFullMap(player, snapshotMetadata)
                        : playerSummaryMap(player, snapshotMetadata))
                .toList();
        Map<String, Object> out = result("players", rows, safeLimit);
        if (rows.isEmpty()) {
            out.put("empty_hint", "No rows. Change at most one filter then answer. Performance ranges exclude players with unknown statistics; remove or broaden the stats filter if needed. askingPriceMax drops unknown fees; omit it for youth. Do not keep searching.");
        }
        return out;
    }

    @Tool(name = "fm26_get_player_details", description = "Get full player details including attributes, positions, CA/PA, reputation, contract and club data.")
    @Transactional(readOnly = true)
    public Map<String, Object> getPlayerDetails(
            @ToolParam(description = "Player name. Exact match is preferred; contains match is used as fallback.") String name,
            @ToolParam(required = false, description = "Maximum matching players to return") Integer limit) {
        int safeLimit = safeLimit(limit);
        String normalized = normalize(name);
        List<Map<String, Object>> exact = allPlayers().stream()
                .filter(MarketValuation::hasPlayablePosition)
                .filter(player -> normalize(player.getName()).equals(normalized))
                .sorted(Comparator.comparing(PlayerEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(safeLimit)
                .map(this::playerFullMap)
                .toList();
        List<Map<String, Object>> rows = exact.isEmpty()
                ? allPlayers().stream()
                        .filter(MarketValuation::hasPlayablePosition)
                        .filter(player -> contains(player.getName(), name))
                        .sorted(Comparator.comparing(PlayerEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                        .limit(safeLimit)
                        .map(this::playerFullMap)
                        .toList()
                : exact;
        return result("players", rows, safeLimit);
    }

    @Tool(name = "fm26_get_role_attributes", description = "Get FM26 positional roles and the primary/secondary attributes that matter for each role. Use this for tactical fit and transfer advice.")
    @Transactional(readOnly = true)
    public Map<String, Object> getRoleAttributes(
            @ToolParam(required = false, description = "Phase exact filter: In Possession or Out of Possession") String phase,
            @ToolParam(required = false, description = "Position group exact filter, for example Striker, Goalkeeper, Centre-Back, Central Midfielder") String positionGroup,
            @ToolParam(required = false, description = "Role name contains filter, for example Advanced Forward, Ball-Playing Centre-Back, Goalkeeper") String roleName,
            @ToolParam(required = false, description = "Maximum roles to return") Integer limit) {
        roleAttributeRowsCache = null;
        int safeLimit = safeLimit(limit);
        List<RoleAttributeRow> rows = roleAttributeRows();

        Map<RoleKey, RoleBucket> grouped = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> blank(phase) || equalsIgnoreCase(row.phase(), phase))
                .filter(row -> blank(positionGroup) || equalsIgnoreCase(row.positionGroup(), positionGroup))
                .filter(row -> blank(roleName) || rolesMatch(row.roleName(), roleName))
                .forEach(row -> grouped
                        .computeIfAbsent(new RoleKey(row.game(), row.positionGroup(), row.roleName(), row.phase()), RoleBucket::new)
                        .add(row));

        List<Map<String, Object>> roles = grouped.values().stream()
                .limit(safeLimit)
                .map(RoleBucket::toMap)
                .toList();

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("phase", phase);
        filters.put("position_group", positionGroup);
        filters.put("role_name", roleName);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", roles.size());
        out.put("limit", safeLimit);
        out.put("filters", filters);
        out.put("usage", "Compare a player's ATTRIBUTES from fm26_get_player_details with primary_attributes and secondary_attributes. Primary attributes matter most for the role.");
        out.put("roles", roles);
        return out;
    }

    @Tool(name = "fm26_transfer_shortlist", description = "Primary recruitment tool. Returns a compact ranked shortlist using squad strength, position and role fit, CA/PA, price, wage, reputation, source club, availability and time at club. Call this before broad player searches or player details.")
    @Transactional(readOnly = true)
    public Map<String, Object> transferShortlist(
            @ToolParam(description = "Managing club name, for example Feyenoord") String managingClub,
            @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST. Full names also work.") String position,
            @ToolParam(required = false, description = "Optional FM26 role name for attribute fit, for example Ball-Playing Centre-Back.") String roleName,
            @ToolParam(required = false, description = "Role phase: In Possession or Out of Possession. Omit to combine matching phases.") String phase,
            @ToolParam(required = false, description = "Minimum position ability 1-20. Defaults to 15 when position is supplied.") Integer minimumPositionScore,
            @ToolParam(required = false, description = "Maximum player age. No limit when omitted.") Integer maxAge,
            @ToolParam(required = false, description = "Minimum current ability. No minimum when omitted.") Integer minCurrentAbility,
            @ToolParam(required = false, description = "Minimum potential ability. No minimum when omitted.") Integer minPotentialAbility,
            @ToolParam(required = false, description = "Maximum asking price in pounds. If omitted, uses the club transfer budget.") Long maxAskingPrice,
            @ToolParam(required = false, description = "Maximum current weekly salary in pounds. Omit to score wage fit without excluding players.") Integer maxWeeklySalary,
            @ToolParam(required = false, description = "Extra player reputation above club reputation considered plausible. Defaults to 750.") Integer reputationMargin,
            @ToolParam(required = false, description = "Minimum time at current club. ISO-8601 period like P1Y or plain days like 365. Defaults to P1Y.") String minimumTimeAtCurrentClub,
            @ToolParam(required = false, description = "Transfer-listed filter. true=only listed, false=exclude listed.") Boolean transferListed,
            @ToolParam(required = false, description = "Loan-listed filter. true=only loan-listed, false=exclude loan-listed.") Boolean listedForLoan,
            @ToolParam(required = false, description = "Transfer-agreed filter. Defaults to false because agreed players are unavailable.") Boolean transferAgreed,
            @ToolParam(required = false, description = "Injury filter. false=fit only, true=injured only, omit=both.") Boolean injured,
            @ToolParam(required = false, description = "Maximum candidates. Defaults to 8, maximum 30.") Integer limit) {
        return transferShortlistInternal(
                managingClub, position, roleName, phase, minimumPositionScore, maxAge, minCurrentAbility,
                minPotentialAbility, maxAskingPrice, maxWeeklySalary, reputationMargin, minimumTimeAtCurrentClub,
                transferListed, listedForLoan, transferAgreed, injured, limit, true);
    }

    private Map<String, Object> transferShortlistInternal(
            String managingClub,
            String position,
            String roleName,
            String phase,
            Integer minimumPositionScore,
            Integer maxAge,
            Integer minCurrentAbility,
            Integer minPotentialAbility,
            Long maxAskingPrice,
            Integer maxWeeklySalary,
            Integer reputationMargin,
            String minimumTimeAtCurrentClub,
            Boolean transferListed,
            Boolean listedForLoan,
            Boolean transferAgreed,
            Boolean injured,
            Integer limit,
            boolean dropUnwilling) {
        roleAttributeRowsCache = null;
        RankedTransfers ranked;
        try {
            ranked = rankTransfers(
                    managingClub, position, roleName, phase, minimumPositionScore, maxAge, minCurrentAbility,
                    minPotentialAbility, maxAskingPrice, maxWeeklySalary, reputationMargin, minimumTimeAtCurrentClub,
                    transferListed, listedForLoan, transferAgreed, injured, dropUnwilling);
        } catch (UnsupportedPositionException ex) {
            return positionError(position);
        }
        int shortlistLimit = limit == null ? DEFAULT_SHORTLIST_LIMIT : Math.max(1, Math.min(limit, MAX_SHORTLIST_LIMIT));
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int index = 0; index < Math.min(shortlistLimit, ranked.candidates().size()); index++) {
            candidates.add(recommendationMap(
                    index + 1,
                    ranked.candidates().get(index),
                    ranked.club(),
                    ranked.benchmarkCa(),
                    ranked.priceCap(),
                    ranked.wageCeiling()));
        }

        Map<String, Object> criteria = new LinkedHashMap<>();
        putIfNotNull(criteria, "position", ranked.positionSpec() == null ? null : ranked.positionSpec().code());
        putIfNotNull(criteria, "minimum_position_score", ranked.positionSpec() == null ? null : ranked.positionMinimum());
        putIfNotNull(criteria, "role", blank(roleName) ? null : roleName);
        putIfNotNull(criteria, "phase", blank(phase) ? null : phase);
        putIfNotNull(criteria, "max_age", maxAge);
        putIfNotNull(criteria, "min_ca", minCurrentAbility == null ? null : minCurrentAbility);
        putIfNotNull(criteria, "min_pa", minPotentialAbility);
        putIfNotNull(criteria, "max_asking_price", priceCapKnown(ranked.priceCap()) ? ranked.priceCap() : null);
        if (!priceCapKnown(ranked.priceCap())) {
            criteria.put("budget_note", "club transfer budget is not decoded; no price cap was applied");
        } else if (ranked.priceCap() <= 0) {
            criteria.put("budget_note", "club transfer budget is £0");
        }
        criteria.put("weekly_wage_ceiling", ranked.wageCeiling());
        criteria.put("reputation_margin", ranked.reputationMargin());
        criteria.put("minimum_time_at_current_club", ranked.minimumTime().toString());
        putIfNotNull(criteria, "transfer_listed", transferListed);
        putIfNotNull(criteria, "listed_for_loan", listedForLoan);
        criteria.put("transfer_agreed", ranked.transferAgreed());
        putIfNotNull(criteria, "injured", injured);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", recruitmentClubMap(ranked.club()));
        out.put("criteria", criteria);
        out.put("position_benchmark", positionBenchmark(ranked.positionSquad(), ranked.positionSpec()));
        if (!ranked.roleProfile().isEmpty()) {
            out.put("role_model", ranked.roleProfile().toMap());
        }
        out.put("candidate_pool_count", ranked.candidates().size());
        out.put("returned", candidates.size());
        out.put("candidates", candidates);
        out.put("guidance", "Ranked estimates, not guaranteed transfers. asking_price=null means unknown, not free. Call fm26_get_player_details only for finalists needing full attributes.");
        attachEmptyRecruitmentHint(out, ranked.candidates().size());
        return out;
    }

    @Tool(name = "fm26_moneyball_shortlist", description = "Moneyball value tool. Finds the best signings for a club and sorts them by signing_rating (0-100), which combines player quality (half CA, half age-adjusted PA) with transfer value (asking price plus 3 years of wages compared with the market median for comparable players). Each candidate also carries a deal_tier: excellent, good, average or overpriced, used-car style. Call this for cheap signings and value transfers; call fm26_transfer_shortlist when tactical or role fit is the priority.")
    @Transactional(readOnly = true)
    public Map<String, Object> moneyballShortlist(
            @ToolParam(description = "Managing club name, for example Feyenoord") String managingClub,
            @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST. Full names also work.") String position,
            @ToolParam(required = false, description = "Optional FM26 role name for attribute fit, for example Ball-Playing Centre-Back.") String roleName,
            @ToolParam(required = false, description = "Role phase: In Possession or Out of Possession.") String phase,
            @ToolParam(required = false, description = "Minimum position ability 1-20. Defaults to 15 when position is supplied; ignored otherwise.") Integer minimumPositionScore,
            @ToolParam(required = false, description = "Minimum current ability. Defaults to the squad first-team average CA minus 15 when the squad is known.") Integer minCurrentAbility,
            @ToolParam(required = false, description = "Minimum potential ability.") Integer minPotentialAbility,
            @ToolParam(required = false, description = "Maximum player age. Defaults to 40 when omitted.") Integer maxAge,
            @ToolParam(required = false, description = "Maximum asking price in pounds. If omitted, uses the club transfer budget.") Long maxAskingPrice,
            @ToolParam(required = false, description = "Maximum weekly salary in pounds.") Integer maxWeeklySalary,
            @ToolParam(required = false, description = "Extra player reputation above club reputation considered plausible. Defaults to 750.") Integer reputationMargin,
            @ToolParam(required = false, description = "Minimum time at current club. ISO-8601 period like P1Y or plain days like 365. Defaults to P1Y.") String minimumTimeAtCurrentClub,
            @ToolParam(required = false, description = "Transfer-listed filter. true=only listed, false=exclude listed.") Boolean transferListed,
            @ToolParam(required = false, description = "Loan-listed filter. true=only loan-listed, false=exclude loan-listed.") Boolean listedForLoan,
            @ToolParam(required = false, description = "Transfer-agreed filter. Defaults to false because agreed players are unavailable.") Boolean transferAgreed,
            @ToolParam(required = false, description = "Injury filter. false=fit only, true=injured only, omit=both.") Boolean injured,
            @ToolParam(required = false, description = "Maximum candidates. Defaults to 8, maximum 30.") Integer limit) {
        roleAttributeRowsCache = null;
        List<PlayerEntity> allPlayers = allPlayers();
        MoneyballParameters params;
        try {
            params = resolveMoneyballParameters(
                    allPlayers, managingClub, position, roleName, phase, minimumPositionScore,
                    minCurrentAbility, minPotentialAbility, maxAge, maxAskingPrice, maxWeeklySalary,
                    reputationMargin, minimumTimeAtCurrentClub, transferListed, listedForLoan, transferAgreed, injured);
        } catch (UnsupportedPositionException ex) {
            return positionError(position);
        }
        int shortlistLimit = limit == null ? DEFAULT_SHORTLIST_LIMIT : Math.max(1, Math.min(limit, MAX_SHORTLIST_LIMIT));

        MarketValuation market = MarketValuation.build(allPlayers);
        MoneyballRated rated = rateMoneyball(allPlayers, clubsByName(allClubs()), market, params);

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int index = 0; index < Math.min(shortlistLimit, rated.rated().size()); index++) {
            candidates.add(moneyballMap(index + 1, rated.rated().get(index), params.benchmarkCa(), params.priceCap(), params.wageCeiling()));
        }

        Map<String, Object> criteria = new LinkedHashMap<>();
        putIfNotNull(criteria, "position", params.positionSpec() == null ? null : params.positionSpec().code());
        putIfNotNull(criteria, "minimum_position_score", params.positionSpec() == null ? null : params.positionMinimum());
        putIfNotNull(criteria, "role", blank(roleName) ? null : roleName);
        putIfNotNull(criteria, "phase", blank(phase) ? null : phase);
        criteria.put("min_ca", params.qualityFloor());
        putIfNotNull(criteria, "min_pa", params.minPa());
        putIfNotNull(criteria, "max_age", params.maxAge());
        putIfNotNull(criteria, "max_asking_price", priceCapKnown(params.priceCap()) ? params.priceCap() : null);
        if (!priceCapKnown(params.priceCap())) {
            criteria.put("budget_note", "club transfer budget is not decoded; no price cap was applied");
        } else if (params.priceCap() <= 0) {
            criteria.put("budget_note", "club transfer budget is £0");
        }
        criteria.put("weekly_wage_ceiling", params.wageCeiling());
        criteria.put("reputation_margin", params.reputationMargin());
        criteria.put("minimum_time_at_current_club", params.minimumTime().toString());
        putIfNotNull(criteria, "transfer_listed", params.transferListed());
        putIfNotNull(criteria, "listed_for_loan", params.listedForLoan());
        criteria.put("transfer_agreed", params.transferAgreed());
        putIfNotNull(criteria, "injured", params.injured());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", recruitmentClubMap(params.club()));
        out.put("criteria", criteria);
        out.put("market_model", marketModelMap(market));
        out.put("position_benchmark", positionBenchmark(params.positionSquad(), params.positionSpec()));
        if (!params.roleProfile().isEmpty()) {
            out.put("role_model", params.roleProfile().toMap());
        }
        out.put("candidate_pool_count", rated.candidatePoolSize());
        out.put("deal_rated_count", rated.rated().size());
        out.put("returned", candidates.size());
        out.put("candidates", candidates);
        out.put("sort", "signing_rating descending");
        out.put("signing_rating_legend", "signing_rating (0-100) = quality_score x value_factor, capped at 100. quality_score blends CA (50%) and age-adjusted PA (50%). value_factor is 1.0 at market price, up to 1.5 for below-market deals and down to 0.3 for overpriced ones.");
        out.put("deal_tier_legend", "deal_tier compares total cost (fee + 3 years of wages) with the market median for comparable players: excellent = cost below 60% of market, good = 60-80%, average = 80-120%, overpriced = above 120%. deal_score is market_cost divided by total_cost, so above 1 means below market.");
        out.put("guidance", "Ranked estimates, not guaranteed transfers. Candidates are sorted by signing_rating; deal_tier labels only the value component. asking_price=null means unknown, not free; only players with a known fee or no club are rated. Call fm26_get_player_details only for finalists and fm26_transfer_shortlist when role fit matters more than value.");
        attachEmptyRecruitmentHint(out, rated.candidatePoolSize());
        return out;
    }

    @Tool(name = "fm26_status", description = "Snapshot status: in-game date, last RAM load time, counts, and current-season statistics availability. Call this before recruitment tools.")
    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>(players.metadata());
        out.put("clubs", clubs.countClubs());
        out.put("competitions", competitions.countCompetitions());
        out.put("loading", ramLoad.loading());
        RamLoadCoordinator.LoadStatus loadStatus = ramLoad.status();
        if (loadStatus.jobId() != null) {
            out.put("load_job", loadStatusMap(loadStatus));
        }
            out.put("guidance", "If count is 0, call fm26_load_from_ram or click Load from RAM in the UI with FM26 running. tactic_formation is the live tactic when RAM load found it.");
        Object gameDate = out.get("game_date");
        if (gameDate == null || String.valueOf(gameDate).isBlank()) {
            out.put("age_note", "In-game date was not decoded, so stored ages may be blank. U21/wonderkid filters now compute age from date of birth (vs today if needed). Reload RAM so the save date is read.");
        }
        return out;
    }

    @Tool(name = "fm26_load_from_ram", description = "Load the current FM26 save from RAM into the local database. FM26 must be running with a save loaded. Slow; do not call repeatedly.")
    public Map<String, Object> loadFromRam() {
        try {
            String jobId = ramLoad.startLoad();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "started");
            out.put("job_id", jobId);
            out.put("message", "RAM load started. Call fm26_status for progress and the completed snapshot counts.");
            return out;
        } catch (RamLoadCoordinator.LoadInProgressException ex) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "already_in_progress");
            out.put("message", "A RAM load is already in progress. Call fm26_status to check progress.");
            return out;
        } catch (RuntimeException | LinkageError ex) {
            throw new IllegalStateException(readerFailureMessage(ex), ex);
        }
    }

    private static Map<String, Object> loadStatusMap(RamLoadCoordinator.LoadStatus status) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", status.jobId());
        out.put("state", status.state());
        if (status.progress() != null) {
            out.put("phase", status.progress().phase().name().toLowerCase(Locale.ROOT));
            out.put("done", status.progress().done());
            out.put("total", status.progress().total());
            out.put("kept", status.progress().kept());
            out.put("detail", status.progress().detail());
            out.put("fraction", status.progress().overallFraction());
        }
        if (status.result() != null) {
            DatabaseLoadAllService.LoadAllResult result = status.result();
            out.put("pid", result.pid());
            out.put("game_date", result.gameDate());
            out.put("players", result.players());
            out.put("clubs", result.clubs());
            out.put("competitions", result.competitions());
            out.put("skip_summary", result.skipSummary());
        }
        if (status.error() != null) {
            out.put("error", status.error());
        }
        return out;
    }

    @Tool(name = "fm26_find_competitions", description = "Find FM26 competitions by name, nation, gender and reputation.")
    @Transactional(readOnly = true)
    public Map<String, Object> findCompetitions(
            @ToolParam(required = false, description = "Competition name contains filter") String name,
            @ToolParam(required = false, description = "Nation exact filter") String nation,
            @ToolParam(required = false, description = "Gender exact filter: male or female") String gender,
            @ToolParam(required = false, description = "Maximum competitions to return") Integer limit) {
        int safeLimit = safeLimit(limit);
        List<Map<String, Object>> rows = competitions.findCompetitions(name, nation, gender, safeLimit);
        return result("competitions", rows, safeLimit);
    }

    @Tool(name = "fm26_ram_table_counts", description = "Research helper: live FM26 offset-table slot counts (People, Team, Club, Nation, Stadium, Agreement, ...). Does not invent player fields. Requires fm.exe.")
    public Map<String, Object> ramTableCounts() {
        try {
            Map<String, Long> counts = loadAll.ramSlotCounts();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("slots", counts);
            out.put("decoded_today", RAM_DECODED_TABLES);
            out.put("not_decoded", RAM_NOT_DECODED_TABLES);
            out.put("guidance", "Counts only. Traits are read when preferred-move name vectors match. Morale, form and match stats stay empty until those offsets are validated.");
            return out;
        } catch (IOException | RuntimeException | LinkageError ex) {
            throw new IllegalStateException(readerFailureMessage(ex), ex);
        }
    }

    private static String readerFailureMessage(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return failure instanceof LinkageError
                ? "The Windows memory reader could not initialize. Restart FM AI Assistent and try again."
                : "fm.exe process not found";
    }

    @Tool(name = "fm26_sell_shortlist", description = "Squad trim. Rank the managing club's own players for sell / loan / keep using depth, CA vs first team, wages and contract. Money values are raw pounds.")
    @Transactional(readOnly = true)
    public Map<String, Object> sellShortlist(
            @ToolParam(description = "Managing club name, for example Feyenoord") String managingClub,
            @ToolParam(required = false, description = "Maximum rows. Defaults to 20.") Integer limit) {
        ClubEntity club = requireClub(managingClub);
        List<SquadAdvice.SellRow> rows = sellRows(managingClub);
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, MAX_LIMIT));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", recruitmentClubMap(club));
        out.put("returned", Math.min(safeLimit, rows.size()));
        out.put("players", rows.stream().limit(safeLimit).map(this::sellMap).toList());
        out.put("guidance", "Estimates from the RAM snapshot. Listed and surplus players rank higher. Call fm26_get_player_details before confirming a sale.");
        return out;
    }

    @Tool(name = "fm26_wonderkid_shortlist", description = "External young signings for a club. Default max age 21 (pass 19 for U19). No default min PA — omit minPotentialAbility unless the user asked for elite potential. Tenure at the source club is not required. Players with a known fee inside the budget stay on the list even if reputation says they are unlikely to join. Unknown-fee players at much bigger clubs are omitted because they are not proven affordable. Do not use moneyball for this. For players you already employ, use fm26_academy. Money values are raw pounds.")
    @Transactional(readOnly = true)
    public Map<String, Object> wonderkidShortlist(
            @ToolParam(description = "Managing club name, for example Feyenoord") String managingClub,
            @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST.") String position,
            @ToolParam(required = false, description = "Optional FM26 role name") String roleName,
            @ToolParam(required = false, description = "Role phase: In Possession or Out of Possession") String phase,
            @ToolParam(required = false, description = "Maximum age. Defaults to 21.") Integer maxAge,
            @ToolParam(required = false, description = "Minimum potential ability. Omit unless the user asked for a PA floor; 150+ empties most U19 GK pools.") Integer minPotentialAbility,
            @ToolParam(required = false, description = "Maximum asking price in pounds") Long maxAskingPrice,
            @ToolParam(required = false, description = "Maximum candidates. Defaults to 8.") Integer limit) {
        return transferShortlistInternal(
                managingClub, position, roleName, phase, null,
                maxAge == null ? DEFAULT_WONDERKID_MAX_AGE : maxAge,
                null, minPotentialAbility, maxAskingPrice, null, null, WONDERKID_MIN_TIME_AT_CLUB,
                null, null, null, null, limit, false);
    }

    @Tool(name = "fm26_academy", description = "In-house youth at the managing club (owned players, not loaned-in). Default max age 21, ranked by PA. Use this for 'what young GKs do I already have' instead of paging fm26_get_club_context.")
    @Transactional(readOnly = true)
    public Map<String, Object> academy(
            @ToolParam(description = "Managing club name, for example Feyenoord") String managingClub,
            @ToolParam(required = false, description = "Maximum age. Defaults to 21.") Integer maxAge,
            @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST.") String position,
            @ToolParam(required = false, description = "Maximum rows. Defaults to 20.") Integer limit) {
        ClubEntity club = requireClub(managingClub);
        PositionSpec positionSpec;
        try {
            positionSpec = resolvePosition(position);
        } catch (UnsupportedPositionException ex) {
            return positionError(position);
        }
        int cap = maxAge == null ? DEFAULT_WONDERKID_MAX_AGE : maxAge;
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Map<String, Object>> rows = academyRows(managingClub, cap, positionSpec).stream()
                .limit(safeLimit)
                .map(FmAiAssistentTools::academyMap)
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", recruitmentClubMap(club));
        out.put("max_age", cap);
        putIfNotNull(out, "position", positionSpec == null ? null : positionSpec.code());
        out.put("returned", rows.size());
        out.put("players", rows);
        out.put("guidance", "Owned youth only, ranked by PA. This is intake, not a world search. For external wonderkids use fm26_wonderkid_shortlist.");
        if (rows.isEmpty()) {
            out.put("empty_hint", "No owned players at that age/position. Answer with that, or call fm26_wonderkid_shortlist for external options.");
        }
        return out;
    }

    @Tool(name = "fm26_compare_squads", description = "Compare two clubs' squads: CA/PA, wage bill, age, and best player per position.")
    @Transactional(readOnly = true)
    public Map<String, Object> compareSquads(
            @ToolParam(description = "First club name") String leftClub,
            @ToolParam(description = "Second club name") String rightClub) {
        ClubEntity left = requireClub(leftClub);
        ClubEntity right = requireClub(rightClub);
        return SquadAdvice.compareSquads(left.getName(), right.getName(),
                squadPlayers(left.getName()),
                squadPlayers(right.getName()));
    }

    @Tool(name = "fm26_compare_players", description = "Compare two players on CA/PA, wages, contract and key attributes.")
    @Transactional(readOnly = true)
    public Map<String, Object> comparePlayers(
            @ToolParam(description = "First player name") String leftName,
            @ToolParam(description = "Second player name") String rightName) {
        PlayerEntity left = requirePlayer(leftName, true);
        PlayerEntity right = requirePlayer(rightName, true);
        if (samePlayer(left, right)) {
            throw new IllegalArgumentException("choose two different players to compare");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", playerSummaryMap(left));
        out.put("right", playerSummaryMap(right));
        out.put("metrics", SquadAdvice.comparePlayers(left, right).stream().map(this::compareMetricMap).toList());
        return out;
    }

    @Tool(name = "fm26_current_tactic", description = "Live tactic from the last RAM load: formation, slot positions, and selected XI names. Roles are not in RAM yet.")
    @Transactional(readOnly = true)
    public Map<String, Object> currentTactic() {
        Map<String, Object> meta = players.metadata();
        Map<String, Object> out = new LinkedHashMap<>();
        Object formationStored = meta.get("tactic_formation");
        String formation = formationStored == null ? "" : String.valueOf(formationStored);
        Object slotsStored = meta.get("tactic_slots");
        String slots = slotsStored == null ? "" : String.valueOf(slotsStored);
        Object selectedStored = meta.get("tactic_selected");
        String selected = selectedStored == null ? "" : String.valueOf(selectedStored);
        out.put("formation", formation.isBlank() ? null : formation);
        out.put("slots", slots.isBlank() ? List.of() : List.of(slots.split("\\R")));
        out.put("selected_xi", selected.isBlank() ? List.of() : List.of(selected.split("\\R")));
        out.put("guidance", formation.isBlank()
                ? "No live tactic in the snapshot. Load from RAM with FM26 running, or paste slots into fm26_best_xi."
                : "Formation and selected XI are from RAM. In/out-of-possession roles are not exported; pass them to fm26_best_xi if you know them.");
        return out;
    }

    @Tool(name = "fm26_best_xi", description = "Pick a first XI from the managing club for a tactic. Injured players are omitted. Omit tacticSlots to use the live RAM formation when available. Otherwise pass 11 lines: position,inPossessionRole,outOfPossessionRole")
    @Transactional(readOnly = true)
    public Map<String, Object> bestXi(
            @ToolParam(description = "Managing club name") String managingClub,
            @ToolParam(required = false, description = "Eleven tactic slots, one per line: GK,Ball Playing GK,Sweeper Keeper. Omit to use the live RAM formation.") String tacticSlots) {
        roleAttributeRowsCache = null;
        ClubEntity club = requireClub(managingClub);
        String slotsText = tacticSlots;
        String source = "pasted";
        if (blank(slotsText)) {
            Object stored = players.metadata().get("tactic_slots");
            slotsText = stored == null ? "" : String.valueOf(stored);
            source = "ram";
        }
        List<SquadAdvice.XiSlot> slots;
        try {
            slots = parseTacticSlots(slotsText);
        } catch (RuntimeException ex) {
            return Map.of(
                    "error", "Invalid tacticSlots format: " + ex.getMessage(),
                    "expected", "POSITION,In Possession Role,Out of Possession Role");
        }
        List<PlayerEntity> squad = squadPlayers(club.getName());
        List<SquadAdvice.XiPick> picks = SquadAdvice.bestXi(squad, slots, this::slotRoleFit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", recruitmentClubMap(club));
        out.put("tactic_source", source);
        out.put("formation", "pasted".equals(source) ? null : players.metadata().get("tactic_formation"));
        out.put("xi", picks.stream().map(this::xiMap).toList());
        List<String> holes = picks.stream().filter(SquadAdvice.XiPick::hole).map(SquadAdvice.XiPick::position).toList();
        out.put("holes", holes);
        out.put("unavailable", unavailablePlayers(squad));
        out.put("suggested_buys", suggestedBuys(managingClub, picks));
        out.put("guidance", "ram".equals(source)
                ? "XI uses the live formation from RAM. Injured players are omitted. Roles were empty unless you pasted them. Call fm26_current_tactic for the actual selected names."
                : "Injured players are omitted. Upgrade holes with fm26_transfer_shortlist using the same position and in-possession role.");
        return out;
    }

    @Transactional(readOnly = true)
    public List<SquadAdvice.SellRow> sellRows(String managingClub) {
        return squadSellCandidates(managingClub).stream()
                .map(FmAiAssistentTools::toSellRow)
                .toList();
    }

    @Tool(name = "fm26_get_player_match_stats", description = "Get imported current-season match-level statistics for one player. Match statistics are available only when a compatible CSV export has been imported; an empty list means no imported match data, not zero performances.")
    @Transactional(readOnly = true)
    public Map<String, Object> getPlayerMatchStats(
            @ToolParam(description = "Player name. Exact match is preferred; contains match is used as fallback.") String name,
            @ToolParam(required = false, description = "Maximum matches to return") Integer limit) {
        PlayerEntity player = playerByName(name);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("player", player.getName());
        out.put("season", statsQuery.season().isBlank() ? null : statsQuery.season());
        out.put("stats_source", statsQuery.source().isBlank() ? null : statsQuery.source());
        out.put("matches", statsQuery.recentMatches(player, limit == null ? 20 : limit).stream().map(match -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", match.date());
            row.put("competition", match.competition());
            row.put("opponent", match.opponent());
            row.put("stats", match.stats());
            return row;
        }).toList());
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SquadSellCandidate> squadSellCandidates(String managingClub) {
        ClubEntity club = requireClub(managingClub);
        return SquadAdvice.sellShortlist(ownedSquad(squadPlayers(club.getName()), club.getName()), club)
                .stream()
                .map(FmAiAssistentTools::toSellCandidate)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SquadAdvice.ContractRow> contractRows(String managingClub) {
        return contractRecommendations(managingClub).stream()
                .map(FmAiAssistentTools::toContractRow)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractRecommendation> contractRecommendations(String managingClub) {
        ClubEntity club = requireClub(managingClub);
        return SquadAdvice.contractQueue(ownedSquad(squadPlayers(club.getName()), club.getName()), club)
                .stream()
                .map(FmAiAssistentTools::toContractRecommendation)
                .toList();
    }

    @Transactional(readOnly = true)
    public SquadAdvice.WageHealth wageHealth(String managingClub) {
        SquadWageHealth health = squadWageHealth(managingClub);
        return new SquadAdvice.WageHealth(health.wageBillWeekly(), health.payrollBudget(), health.usedFraction());
    }

    @Override
    @Transactional(readOnly = true)
    public SquadWageHealth squadWageHealth(String managingClub) {
        ClubEntity club = requireClub(managingClub);
        SquadAdvice.WageHealth health = SquadAdvice.wageHealth(
                ownedSquad(squadPlayers(club.getName()), club.getName()), club);
        return new SquadWageHealth(health.wageBillWeekly(), health.payrollBudget(), health.usedFraction());
    }

    private static SquadSellCandidate toSellCandidate(SquadAdvice.SellRow row) {
        return new SquadSellCandidate(row.rank(), row.name(), row.age(), row.position(), row.ca(), row.pa(),
                row.salaryWeekly(), row.askingPrice(), row.contractEnd(), row.depthAtPosition(),
                row.caVsFirstTeam(), row.recommendation(), row.sellScore(), row.reasons());
    }

    private static SquadAdvice.SellRow toSellRow(SquadSellCandidate row) {
        return new SquadAdvice.SellRow(row.rank(), row.name(), row.age(), row.position(), row.ca(), row.pa(),
                row.salaryWeekly(), row.askingPrice(), row.contractEnd(), row.depthAtPosition(),
                row.caVsFirstTeam(), row.recommendation(), row.sellScore(), row.reasons());
    }

    private static ContractRecommendation toContractRecommendation(SquadAdvice.ContractRow row) {
        return new ContractRecommendation(row.name(), row.position(), row.age(), row.ca(), row.salaryWeekly(),
                row.contractEnd(), row.daysUntilExpiry(), row.action(), row.reasons());
    }

    private static SquadAdvice.ContractRow toContractRow(ContractRecommendation row) {
        return new SquadAdvice.ContractRow(row.name(), row.position(), row.age(), row.ca(), row.salaryWeekly(),
                row.contractEnd(), row.daysUntilExpiry(), row.action(), row.reasons());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> unavailableForClub(String managingClub) {
        ClubEntity club = requireClub(managingClub);
        return unavailablePlayers(squadPlayers(club.getName()));
    }

    @Transactional(readOnly = true)
    public PlayerEntity playerByName(String name) {
        return pickPlayer(playerSearch.find(PlayerFilterCriteria.empty()), name, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> snapshotMetadata() {
        return players.metadata();
    }

    @Transactional(readOnly = true)
    public List<SquadAdvice.AcademyRow> academyRows(String managingClub, Integer maxAge) {
        return academyRows(managingClub, maxAge, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademyCandidate> academyCandidates(String managingClub, Integer maxAge) {
        return academyRows(managingClub, maxAge).stream()
                .map(FmAiAssistentTools::toAcademyCandidate)
                .toList();
    }

    private List<SquadAdvice.AcademyRow> academyRows(String managingClub, Integer maxAge, PositionSpec position) {
        ClubEntity club = requireClub(managingClub);
        int cap = maxAge == null ? DEFAULT_WONDERKID_MAX_AGE : maxAge;
        List<PlayerEntity> firstTeam = squadPlayers(club.getName()).stream()
                .filter(MarketValuation::hasPlayablePosition)
                .toList();
        int firstTeamCa = SquadAdvice.firstTeamAverageCa(firstTeam);
        List<PlayerEntity> youth = allPlayers().stream()
                .filter(MarketValuation::hasPlayablePosition)
                .filter(player -> position == null || positionScore(player, position) >= DEFAULT_MIN_POSITION_SCORE)
                .filter(player -> inClubFamily(player.getClub(), club.getName()))
                .toList();
        return SquadAdvice.academy(youth, cap, firstTeamCa);
    }

    /**
     * Web UI entry point: the same pipeline as fm26_transfer_shortlist with no candidate cap.
     */
    @Transactional(readOnly = true)
    public List<TransferShortlistRow> transferShortlistRows(
            String managingClub,
            String position,
            String roleName,
            Integer maxAge,
            Integer minCurrentAbility,
            Integer minPotentialAbility,
            Long maxAskingPrice,
            Integer maxWeeklySalary) {
        return transferShortlistCandidates(new TransferShortlistQuery(
                managingClub, position, roleName, maxAge, minCurrentAbility, minPotentialAbility,
                maxAskingPrice, maxWeeklySalary)).stream()
                .map(FmAiAssistentTools::toTransferShortlistRow)
                .toList();
    }

    /** Domain-facing typed recruitment seam used by desktop analysis modules. */
    @Override
    @Transactional(readOnly = true)
    public List<TransferShortlistCandidate> transferShortlistCandidates(TransferShortlistQuery query) {
        TransferShortlistQuery requested = query == null
                ? new TransferShortlistQuery(null, null, null, null, null, null, null, null)
                : query;
        RankedTransfers ranked = rankTransfers(
                requested.managingClub(), requested.position(), requested.roleName(), null, null,
                requested.maxAge(), requested.minCurrentAbility(), requested.minPotentialAbility(),
                requested.maxAskingPrice(), requested.maxWeeklySalary(), null, null, null, null, null, null, true);
        List<TransferShortlistRow> rows = new ArrayList<>();
        for (int index = 0; index < ranked.candidates().size(); index++) {
            ScoredCandidate candidate = ranked.candidates().get(index);
            rows.add(new TransferShortlistRow(
                    index + 1,
                    candidate.decisionScore(),
                    candidate.player().getName(),
                    effectiveAge(candidate.player()),
                    candidate.player().getNationality(),
                    candidate.player().getClub(),
                    candidate.positionScore(),
                    candidate.roleFit().score(),
                    value(candidate.player().getCa()),
                    value(candidate.player().getPa()),
                    effectivePotential(candidate.player()) - value(candidate.player().getCa()),
                    candidate.priceKnown() ? candidate.player().getAskingPrice() : null,
                    value(candidate.player().getSalaryWeeklyRaw()),
                    candidate.willingness().name().toLowerCase(Locale.ROOT),
                    candidate.freeAgent(),
                    Boolean.TRUE.equals(candidate.player().getTransferListed()),
                    Boolean.TRUE.equals(candidate.player().getInjured()),
                    candidateSignals(candidate.asCandidate(), ranked.benchmarkCa())));
        }
        return rows.stream().map(FmAiAssistentTools::toDomainCandidate).toList();
    }

    private static TransferShortlistCandidate toDomainCandidate(TransferShortlistRow row) {
        return new TransferShortlistCandidate(
                row.rank(), row.score(), row.name(), row.age(), row.nationality(), row.club(),
                row.positionScore(), row.roleFit(), row.ca(), row.pa(), row.developmentUpside(),
                row.askingPrice(), row.salaryWeekly(), row.willingness(), row.freeAgent(),
                row.transferListed(), row.injured(), row.signals());
    }

    private static TransferShortlistRow toTransferShortlistRow(TransferShortlistCandidate candidate) {
        return new TransferShortlistRow(
                candidate.rank(), candidate.score(), candidate.name(), candidate.age(), candidate.nationality(),
                candidate.club(), candidate.positionScore(), candidate.roleFit(), candidate.ca(), candidate.pa(),
                candidate.developmentUpside(), candidate.askingPrice(), candidate.salaryWeekly(),
                candidate.willingness(), candidate.freeAgent(), candidate.transferListed(), candidate.injured(),
                candidate.signals());
    }

    @Transactional(readOnly = true)
    public List<SquadAdvice.XiPick> bestXiRows(String managingClub, List<SquadAdvice.XiSlot> slots) {
        ClubEntity club = requireClub(managingClub);
        return SquadAdvice.bestXi(squadPlayers(club.getName()), slots, this::slotRoleFit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FirstXiPick> bestXi(String managingClub, List<FirstXiSlot> slots) {
        List<SquadAdvice.XiSlot> transportSlots = slots == null ? List.of() : slots.stream()
                .map(slot -> new SquadAdvice.XiSlot(
                        slot.position(), slot.inPossessionRole(), slot.outOfPossessionRole()))
                .toList();
        return bestXiRows(managingClub, transportSlots).stream()
                .map(FmAiAssistentTools::toFirstXiPick)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> suggestedBuys(String managingClub, List<SquadAdvice.XiPick> picks) {
        List<Map<String, Object>> upgrades = new ArrayList<>();
        for (SquadAdvice.XiPick pick : picks) {
            if (!pick.hole()) {
                continue;
            }
            boolean already = upgrades.stream().anyMatch(row -> pick.position().equals(row.get("position")));
            if (already) {
                continue;
            }
            Map<String, Object> shortlist;
            try {
                shortlist = transferShortlist(
                        managingClub, pick.position(), pick.inPossessionRole(), "In Possession",
                        null, null, null, null, null, null, null, null, null, null, null, null, 5);
            } catch (IllegalArgumentException ex) {
                upgrades.add(Map.of(
                        "position", pick.position(),
                        "in_possession_role", pick.inPossessionRole(),
                        "skipped", "role fit lookup failed: " + ex.getMessage()));
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("position", pick.position());
            row.put("in_possession_role", pick.inPossessionRole());
            row.put("tool", "fm26_transfer_shortlist");
            row.put("candidates", shortlist.get("candidates"));
            upgrades.add(row);
        }
        return upgrades;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> suggestedBuys(FirstXiSuggestionQuery query) {
        FirstXiSuggestionQuery requested = query == null
                ? new FirstXiSuggestionQuery(null, List.of()) : query;
        List<SquadAdvice.XiPick> picks = requested.picks().stream()
                .map(pick -> new SquadAdvice.XiPick(
                        pick.position(), pick.inPossessionRole(), pick.outOfPossessionRole(), pick.playerName(),
                        pick.positionScore(), pick.roleFit(), pick.ca(), pick.pa(), pick.hole()))
                .toList();
        return suggestedBuys(requested.managingClub(), picks);
    }

    private static AcademyCandidate toAcademyCandidate(SquadAdvice.AcademyRow row) {
        return new AcademyCandidate(row.name(), row.position(), row.age(), row.ca(), row.pa(), row.upside(),
                row.vsFirstTeam(), row.dualPositions(), row.salaryWeekly(), row.contractEnd());
    }

    private static FirstXiPick toFirstXiPick(SquadAdvice.XiPick row) {
        return new FirstXiPick(row.position(), row.inPossessionRole(), row.outOfPossessionRole(), row.playerName(),
                row.positionScore(), row.roleFit(), row.ca(), row.pa(), row.hole());
    }

    /** Typed transfer-shortlist candidate, shared by the MCP tool and the web UI. */
    public record TransferShortlistRow(
            int rank,
            double score,
            String name,
            Integer age,
            String nationality,
            String club,
            int positionScore,
            Double roleFit,
            int ca,
            int pa,
            int developmentUpside,
            Long askingPrice,
            int salaryWeekly,
            String willingness,
            boolean freeAgent,
            boolean transferListed,
            boolean injured,
            List<String> signals) {
    }

    /** Typed moneyball candidate, shared by the MCP tool and the web UI. */
    public record MoneyballRow(
            int rank,
            String name,
            Integer age,
            String nationality,
            String club,
            int positionScore,
            int ca,
            int pa,
            int developmentUpside,
            String ageCurve,
            String willingness,
            boolean freeAgent,
            long costFee,
            long salaryWeekly,
            MarketValuation.Deal deal,
            double qualityScore,
            int signingRating) {
    }

    /** Result of {@link #moneyballRows}: ranked candidates plus the market model behind them. */
    public record MoneyballResult(
            List<MoneyballRow> rows,
            int candidatePoolSize,
            int ratedCount,
            int pricedPlayers,
            int bucketCount) {
    }

    private record RankedTransfers(
            ClubEntity club,
            PositionSpec positionSpec,
            int positionMinimum,
            RoleProfile roleProfile,
            List<PlayerEntity> positionSquad,
            int benchmarkCa,
            long priceCap,
            long wageCeiling,
            int reputationMargin,
            Period minimumTime,
            Boolean transferAgreed,
            List<ScoredCandidate> candidates) {
    }

    private record MoneyballRated(List<DealCandidate> rated, int candidatePoolSize) {
    }

    private record MoneyballParameters(
            ClubEntity club,
            PositionSpec positionSpec,
            RoleProfile roleProfile,
            int positionMinimum,
            List<PlayerEntity> positionSquad,
            int benchmarkCa,
            int qualityFloor,
            long priceCap,
            long wageCeiling,
            int reputationMargin,
            Period minimumTime,
            boolean transferAgreed,
            Boolean transferListed,
            Boolean listedForLoan,
            Boolean injured,
            Integer maxAge,
            Integer minPa,
            Integer maxWeeklySalary) {
    }

    /**
     * Web UI entry point: the same pipeline as fm26_moneyball_shortlist with
     * defaults for role/phase/listing filters and no candidate cap.
     */
    @Transactional(readOnly = true)
    public MoneyballResult moneyballRows(
            String managingClub,
            String position,
            Integer minCurrentAbility,
            Integer minPotentialAbility,
            Integer maxAge,
            Long maxAskingPrice,
            Integer maxWeeklySalary) {
        MoneyballAnalysisResult result = moneyballCandidates(new MoneyballQuery(
                managingClub, position, minCurrentAbility, minPotentialAbility, maxAge,
                maxAskingPrice, maxWeeklySalary));
        List<MoneyballRow> rows = result.rows().stream()
                .map(FmAiAssistentTools::toMoneyballRow)
                .toList();
        return new MoneyballResult(rows, result.candidatePoolSize(), result.ratedCount(),
                result.pricedPlayers(), result.bucketCount());
    }

    /** Domain-facing typed value-analysis seam used by the moneyball workspace. */
    @Override
    @Transactional(readOnly = true)
    public MoneyballAnalysisResult moneyballCandidates(MoneyballQuery query) {
        MoneyballQuery requested = query == null
                ? new MoneyballQuery(null, null, null, null, null, null, null)
                : query;
        List<PlayerEntity> allPlayers = allPlayers();
        MoneyballParameters params = resolveMoneyballParameters(
                allPlayers, requested.managingClub(), requested.position(), null, null, null,
                requested.minCurrentAbility(), requested.minPotentialAbility(), requested.maxAge(),
                requested.maxAskingPrice(), requested.maxWeeklySalary(),
                null, null, null, null, null, null);
        MarketValuation market = MarketValuation.build(allPlayers);
        MoneyballRated rated = rateMoneyball(allPlayers, clubsByName(allClubs()), market, params);
        List<MoneyballCandidate> rows = new ArrayList<>();
        for (int index = 0; index < rated.rated().size(); index++) {
            rows.add(toMoneyballCandidate(index + 1, rated.rated().get(index)));
        }
        return new MoneyballAnalysisResult(rows, rated.candidatePoolSize(), rows.size(),
                market.pricedPlayers(), market.bucketCount());
    }

    private MoneyballParameters resolveMoneyballParameters(
            List<PlayerEntity> allPlayers,
            String managingClub,
            String position,
            String roleName,
            String phase,
            Integer minimumPositionScore,
            Integer minCurrentAbility,
            Integer minPotentialAbility,
            Integer maxAge,
            Long maxAskingPrice,
            Integer maxWeeklySalary,
            Integer reputationMargin,
            String minimumTimeAtCurrentClub,
            Boolean transferListed,
            Boolean listedForLoan,
            Boolean transferAgreed,
            Boolean injured) {
        ClubEntity club = requireClub(managingClub);
        PositionSpec positionSpec = resolvePosition(position);
        if (!blank(roleName) && positionSpec == null) {
            throw new IllegalArgumentException("position is required when roleName is supplied");
        }
        RoleProfile roleProfile = resolveRoleProfile(positionSpec, roleName, phase);
        int positionMinimum = positionSpec == null
                ? 1
                : Math.max(1, Math.min(20, minimumPositionScore == null ? DEFAULT_MIN_POSITION_SCORE : minimumPositionScore));

        List<PlayerEntity> squad = currentSquad(allPlayers, club.getName());
        List<PlayerEntity> positionSquad = positionSpec == null ? squad
                : squad.stream().filter(player -> positionScore(player, positionSpec) >= positionMinimum).toList();
        int benchmarkCa = firstTeamAverageCa(positionSquad);
        int qualityFloor = minCurrentAbility == null
                ? Math.max(0, benchmarkCa - DEFAULT_MONEYBALL_QUALITY_GAP)
                : minCurrentAbility;
        int safeReputationMargin = reputationMargin == null ? DEFAULT_REPUTATION_MARGIN : Math.max(0, reputationMargin);
        Period minimumTime = parsePeriod(minimumTimeAtCurrentClub, Period.ofYears(1));
        long priceCap = resolvePriceCap(maxAskingPrice, club.getTransferBudget());
        long wageCeiling = maxWeeklySalary == null
                ? inferredWeeklyWageCeiling(squad, club)
                : Math.max(0L, maxWeeklySalary);
        boolean effectiveTransferAgreed = transferAgreed == null ? Boolean.FALSE : transferAgreed;
        int effectiveMaxAge = maxAge == null ? DEFAULT_MONEYBALL_MAX_AGE : maxAge;
        return new MoneyballParameters(
                club, positionSpec, roleProfile, positionMinimum, positionSquad,
                benchmarkCa, qualityFloor, priceCap, wageCeiling, safeReputationMargin, minimumTime,
                effectiveTransferAgreed, transferListed, listedForLoan, injured,
                effectiveMaxAge, minPotentialAbility, maxWeeklySalary);
    }

    private static MoneyballRated rateMoneyball(
            List<PlayerEntity> allPlayers,
            Map<String, ClubEntity> clubsByName,
            MarketValuation market,
            MoneyballParameters params) {
        List<Candidate> pool = buildCandidatePool(
                allPlayers, clubsByName, params.club(), params.positionSpec(), params.positionMinimum(), params.roleProfile(),
                params.maxAge(), params.qualityFloor(), params.minPa(), params.transferListed(), params.listedForLoan(),
                params.transferAgreed(), params.injured(), params.priceCap(), params.maxWeeklySalary(),
                params.reputationMargin(), params.minimumTime(), true);
        List<DealCandidate> rated = new ArrayList<>();
        for (Candidate candidate : pool) {
            if (!candidate.priceKnown() && !candidate.freeAgent()) {
                continue;
            }
            MarketValuation.Deal deal = market.deal(candidate.player(), candidate.freeAgent() ? 0 : value(candidate.player().getAskingPrice()));
            if (deal == null) {
                continue;
            }
            double quality = qualityScore(candidate.player());
            rated.add(new DealCandidate(candidate, deal, quality, MarketValuation.signingRating(quality, deal)));
        }
        rated.sort(Comparator
                .comparingInt((DealCandidate candidate) -> candidate.signingRating()).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (DealCandidate candidate) -> candidate.deal().score()).reversed())
                .thenComparing(Comparator.comparingInt(
                        (DealCandidate candidate) -> value(candidate.candidate().player().getPa())).reversed())
                .thenComparing(Comparator.comparingInt(
                        (DealCandidate candidate) -> value(candidate.candidate().player().getCa())).reversed())
                .thenComparing(candidate -> candidate.candidate().player().getName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new MoneyballRated(rated, pool.size());
    }

    private static MoneyballCandidate toMoneyballCandidate(int rank, DealCandidate dealCandidate) {
        Candidate candidate = dealCandidate.candidate();
        PlayerEntity player = candidate.player();
        MarketValuation.Deal deal = dealCandidate.deal();
        return new MoneyballCandidate(
                rank,
                player.getName(),
                effectiveAge(player),
                player.getNationality(),
                player.getClub(),
                candidate.positionScore(),
                value(player.getCa()),
                value(player.getPa()),
                effectivePotential(player) - value(player.getCa()),
                ageCurve(player),
                candidate.willingness().name().toLowerCase(Locale.ROOT),
                candidate.freeAgent(),
                candidate.freeAgent() ? 0 : value(candidate.player().getAskingPrice()),
                value(candidate.player().getSalaryWeeklyRaw()),
                new MoneyballDeal(
                        deal.score(), deal.tier(), deal.market().price(), deal.market().wage(),
                        deal.market().samples(), deal.totalCost(), deal.marketCost()),
                dealCandidate.qualityScore(),
                dealCandidate.signingRating());
    }

    private static MoneyballRow toMoneyballRow(MoneyballCandidate candidate) {
        MoneyballDeal deal = candidate.deal();
        MarketValuation.Market market = new MarketValuation.Market(
                deal.marketPrice(), deal.marketWage(), deal.marketSamples());
        MarketValuation.Deal mcpDeal = new MarketValuation.Deal(
                deal.score(), deal.tier(), market, deal.totalCost(), deal.marketCost());
        return new MoneyballRow(
                candidate.rank(), candidate.name(), candidate.age(), candidate.nationality(), candidate.club(),
                candidate.positionScore(), candidate.ca(), candidate.pa(), candidate.developmentUpside(),
                candidate.ageCurve(), candidate.willingness(), candidate.freeAgent(), candidate.costFee(),
                candidate.salaryWeekly(), mcpDeal, candidate.qualityScore(), candidate.signingRating());
    }

    private static List<Candidate> buildCandidatePool(
            List<PlayerEntity> allPlayers,
            Map<String, ClubEntity> clubsByName,
            ClubEntity managingClub,
            PositionSpec positionSpec,
            int positionMinimum,
            RoleProfile roleProfile,
            Integer maxAge,
            Integer minCa,
            Integer minPa,
            Boolean transferListed,
            Boolean listedForLoan,
            Boolean transferAgreed,
            Boolean injured,
            long priceCap,
            Integer maxWeeklySalary,
            int reputationMargin,
            Period minimumTime,
            boolean dropUnwilling) {
        List<Candidate> pool = new ArrayList<>();
        for (PlayerEntity player : allPlayers) {
            if (belongsToClub(player, managingClub.getName())
                    || !sameGender(player.getGender(), managingClub.getGender())
                    || !inRange(effectiveAge(player), null, maxAge)
                    || !inRange(player.getCa(), minCa, null)
                    || !inRange(player.getPa(), minPa, null)
                    || !matchesBoolean(player.getTransferListed(), transferListed)
                    || !matchesBoolean(player.getListedForLoan(), listedForLoan)
                    || !matchesBoolean(player.getTransferAgreed(), transferAgreed)
                    || !matchesBoolean(player.getInjured(), injured)) {
                continue;
            }
            if (!MarketValuation.hasPlayablePosition(player)) {
                continue;
            }
            int candidatePositionScore = positionSpec == null ? bestPositionScore(player) : positionScore(player, positionSpec);
            if (candidatePositionScore < positionMinimum) {
                continue;
            }
            long askingPrice = value(player.getAskingPrice());
            boolean priceKnown = askingPrice > 0;
            boolean freeAgent = blank(player.getClub());
            if (priceKnown && priceCap != Long.MAX_VALUE && askingPrice > priceCap) {
                continue;
            }
            if (!salaryWithinMax(player.getSalaryWeeklyRaw(), maxWeeklySalary)) {
                continue;
            }
            ClubEntity sourceClub = clubsByName.get(normalize(player.getClub()));
            Willingness willingness = willingness(player, managingClub, sourceClub, reputationMargin, minimumTime);
            if (dropUnwillingCandidate(dropUnwilling, willingness == Willingness.LOW, priceKnown, freeAgent)) {
                continue;
            }
            pool.add(new Candidate(
                    player,
                    sourceClub,
                    candidatePositionScore,
                    roleFit(player, roleProfile),
                    willingness,
                    priceKnown,
                    freeAgent));
        }
        return pool;
    }

    private Map<String, Object> moneyballMap(
            int rank,
            DealCandidate dealCandidate,
            int benchmarkCa,
            long priceCap,
            long wageCeiling) {
        Candidate candidate = dealCandidate.candidate();
        PlayerEntity player = candidate.player();
        MarketValuation.Deal deal = dealCandidate.deal();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rank", rank);
        out.put("signing_rating", dealCandidate.signingRating());
        out.put("quality_score", Math.round(dealCandidate.qualityScore() * 100));
        out.put("deal_tier", deal.tier());
        out.put("deal_score", deal.score());
        out.put("market_value", deal.market().price());
        out.put("market_wage_weekly", deal.market().wage());
        out.put("market_samples", deal.market().samples());
        out.put("cost_fee", candidate.freeAgent() ? 0 : value(player.getAskingPrice()));
        out.put("cost_wages_3yr", MarketValuation.CONTRACT_YEARS * MarketValuation.WEEKS_PER_YEAR
                * (player.getSalaryWeeklyRaw() == null ? deal.market().wage() : player.getSalaryWeeklyRaw().longValue()));
        out.put("total_cost_3yr", deal.totalCost());
        out.put("value_gap", deal.marketCost() - deal.totalCost());
        out.put("name", player.getName());
        out.put("age", effectiveAge(player));
        out.put("nationality", player.getNationality());
        out.put("club", player.getClub());
        out.put("position_score", candidate.positionScore());
        out.put("ca", player.getCa());
        out.put("pa", player.getPa());
        out.put("development_upside", effectivePotential(player) - value(player.getCa()));
        out.put("age_curve", ageCurve(player));
        out.put("ca_vs_squad_position_avg", value(player.getCa()) - benchmarkCa);
        if (candidate.roleFit().score() != null) {
            out.put("role_fit", candidate.roleFit().score());
            out.put("role_strengths", candidate.roleFit().strengths());
            out.put("role_gaps", candidate.roleFit().gaps());
        }
        out.put("asking_price", candidate.priceKnown() ? player.getAskingPrice() : null);
        out.put("price_fit", candidate.freeAgent() ? "free_agent" : !candidate.priceKnown() ? "unknown"
                : !priceCapKnown(priceCap) ? "budget_unknown"
                : value(player.getAskingPrice()) <= priceCap ? "within_budget" : "over_budget");
        out.put("salary_weekly", player.getSalaryWeeklyRaw());
        out.put("wage_fit", wageFits(player.getSalaryWeeklyRaw(), wageCeiling));
        out.put("willingness", candidate.willingness().name().toLowerCase(Locale.ROOT));
        out.put("signals", candidateSignals(candidate, benchmarkCa));
        return out;
    }

    private static Map<String, Object> marketModelMap(MarketValuation market) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("priced_players", market.pricedPlayers());
        out.put("buckets", market.bucketCount());
        out.put("minimum_samples_per_bucket", MarketValuation.MIN_BUCKET_SAMPLES);
        out.put("contract_years_for_wage_cost", MarketValuation.CONTRACT_YEARS);
        return out;
    }

    private List<PlayerEntity> allPlayers() {
        return players.findAllPlayerEntities();
    }

    private List<ClubEntity> allClubs() {
        return clubs.findAllClubs();
    }

    private ClubEntity requireClub(String clubName) {
        return clubs.requireNamed(clubName);
    }

    private PlayerEntity requirePlayer(String name) {
        return requirePlayer(name, false);
    }

    private PlayerEntity requirePlayer(String name, boolean uniqueContains) {
        return pickPlayer(allPlayers(), name, uniqueContains);
    }

    static PlayerEntity pickPlayer(List<PlayerEntity> players, String name, boolean uniqueContains) {
        String normalized = normalize(name);
        List<PlayerEntity> exact = players.stream()
                .filter(player -> normalize(player.getName()).equals(normalized))
                .toList();
        List<PlayerEntity> rows = exact.isEmpty()
                ? players.stream().filter(player -> contains(player.getName(), name)).toList()
                : exact;
        if (uniqueContains && rows.size() > 1) {
            throw new IllegalArgumentException("player name is ambiguous: " + name);
        }
        return rows.stream()
                .filter(MarketValuation::hasPlayablePosition)
                .max(Comparator.comparingInt(player -> value(player.getCa())))
                .orElseThrow(() -> new IllegalArgumentException(
                        !exact.isEmpty() ? "player found but has no playable position: " + name
                                : "player not found: " + name));
    }

    static boolean samePlayer(PlayerEntity left, PlayerEntity right) {
        if (left == right) {
            return true;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        String leftRecord = normalize(String.valueOf(left.getRecordAddress()));
        String rightRecord = normalize(String.valueOf(right.getRecordAddress()));
        if (!leftRecord.isEmpty() && !rightRecord.isEmpty() && !leftRecord.equals(rightRecord)) {
            return false;
        }
        return normalize(left.getName()).equals(normalize(right.getName()))
                && normalize(left.getClub()).equals(normalize(right.getClub()));
    }

    private Double slotRoleFit(PlayerEntity player, SquadAdvice.XiSlot slot) {
        PositionSpec spec = resolvePosition(slot.position());
        RoleProfile inPossession = tryRoleProfile(spec, slot.inPossessionRole(), "In Possession");
        RoleProfile outOfPossession = tryRoleProfile(spec, slot.outOfPossessionRole(), "Out of Possession");
        Double inFit = roleFit(player, inPossession).score();
        Double outFit = roleFit(player, outOfPossession).score();
        if (inFit == null) {
            return outFit;
        }
        if (outFit == null) {
            return inFit;
        }
        return round1((inFit + outFit) / 2.0);
    }

    private RoleProfile tryRoleProfile(PositionSpec spec, String roleName, String phase) {
        if (blank(roleName) || spec == null) {
            return RoleProfile.empty();
        }
        try {
            return resolveRoleProfile(spec, roleName, phase);
        } catch (IllegalArgumentException ex) {
            return RoleProfile.empty();
        }
    }

    public static List<SquadAdvice.XiSlot> parseTacticSlots(String tacticSlots) {
        if (tacticSlots == null || tacticSlots.isBlank()) {
            throw new IllegalArgumentException("tacticSlots is required. One line per slot: position,inPossessionRole,outOfPossessionRole");
        }
        List<SquadAdvice.XiSlot> slots = new ArrayList<>();
        for (String line : tacticSlots.split("\\R")) {
            if (line.isBlank() || line.toLowerCase(Locale.ROOT).startsWith("player")) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 1 || parts[0].isBlank()) {
                continue;
            }
            String position = Positions.canonicalCode(parts[0].trim());
            String inPossession = parts.length > 1 ? parts[1].trim() : "";
            String outOfPossession = parts.length > 2 ? parts[2].trim() : "";
            slots.add(new SquadAdvice.XiSlot(position, inPossession, outOfPossession));
        }
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("no tactic slots parsed");
        }
        if (slots.size() > 11) {
            throw new IllegalArgumentException("expected at most 11 tactic slots, got " + slots.size());
        }
        return slots;
    }

    private Map<String, Object> sellMap(SquadAdvice.SellRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rank", row.rank());
        out.put("name", row.name());
        out.put("age", row.age());
        out.put("position", row.position());
        out.put("ca", row.ca());
        out.put("pa", row.pa());
        out.put("salary_weekly", row.salaryWeekly());
        out.put("asking_price", row.askingPrice());
        out.put("contract_end", row.contractEnd());
        out.put("depth_at_position", row.depthAtPosition());
        out.put("ca_vs_first_team", row.caVsFirstTeam());
        out.put("recommendation", row.recommendation());
        out.put("sell_score", row.sellScore());
        out.put("reasons", row.reasons());
        return out;
    }

    private Map<String, Object> compareMetricMap(SquadAdvice.PlayerCompareMetric metric) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", metric.label());
        out.put("left", metric.left());
        out.put("right", metric.right());
        out.put("winner", metric.winner() == null ? "tie" : metric.winner() < 0 ? "left" : "right");
        return out;
    }

    private Map<String, Object> xiMap(SquadAdvice.XiPick pick) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("position", pick.position());
        out.put("in_possession_role", pick.inPossessionRole());
        out.put("out_of_possession_role", pick.outOfPossessionRole());
        out.put("player", pick.playerName());
        out.put("position_score", pick.positionScore());
        out.put("role_fit", pick.roleFit());
        out.put("ca", pick.ca());
        out.put("pa", pick.pa());
        out.put("hole", pick.hole());
        return out;
    }

    private List<Map<String, Object>> unavailablePlayers(List<PlayerEntity> squad) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlayerEntity player : squad) {
            if (!MarketValuation.hasPlayablePosition(player) || !Boolean.TRUE.equals(player.getInjured())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", player.getName());
            row.put("position", Positions.bestCode(player));
            row.put("injury", player.getInjury());
            row.put("expected_return", player.getInjuryExpectedReturn());
            row.put("min_days_remaining", player.getInjuryMinDaysRemaining());
            row.put("max_days_remaining", player.getInjuryMaxDaysRemaining());
            rows.add(row);
        }
        return rows;
    }

    private List<PlayerEntity> squadPlayers(String clubName) {
        if (blank(clubName)) {
            return List.of();
        }
        return currentSquad(players.findPlayerEntities(PlayerFilterCriteria.clubOnly(clubName)), clubName);
    }

    private static Map<String, ClubEntity> clubsByName(List<ClubEntity> clubs) {
        Map<String, ClubEntity> out = new HashMap<>();
        for (ClubEntity club : clubs) {
            if (blank(club.getName())) {
                continue;
            }
            out.merge(normalize(club.getName()), club, (left, right) ->
                    value(right.getReputation()) > value(left.getReputation()) ? right : left);
        }
        return out;
    }

    static List<PlayerEntity> ownedSquad(List<PlayerEntity> squad, String clubName) {
        return squad.stream()
                .filter(player -> equalsIgnoreCase(player.getClub(), clubName))
                .toList();
    }

    static List<PlayerEntity> currentSquad(List<PlayerEntity> players, String clubName) {
        return players.stream()
                .filter(player -> equalsIgnoreCase(player.getPlayingClub(), clubName)
                        || (blank(player.getPlayingClub()) && equalsIgnoreCase(player.getClub(), clubName)))
                .toList();
    }

    private static boolean belongsToClub(PlayerEntity player, String clubName) {
        return inClubFamily(player.getClub(), clubName) || inClubFamily(player.getPlayingClub(), clubName);
    }

    static boolean dropUnwillingCandidate(boolean dropUnwilling, boolean lowWillingness, boolean priceKnown, boolean freeAgent) {
        return PlayerAnalysisRules.dropUnwillingCandidate(dropUnwilling, lowWillingness, priceKnown, freeAgent);
    }

    static boolean inClubFamily(String playerClub, String managingClub) {
        return PlayerAnalysisRules.inClubFamily(playerClub, managingClub);
    }

    static String clubFamilyStem(String name) {
        return PlayerAnalysisRules.clubFamilyStem(name);
    }

    private static boolean sameGender(String playerGender, String clubGender) {
        return blank(playerGender) || blank(clubGender) || normalize(playerGender).equals(normalize(clubGender));
    }

    private static PositionSpec resolvePosition(String position) {
        if (blank(position)) {
            return null;
        }
        try {
            String code = Positions.canonicalCode(position);
            return new PositionSpec(code, Positions.column(code), Positions.positionGroup(code));
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedPositionException(position, ex);
        }
    }

    private RoleProfile resolveRoleProfile(PositionSpec position, String roleName, String phase) {
        if (blank(roleName)) {
            return RoleProfile.empty();
        }
        List<RoleAttributeRow> positionRows = roleAttributeRows().stream()
                .filter(row -> position == null || equalsIgnoreCase(row.positionGroup(), position.positionGroup()))
                .filter(row -> blank(phase) || equalsIgnoreCase(row.phase(), phase))
                .toList();
        List<RoleAttributeRow> exactRows = positionRows.stream()
                .filter(row -> roleKeysEqual(row.roleName(), roleName))
                .toList();
        List<RoleAttributeRow> rows = exactRows.isEmpty()
                ? positionRows.stream().filter(row -> rolesMatch(row.roleName(), roleName)).toList()
                : exactRows;
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("role not found for " + position.positionGroup() + ": " + roleName);
        }
        if (exactRows.isEmpty()) {
            String firstRole = rows.getFirst().roleName();
            rows = rows.stream().filter(row -> roleKeysEqual(row.roleName(), firstRole)).toList();
        }

        Map<String, Integer> weights = new LinkedHashMap<>();
        List<String> roles = new ArrayList<>();
        List<String> primary = new ArrayList<>();
        List<String> secondary = new ArrayList<>();
        for (RoleAttributeRow row : rows) {
            String role = row.roleName() + " (" + row.phase() + ")";
            if (!roles.contains(role)) {
                roles.add(role);
            }
            String attribute = playerAttributeKey(row.attributeName());
            int weight = "primary".equalsIgnoreCase(row.attributePriority()) ? 2 : 1;
            weights.merge(attribute, weight, Math::max);
            List<String> target = weight == 2 ? primary : secondary;
            if (!target.contains(attribute)) {
                target.add(attribute);
            }
        }
        secondary.removeAll(primary);
        return new RoleProfile(roles, primary, secondary, weights);
    }

    private List<RoleAttributeRow> roleAttributeRows() {
        List<RoleAttributeRow> cached = roleAttributeRowsCache;
        if (cached != null) {
            return cached;
        }
        List<RoleAttributeRow> rows = jdbc.query("""
                        SELECT r.game, r.position_group, r.role_name, r.phase,
                               ra.attribute_priority, a.attribute_name, ra.sort_order
                        FROM fm_role r
                        JOIN fm_role_attribute ra ON ra.role_id = r.id
                        JOIN fm_attribute a ON a.id = ra.attribute_id
                        ORDER BY r.position_group, r.role_name, r.phase,
                                 CASE ra.attribute_priority WHEN 'primary' THEN 0 ELSE 1 END,
                                 ra.sort_order, a.attribute_name
                        """,
                 (rs, rowNum) -> new RoleAttributeRow(
                         rs.getString("game"),
                         rs.getString("position_group"),
                         rs.getString("role_name"),
                         rs.getString("phase"),
                         rs.getString("attribute_priority"),
                         rs.getString("attribute_name"),
                         rs.getInt("sort_order")));
        roleAttributeRowsCache = rows;
        return rows;
    }

    private static int positionScore(PlayerEntity player, PositionSpec position) {
        Object score = player.getColumnValue(position.column());
        return score instanceof Number number ? number.intValue() : 0;
    }

    private static int bestPositionScore(PlayerEntity player) {
        return AttributeDefinitions.POSITION_FIELDS.stream()
                .map(FmAiAssistentTools::columnName)
                .map(player::getColumnValue)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .max()
                .orElse(0);
    }

    private static long inferredWeeklyWageCeiling(List<PlayerEntity> squad, ClubEntity club) {
        long currentMaximum = squad.stream().map(PlayerEntity::getSalaryWeeklyRaw).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).max().orElse(0L);
        long squadStructure = Math.round(currentMaximum * 1.25);
        long payrollStructure = squad.isEmpty() ? 0L : value(club.getPayrollBudget()) / squad.size() * 2L;
        return Math.max(squadStructure, payrollStructure);
    }

    private static Willingness willingness(
            PlayerEntity player,
            ClubEntity managingClub,
            ClubEntity sourceClub,
            int reputationMargin,
            Period minimumTimeAtCurrentClub) {
        int managingReputation = value(managingClub.getReputation());
        int playerGap = highestReputation(player) - managingReputation;
        int sourceGap = sourceClub == null ? 0 : value(sourceClub.getReputation()) - managingReputation;
        boolean listed = Boolean.TRUE.equals(player.getTransferListed()) || Boolean.TRUE.equals(player.getListedForLoan());
        boolean recentlyJoined = recentlyJoinedCurrentClub(player, minimumTimeAtCurrentClub);

        if (playerGap > reputationMargin + (listed ? 500 : 0)
                || (sourceGap > SOURCE_CLUB_REPUTATION_MARGIN && !listed)
                || (recentlyJoined && !listed)) {
            return Willingness.LOW;
        }
        if (playerGap > 0 || sourceGap > 250 || recentlyJoined) {
            return Willingness.MEDIUM;
        }
        return Willingness.HIGH;
    }

    private static RoleFit roleFit(PlayerEntity player, RoleProfile role) {
        if (role.isEmpty()) {
            return RoleFit.empty();
        }
        List<AttributeScore> scores = role.weights().entrySet().stream()
                .map(entry -> new AttributeScore(
                        entry.getKey(),
                        attributeScore(player, entry.getKey()),
                        entry.getValue()))
                .toList();
        int weightTotal = scores.stream().mapToInt(AttributeScore::weight).sum();
        double weightedTotal = scores.stream().mapToDouble(score -> score.score() * score.weight()).sum();
        Double fit = weightTotal == 0 ? null : round1(weightedTotal / weightTotal);
        List<String> strengths = scores.stream()
                .sorted(Comparator.comparingInt(AttributeScore::score).reversed().thenComparing(AttributeScore::name))
                .limit(3)
                .map(AttributeScore::compact)
                .toList();
        List<String> gaps = scores.stream()
                .sorted(Comparator.comparingInt(AttributeScore::score).thenComparing(AttributeScore::name))
                .limit(3)
                .map(AttributeScore::compact)
                .toList();
        return new RoleFit(fit, strengths, gaps);
    }

    private static int attributeScore(PlayerEntity player, String attribute) {
        Object value = player.getColumnValue(attribute.toUpperCase(Locale.ROOT));
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double decisionScore(
            PlayerEntity player,
            int positionScore,
            int benchmarkCa,
            boolean priceKnown,
            boolean freeAgent,
            long priceCap,
            long wageCeiling,
            Willingness willingness,
            RoleFit roleFit) {
        double position = clamp(positionScore / 20.0);
        double ca = clamp(value(player.getCa()) / 200.0);
        double futureQuality = clamp(effectivePotential(player) / 200.0);
        double improvement = benchmarkCa <= 0 ? 1.0 : clamp((value(player.getCa()) - benchmarkCa + 25.0) / 50.0);
        double growth = clamp((effectivePotential(player) - value(player.getCa())) / 50.0);
        double age = ageScore(player);
        double price = freeAgent ? 1.0 : !priceKnown ? 0.35 : !priceCapKnown(priceCap)
                ? 0.35
                : clamp(1.0 - value(player.getAskingPrice()) / (double) priceCap);
        double wage = wageFitScore(player.getSalaryWeeklyRaw(), wageCeiling);
        double willingnessScore = switch (willingness) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.6;
            case LOW -> 0.25;
        };

        if (roleFit.score() != null) {
            return round1(position * 15 + ca * 20 + futureQuality * 10 + improvement * 15 + growth * 5 + age * 5
                    + clamp(roleFit.score() / 20.0) * 20 + ((price + wage) / 2.0) * 5 + willingnessScore * 5);
        }
        return round1(position * 20 + ca * 25 + futureQuality * 10 + improvement * 15 + growth * 10 + age * 10
                + ((price + wage) / 2.0) * 5 + willingnessScore * 5);
    }

    private RankedTransfers rankTransfers(
            String managingClub,
            String position,
            String roleName,
            String phase,
            Integer minimumPositionScore,
            Integer maxAge,
            Integer minCurrentAbility,
            Integer minPotentialAbility,
            Long maxAskingPrice,
            Integer maxWeeklySalary,
            Integer reputationMargin,
            String minimumTimeAtCurrentClub,
            Boolean transferListed,
            Boolean listedForLoan,
            Boolean transferAgreed,
            Boolean injured,
            boolean dropUnwilling) {
        ClubEntity club = requireClub(managingClub);
        PositionSpec positionSpec = resolvePosition(position);
        if (!blank(roleName) && positionSpec == null) {
            throw new IllegalArgumentException("position is required when roleName is supplied");
        }
        int positionMinimum = positionSpec == null
                ? 1
                : Math.max(1, Math.min(20, minimumPositionScore == null ? DEFAULT_MIN_POSITION_SCORE : minimumPositionScore));
        RoleProfile roleProfile = resolveRoleProfile(positionSpec, roleName, phase);

        List<PlayerEntity> allPlayers = allPlayers();
        Map<String, ClubEntity> clubsByName = clubsByName(allClubs());
        List<PlayerEntity> squad = currentSquad(allPlayers, club.getName());
        List<PlayerEntity> positionSquad = positionSpec == null
                ? squad
                : squad.stream().filter(player -> positionScore(player, positionSpec) >= positionMinimum).toList();

        int safeReputationMargin = reputationMargin == null ? DEFAULT_REPUTATION_MARGIN : Math.max(0, reputationMargin);
        Period minimumTime = parsePeriod(minimumTimeAtCurrentClub, Period.ofYears(1));
        long priceCap = resolvePriceCap(maxAskingPrice, club.getTransferBudget());
        long wageCeiling = maxWeeklySalary == null
                ? inferredWeeklyWageCeiling(squad, club)
                : Math.max(0L, maxWeeklySalary);
        int benchmarkCa = firstTeamAverageCa(positionSquad);
        Boolean effectiveTransferAgreed = transferAgreed == null ? Boolean.FALSE : transferAgreed;

        List<Candidate> pool = buildCandidatePool(
                allPlayers, clubsByName, club, positionSpec, positionMinimum, roleProfile,
                maxAge, minCurrentAbility, minPotentialAbility, transferListed, listedForLoan,
                effectiveTransferAgreed, injured, priceCap, maxWeeklySalary, safeReputationMargin, minimumTime,
                dropUnwilling);
        List<ScoredCandidate> candidatePool = new ArrayList<>(pool.stream()
                .map(candidate -> new ScoredCandidate(
                        candidate.player(),
                        candidate.sourceClub(),
                        candidate.positionScore(),
                        candidate.roleFit(),
                        candidate.willingness(),
                        candidate.priceKnown(),
                        candidate.freeAgent(),
                        decisionScore(
                                candidate.player(),
                                candidate.positionScore(),
                                benchmarkCa,
                                candidate.priceKnown(),
                                candidate.freeAgent(),
                                priceCap,
                                wageCeiling,
                                candidate.willingness(),
                                candidate.roleFit())))
                .toList());

        candidatePool.sort(Comparator
                .comparingDouble(ScoredCandidate::decisionScore).reversed()
                .thenComparing(Comparator.comparingInt(
                        (ScoredCandidate candidate) -> value(candidate.player().getPa())).reversed())
                .thenComparing(Comparator.comparingInt(
                        (ScoredCandidate candidate) -> value(candidate.player().getCa())).reversed())
                .thenComparing(candidate -> candidate.player().getName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new RankedTransfers(
                club, positionSpec, positionMinimum, roleProfile, positionSquad, benchmarkCa, priceCap, wageCeiling,
                safeReputationMargin, minimumTime, effectiveTransferAgreed, candidatePool);
    }

    private static Map<String, Object> recommendationMap(
            int rank,
            ScoredCandidate candidate,
            ClubEntity managingClub,
            int benchmarkCa,
            long priceCap,
            long wageCeiling) {
        PlayerEntity player = candidate.player();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rank", rank);
        out.put("score", candidate.decisionScore());
        out.put("name", player.getName());
        out.put("age", effectiveAge(player));
        out.put("nationality", player.getNationality());
        out.put("club", player.getClub());
        out.put("position_score", candidate.positionScore());
        out.put("ca", player.getCa());
        out.put("pa", player.getPa());
        out.put("development_upside", effectivePotential(player) - value(player.getCa()));
        out.put("age_curve", ageCurve(player));
        out.put("ca_vs_squad_position_avg", value(player.getCa()) - benchmarkCa);
        if (candidate.roleFit().score() != null) {
            out.put("role_fit", candidate.roleFit().score());
            out.put("role_strengths", candidate.roleFit().strengths());
            out.put("role_gaps", candidate.roleFit().gaps());
        }
        out.put("asking_price", candidate.priceKnown() ? player.getAskingPrice() : null);
        out.put("price_fit", candidate.freeAgent() ? "free_agent" : !candidate.priceKnown() ? "unknown"
                : !priceCapKnown(priceCap) ? "budget_unknown"
                : value(player.getAskingPrice()) <= priceCap ? "within_budget" : "over_budget");
        out.put("salary_weekly", player.getSalaryWeeklyRaw());
        out.put("wage_fit", wageFits(player.getSalaryWeeklyRaw(), wageCeiling));
        out.put("willingness", candidate.willingness().name().toLowerCase(Locale.ROOT));
        out.put("player_reputation", highestReputation(player));
        out.put("reputation_gap", highestReputation(player) - value(managingClub.getReputation()));
        out.put("source_club_reputation", candidate.sourceClub() == null ? null : candidate.sourceClub().getReputation());
        out.put("joined_club", player.getJoinedClubDate());
        out.put("contract_end", player.getContractEndDate());
        out.put("transfer_listed", player.getTransferListed());
        out.put("loan_listed", player.getListedForLoan());
        out.put("injured", player.getInjured());
        out.put("signals", candidateSignals(candidate.asCandidate(), benchmarkCa));
        return out;
    }

    private static List<String> candidateSignals(Candidate candidate, int benchmarkCa) {
        PlayerEntity player = candidate.player();
        List<String> signals = new ArrayList<>();
        signals.add(candidate.positionScore() >= 18 ? "natural_position" : "accomplished_position");
        int caDifference = value(player.getCa()) - benchmarkCa;
        if (benchmarkCa > 0) {
            signals.add("ca_vs_squad:" + signed(caDifference));
        }
        signals.add("development_upside:" + signed(effectivePotential(player) - value(player.getCa())));
        signals.add(candidate.freeAgent() ? "free_agent" : candidate.priceKnown() ? "price_known" : "price_unknown");
        if (Boolean.TRUE.equals(player.getTransferListed())) {
            signals.add("transfer_listed");
        }
        if (Boolean.TRUE.equals(player.getListedForLoan())) {
            signals.add("loan_listed");
        }
        if (Boolean.TRUE.equals(player.getInjured())) {
            signals.add("injured");
        }
        return signals;
    }

    private static Map<String, Object> recruitmentClubMap(ClubEntity club) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", club.getName());
        out.put("competition", club.getCompetition());
        out.put("nation", club.getNation());
        out.put("gender", club.getGender());
        out.put("reputation", club.getReputation());
        out.put("balance", club.getBalance());
        out.put("transfer_budget", club.getTransferBudget());
        out.put("payroll_budget_weekly", club.getPayrollBudget());
        return out;
    }

    private static Map<String, Object> positionBenchmark(List<PlayerEntity> squad, PositionSpec position) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("position", position == null ? "any" : position.code());
        out.put("players", squad.size());
        out.put("average_ca", averageInt(squad, PlayerEntity::getCa));
        out.put("first_team_average_ca", firstTeamAverageCa(squad));
        out.put("best_ca", squad.stream().map(PlayerEntity::getCa).filter(Objects::nonNull).max(Integer::compareTo).orElse(0));
        out.put("current_options", squad.stream()
                .sorted(Comparator.comparing((PlayerEntity player) -> value(player.getCa())).reversed())
                .limit(6)
                .map(player -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", player.getName());
                    row.put("age", effectiveAge(player));
                    row.put("ca", player.getCa());
                    row.put("pa", player.getPa());
                    if (position != null) {
                        row.put("position_score", positionScore(player, position));
                    }
                    row.put("salary_weekly", player.getSalaryWeeklyRaw());
                    return row;
                })
                .toList());
        return out;
    }

    private static int firstTeamAverageCa(List<PlayerEntity> squad) {
        return (int) Math.round(squad.stream()
                .map(PlayerEntity::getCa)
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .limit(11)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));
    }

    private static int effectivePotential(PlayerEntity player) {
        int ca = value(player.getCa());
        int availableGrowth = Math.max(0, value(player.getPa()) - ca);
        return ca + (int) Math.round(availableGrowth * developmentFactor(player));
    }

    /** 0-1 quality: half current ability, half age-adjusted potential. Young players' PA counts, veterans' CA only. */
    private static double qualityScore(PlayerEntity player) {
        double ca = clamp(value(player.getCa()) / 200.0);
        double upside = clamp(effectivePotential(player) / 200.0);
        return 0.5 * ca + 0.5 * upside;
    }

    private static double developmentFactor(PlayerEntity player) {
        int age = Optional.ofNullable(effectiveAge(player)).orElse(40);
        if (age <= 21) {
            return 1.0;
        }
        if (age == 22) {
            return 0.9;
        }
        if (age == 23) {
            return 0.8;
        }
        if (age == 24) {
            return 0.65;
        }
        if (age == 25) {
            return 0.5;
        }
        if (age == 26) {
            return 0.35;
        }
        if (age == 27) {
            return 0.2;
        }
        if (age == 28) {
            return 0.1;
        }
        return 0.0;
    }

    private static double ageScore(PlayerEntity player) {
        int age = Optional.ofNullable(effectiveAge(player)).orElse(40);
        if (age <= 20) {
            return 1.0;
        }
        if (age <= 23) {
            return 0.95;
        }
        if (age <= 26) {
            return 0.85;
        }
        if (age <= 29) {
            return 0.7;
        }
        if (age <= 32) {
            return 0.5;
        }
        if (age <= 35) {
            return 0.25;
        }
        return 0.05;
    }

    private static String ageCurve(PlayerEntity player) {
        int age = Optional.ofNullable(effectiveAge(player)).orElse(40);
        if (age <= 23) {
            return "developing";
        }
        if (age <= 29) {
            return "prime";
        }
        if (age <= 33) {
            return "late_prime";
        }
        return "veteran";
    }

    static double wageFitScore(Integer weeklyWage, long wageCeiling) {
        if (weeklyWage == null || weeklyWage == 0 || wageCeiling <= 0) {
            return 0.35;
        }
        return clamp(wageCeiling / (double) weeklyWage);
    }

    public static Long askingPriceOrNull(Long askingPrice) {
        return askingPrice == null || askingPrice == 0L ? null : askingPrice;
    }

    static void stripUnreadRamFields(Map<String, Object> out) {
        if (out == null) {
            return;
        }
        for (String field : FIELDS_NOT_IN_RAM) {
            out.remove(field);
            out.remove(field.toUpperCase(Locale.ROOT));
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private Map<String, Object> playerSummaryMap(PlayerEntity player) {
        return playerSummaryMap(player, players.metadata());
    }

    private Map<String, Object> playerSummaryMap(PlayerEntity player, Map<String, Object> metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", player.getId());
        out.put("name", player.getName());
        out.put("age", effectiveAge(player));
        out.put("gender", player.getGender());
        out.put("nationality", player.getNationality());
        out.put("club", player.getClub());
        out.put("playing_club", player.getPlayingClub());
        out.put("playing_nation", playingNation(player));
        out.put("playing_competition", playingCompetition(player));
        out.put("position_text", PositionTextFormatter.format(player));
        out.put("ca", player.getCa());
        out.put("pa", player.getPa());
        out.put("asking_price", askingPriceOrNull(player.getAskingPrice()));
        out.put("salary_weekly_raw", player.getSalaryWeeklyRaw());
        out.put("joined_club_date", player.getJoinedClubDate());
        out.put("transfer_listed", player.getTransferListed());
        out.put("listed_for_loan", player.getListedForLoan());
        out.put("transfer_agreed", player.getTransferAgreed());
        out.put("future_transfer_club", player.getFutureTransferClub());
        out.put("future_transfer_date", player.getFutureTransferDate());
        out.put("future_transfer_contract_end_date", player.getFutureTransferContractEndDate());
        out.put("injured", player.getInjured());
        out.put("injury", player.getInjury());
        out.put("injury_start_date", player.getInjuryStartDate());
        out.put("injury_light_training_days_remaining", player.getInjuryLightTrainingDaysRemaining());
        out.put("injury_full_training_days_remaining", player.getInjuryFullTrainingDaysRemaining());
        out.put("injury_min_days_remaining", player.getInjuryMinDaysRemaining());
        out.put("injury_max_days_remaining", player.getInjuryMaxDaysRemaining());
        out.put("injury_expected_return", player.getInjuryExpectedReturn());
        out.put("contract_end_date", player.getContractEndDate());
        out.put("current_reputation", player.getCurrentReputation());
        out.put("home_reputation", player.getHomeReputation());
        out.put("world_reputation", player.getWorldReputation());
        out.put("height_cm", player.getHeightCm());
        out.put("traits", player.getTraits());
        addSeasonStats(out, player, metadata);
        addCandidateFieldMetadata(out, player);
        return out;
    }

    private static void addCandidateFieldMetadata(Map<String, Object> out, PlayerEntity player) {
        String state = player.getCandidateFieldsState() == null ? "unavailable" : player.getCandidateFieldsState();
        out.put("candidate_fields_state", state);
        out.put("candidate_fields_source", "native_memory");
        Map<String, String> fieldStates = new LinkedHashMap<>();
        fieldStates.put("source_uid", fieldState(player.getSourceUid(), state));
        fieldStates.put("morale", fieldState(player.getMorale(), state));
        fieldStates.put("condition", fieldState(player.getCondition(), state));
        fieldStates.put("guide_value", fieldState(player.getGuideValue(), state));
        fieldStates.put("transfer_value", fieldState(player.getTransferValue(), state));
        out.put("candidate_field_states", fieldStates);
    }

    private static String fieldState(Object value, String groupState) {
        return value == null ? "unavailable" : groupState;
    }

    private static PlayerFilterCriteria.LoanStatus parseLoanStatus(String value) {
        if (blank(value)) return PlayerFilterCriteria.LoanStatus.ANY;
        try {
            return PlayerFilterCriteria.LoanStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PlayerFilterCriteria.LoanStatus.ANY;
        }
    }

    private static PlayerFilterCriteria.ClubScope parseClubScope(String value) {
        if (blank(value)) return PlayerFilterCriteria.ClubScope.EITHER;
        try {
            return PlayerFilterCriteria.ClubScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PlayerFilterCriteria.ClubScope.EITHER;
        }
    }

    private static LocalDate parseDate(String value) {
        if (blank(value)) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Map<String, Object> playerFullMap(PlayerEntity player) {
        return playerFullMap(player, players.metadata());
    }

    private Map<String, Object> playerFullMap(PlayerEntity player, Map<String, Object> metadata) {
        Map<String, Object> out = new LinkedHashMap<>(playerMapper.apply(player));
        stripUnreadRamFields(out);
        out.put("POSITION_TEXT", PositionTextFormatter.format(player));
        out.put("POSITIONS", positionMap(player));
        out.put("ATTRIBUTES", attributeMap(player, AttributeDefinitions.VISIBLE_FIELDS));
        out.put("HIDDEN_ATTRIBUTES", attributeMap(player, AttributeDefinitions.HIDDEN_DIRECT_FIELDS));
        out.put("fields_not_in_ram", FIELDS_NOT_IN_RAM);
        addSeasonStats(out, player, metadata);
        addCandidateFieldMetadata(out, player);
        return out;
    }

    private void addSeasonStats(Map<String, Object> out, PlayerEntity player) {
        addSeasonStats(out, player, players.metadata());
    }

    private void addSeasonStats(Map<String, Object> out, PlayerEntity player, Map<String, Object> metadata) {
        out.put("season", blank(String.valueOf(metadata.getOrDefault("season_key", "")))
                ? null : metadata.get("season_key"));
        out.put("game_build", metadata.get("game_build"));
        out.put("stats_scope", metadata.getOrDefault("season_stats_scope", "all_competitions"));
        out.put("appearances", player.getAppearances());
        out.put("starts", player.getStarts());
        out.put("minutes", player.getMinutes());
        out.put("goals", player.getGoals());
        out.put("assists", player.getAssists());
        out.put("average_rating", player.getAverageRating());
        out.put("season_stats_available", "available".equals(metadata.get("season_stats_state")));
        out.put("season_stats_state", metadata.getOrDefault("season_stats_state", "unavailable"));
        out.put("season_stats_read_at", metadata.get("season_stats_read_at"));
        out.put("season_stats_imported_at", metadata.get("season_stats_imported_at"));
        out.put("stats_source", metadata.getOrDefault("season_stats_source", "unknown"));
        out.put("imported_stats", statsQuery.importedStats(player));
    }

    private Map<String, Object> clubMap(ClubEntity club) {
        Map<String, Object> out = new LinkedHashMap<>(club.toApiMap());
        out.put("id", club.getId());
        out.put("name", club.getName());
        out.put("gender", club.getGender());
        out.put("competition", club.getCompetition());
        out.put("nation", club.getNation());
        out.put("reputation", club.getReputation());
        out.put("balance", club.getBalance());
        out.put("transfer_budget", club.getTransferBudget());
        out.put("payroll_budget", club.getPayrollBudget());
        return out;
    }

    private Map<String, Object> squadSummary(List<PlayerEntity> squad, ClubEntity club) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("player_count", squad.size());
        out.put("average_ca", averageInt(squad, PlayerEntity::getCa));
        out.put("average_pa", averageInt(squad, PlayerEntity::getPa));
        out.put("max_ca", squad.stream().map(PlayerEntity::getCa).filter(Objects::nonNull).max(Integer::compareTo).orElse(0));
        out.put("max_pa", squad.stream().map(PlayerEntity::getPa).filter(Objects::nonNull).max(Integer::compareTo).orElse(0));
        out.put("under_24_high_potential_count", squad.stream()
                .filter(player -> inRange(effectiveAge(player), null, 23))
                .filter(player -> value(player.getPa()) >= 150)
                .count());
        SquadAdvice.WageHealth health = SquadAdvice.wageHealth(squad, club);
        out.put("wage_bill_weekly", health.wageBillWeekly());
        out.put("payroll_budget", health.payrollBudget());
        if (health.payrollBudget() != null) {
            out.put("payroll_headroom_weekly", health.payrollBudget() - health.wageBillWeekly());
        }
        out.put("wage_bill_used_fraction", health.usedFraction());
        return out;
    }

    private static Map<String, Object> result(String key, List<Map<String, Object>> rows, int limit) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", rows.size());
        out.put("limit", limit);
        out.put(key, rows);
        return out;
    }

    private static Map<String, Object> positionMap(PlayerEntity player) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldDef field : AttributeDefinitions.POSITION_FIELDS) {
            String column = columnName(field);
            Object score = player.getColumnValue(column);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("score", score);
            value.put("text", PositionTextFormatter.positionLevelText(score));
            out.put(column, value);
        }
        return out;
    }

    private static Map<String, Object> attributeMap(PlayerEntity player, List<FieldDef> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldDef field : fields) {
            String column = columnName(field);
            out.put(column, player.getColumnValue(column));
        }
        return out;
    }

    static String columnName(FieldDef field) {
        String name = field.name();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                out.append('_');
            }
            out.append(Character.toUpperCase(ch));
        }
        return out.toString();
    }

    private static String playerAttributeKey(String attributeName) {
        if (attributeName == null || attributeName.isBlank()) {
            return "";
        }
        String normalized = attributeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return switch (normalized) {
            case "aerial_reach" -> "aerial_ability";
            case "jumping" -> "jumping_reach";
            case "team_work" -> "teamwork";
            case "free_kick_taking" -> "free_kicks";
            case "penalty_taking" -> "penalties";
            default -> normalized;
        };
    }

    private static String playerAttributeColumn(String attributeName) {
        return playerAttributeKey(attributeName).toUpperCase(Locale.ROOT);
    }

    static boolean recentlyJoinedCurrentClub(PlayerEntity player, Period minimumTimeAtCurrentClub) {
        return PlayerAnalysisRules.recentlyJoinedCurrentClub(player, minimumTimeAtCurrentClub);
    }

    private static Period parsePeriod(String value, Period defaultValue) {
        if (blank(value)) {
            return defaultValue;
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        try {
            if (trimmed.chars().allMatch(Character::isDigit)) {
                return Period.ofDays(Integer.parseInt(trimmed));
            }
            if (trimmed.endsWith("D") && trimmed.substring(0, trimmed.length() - 1).chars().allMatch(Character::isDigit)) {
                return Period.ofDays(Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)));
            }
            if (!trimmed.startsWith("P")) {
                trimmed = "P" + trimmed;
            }
            return Period.parse(trimmed);
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private static int highestReputation(PlayerEntity player) {
        return Math.max(value(player.getWorldReputation()), Math.max(value(player.getCurrentReputation()), value(player.getHomeReputation())));
    }

    private static int averageInt(List<PlayerEntity> players, java.util.function.Function<PlayerEntity, Integer> value) {
        return (int) Math.round(players.stream()
                .map(value)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));
    }

    private static String playingNation(PlayerEntity player) {
        return Optional.ofNullable(player.getPlayingClubEntity()).map(ClubEntity::getNation).orElse(null);
    }

    private static String playingCompetition(PlayerEntity player) {
        return Optional.ofNullable(player.getPlayingClubEntity()).map(ClubEntity::getCompetition).orElse(null);
    }

    private static boolean contains(String value, String needle) {
        return blank(needle) || normalize(value).contains(normalize(needle));
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return blank(right) || normalize(left).equals(normalize(right));
    }

    static boolean inRange(Integer value, Integer min, Integer max) {
        return PlayerAnalysisRules.inRange(value, min, max);
    }

    static boolean inDoubleRange(Double value, Double min, Double max) {
        if (min == null && max == null) return true;
        if (value == null) return false;
        return (min == null || value >= min) && (max == null || value <= max);
    }

    /** Unknown asking prices are not free. Free agents (no club) still pass a max-price filter. */
    static boolean askingPriceWithinMax(Long askingPrice, String club, Long max) {
        return PlayerAnalysisRules.askingPriceWithinMax(askingPrice, club, max);
    }

    static boolean salaryWithinMax(Integer salaryWeekly, Integer max) {
        return PlayerAnalysisRules.salaryWithinMax(salaryWeekly, max);
    }

    static boolean wageFits(Integer salaryWeekly, long wageCeiling) {
        return PlayerAnalysisRules.wageFits(salaryWeekly, wageCeiling);
    }

    static long resolvePriceCap(Long maxAskingPrice, Long budget) {
        return PlayerAnalysisRules.resolvePriceCap(maxAskingPrice, budget);
    }

    static boolean priceCapKnown(long priceCap) {
        return PlayerAnalysisRules.priceCapKnown(priceCap);
    }

    static boolean rolesMatch(String catalogName, String query) {
        return PlayerAnalysisRules.rolesMatch(catalogName, query);
    }

    static boolean roleKeysEqual(String left, String right) {
        return PlayerAnalysisRules.roleKeysEqual(left, right);
    }

    static String roleKey(String value) {
        return PlayerAnalysisRules.roleKey(value);
    }

    static boolean matchesBoolean(Boolean value, Boolean expected) {
        return PlayerAnalysisRules.matchesBoolean(value, expected);
    }

    static Integer effectiveAge(PlayerEntity player) {
        return PlayerAnalysisRules.effectiveAge(player);
    }

    private static Integer asInteger(String value) {
        if (blank(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ex) {
            try {
                double parsed = Double.parseDouble(trimmed);
                if (!Double.isFinite(parsed)) {
                    return null;
                }
                return (int) parsed;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static int safeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static Map<String, Object> academyMap(SquadAdvice.AcademyRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", row.name());
        out.put("position", row.position());
        out.put("age", row.age());
        out.put("ca", row.ca());
        out.put("pa", row.pa());
        out.put("upside", row.upside());
        out.put("ca_vs_first_team", row.vsFirstTeam());
        out.put("natural_positions", row.dualPositions());
        out.put("salary_weekly", row.salaryWeekly());
        out.put("contract_end", row.contractEnd());
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void attachEmptyRecruitmentHint(Map<String, Object> out, int poolCount) {
        Object returned = out.get("returned");
        int returnedCount = returned instanceof Number number ? number.intValue() : 0;
        if (poolCount > 0 && returnedCount > 0) {
            return;
        }
        Map<String, Object> criteria = out.get("criteria") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        String hint = emptyRecruitmentHint(criteria);
        out.put("empty_hint", hint);
    }

    static String emptyRecruitmentHint(Map<String, Object> criteria) {
        StringBuilder hint = new StringBuilder(
                "No candidates matched. Do not repeat the same search. Broaden at most once, then answer. ");
        hint.append("Silent filters still applied: club transfer budget as price cap, position score 15, ");
        hint.append("reputation/willingness, transfer_agreed=false. ");
        Object tenure = criteria == null ? null : criteria.get("minimum_time_at_current_club");
        if (tenure != null && !"P0D".equals(String.valueOf(tenure))) {
            hint.append("Tenure is ").append(tenure)
                    .append(" — recently joined youth count as unwilling; pass minimumTimeAtCurrentClub=P0D. ");
        }
        Object minPa = criteria == null ? null : criteria.get("min_pa");
        if (minPa != null) {
            hint.append("min_pa=").append(minPa).append(" is often too high for U19; omit it. ");
        }
        Object maxAge = criteria == null ? null : criteria.get("max_age");
        if (maxAge instanceof Number age && age.intValue() < DEFAULT_WONDERKID_MAX_AGE) {
            hint.append("max_age=").append(maxAge).append(" is tight; try 21. ");
        }
        hint.append("If stored ages are blank (in-game date unknown), age is computed from date of birth; missing DOB still drops U21 filters. ");
        hint.append("Do not use moneyball for U21s (it floors CA near first-team level and drops unknown fees). ");
        hint.append("If this was fm26_find_players with askingPriceMax, unknown fees were excluded — omit the price cap. ");
        hint.append("Then answer with in-house names from fm26_academy; do not keep searching.");
        return hint.toString();
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('ð', 'd')
                .replace('Ð', 'd')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static boolean matchesAdvanced(PlayerEntity player, PlayerFilterCriteria.Advanced advanced) {
        return matchesBoolean(player.getInjured(), advanced.injured())
                && matchesBoolean(player.getTransferListed(), advanced.transferListed())
                && matchesBoolean(player.getListedForLoan(), advanced.listedForLoan())
                && matchesBoolean(player.getTransferAgreed(), advanced.transferAgreed())
                && (advanced.freeAgent() == null || advanced.freeAgent() == isFreeAgent(player))
                && (advanced.loanStatus() == PlayerFilterCriteria.LoanStatus.ANY
                || (advanced.loanStatus() == PlayerFilterCriteria.LoanStatus.LOANED
                ? "yes".equalsIgnoreCase(player.getIsLoanedOut())
                : !"yes".equalsIgnoreCase(player.getIsLoanedOut())))
                && inRange(player.getAppearances(), advanced.appearancesMin(), advanced.appearancesMax())
                && inRange(player.getStarts(), advanced.startsMin(), advanced.startsMax())
                && inRange(player.getMinutes(), advanced.minutesMin(), advanced.minutesMax())
                && inRange(player.getGoals(), advanced.goalsMin(), advanced.goalsMax())
                && inRange(player.getAssists(), advanced.assistsMin(), advanced.assistsMax())
                && inDoubleRange(player.getAverageRating(), advanced.averageRatingMin(), advanced.averageRatingMax());
    }

    private static boolean matchesClubScope(PlayerEntity player, String club, PlayerFilterCriteria.ClubScope scope) {
        if (blank(club)) return true;
        String contractedEntity = player.getClubEntity() == null ? null : player.getClubEntity().getName();
        String playingEntity = player.getPlayingClubEntity() == null ? null : player.getPlayingClubEntity().getName();
        return switch (scope) {
            case CONTRACTED -> equalsIgnoreCase(player.getClub(), club) || equalsIgnoreCase(contractedEntity, club);
            case PLAYING -> equalsIgnoreCase(player.getPlayingClub(), club) || equalsIgnoreCase(playingEntity, club);
            case EITHER -> equalsIgnoreCase(player.getClub(), club) || equalsIgnoreCase(player.getPlayingClub(), club)
                    || equalsIgnoreCase(contractedEntity, club) || equalsIgnoreCase(playingEntity, club);
        };
    }

    private static boolean isFreeAgent(PlayerEntity player) {
        return player.getClub() == null || player.getClub().isBlank();
    }

    private record PositionSpec(String code, String column, String positionGroup) {
    }

    private static final class UnsupportedPositionException extends IllegalArgumentException {
        private UnsupportedPositionException(String position, Throwable cause) {
            super("unsupported position: " + position
                    + ". Use GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST", cause);
        }
    }

    private static Map<String, Object> positionError(String position) {
        return Map.of(
                "error", new UnsupportedPositionException(position, null).getMessage(),
                "expected", "GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST");
    }

    private record RoleProfile(
            List<String> roles,
            List<String> primary,
            List<String> secondary,
            Map<String, Integer> weights) {

        private static RoleProfile empty() {
            return new RoleProfile(List.of(), List.of(), List.of(), Map.of());
        }

        private boolean isEmpty() {
            return weights.isEmpty();
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("roles", roles);
            out.put("primary", primary);
            out.put("secondary", secondary);
            return out;
        }
    }

    private record RoleFit(Double score, List<String> strengths, List<String> gaps) {
        private static RoleFit empty() {
            return new RoleFit(null, List.of(), List.of());
        }
    }

    private record AttributeScore(String name, int score, int weight) {
        private String compact() {
            return name + ":" + score;
        }
    }

    private record ScoredCandidate(
            PlayerEntity player,
            ClubEntity sourceClub,
            int positionScore,
            RoleFit roleFit,
            Willingness willingness,
            boolean priceKnown,
            boolean freeAgent,
            double decisionScore) {

        private Candidate asCandidate() {
            return new Candidate(player, sourceClub, positionScore, roleFit, willingness, priceKnown, freeAgent);
        }
    }

    private record Candidate(
            PlayerEntity player,
            ClubEntity sourceClub,
            int positionScore,
            RoleFit roleFit,
            Willingness willingness,
            boolean priceKnown,
            boolean freeAgent) {
    }

    private record DealCandidate(Candidate candidate, MarketValuation.Deal deal, double qualityScore, int signingRating) {
    }

    private enum Willingness {
        HIGH,
        MEDIUM,
        LOW
    }

    private record RoleAttributeRow(
            String game,
            String positionGroup,
            String roleName,
            String phase,
            String attributePriority,
            String attributeName,
            int sortOrder) {
    }

    private record RoleKey(String game, String positionGroup, String roleName, String phase) {
    }

    private static final class RoleBucket {
        private final RoleKey key;
        private final List<Map<String, Object>> primaryAttributes = new java.util.ArrayList<>();
        private final List<Map<String, Object>> secondaryAttributes = new java.util.ArrayList<>();

        private RoleBucket(RoleKey key) {
            this.key = key;
        }

        private void add(RoleAttributeRow row) {
            Map<String, Object> attribute = new LinkedHashMap<>();
            attribute.put("name", row.attributeName());
            attribute.put("player_attribute_key", playerAttributeKey(row.attributeName()));
            attribute.put("player_attribute_column", playerAttributeColumn(row.attributeName()));
            attribute.put("sort_order", row.sortOrder());
            if ("primary".equalsIgnoreCase(row.attributePriority())) {
                primaryAttributes.add(attribute);
            } else {
                secondaryAttributes.add(attribute);
            }
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("game", key.game());
            out.put("position_group", key.positionGroup());
            out.put("role_name", key.roleName());
            out.put("phase", key.phase());
            out.put("primary_attributes", primaryAttributes);
            out.put("secondary_attributes", secondaryAttributes);
            return out;
        }
    }
}
