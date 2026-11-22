package dev.ijustseen.craftautocomplete.rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Orders an already bounded set of craftable recipes as a ladder of tiers.
 * Retrieval and {@code canCraft} stay outside: this only orders.
 */
public final class SuggestionRanker {
    public static final double RECENCY_WINDOW_MS = 60_000d;
    public static final double RECENCY_TAU_MS = 20_000d;
    public static final long REPEAT_WINDOW_MS = 10_000L;
    public static final double RECENCY_WEIGHT = 10d;
    public static final double FILL_WEIGHT = 20d;
    public static final int FRESH_OUTPUT = 40;
    public static final int OWNED = -30;
    public static final int COMPRESSION = -50;

    /** Declaration order is the ladder, top first. */
    public enum Tier {
        /** The grid already holds this whole recipe; Tab adds one more set. */
        COMPLETE,
        /** Same output as the craft just taken from the table. */
        REPEAT,
        /** No furnace, and the player holds something that smelts into a gear material. */
        SMELTER,
        /**
         * A recipe the server never sent in the book, almost always from a mod, and the player
         * does not own the result yet. It outranks gear made from the same materials.
         */
        OFF_BOOK,
        /** Gear of a kind the player lacks, or better than what they hold. One per kind. */
        GEAR_GAP,
        /** Furnace, chest, torch or bed the player does not own. */
        ESSENTIAL,
        /** Food while the hunger bar is down. */
        HUNGRY_FOOD,
        /** Feeds gear or essentials and the player has under a stack of it. */
        MATERIAL,
        FOOD,
        OTHER
    }

    public record Candidate(RecipeFacts facts, GridMatch match, int sourceIndex) {
        public Candidate(RecipeFacts facts, GridMatch match) {
            this(facts, match, -1);
        }

        Candidate at(int index) {
            return new Candidate(facts, match, index);
        }
    }

    /**
     * @param bestQuality highest durability owned per gear kind
     * @param smeltNeed the player holds an item that smelts into a gear material
     * @param hungry the hunger bar has dropped enough that food should come before materials
     * @param carriedItem item on the cursor, -1 when empty; it is what the player is about to place
     * @param partial the grid already holds items the player placed
     */
    public record Context(
            Map<Integer, Integer> inventory,
            Map<Integer, Long> recentTimes,
            Map<GearKind, Integer> bestQuality,
            boolean smeltNeed,
            boolean hungry,
            int carriedItem,
            int lastCraftItem,
            long lastCraftTime,
            long nowMillis,
            boolean partial
    ) {}

    public record Scored(int index, ScoreBreakdown score) {}

    private SuggestionRanker() {}

