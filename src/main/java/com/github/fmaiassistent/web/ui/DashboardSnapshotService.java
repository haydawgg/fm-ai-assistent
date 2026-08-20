package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.football.AcademyCandidate;
import com.github.fmaiassistent.football.ContractRecommendation;
import com.github.fmaiassistent.football.FirstXiPick;
import com.github.fmaiassistent.football.FirstXiSlot;
import com.github.fmaiassistent.football.PlayerAnalysisPort;
import com.github.fmaiassistent.football.SquadAdvicePort;
import com.github.fmaiassistent.football.SquadSellCandidate;
import com.github.fmaiassistent.football.TransferShortlistCandidate;
import com.github.fmaiassistent.football.TransferShortlistPort;
import com.github.fmaiassistent.football.TransferShortlistQuery;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.Positions;
import com.github.fmaiassistent.mcp.SquadAdvice;
import com.github.fmaiassistent.service.AppSettingsService;
import com.github.fmaiassistent.service.ClubDatabaseService;
import com.github.fmaiassistent.service.PlayerDatabaseService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deep module that turns the existing football advice pipelines into one dashboard read model. */
@Service
public class DashboardSnapshotService {
    private static final List<String> DEPTH_POSITIONS = List.of("GK", "DL", "DC", "DR", "DMC", "MC", "AML", "AMR", "ST");
    private static final String DEFAULT_TACTIC = """
            GK,Ball Playing GK,Sweeper Keeper
            DL,Inverted Full Back,Holding Full Back
            DC,Centre-Back,Centre-Back
            DC,Ball-Playing Centre-Back,Centre-Back
            DR,Inverted Wing-Back,Pressing Full Back
            DMC,Deep Lying Playmaker,Defensive Midfielder
            MC,Midfield Playmaker,Central Midfielder
            MC,Advanced Playmaker,Pressing Central Midfielder
            AML,Wide Forward,Winger
            AMR,Winger,Winger
            ST,Deep Lying Forward,Tracking Centre Forward
            """.stripIndent().trim();

    private final PlayerDatabaseService players;
    private final ClubDatabaseService clubs;
    private final PlayerAnalysisPort tools;
    private final TransferShortlistPort shortlist;
    private final SquadAdvicePort squadAdvice;
    private final AppSettingsService settings;

    @Autowired
    public DashboardSnapshotService(
            PlayerDatabaseService players,
            ClubDatabaseService clubs,
            PlayerAnalysisPort tools,
            TransferShortlistPort shortlist,
            SquadAdvicePort squadAdvice,
            AppSettingsService settings) {
        this.players = players;
        this.clubs = clubs;
        this.tools = tools;
        this.shortlist = shortlist;
        this.squadAdvice = squadAdvice;
        this.settings = settings;
    }

