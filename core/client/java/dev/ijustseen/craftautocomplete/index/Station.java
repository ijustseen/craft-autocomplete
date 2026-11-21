package dev.ijustseen.craftautocomplete.index;

import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;

/**
 * Which block a recipe-book entry is placed in. The 2x2 inventory grid uses {@link #CRAFTING}.
 */
public enum Station {
    CRAFTING,
    FURNACE,
    BLAST_FURNACE,
    SMOKER;

    public static Station of(RecipeBookCategory category) {
        if (category == RecipeBookCategories.CRAFTING_BUILDING_BLOCKS
                || category == RecipeBookCategories.CRAFTING_EQUIPMENT
                || category == RecipeBookCategories.CRAFTING_MISC
                || category == RecipeBookCategories.CRAFTING_REDSTONE) {
            return CRAFTING;
        }
        if (category == RecipeBookCategories.FURNACE_BLOCKS
                || category == RecipeBookCategories.FURNACE_MISC
                || category == RecipeBookCategories.FURNACE_FOOD) {
            return FURNACE;
        }
        if (category == RecipeBookCategories.BLAST_FURNACE_BLOCKS
                || category == RecipeBookCategories.BLAST_FURNACE_MISC) {
            return BLAST_FURNACE;
        }
        if (category == RecipeBookCategories.SMOKER_FOOD) {
            return SMOKER;
        }
        return null;
    }
}
