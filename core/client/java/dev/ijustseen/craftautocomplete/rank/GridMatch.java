package dev.ijustseen.craftautocomplete.rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How a recipe fits the items already in the crafting grid.
 *
 * @param missing per grid slot, the item ids to ghost into that empty slot, or null
 * @param filled ingredient slots the player already filled
 * @param required ingredient slots the recipe has
 * @param exact the grid already holds this whole recipe and nothing else
 */
public record GridMatch(int[][] missing, int filled, int required, boolean exact) {
    private static final int[] BLANK = new int[0];

    public double fill() {
        return required == 0 ? 0d : (double) filled / required;
    }

    /**
     * @param grid item id per grid slot in row-major order, -1 for an empty slot
     * @return null when the placed items cannot be part of this recipe
     */
    public static GridMatch of(RecipeFacts recipe, int[] grid, int gridWidth, int gridHeight) {
        return recipe.shaped()
                ? shaped(recipe, grid, gridWidth, gridHeight)
                : shapeless(recipe.ingredients(), grid);
    }

    private static GridMatch shaped(RecipeFacts recipe, int[] grid, int gridWidth, int gridHeight) {
        int width = recipe.width();
        int height = recipe.height();
        if (width > gridWidth || height > gridHeight) {
            return null;
        }
        // Vanilla centres a recipe narrower than half the grid; try its placement first so the
        // ghost on an empty grid lands where Tab will put the items.
        int vanillaX = width < gridWidth / 2f ? (int) Math.floor(gridWidth / 2f - width / 2f) : 0;
        int vanillaY = height < gridHeight / 2f ? (int) Math.floor(gridHeight / 2f - height / 2f) : 0;
        GridMatch match = shapedAt(recipe, grid, gridWidth, gridHeight, vanillaX, vanillaY, false);
        if (match == null) {
            match = shapedAt(recipe, grid, gridWidth, gridHeight, vanillaX, vanillaY, true);
        }
        for (int offsetY = 0; match == null && offsetY <= gridHeight - height; offsetY++) {
            for (int offsetX = 0; match == null && offsetX <= gridWidth - width; offsetX++) {
                match = shapedAt(recipe, grid, gridWidth, gridHeight, offsetX, offsetY, false);
                if (match == null) {
                    match = shapedAt(recipe, grid, gridWidth, gridHeight, offsetX, offsetY, true);
                }
            }
        }
        return match;
    }

    private static GridMatch shapedAt(
            RecipeFacts recipe,
            int[] grid,
            int gridWidth,
            int gridHeight,
            int offsetX,
            int offsetY,
            boolean mirrored
    ) {
        int width = recipe.width();
        int height = recipe.height();
        int[][] missing = new int[grid.length][];
        int filled = 0;
        int required = 0;
        for (int gridY = 0; gridY < gridHeight; gridY++) {
            for (int gridX = 0; gridX < gridWidth; gridX++) {
                int slot = gridY * gridWidth + gridX;
                int recipeX = gridX - offsetX;
                int recipeY = gridY - offsetY;
                int[] cell = BLANK;
                if (recipeX >= 0 && recipeX < width && recipeY >= 0 && recipeY < height) {
                    int column = mirrored ? width - 1 - recipeX : recipeX;
                    cell = recipe.cells()[recipeY * width + column];
                }
                int placed = grid[slot];
                if (cell.length == 0) {
                    if (placed >= 0) {
                        return null;
                    }
                    continue;
                }
                required++;
                if (placed < 0) {
                    missing[slot] = cell;
                } else if (Arrays.binarySearch(cell, placed) >= 0) {
                    filled++;
                } else {
                    return null;
                }
            }
        }
        return new GridMatch(missing, filled, required, filled == required);
    }

    private static GridMatch shapeless(int[][] ingredients, int[] grid) {
        if (ingredients.length > grid.length) {
            return null;
        }
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < grid.length; slot++) {
            if (grid[slot] >= 0) {
                occupied.add(slot);
            }
        }
        if (occupied.size() > ingredients.length) {
            return null;
        }
        int[] ingredientOwner = new int[ingredients.length];
        Arrays.fill(ingredientOwner, -1);
        for (int placed = 0; placed < occupied.size(); placed++) {
            if (!assign(placed, occupied, grid, ingredients, ingredientOwner, new boolean[ingredients.length])) {
                return null;
            }
        }
        int[][] missing = new int[grid.length][];
        int nextSlot = 0;
        for (int ingredient = 0; ingredient < ingredients.length; ingredient++) {
            if (ingredientOwner[ingredient] >= 0) {
                continue;
            }
            while (grid[nextSlot] >= 0) {
                nextSlot++;
            }
            missing[nextSlot++] = ingredients[ingredient];
        }
        int filled = occupied.size();
        return new GridMatch(missing, filled, ingredients.length, filled == ingredients.length);
    }

    /** One augmenting-path step of bipartite matching: placed item to a distinct ingredient. */
    private static boolean assign(
            int placed,
            List<Integer> occupied,
            int[] grid,
            int[][] ingredients,
            int[] ingredientOwner,
            boolean[] visited
    ) {
        int item = grid[occupied.get(placed)];
        for (int ingredient = 0; ingredient < ingredients.length; ingredient++) {
            if (visited[ingredient] || Arrays.binarySearch(ingredients[ingredient], item) < 0) {
                continue;
            }
            visited[ingredient] = true;
            if (ingredientOwner[ingredient] < 0
                    || assign(ingredientOwner[ingredient], occupied, grid, ingredients, ingredientOwner, visited)) {
                ingredientOwner[ingredient] = placed;
                return true;
            }
        }
        return false;
    }
}
