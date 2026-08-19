package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.CompetitionEntity;
import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.ClubRepository;
import com.github.fmaiassistent.repository.CompetitionRepository;
import com.github.fmaiassistent.repository.LoadMetadataRepository;
import com.github.fmaiassistent.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in fixture data used only to develop and visually verify the desktop UI.
 * It never overwrites an existing snapshot and is deliberately marked in the UI.
 */
@Service
public class DemoDataService implements ApplicationRunner {
    private final boolean enabled;
    private final PlayerRepository players;
    private final ClubRepository clubs;
    private final CompetitionRepository competitions;
    private final LoadMetadataRepository metadata;

    public DemoDataService(
            @Value("${app.ui.demo-data:false}") boolean enabled,
            PlayerRepository players,
            ClubRepository clubs,
            CompetitionRepository competitions,
            LoadMetadataRepository metadata) {
        this.enabled = enabled;
        this.players = players;
        this.clubs = clubs;
        this.competitions = competitions;
        this.metadata = metadata;
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || players.count() > 0) {
            return;
        }
        CompetitionEntity eredivisie = competitions.save(competition("Eredivisie", "Netherlands", 155, 1001));
        CompetitionEntity premierLeague = competitions.save(competition("Premier League", "England", 190, 1002));
        CompetitionEntity primeiraLiga = competitions.save(competition("Primeira Liga", "Portugal", 160, 1003));
        ClubEntity feyenoord = clubs.save(club("Feyenoord", "Eredivisie", "Netherlands", 154, 18_500_000L, 24_000_000L, 10001, eredivisie));
        ClubEntity brighton = clubs.save(club("Brighton", "Premier League", "England", 162, 63_000_000L, 47_000_000L, 10002, premierLeague));
        ClubEntity porto = clubs.save(club("FC Porto", "Primeira Liga", "Portugal", 159, 31_000_000L, 34_000_000L, 10003, primeiraLiga));

        List<PlayerEntity> rows = new ArrayList<>();
        rows.add(player("Jasper Vermeer", "Netherlands", feyenoord, "GK", 142, 150, 29, 18_000, 12_000_000L, false));
        rows.add(player("Milan de Graaf", "Netherlands", feyenoord, "DL", 133, 147, 24, 22_000, 9_500_000L, false));
        rows.add(player("Ruben Smit", "Netherlands", feyenoord, "DC", 139, 144, 27, 28_000, 14_500_000L, true));
        rows.add(player("Tiago Costa", "Portugal", feyenoord, "DC", 145, 153, 23, 31_000, 18_000_000L, false));
        rows.add(player("Ilyas El Amrani", "Morocco", feyenoord, "DR", 132, 151, 21, 15_000, 8_000_000L, false));
        rows.add(player("Daan Koster", "Netherlands", feyenoord, "DMC", 141, 148, 26, 35_000, 16_000_000L, false));
        rows.add(player("Luca van Dijk", "Netherlands", feyenoord, "MC", 146, 158, 22, 38_000, 26_000_000L, false));
        rows.add(player("Mateo Ruiz", "Spain", feyenoord, "AMC", 138, 156, 20, 19_000, 15_000_000L, false));
        rows.add(player("Noah Mensah", "Ghana", feyenoord, "AML", 144, 160, 21, 32_000, 29_000_000L, false));
        rows.add(player("Yassin Bakker", "Netherlands", feyenoord, "AMR", 136, 149, 25, 24_000, 13_000_000L, false));
        rows.add(player("André Silva", "Portugal", feyenoord, "ST", 147, 154, 27, 43_000, 31_000_000L, false));
        rows.add(player("Elias Berg", "Sweden", feyenoord, "MC", 118, 156, 18, 6_000, 3_000_000L, false));
        rows.add(player("Callum Price", "England", brighton, "DL", 149, 153, 25, 54_000, 37_000_000L, false));
        rows.add(player("Rafael Moreira", "Brazil", brighton, "ST", 151, 162, 22, 61_000, 48_000_000L, false));
        rows.add(player("Diogo Santos", "Portugal", porto, "AMC", 143, 165, 19, 17_000, 18_000_000L, false));
        players.saveAll(rows);
        metadata.save(new LoadMetadataEntity("game_date", "18 August 2026"));
        metadata.save(new LoadMetadataEntity("loaded_at", OffsetDateTime.now().toString()));
        metadata.save(new LoadMetadataEntity("demo_data", "true"));
    }

    private static CompetitionEntity competition(String name, String nation, int reputation, long address) {
        return CompetitionEntity.fromExportRow(Map.of(
                "name", name, "nation", nation, "reputation", reputation, "gender", "Male", "sourceAddress", address));
    }

    private static ClubEntity club(
            String name, String competition, String nation, int reputation, long balance, long payroll, long address,
            CompetitionEntity competitionEntity) {
        ClubEntity entity = ClubEntity.fromExportRow(Map.of(
                "name", name, "competition", competition, "nation", nation, "reputation", reputation,
                "balance", balance, "transferBudget", balance / 3, "payrollBudget", payroll,
                "gender", "Male", "sourceAddress", address));
        entity.setCompetitionEntity(competitionEntity);
        return entity;
    }

    private static PlayerEntity player(
            String name, String nationality, ClubEntity club, String position, int ca, int pa, int age,
            int wage, long askingPrice, boolean injured) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("nationality", nationality);
        row.put("club", club.getName());
        row.put("playingClub", club.getName());
        row.put("ca", ca);
        row.put("pa", pa);
        row.put("age", String.valueOf(age));
        row.put("salaryWeeklyRaw", wage);
        row.put("askingPrice", askingPrice);
        row.put("contractEndDate", "30 June 2028");
        row.put("injured", injured);
        row.put("injury", injured ? "Hamstring strain" : "");
        row.put("injuryMinDaysRemaining", injured ? 18 : null);
        row.put("injuryMaxDaysRemaining", injured ? 27 : null);
        row.put("injuryExpectedReturn", injured ? "Early September 2026" : "");
        row.put("recordAddress", "demo-" + name.toLowerCase().replace(' ', '-'));
        row.put("playerIndex", "demo");
        row.put("gender", "Male");
        row.put("pace", Math.min(20, Math.max(9, ca / 8)));
        row.put("stamina", Math.min(20, Math.max(9, ca / 9)));
        row.put("workRate", Math.min(20, Math.max(9, pa / 9)));
        row.put("decisions", Math.min(20, Math.max(9, ca / 9)));
        row.put("technique", Math.min(20, Math.max(9, pa / 9)));
        row.put("passing", Math.min(20, Math.max(9, ca / 9)));
        row.put(positionField(position), 20);
        PlayerEntity entity = PlayerEntity.fromExportRow(row);
        entity.setClubEntity(club);
        entity.setPlayingClubEntity(club);
        return entity;
    }

    private static String positionField(String position) {
        return switch (position) {
            case "GK" -> "goalkeeper";
            case "DL" -> "defenderLeft";
            case "DC" -> "defenderCentral";
            case "DR" -> "defenderRight";
            case "DMC" -> "defensiveMidfielder";
            case "MC" -> "midfielderCentral";
            case "AML" -> "attackingMidfielderLeft";
            case "AMC" -> "attackingMidfielderCentral";
            case "AMR" -> "attackingMidfielderRight";
            case "ST" -> "striker";
            default -> throw new IllegalArgumentException("Unsupported demo position: " + position);
        };
    }
}
