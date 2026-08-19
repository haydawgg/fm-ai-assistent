package com.github.fmaiassistent.football;

import java.util.List;

/** Small domain seam for ranked recruitment queries. */
public interface TransferShortlistPort {
    List<TransferShortlistCandidate> transferShortlistCandidates(TransferShortlistQuery query);
}