    public DashboardSnapshot load(String requestedClub) {
        List<PlayerEntity> all = players.findAllPlayerEntities();
        SnapshotHeartbeat.Status heartbeat = SnapshotHeartbeat.from(players.metadata(), all.size());
        String clubName = requestedClub == null ? "" : requestedClub.strip();
        if (all.isEmpty()) {
            return empty(heartbeat, clubName, false);
        }

        ClubEntity club = findClub(clubName);
        if (club == null || clubName.isBlank()) {
            return empty(heartbeat, clubName, false);
        }

        List<PlayerEntity> squad = all.stream().filter(player -> belongsTo(player, clubName)).toList();
        DashboardSnapshot.Metrics metrics = metrics(squad, club);
        List<SquadAdvice.ContractRow> contracts = safeContracts(clubName);
        List<SquadAdvice.SellRow> sellRows = safeSellRows(clubName);
        List<SquadAdvice.AcademyRow> academyRows = safeAcademyRows(clubName);
        DashboardSnapshot.Tactical tactical = tactical(clubName, players.metadata());
        List<FmAiAssistentTools.TransferShortlistRow> shortlist = safeShortlist(clubName);

        List<DashboardSnapshot.Action> actions = new ArrayList<>();
        long expiring = contracts.stream().filter(row -> row.daysUntilExpiry() != null
                && row.daysUntilExpiry() >= 0 && row.daysUntilExpiry() <= 180).count();
        actions.add(new DashboardSnapshot.Action(
                "Contract decisions",
                expiring == 0 ? "No contracts expire in the next six months." : "Renew, sell, or protect the next contract decisions.",
                expiring + " tracked",
                "contracts",
                expiring > 0 ? "warning" : "quiet"));
        long injured = squad.stream().filter(player -> Boolean.TRUE.equals(player.getInjured())).count();
        actions.add(new DashboardSnapshot.Action(
                "Availability check",
                injured == 0 ? "No unavailable players are recorded." : "Review injuries before setting the next XI.",
                injured + " unavailable",
                "desk",
                injured > 0 ? "danger" : "quiet"));
        long sell = sellRows.stream().filter(row -> "sell".equalsIgnoreCase(row.recommendation())).count();
        actions.add(new DashboardSnapshot.Action(
                "Squad trim",
                sell == 0 ? "No high-confidence sales are flagged." : "High-confidence exits can create room and budget.",
                sell + " sell",
                "squad-trim",
                sell > 0 ? "danger" : "quiet"));
        boolean tacticConfigured = !text(players.metadata().get("tactic_slots")).isBlank();
        actions.add(new DashboardSnapshot.Action(
                "Tactical upgrades",
                !tacticConfigured ? "Add a live or pasted tactic to evaluate role fit." : tactical.holes() == 0
                        ? "Your current XI has no recorded positional holes." : "The live XI identifies positions that need attention.",
                !tacticConfigured ? "Needs tactic" : tactical.holes() + " holes",
                !tacticConfigured ? "first-xi" : tactical.holes() > 0 ? "shortlist" : "first-xi",
                !tacticConfigured ? "warning" : tactical.holes() > 0 ? "accent" : "quiet"));
        actions.add(new DashboardSnapshot.Action(
                "Academy pathway",
                academyRows.isEmpty() ? "No academy candidates match the current criteria." : "Young players with first-team upside are ready for review.",
                academyRows.size() + " candidates",
                "academy",
                academyRows.isEmpty() ? "quiet" : "info"));
        actions.add(new DashboardSnapshot.Action(
                "Recruitment pipeline",
                shortlist.isEmpty() ? "No shortlist candidates are available for review." : "Review the highest-ranked targets against squad needs.",
                shortlist.size() + " targets",
                "shortlist",
                shortlist.isEmpty() ? "quiet" : "accent"));

        boolean partial = squad.isEmpty() || !tacticConfigured;
        return new DashboardSnapshot(
                heartbeat,
                clubName,
                true,
                partial,
                metrics,
                List.copyOf(actions),
                tactical,
                shortlist.stream().limit(5).toList(),
                depth(squad),
                trim(sellRows),
                !settings.openRouterApiKey().isBlank(),
                SnapshotStatusModel.from(players.metadata(), all.size()));
    }

    private DashboardSnapshot empty(SnapshotHeartbeat.Status heartbeat, String clubName, boolean aiConfigured) {
        DashboardSnapshot.Metrics metrics = new DashboardSnapshot.Metrics(0, null, null, null, 0, 0, null, 0, 0);
        DashboardSnapshot.Tactical tactical = new DashboardSnapshot.Tactical(List.of(), List.of(), "", 0, null, null);
        return new DashboardSnapshot(heartbeat, clubName, false, false, metrics, List.of(), tactical,
                List.of(), List.of(), new DashboardSnapshot.TrimSummary(0, 0, 0, null), aiConfigured,
                SnapshotStatusModel.fromHeartbeat(heartbeat, 0));
    }

    private DashboardSnapshot.Metrics metrics(List<PlayerEntity> squad, ClubEntity club) {
        int knownValues = (int) squad.stream().filter(this::hasAskingPrice).count();
        long squadValue = squad.stream().map(PlayerEntity::getAskingPrice).filter(DashboardSnapshotService::positive)
                .mapToLong(Long::longValue).sum();
        long wages = squad.stream().map(PlayerEntity::getSalaryWeeklyRaw).filter(value -> value != null)
                .mapToLong(Integer::longValue).sum();
        return new DashboardSnapshot.Metrics(
                squad.size(),
                averageCa(squad),
                club.getTransferBudget(),
                knownValues == 0 ? null : squadValue,
                knownValues,
                wages,
                club.getPayrollBudget(),
                squad.stream().filter(player -> Boolean.TRUE.equals(player.getInjured())).count(),
                squad.stream().filter(this::contractExpiringSoon).count());
    }

