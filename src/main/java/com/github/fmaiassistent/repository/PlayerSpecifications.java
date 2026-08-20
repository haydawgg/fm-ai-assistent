package com.github.fmaiassistent.repository;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlayerSpecifications {

    private PlayerSpecifications() {
    }

    public static Specification<PlayerEntity> fromFilter(PlayerFilterCriteria filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String name = filter.name();
            if (name != null && !name.isBlank()) {
                String escaped = name.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + escaped + "%", '\\'));
            }
            if (filter.club() != null && !filter.club().isBlank()) {
                String clubLower = filter.club().trim().toLowerCase(Locale.ROOT);
                if (filter.advanced().clubScope() == PlayerFilterCriteria.ClubScope.CONTRACTED) {
                    predicates.add(cb.or(cb.equal(cb.lower(root.get("club")), clubLower),
                            cb.equal(cb.lower(root.get("clubEntity").get("name")), clubLower)));
                } else if (filter.advanced().clubScope() == PlayerFilterCriteria.ClubScope.PLAYING) {
                    predicates.add(cb.or(cb.equal(cb.lower(root.get("playingClub")), clubLower),
                            cb.equal(cb.lower(root.get("playingClubEntity").get("name")), clubLower)));
                } else {
                    predicates.add(cb.or(cb.equal(cb.lower(root.get("club")), clubLower),
                            cb.equal(cb.lower(root.get("playingClub")), clubLower),
                            cb.equal(cb.lower(root.get("clubEntity").get("name")), clubLower),
                            cb.equal(cb.lower(root.get("playingClubEntity").get("name")), clubLower)));
                }
            }
            addEqualsIgnoreCase(predicates, cb, root, "gender", filter.gender());
            addEqualsIgnoreCase(predicates, cb, root, "nationality", filter.nationality());
            if (filter.playingNation() != null && !filter.playingNation().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("playingClubEntity").get("nation")),
                        filter.playingNation().trim().toLowerCase(Locale.ROOT)));
            }
            if (filter.playingCompetition() != null && !filter.playingCompetition().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("playingClubEntity").get("competition")),
                        filter.playingCompetition().trim().toLowerCase(Locale.ROOT)));
            }
            addIntRange(predicates, cb, root, "heightCm", filter.heightMin(), filter.heightMax());
            addIntRange(predicates, cb, root, "ageNumeric", filter.ageMin(), filter.ageMax());
            addIntRange(predicates, cb, root, "currentReputation", filter.currentReputationMin(), filter.currentReputationMax());
            addIntRange(predicates, cb, root, "homeReputation", filter.homeReputationMin(), filter.homeReputationMax());
            addIntRange(predicates, cb, root, "worldReputation", filter.worldReputationMin(), filter.worldReputationMax());
            addIntRange(predicates, cb, root, "ca", filter.caMin(), filter.caMax());
            addIntRange(predicates, cb, root, "pa", filter.paMin(), filter.paMax());
            addLongRange(predicates, cb, root, "askingPrice", filter.askingPriceMin(), filter.askingPriceMax());
            if (filter.salaryMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("salaryWeeklyRaw"), filter.salaryMax()));
            }
            PlayerFilterCriteria.Advanced advanced = filter.advanced();
            addBoolean(predicates, cb, root, "injured", advanced.injured());
            addBoolean(predicates, cb, root, "transferListed", advanced.transferListed());
            addBoolean(predicates, cb, root, "listedForLoan", advanced.listedForLoan());
            addBoolean(predicates, cb, root, "transferAgreed", advanced.transferAgreed());
            if (advanced.freeAgent() != null) {
                Predicate noClub = cb.or(cb.isNull(root.get("club")), cb.equal(root.get("club"), ""));
                predicates.add(advanced.freeAgent() ? noClub : cb.not(noClub));
            }
            if (advanced.loanStatus() == PlayerFilterCriteria.LoanStatus.LOANED) {
                predicates.add(cb.equal(cb.lower(root.get("isLoanedOut")), "yes"));
            } else if (advanced.loanStatus() == PlayerFilterCriteria.LoanStatus.NOT_LOANED) {
                predicates.add(cb.or(cb.isNull(root.get("isLoanedOut")),
                        cb.notEqual(cb.lower(root.get("isLoanedOut")), "yes")));
            }
            addIntRange(predicates, cb, root, "appearances", advanced.appearancesMin(), advanced.appearancesMax());
            addIntRange(predicates, cb, root, "starts", advanced.startsMin(), advanced.startsMax());
            addIntRange(predicates, cb, root, "minutes", advanced.minutesMin(), advanced.minutesMax());
            addIntRange(predicates, cb, root, "goals", advanced.goalsMin(), advanced.goalsMax());
            addIntRange(predicates, cb, root, "assists", advanced.assistsMin(), advanced.assistsMax());
            addDoubleRange(predicates, cb, root, "averageRating", advanced.averageRatingMin(), advanced.averageRatingMax());
            addStringRange(predicates, cb, root, "contractEndDate",
                    filter.contractEndDateFrom() == null ? null : filter.contractEndDateFrom().toString(),
                    filter.contractEndDateTo() == null ? null : filter.contractEndDateTo().toString());
            addMinimums(predicates, cb, root, filter.positionMinimums());
            addMinimums(predicates, cb, root, filter.attributeMinimums());
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addEqualsIgnoreCase(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                            jakarta.persistence.criteria.Root<PlayerEntity> root,
                                            String field, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(cb.equal(cb.lower(root.get(field)), value.trim().toLowerCase(Locale.ROOT)));
        }
    }

    private static void addIntRange(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                    jakarta.persistence.criteria.Root<PlayerEntity> root,
                                    String field, Integer min, Integer max) {
        if (min == null && max == null) {
            return;
        }
        if (min != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(field), min));
        }
        if (max != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(field), max));
        }
    }

    private static void addLongRange(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                     jakarta.persistence.criteria.Root<PlayerEntity> root,
                                     String field, Long min, Long max) {
        if (min == null && max == null) {
            return;
        }
        if (min != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(field), min));
        }
        if (max != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(field), max));
        }
    }

    private static void addDoubleRange(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                       jakarta.persistence.criteria.Root<PlayerEntity> root, String field,
                                       Double min, Double max) {
        if (min != null) predicates.add(cb.greaterThanOrEqualTo(root.get(field), min));
        if (max != null) predicates.add(cb.lessThanOrEqualTo(root.get(field), max));
    }

    private static void addStringRange(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                       jakarta.persistence.criteria.Root<PlayerEntity> root, String field,
                                       String min, String max) {
        if (min != null) predicates.add(cb.greaterThanOrEqualTo(root.get(field), min));
        if (max != null) predicates.add(cb.lessThanOrEqualTo(root.get(field), max));
    }

    private static void addBoolean(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                   jakarta.persistence.criteria.Root<PlayerEntity> root, String field, Boolean value) {
        if (value != null) predicates.add(value ? cb.isTrue(root.get(field)) : cb.isFalse(root.get(field)));
    }

    @SuppressWarnings("unchecked")
    private static void addMinimums(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                    jakarta.persistence.criteria.Root<PlayerEntity> root,
                                    Map<String, Integer> minimums) {
        for (Map.Entry<String, Integer> entry : minimums.entrySet()) {
            String fieldName = PlayerColumnNames.toEntityFieldName(entry.getKey().toLowerCase(Locale.ROOT));
            predicates.add(cb.greaterThanOrEqualTo(root.get(fieldName), entry.getValue()));
        }
    }
}
