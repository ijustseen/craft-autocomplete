package dev.ijustseen.craftautocomplete.rank;

/**
 * Equipment slots the ranker fills. Declaration order is the priority when several gaps are craftable.
 */
public enum GearKind {
    PICKAXE,
    SWORD,
    AXE,
    CHESTPLATE,
    LEGGINGS,
    HELMET,
    BOOTS,
    SHIELD,
    SHOVEL,
    HOE;

    public double priority() {
        return (values().length - ordinal()) * 0.5d;
    }
}
