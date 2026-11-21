package dev.ijustseen.craftautocomplete.rank;

/**
 * Candidates sort by {@code tier} first, then by {@code within} inside the tier.
 *
 * @param recency freshest acquired ingredient, 0 to 1
 * @param fill share of the recipe already in the grid, 0 to 1
 * @param legacy additive score that orders the {@link SuggestionRanker.Tier#OTHER} tier
 */
public record ScoreBreakdown(
        SuggestionRanker.Tier tier,
        double within,
        double recency,
        double fill,
        double legacy
) {}
