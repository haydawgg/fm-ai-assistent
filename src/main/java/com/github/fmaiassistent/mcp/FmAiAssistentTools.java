package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.service.CompetitionDatabaseService;
import com.github.fmaiassistent.service.DatabaseLoadAllService;
import com.github.fmaiassistent.service.PlayerDatabaseService;
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
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Service
public class FmAiAssistentTools {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 250;
    private static final int DEFAULT_SHORTLIST_LIMIT = 8;
    private static final int MAX_SHORTLIST_LIMIT = 30;
    private static final int DEFAULT_REPUTATION_MARGIN = 750;
    private static final int DEFAULT_MIN_POSITION_SCORE = 15;
    private static final int DEFAULT_MONEYBALL_QUALITY_GAP = 15;
    private static final int DEFAULT_MONEYBALL_MAX_AGE = 40;
    private static final int DEFAULT_WONDERKID_MAX_AGE = 21;
    static final List<String> FIELDS_NOT_IN_RAM = List.of("morale", "form", "appearances", "goals", "assists");
    private static final int SOURCE_CLUB_REPUTATION_MARGIN = 1000;

    private final PlayerDatabaseService players;
    private final ClubDatabaseService clubs;
    private final CompetitionDatabaseService competitions;
    private final PlayerMapper playerMapper;
    private final JdbcTemplate jdbc;
    private final RamLoadCoordinator ramLoad;
    private final DatabaseLoadAllService loadAll;

