package dev.ijustseen.craftautocomplete.rank;

/**
 * One crafting recipe reduced to item ids. The ranker never sees Minecraft types.
 *
 * @param ingredients each row is the sorted set of item ids that satisfy that ingredient
 * @param cells shaped layout, {@code width * height} rows, an empty row is a blank cell;
 *              for a shapeless recipe this is {@code ingredients} and both sizes are 0
 * @param bulkItem the single item id when this is a 9-of-the-same compression, otherwise -1
 * @param bulkPenaltyBelow owned count of {@code bulkItem} below which compression is penalised
 * @param gearKind equipment slot the output fills, or null
 * @param quality durability of the output, used to compare gear of one kind
 * @param essential furnace, chest, torch or bed
 * @param smelter the output is a furnace
 * @param food the output is edible
 * @param feedsUseful how many gear or essential recipes take the output as an ingredient
 */
public record RecipeFacts(
        int displayId,
        int outputItem,
        int[][] ingredients,
        boolean shaped,
        int width,
        int height,
        int[][] cells,
        boolean bulkCompression,
        int bulkItem,
        int bulkPenaltyBelow,
        GearKind gearKind,
        int quality,
        boolean essential,
        boolean smelter,
        boolean food,
        int outputMaxStack,
        int feedsUseful
) {}
