package dev.ijustseen.craftautocomplete.rank;

import java.util.Map;
import java.util.function.IntFunction;

/**
 * Coarse keys the learned weights use. Gear of one kind shares a key, so a stone pickaxe and an
 * iron pickaxe teach the same preference. The situation ignores item counts: it repeats.
 */
public final class CraftSignals {
    private CraftSignals() {}

    public static String situation(
            String station,
            boolean hungry,
            boolean smeltNeed,
            Map<GearKind, Integer> bestQuality,
            boolean partial
    ) {
        String gap = "none";
        for (GearKind kind : GearKind.values()) {
            if (bestQuality.getOrDefault(kind, 0) == 0) {
                gap = kind.name();
                break;
            }
        }
        return station
                + "|h" + (hungry ? 1 : 0)
                + "|s" + (smeltNeed ? 1 : 0)
                + "|g" + gap
                + "|p" + (partial ? 1 : 0);
    }

    public static String family(RecipeFacts facts, IntFunction<String> itemKey) {
        if (facts.gearKind() != null) {
            return "gear:" + facts.gearKind().name();
        }
        if (facts.essential()) {
            return "essential:" + itemKey.apply(facts.outputItem());
        }
        if (facts.food()) {
            return "food";
        }
        return "item:" + itemKey.apply(facts.outputItem());
    }
}
