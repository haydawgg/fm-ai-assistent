package com.github.fmaiassistent.football;

import java.util.List;

/** Domain seam for squad-trim, contract, and wage advice. */
public interface SquadAdvicePort {
    List<SquadSellCandidate> squadSellCandidates(String managingClub);

    List<ContractRecommendation> contractRecommendations(String managingClub);

    SquadWageHealth squadWageHealth(String managingClub);
}