    private DashboardSnapshot.Tactical tactical(String clubName, Map<String, Object> metadata) {
        try {
            String source = text(metadata.get("tactic_slots"));
            List<SquadAdvice.XiSlot> slots = FmAiAssistentTools.parseTacticSlots(source.isBlank() ? DEFAULT_TACTIC : source);
            List<FirstXiPick> picks = tools.bestXi(clubName, slots.stream()
                            .map(slot -> new FirstXiSlot(
                                    slot.position(), slot.inPossessionRole(), slot.outOfPossessionRole()))
                            .toList());
            List<String> unavailable = tools.unavailableForClub(clubName).stream()
                    .map(row -> text(row.get("name"))).filter(value -> !value.isBlank()).toList();
            List<Double> fits = picks.stream().map(FirstXiPick::roleFit).filter(value -> value != null).toList();
            Integer fit = fits.isEmpty() ? null : (int) Math.round(fits.stream().mapToDouble(Double::doubleValue).average().orElse(0) / 20d * 100d);
            List<FirstXiPick> filled = picks.stream().filter(pick -> !pick.hole() && pick.ca() > 0).toList();
            int strength = filled.isEmpty() ? 0 : (int) Math.round(filled.stream().mapToInt(FirstXiPick::ca).average().orElse(0));
            return new DashboardSnapshot.Tactical(
                    picks,
                    unavailable,
                    text(metadata.get("tactic_formation")),
                    (int) picks.stream().filter(FirstXiPick::hole).count(), filled.isEmpty() ? null : strength,
                    fit);
        } catch (RuntimeException ignored) {
            return new DashboardSnapshot.Tactical(List.of(), List.of(), text(metadata.get("tactic_formation")), 0, null, null);
        }
    }

    private List<FmAiAssistentTools.TransferShortlistRow> safeShortlist(String clubName) {
        DashboardSectionState<List<FmAiAssistentTools.TransferShortlistRow>> state = DashboardSectionLoader.load(
                () -> shortlist.transferShortlistCandidates(new TransferShortlistQuery(
                                clubName, null, null, 24, null, null, null, null)).stream()
                        .sorted(Comparator.comparingDouble(TransferShortlistCandidate::score).reversed())
                        .map(DashboardSnapshotService::toTransferShortlistRow)
                        .toList(),
                List::isEmpty,
                "Shortlist unavailable");
        return DashboardSectionLoader.or(state, List.of());
    }

    private List<SquadAdvice.ContractRow> safeContracts(String clubName) {
        DashboardSectionState<List<SquadAdvice.ContractRow>> state = DashboardSectionLoader.load(
                () -> squadAdvice.contractRecommendations(clubName).stream()
                        .map(DashboardSnapshotService::toContractRow)
                        .toList(), List::isEmpty, "Contracts unavailable");
        return DashboardSectionLoader.or(state, List.of());
    }

    private List<SquadAdvice.SellRow> safeSellRows(String clubName) {
        DashboardSectionState<List<SquadAdvice.SellRow>> state = DashboardSectionLoader.load(
                () -> squadAdvice.squadSellCandidates(clubName).stream()
                        .map(DashboardSnapshotService::toSellRow)
                        .toList(), List::isEmpty, "Squad trim unavailable");
        return DashboardSectionLoader.or(state, List.of());
    }

    private List<SquadAdvice.AcademyRow> safeAcademyRows(String clubName) {
        DashboardSectionState<List<SquadAdvice.AcademyRow>> state = DashboardSectionLoader.load(
                () -> tools.academyCandidates(clubName, null).stream()
                        .map(DashboardSnapshotService::toAcademyRow)
                        .toList(), List::isEmpty, "Academy unavailable");
        return DashboardSectionLoader.or(state, List.of());
    }

