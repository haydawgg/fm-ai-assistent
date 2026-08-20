package com.github.fmaiassistent.football;

import java.util.List;

/** Input for upgrade suggestions derived from a first-XI result. */
public record FirstXiSuggestionQuery(String managingClub, List<FirstXiPick> picks) {
    public FirstXiSuggestionQuery {
        picks = picks == null ? List.of() : List.copyOf(picks);
    }
}
