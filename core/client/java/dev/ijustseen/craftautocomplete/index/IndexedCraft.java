package dev.ijustseen.craftautocomplete.index;

import dev.ijustseen.craftautocomplete.rank.RecipeFacts;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

public record IndexedCraft(RecipeDisplayEntry entry, RecipeFacts facts) {}
