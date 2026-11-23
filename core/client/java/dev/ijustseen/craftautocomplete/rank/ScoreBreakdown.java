package dev.ijustseen.craftautocomplete.rank;

/**
 * Candidates sort by ladder rung plus {@code evidence}, then by {@code within}.
 *
 * @param recency freshest acquired ingredient, 0 to 1
 * @param fill share of the recipe already in the grid, 0 to 1
 * @param legacy additive score that orders the {@link SuggestionRanker.Tier#OTHER} tier
 * @param evidence learned accept/reject weight for this recipe's family in the current situation
 */
public record ScoreBreakdown(
        SuggestionRanker.Tier tier,
        double within,
        double recency,
        double fill,
        double legacy,
        double evidence
) {}