    public FmAiAssistentTools(
            PlayerDatabaseService players,
            ClubDatabaseService clubs,
            CompetitionDatabaseService competitions,
            PlayerMapper playerMapper,
            JdbcTemplate jdbc,
            RamLoadCoordinator ramLoad,
            DatabaseLoadAllService loadAll) {
        this.players = players;
        this.clubs = clubs;
        this.competitions = competitions;
        this.playerMapper = playerMapper;
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
        List<Map<String, Object>> rows = allClubs().stream()
                .filter(club -> contains(club.getName(), name))
                .filter(club -> blank(nation) || equalsIgnoreCase(club.getNation(), nation))
                .filter(club -> blank(competition) || equalsIgnoreCase(club.getCompetition(), competition))
                .filter(club -> reputationMin == null || value(club.getReputation()) >= reputationMin)
                .sorted(Comparator
                        .comparing((ClubEntity club) -> value(club.getReputation())).reversed()
                        .thenComparing(ClubEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(safeLimit)
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
        out.put("squad_summary", squadSummary(squad));
        out.put("squad", squad.stream()
                .limit(safeLimit(squadLimit))
                .map(this::playerSummaryMap)
                .toList());
        return out;
    }

    @Tool(name = "fm26_find_players", description = "Search FM26 players using the same data available in the UI. Money values are raw pounds.")
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
            @ToolParam(required = false, description = "Minimum world reputation") Integer worldReputationMin,
            @ToolParam(required = false, description = "Maximum world reputation") Integer worldReputationMax,
            @ToolParam(required = false, description = "Transfer-listed filter. Use true for only transfer-listed players, false for only players not transfer-listed.") Boolean transferListed,
            @ToolParam(required = false, description = "Listed-for-loan filter. Use true for only loan-listed players, false for only players not listed for loan.") Boolean listedForLoan,
            @ToolParam(required = false, description = "Transfer-agreed filter. Use true for players who already agreed a future move, false to exclude them.") Boolean transferAgreed,
            @ToolParam(required = false, description = "Future transfer destination club exact filter.") String futureTransferClub,
            @ToolParam(required = false, description = "Injury filter. Use true for only injured players, false for only currently fit players.") Boolean injured,
            @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST.") String position,
            @ToolParam(required = false, description = "Minimum position ability 1-20 when position is supplied. Defaults to 15.") Integer minimumPositionScore,
            @ToolParam(required = false, description = "If true, return full attributes. Defaults to compact summaries; use fm26_get_player_details for finalists.") Boolean details,
            @ToolParam(required = false, description = "Maximum players to return") Integer limit) {
        int safeLimit = safeLimit(limit);
        PositionSpec positionSpec = resolvePosition(position);
        int positionMinimum = positionSpec == null
                ? 1
                : Math.max(1, Math.min(20, minimumPositionScore == null ? DEFAULT_MIN_POSITION_SCORE : minimumPositionScore));
        boolean fullDetails = Boolean.TRUE.equals(details);
        Predicate<PlayerEntity> filter = player ->
                contains(player.getName(), name)
                        && (blank(gender) || equalsIgnoreCase(player.getGender(), gender))
                        && (blank(nationality) || equalsIgnoreCase(player.getNationality(), nationality))
                        && (blank(playingNation) || equalsIgnoreCase(playingNation(player), playingNation))
                        && (blank(playingCompetition) || equalsIgnoreCase(playingCompetition(player), playingCompetition))
                        && (blank(club) || equalsIgnoreCase(player.getClub(), club) || equalsIgnoreCase(player.getPlayingClub(), club))
                        && inRange(asInteger(player.getAge()), ageMin, ageMax)
                        && inRange(player.getCa(), caMin, caMax)
                        && inRange(player.getPa(), paMin, paMax)
                        && askingPriceWithinMax(player.getAskingPrice(), player.getClub(), askingPriceMax)
                        && salaryWithinMax(player.getSalaryWeeklyRaw(), salaryWeeklyMax)
                        && inRange(player.getWorldReputation(), worldReputationMin, worldReputationMax)
                        && matchesBoolean(player.getTransferListed(), transferListed)
                        && matchesBoolean(player.getListedForLoan(), listedForLoan)
                        && matchesBoolean(player.getTransferAgreed(), transferAgreed)
                        && (blank(futureTransferClub) || equalsIgnoreCase(player.getFutureTransferClub(), futureTransferClub))
                        && matchesBoolean(player.getInjured(), injured)
                        && MarketValuation.hasPlayablePosition(player)
                        && (positionSpec == null || positionScore(player, positionSpec) >= positionMinimum);
        List<Map<String, Object>> rows = allPlayers().stream()
                .filter(filter)
                .sorted(Comparator
                        .comparing((PlayerEntity player) -> value(player.getPa())).reversed()
                        .thenComparing((PlayerEntity player) -> value(player.getCa())).reversed()
                        .thenComparing(PlayerEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(safeLimit)
                .map(fullDetails ? this::playerFullMap : this::playerSummaryMap)
                .toList();
        return result("players", rows, safeLimit);
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
        RankedTransfers ranked = rankTransfers(
                managingClub, position, roleName, phase, minimumPositionScore, maxAge, minCurrentAbility,
                minPotentialAbility, maxAskingPrice, maxWeeklySalary, reputationMargin, minimumTimeAtCurrentClub,
                transferListed, listedForLoan, transferAgreed, injured);
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
        putIfNotNull(criteria, "min_ca", minCurrentAbility);
        putIfNotNull(criteria, "min_pa", minPotentialAbility);
        criteria.put("max_asking_price", ranked.priceCap());
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
        return out;
    }

    @Tool(name = "fm26_moneyball_shortlist", description = "Moneyball value tool. Finds the best signings for a club and sorts them by signing_rating (0-100), which combines player quality (half CA, half age-adjusted PA) with transfer value (asking price plus 3 years of wages compared with the market median for comparable players). Each candidate also carries a deal_tier: excellent, good, average or overpriced, used-car style. Call this for cheap signings and value transfers; call fm26_transfer_shortlist when tactical or role fit is the priority.")
    @Transactional(readOnly = true)
    public Map<String, Object> moneyballShortlist(
            @ToolParam(description = "Managing club name, for example Feyenoord") String managingClub,
            @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST. Full names also work.") String position,
            @ToolParam(required = false, description = "Optional FM26 role name for attribute fit, for example Ball-Playing Centre-Back.") String roleName,
            @ToolParam(required = false, description = "Role phase: In Possession or Out of Possession.") String phase,
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
        List<PlayerEntity> allPlayers = allPlayers();
        MoneyballParameters params = resolveMoneyballParameters(
                allPlayers, managingClub, position, roleName, phase,
                minCurrentAbility, minPotentialAbility, maxAge, maxAskingPrice, maxWeeklySalary,
                reputationMargin, minimumTimeAtCurrentClub, transferListed, listedForLoan, transferAgreed, injured);
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
        criteria.put("max_asking_price", params.priceCap());
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
        return out;
    }

    @Tool(name = "fm26_status", description = "Snapshot status: in-game date, last RAM load time, and player/club/competition counts. Call this before recruitment tools.")
    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>(players.metadata());
        out.put("clubs", clubs.countClubs());
        out.put("competitions", competitions.countCompetitions());
        out.put("loading", ramLoad.loading());
            out.put("guidance", "If count is 0, call fm26_load_from_ram or click Load from RAM in the UI with FM26 running. tactic_formation is the live tactic when RAM load found it.");
        return out;
    }

    @Tool(name = "fm26_load_from_ram", description = "Load the current FM26 save from RAM into the local database. FM26 must be running with a save loaded. Slow; do not call repeatedly.")
    public Map<String, Object> loadFromRam() {
        try {
            DatabaseLoadAllService.LoadAllResult result = ramLoad.loadFromRam();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("pid", result.pid());
            out.put("game_date", result.gameDate());
            out.put("players", result.players());
            out.put("clubs", result.clubs());
            out.put("competitions", result.competitions());
            return out;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "fm.exe process not found" : ex.getMessage(), ex);
        }
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
            out.put("decoded_today", List.of("PeopleOffset", "TeamOffset", "CompetitionOffset"));
            out.put("not_decoded", List.of("NationOffset", "StadiumOffset", "AgreementOffset", "ClubOffset",
                    "CityOffset", "ContinentOffset", "RegionOffset", "CurrencyOffset"));
            out.put("guidance", "Counts only. Traits are read when preferred-move name vectors match. Morale, form and match stats stay empty until those offsets are validated.");
            return out;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "fm.exe process not found" : ex.getMessage(), ex);
        }
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

