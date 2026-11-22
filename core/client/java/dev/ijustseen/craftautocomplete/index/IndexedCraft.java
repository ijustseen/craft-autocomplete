package dev.ijustseen.craftautocomplete.index;

import dev.ijustseen.craftautocomplete.rank.RecipeFacts;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/**
 * @param entry the recipe-book row, or null when the world has the recipe and the book does not
 * @param handRecipe the world recipe placed by moving items, when {@code entry} is null
 */
public record IndexedCraft(RecipeDisplayEntry entry, RecipeFacts facts, Recipe<?> handRecipe) {
    public boolean canCraft(StackedItemContents contents) {
        if (entry != null) {
            return entry.canCraft(contents);
        }
        return contents.canCraft(handRecipe, ignored -> {});
    }
}