    private static FmAiAssistentTools.TransferShortlistRow toTransferShortlistRow(
            TransferShortlistCandidate candidate) {
        return new FmAiAssistentTools.TransferShortlistRow(
                candidate.rank(), candidate.score(), candidate.name(), candidate.age(), candidate.nationality(),
                candidate.club(), candidate.positionScore(), candidate.roleFit(), candidate.ca(), candidate.pa(),
                candidate.developmentUpside(), candidate.askingPrice(), candidate.salaryWeekly(),
                candidate.willingness(), candidate.freeAgent(), candidate.transferListed(), candidate.injured(),
                candidate.signals());
    }

    private static SquadAdvice.ContractRow toContractRow(ContractRecommendation recommendation) {
        return new SquadAdvice.ContractRow(
                recommendation.name(), recommendation.position(), recommendation.age(), recommendation.ca(),
                recommendation.salaryWeekly(), recommendation.contractEnd(), recommendation.daysUntilExpiry(),
                recommendation.action(), recommendation.reasons());
    }

    private static SquadAdvice.SellRow toSellRow(SquadSellCandidate candidate) {
        return new SquadAdvice.SellRow(
                candidate.rank(), candidate.name(), candidate.age(), candidate.position(), candidate.ca(),
                candidate.pa(), candidate.salaryWeekly(), candidate.askingPrice(), candidate.contractEnd(),
                candidate.depthAtPosition(), candidate.caVsFirstTeam(), candidate.recommendation(),
                candidate.sellScore(), candidate.reasons());
    }

    private static SquadAdvice.AcademyRow toAcademyRow(AcademyCandidate candidate) {
        return new SquadAdvice.AcademyRow(candidate.name(), candidate.position(), candidate.age(), candidate.ca(),
                candidate.pa(), candidate.upside(), candidate.vsFirstTeam(), candidate.dualPositions(),
                candidate.salaryWeekly(), candidate.contractEnd());
    }

    static List<DashboardSnapshot.Depth> depth(List<PlayerEntity> squad) {
        List<DashboardSnapshot.Depth> rows = new ArrayList<>();
        for (String position : DEPTH_POSITIONS) {
            List<Integer> scores = squad.stream().map(player -> Positions.score(player, position))
                    .filter(score -> score >= 10).sorted(Comparator.reverseOrder()).toList();
            int count = scores.size();
            int average = count == 0 ? 0 : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
            rows.add(new DashboardSnapshot.Depth(position, count, average));
        }
        return List.copyOf(rows);
    }

    static DashboardSnapshot.TrimSummary trim(List<SquadAdvice.SellRow> rows) {
        int sell = 0;
        int loan = 0;
        int keep = 0;
        long knownValue = 0;
        boolean hasValue = false;
        for (SquadAdvice.SellRow row : rows) {
            switch (row.recommendation().toLowerCase(Locale.ROOT)) {
                case "sell" -> sell++;
                case "loan" -> loan++;
                default -> keep++;
            }
            if (row.askingPrice() != null && row.askingPrice() > 0) {
                knownValue += row.askingPrice();
                hasValue = true;
            }
        }
        return new DashboardSnapshot.TrimSummary(sell, loan, keep, hasValue ? knownValue : null);
    }

    private ClubEntity findClub(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return clubs.requireNamed(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean belongsTo(PlayerEntity player, String club) {
        return equalsIgnoreCase(player.getClub(), club) || equalsIgnoreCase(player.getPlayingClub(), club);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static Integer averageCa(List<PlayerEntity> squad) {
        return squad.stream().map(PlayerEntity::getCa).filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue).average().stream().mapToInt(value -> (int) Math.round(value)).boxed().findFirst().orElse(null);
    }

    private boolean contractExpiringSoon(PlayerEntity player) {
        Long days = SquadAdvice.daysUntilExpiry(player);
        return days != null && days >= 0 && days <= 180;
    }

    private boolean hasAskingPrice(PlayerEntity player) {
        return positive(player.getAskingPrice());
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