    @Tool(name = "fm26_wonderkid_shortlist", description = "Young high-PA signings. Same recruitment filters as fm26_transfer_shortlist with a default max age of 21. Money values are raw pounds.")
    @Transactional(readOnly = true)
    public Map<String, Object> wonderkidShortlist(
            @ToolParam(description = "Managing club name, for example Feyenoord") String managingClub,
            @ToolParam(required = false, description = "Position: GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST.") String position,
            @ToolParam(required = false, description = "Optional FM26 role name") String roleName,
            @ToolParam(required = false, description = "Role phase: In Possession or Out of Possession") String phase,
            @ToolParam(required = false, description = "Maximum age. Defaults to 21.") Integer maxAge,
            @ToolParam(required = false, description = "Minimum potential ability") Integer minPotentialAbility,
            @ToolParam(required = false, description = "Maximum asking price in pounds") Long maxAskingPrice,
            @ToolParam(required = false, description = "Maximum candidates. Defaults to 8.") Integer limit) {
        return transferShortlist(
                managingClub, position, roleName, phase, null,
                maxAge == null ? DEFAULT_WONDERKID_MAX_AGE : maxAge,
                null, minPotentialAbility, maxAskingPrice, null, null, null, null, null, null, null, limit);
    }

    @Tool(name = "fm26_compare_squads", description = "Compare two clubs' squads: CA/PA, wage bill, age, and best player per position.")
    @Transactional(readOnly = true)
    public Map<String, Object> compareSquads(
            @ToolParam(description = "First club name") String leftClub,
            @ToolParam(description = "Second club name") String rightClub) {
        ClubEntity left = requireClub(leftClub);
        ClubEntity right = requireClub(rightClub);
        return SquadAdvice.compareSquads(left.getName(), right.getName(),
                currentSquad(allPlayers(), left.getName()),
                currentSquad(allPlayers(), right.getName()));
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
        String formation = String.valueOf(meta.getOrDefault("tactic_formation", ""));
        String slots = String.valueOf(meta.getOrDefault("tactic_slots", ""));
        String selected = String.valueOf(meta.getOrDefault("tactic_selected", ""));
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
        List<PlayerEntity> squad = currentSquad(allPlayers(), club.getName());
        List<SquadAdvice.XiPick> picks = SquadAdvice.bestXi(squad, slots, this::slotRoleFit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("club", recruitmentClubMap(club));
        out.put("tactic_source", source);
        out.put("formation", players.metadata().get("tactic_formation"));
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
        ClubEntity club = requireClub(managingClub);
        return SquadAdvice.sellShortlist(
                ownedSquad(currentSquad(allPlayers(), club.getName()), club.getName()), club);
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
        RankedTransfers ranked = rankTransfers(
                managingClub, position, roleName, null, null, maxAge, minCurrentAbility, minPotentialAbility,
                maxAskingPrice, maxWeeklySalary, null, null, null, null, null, null);
        List<TransferShortlistRow> rows = new ArrayList<>();
        for (int index = 0; index < ranked.candidates().size(); index++) {
            ScoredCandidate candidate = ranked.candidates().get(index);
            PlayerEntity player = candidate.player();
            rows.add(new TransferShortlistRow(
                    index + 1,
                    candidate.decisionScore(),
                    player.getName(),
                    asInteger(player.getAge()),
                    player.getNationality(),
                    player.getClub(),
                    candidate.positionScore(),
                    candidate.roleFit().score(),
                    value(player.getCa()),
                    value(player.getPa()),
                    effectivePotential(player) - value(player.getCa()),
                    candidate.priceKnown() ? player.getAskingPrice() : null,
                    value(player.getSalaryWeeklyRaw()),
                    candidate.willingness().name().toLowerCase(Locale.ROOT),
                    candidate.freeAgent(),
                    Boolean.TRUE.equals(player.getTransferListed()),
                    Boolean.TRUE.equals(player.getInjured()),
                    candidateSignals(candidate.asCandidate(), ranked.benchmarkCa())));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<SquadAdvice.XiPick> bestXiRows(String managingClub, List<SquadAdvice.XiSlot> slots) {
        ClubEntity club = requireClub(managingClub);
        return SquadAdvice.bestXi(currentSquad(allPlayers(), club.getName()), slots, this::slotRoleFit);
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
            Map<String, Object> shortlist = transferShortlist(
                    managingClub, pick.position(), pick.inPossessionRole(), "In Possession",
                    null, null, null, null, null, null, null, null, null, null, null, null, 5);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("position", pick.position());
            row.put("in_possession_role", pick.inPossessionRole());
            row.put("tool", "fm26_transfer_shortlist");
            row.put("candidates", shortlist.get("candidates"));
            upgrades.add(row);
        }
        return upgrades;
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
        List<PlayerEntity> allPlayers = allPlayers();
        MoneyballParameters params = resolveMoneyballParameters(
                allPlayers, managingClub, position, null, null,
                minCurrentAbility, minPotentialAbility, maxAge, maxAskingPrice, maxWeeklySalary,
                null, null, null, null, null, null);
        MarketValuation market = MarketValuation.build(allPlayers);
        MoneyballRated rated = rateMoneyball(allPlayers, clubsByName(allClubs()), market, params);
        List<MoneyballRow> rows = new ArrayList<>();
        for (int index = 0; index < rated.rated().size(); index++) {
            rows.add(toRow(index + 1, rated.rated().get(index)));
        }
        return new MoneyballResult(rows, rated.candidatePoolSize(), rows.size(),
                market.pricedPlayers(), market.bucketCount());
    }

    private MoneyballParameters resolveMoneyballParameters(
            List<PlayerEntity> allPlayers,
            String managingClub,
            String position,
            String roleName,
            String phase,
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
        int positionMinimum = positionSpec == null ? 1 : DEFAULT_MIN_POSITION_SCORE;

        List<PlayerEntity> squad = currentSquad(allPlayers, club.getName());
        List<PlayerEntity> positionSquad = positionSpec == null ? squad
                : squad.stream().filter(player -> positionScore(player, positionSpec) >= positionMinimum).toList();
        int benchmarkCa = firstTeamAverageCa(positionSquad);
        int qualityFloor = minCurrentAbility == null
                ? Math.max(0, benchmarkCa - DEFAULT_MONEYBALL_QUALITY_GAP)
                : minCurrentAbility;
        long budget = Math.max(0L, value(club.getTransferBudget()));
        int safeReputationMargin = reputationMargin == null ? DEFAULT_REPUTATION_MARGIN : Math.max(0, reputationMargin);
        Period minimumTime = parsePeriod(minimumTimeAtCurrentClub, Period.ofYears(1));
        long priceCap = resolvePriceCap(maxAskingPrice, budget);
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
                params.reputationMargin(), params.minimumTime());
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

    private static MoneyballRow toRow(int rank, DealCandidate dealCandidate) {
        Candidate candidate = dealCandidate.candidate();
        PlayerEntity player = candidate.player();
        return new MoneyballRow(
                rank,
                player.getName(),
                asInteger(player.getAge()),
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
                dealCandidate.deal(),
                dealCandidate.qualityScore(),
                dealCandidate.signingRating());
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
            Period minimumTime) {
        List<Candidate> pool = new ArrayList<>();
        for (PlayerEntity player : allPlayers) {
            if (belongsToClub(player, managingClub.getName())
                    || !sameGender(player.getGender(), managingClub.getGender())
                    || !inRange(asInteger(player.getAge()), null, maxAge)
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
            if (priceKnown && askingPrice > priceCap) {
                continue;
            }
            if (!salaryWithinMax(player.getSalaryWeeklyRaw(), maxWeeklySalary)) {
                continue;
            }
            ClubEntity sourceClub = clubsByName.get(normalize(player.getClub()));
            Willingness willingness = willingness(player, managingClub, sourceClub, reputationMargin, minimumTime);
            if (willingness == Willingness.LOW) {
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
        out.put("cost_wages_3yr", MarketValuation.CONTRACT_YEARS * MarketValuation.WEEKS_PER_YEAR * value(player.getSalaryWeeklyRaw()));
        out.put("total_cost_3yr", deal.totalCost());
        out.put("value_gap", deal.marketCost() - deal.totalCost());
        out.put("name", player.getName());
        out.put("age", asInteger(player.getAge()));
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
        return allClubs().stream()
                .filter(club -> equalsIgnoreCase(club.getName(), clubName))
                .max(Comparator.comparingInt(club -> value(club.getReputation())))
                .or(() -> allClubs().stream()
                        .filter(club -> contains(club.getName(), clubName))
                        .max(Comparator.comparingInt(club -> value(club.getReputation()))))
                .orElseThrow(() -> new IllegalArgumentException("club not found: " + clubName));
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
                .orElseThrow(() -> new IllegalArgumentException("player not found: " + name));
    }

    static boolean samePlayer(PlayerEntity left, PlayerEntity right) {
        if (left == right) {
            return true;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
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
        return currentSquad(allPlayers(), clubName);
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
        return equalsIgnoreCase(player.getClub(), clubName) || equalsIgnoreCase(player.getPlayingClub(), clubName);
    }

    private static boolean sameGender(String playerGender, String clubGender) {
        return blank(playerGender) || blank(clubGender) || normalize(playerGender).equals(normalize(clubGender));
    }

    private static PositionSpec resolvePosition(String position) {
        if (blank(position)) {
            return null;
        }
        String code = Positions.canonicalCode(position);
        return new PositionSpec(code, Positions.column(code), Positions.positionGroup(code));
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
        return jdbc.query("""
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
        double price = freeAgent ? 1.0 : !priceKnown ? 0.35 : priceCap <= 0
                ? 0.0
                : clamp(1.0 - value(player.getAskingPrice()) / (double) priceCap);
        double wage = wageFitScore(player.getSalaryWeeklyRaw(), wageCeiling);
        double willingnessScore = willingness == Willingness.HIGH ? 1.0 : 0.6;

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
            Boolean injured) {
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

        long budget = Math.max(0L, value(club.getTransferBudget()));
        int safeReputationMargin = reputationMargin == null ? DEFAULT_REPUTATION_MARGIN : Math.max(0, reputationMargin);
        Period minimumTime = parsePeriod(minimumTimeAtCurrentClub, Period.ofYears(1));
        long priceCap = resolvePriceCap(maxAskingPrice, budget);
        long wageCeiling = maxWeeklySalary == null
                ? inferredWeeklyWageCeiling(squad, club)
                : Math.max(0L, maxWeeklySalary);
        int benchmarkCa = firstTeamAverageCa(positionSquad);
        Boolean effectiveTransferAgreed = transferAgreed == null ? Boolean.FALSE : transferAgreed;

        List<Candidate> pool = buildCandidatePool(
                allPlayers, clubsByName, club, positionSpec, positionMinimum, roleProfile,
                maxAge, minCurrentAbility, minPotentialAbility, transferListed, listedForLoan,
                effectiveTransferAgreed, injured, priceCap, maxWeeklySalary, safeReputationMargin, minimumTime);
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
        out.put("age", asInteger(player.getAge()));
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
                    row.put("age", asInteger(player.getAge()));
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
                .limit(5)
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
        int age = Optional.ofNullable(asInteger(player.getAge())).orElse(40);
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
        int age = Optional.ofNullable(asInteger(player.getAge())).orElse(40);
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
        int age = Optional.ofNullable(asInteger(player.getAge())).orElse(40);
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

    static Long askingPriceOrNull(Long askingPrice) {
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
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", player.getId());
        out.put("name", player.getName());
        out.put("age", player.getAge());
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
        return out;
    }

    private Map<String, Object> playerFullMap(PlayerEntity player) {
        Map<String, Object> out = new LinkedHashMap<>(playerMapper.apply(player));
        stripUnreadRamFields(out);
        out.put("POSITION_TEXT", PositionTextFormatter.format(player));
        out.put("POSITIONS", positionMap(player));
        out.put("ATTRIBUTES", attributeMap(player, AttributeDefinitions.VISIBLE_FIELDS));
        out.put("HIDDEN_ATTRIBUTES", attributeMap(player, AttributeDefinitions.HIDDEN_DIRECT_FIELDS));
        out.put("fields_not_in_ram", FIELDS_NOT_IN_RAM);
        return out;
    }

    private Map<String, Object> clubMap(ClubEntity club) {
        Map<String, Object> out = new LinkedHashMap<>(club.toApiMap());
        out.put("ID", club.getId());
        out.put("NAME", club.getName());
        out.put("GENDER", club.getGender());
        out.put("COMPETITION", club.getCompetition());
        out.put("NATION", club.getNation());
        out.put("REPUTATION", club.getReputation());
        out.put("BALANCE", club.getBalance());
        out.put("TRANSFER_BUDGET", club.getTransferBudget());
        out.put("PAYROLL_BUDGET", club.getPayrollBudget());
        return out;
    }

    private Map<String, Object> squadSummary(List<PlayerEntity> squad) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("player_count", squad.size());
        out.put("average_ca", averageInt(squad, PlayerEntity::getCa));
        out.put("average_pa", averageInt(squad, PlayerEntity::getPa));
        out.put("max_ca", squad.stream().map(PlayerEntity::getCa).filter(Objects::nonNull).max(Integer::compareTo).orElse(0));
        out.put("max_pa", squad.stream().map(PlayerEntity::getPa).filter(Objects::nonNull).max(Integer::compareTo).orElse(0));
        out.put("under_24_high_potential_count", squad.stream()
                .filter(player -> inRange(asInteger(player.getAge()), null, 23))
                .filter(player -> value(player.getPa()) >= 150)
                .count());
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

    private static boolean recentlyJoinedCurrentClub(PlayerEntity player, Period minimumTimeAtCurrentClub) {
        LocalDate joined = parseDate(player.getJoinedClubDate());
        LocalDate gameDate = parseDate(player.getAgeAsOf());
        if (joined == null || gameDate == null) {
            return false;
        }
        return joined.isAfter(gameDate.minus(minimumTimeAtCurrentClub));
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

    private static LocalDate parseDate(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
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
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return (min == null || value >= min) && (max == null || value <= max);
    }

    /** Unknown asking prices are not free. Free agents (no club) still pass a max-price filter. */
    static boolean askingPriceWithinMax(Long askingPrice, String club, Long max) {
        if (max == null) {
            return true;
        }
        if (askingPrice == null || askingPrice <= 0) {
            return blank(club);
        }
        return askingPrice <= max;
    }

    static boolean salaryWithinMax(Integer salaryWeekly, Integer max) {
        if (max == null) {
            return true;
        }
        if (salaryWeekly == null) {
            return false;
        }
        return salaryWeekly <= max;
    }

    static boolean wageFits(Integer salaryWeekly, long wageCeiling) {
        return salaryWeekly != null && salaryWeekly <= wageCeiling;
    }

    static long resolvePriceCap(Long maxAskingPrice, long budget) {
        return maxAskingPrice == null ? budget : Math.max(0L, maxAskingPrice);
    }

    static boolean rolesMatch(String catalogName, String query) {
        if (blank(query)) {
            return true;
        }
        String catalog = roleKey(catalogName);
        String needle = roleKey(query);
        return !catalog.isEmpty() && !needle.isEmpty()
                && (catalog.equals(needle) || catalog.contains(needle) || needle.contains(catalog));
    }

    static boolean roleKeysEqual(String left, String right) {
        return roleKey(left).equals(roleKey(right));
    }

    static String roleKey(String value) {
        String normalized = normalize(value).replaceAll("\\bgk\\b", "goalkeeper");
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    private static boolean matchesBoolean(Boolean value, Boolean expected) {
        return expected == null || Objects.equals(Boolean.TRUE.equals(value), expected);
    }

    private static Integer asInteger(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
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

    private record PositionSpec(String code, String column, String positionGroup) {
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