    public static List<Scored> rank(List<Candidate> candidates, Context context) {
        candidates = usingCarried(candidates, context.carriedItem());
        int size = candidates.size();
        Tier[] tiers = new Tier[size];
        Map<GearKind, Integer> bestGap = new EnumMap<>(GearKind.class);
        for (int index = 0; index < size; index++) {
            RecipeFacts facts = candidates.get(index).facts();
            tiers[index] = baseTier(facts, context);
            if (tiers[index] == Tier.GEAR_GAP) {
                bestGap.merge(facts.gearKind(), facts.quality(), Math::max);
            }
        }
        boolean anyUseful = false;
        for (int index = 0; index < size; index++) {
            RecipeFacts facts = candidates.get(index).facts();
            if (tiers[index] == Tier.GEAR_GAP && facts.quality() < bestGap.get(facts.gearKind())) {
                tiers[index] = Tier.OTHER;
            }
            anyUseful |= tiers[index] != Tier.OTHER;
        }
        if (context.partial()) {
            // A finished grid wins unless it is filler and the placed items also start something useful.
            for (int index = 0; index < size; index++) {
                GridMatch match = candidates.get(index).match();
                if (match != null && match.exact() && (tiers[index] != Tier.OTHER || !anyUseful)) {
                    tiers[index] = Tier.COMPLETE;
                }
            }
        }
        List<Scored> scored = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            if (!context.partial() && anyUseful && tiers[index] == Tier.OTHER) {
                continue;
            }
            scored.add(new Scored(index, score(candidates.get(index), tiers[index], context)));
        }
        List<Candidate> ordered = candidates;
        scored.sort(Comparator
                .comparingInt((Scored entry) -> entry.score().tier().ordinal())
                .thenComparing(Comparator.comparingDouble((Scored entry) -> entry.score().within()).reversed())
                .thenComparingInt(entry -> ordered.get(entry.index()).facts().displayId()));
        List<Scored> remapped = new ArrayList<>(scored.size());
        for (Scored entry : scored) {
            remapped.add(new Scored(ordered.get(entry.index()).sourceIndex, entry.score()));
        }
        return remapped;
    }

    /**
     * Keeps only recipes that use the item on the cursor, when there are any.
     * Indexes in the result still point into the caller's list.
     */
    private static List<Candidate> usingCarried(List<Candidate> candidates, int carriedItem) {
        List<Candidate> all = new ArrayList<>(candidates.size());
        List<Candidate> using = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index).at(index);
            all.add(candidate);
            if (carriedItem >= 0 && mentions(candidate.facts(), carriedItem)) {
                using.add(candidate);
            }
        }
        return using.isEmpty() ? all : using;
    }

    private static boolean mentions(RecipeFacts facts, int itemId) {
        for (int[] ingredient : facts.ingredients()) {
            if (Arrays.binarySearch(ingredient, itemId) >= 0) {
                return true;
            }
        }
        return false;
    }

    static Tier baseTier(RecipeFacts facts, Context context) {
        int owned = context.inventory().getOrDefault(facts.outputItem(), 0);
        if (isRepeat(facts, context)) {
            return Tier.REPEAT;
        }
        if (facts.smelter() && owned == 0 && context.smeltNeed()) {
            return Tier.SMELTER;
        }
        if (facts.essential() && owned == 0) {
            return Tier.ESSENTIAL;
        }
        if (facts.gearKind() != null
                && facts.quality() > context.bestQuality().getOrDefault(facts.gearKind(), 0)) {
            return Tier.GEAR_GAP;
        }
        if (facts.offBook() && owned == 0 && !facts.bulkCompression()) {
            return Tier.OFF_BOOK;
        }
        if (facts.food()) {
            return context.hungry() ? Tier.HUNGRY_FOOD : Tier.FOOD;
        }
        if (facts.feedsUseful() > 0 && !facts.bulkCompression() && owned < facts.outputMaxStack()) {
            return Tier.MATERIAL;
        }
        return Tier.OTHER;
    }

    private static ScoreBreakdown score(Candidate candidate, Tier tier, Context context) {
        RecipeFacts facts = candidate.facts();
        double recency = 0d;
        for (int[] ingredient : facts.ingredients()) {
            recency = Math.max(recency, bestRecency(ingredient, context.recentTimes(), context.nowMillis()));
        }
        if (context.carriedItem() >= 0 && mentions(facts, context.carriedItem())) {
            recency = 1d;
        }
        double fill = candidate.match() == null ? 0d : candidate.match().fill();
        double legacy = legacy(facts, context);
        double within = fill * FILL_WEIGHT;
        if (tier == Tier.OTHER) {
            within += legacy;
        } else {
            within += recency * RECENCY_WEIGHT;
            if (tier == Tier.GEAR_GAP) {
                within += facts.gearKind().priority();
            }
        }
        return new ScoreBreakdown(tier, within, recency, fill, legacy);
    }

    /** Fallback order for filler: summed ingredient freshness, new output, already owned, compression. */
    static double legacy(RecipeFacts facts, Context context) {
        double recency = 0d;
        for (int[] ingredient : facts.ingredients()) {
            recency += bestRecency(ingredient, context.recentTimes(), context.nowMillis());
        }
        int owned = context.inventory().getOrDefault(facts.outputItem(), 0);
        double total = recency;
        if (owned == 0) {
            total += FRESH_OUTPUT;
        } else if (!isRepeat(facts, context)) {
            total += OWNED;
        }
        if (facts.bulkCompression()
                && facts.bulkItem() >= 0
                && context.inventory().getOrDefault(facts.bulkItem(), 0) < facts.bulkPenaltyBelow()) {
            total += COMPRESSION;
        }
        return total;
    }

    private static boolean isRepeat(RecipeFacts facts, Context context) {
        return context.lastCraftItem() == facts.outputItem()
                && context.lastCraftTime() > 0L
                && context.nowMillis() - context.lastCraftTime() < REPEAT_WINDOW_MS;
    }

    private static double bestRecency(int[] acceptedItems, Map<Integer, Long> recentTimes, long nowMillis) {
        double best = 0d;
        for (int itemId : acceptedItems) {
            Long acquiredAt = recentTimes.get(itemId);
            if (acquiredAt == null) {
                continue;
            }
            double age = nowMillis - acquiredAt;
            if (age < 0d || age > RECENCY_WINDOW_MS) {
                continue;
            }
            best = Math.max(best, Math.exp(-age / RECENCY_TAU_MS));
        }
        return best;
    }
}
