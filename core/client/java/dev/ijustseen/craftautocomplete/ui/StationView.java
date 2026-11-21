package dev.ijustseen.craftautocomplete.ui;

import dev.ijustseen.craftautocomplete.index.Station;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmokerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The slots of one recipe-book screen the controller works with.
 *
 * @param inputs grid slots in row-major order; a furnace has one
 * @param fuel the furnace fuel slot, or null
 * @param bigResult the result slot has the wide frame of the crafting table and furnaces
 */
public record StationView(
        AbstractRecipeBookScreen<?> screen,
        Station station,
        RecipeBookMenu menu,
        List<Slot> inputs,
        int gridWidth,
        int gridHeight,
        Slot result,
        Slot fuel,
        boolean bigResult
) {
    public static StationView of(Screen screen) {
        if (screen instanceof CraftingScreen crafting) {
            return crafting(crafting, crafting.getMenu(), true);
        }
        if (screen instanceof InventoryScreen inventory) {
            return crafting(inventory, inventory.getMenu(), false);
        }
        if (screen instanceof AbstractFurnaceScreen<?> furnace) {
            AbstractFurnaceMenu menu = furnace.getMenu();
            Station station = menu instanceof BlastFurnaceMenu
                    ? Station.BLAST_FURNACE
                    : menu instanceof SmokerMenu ? Station.SMOKER : Station.FURNACE;
            return new StationView(
                    furnace,
                    station,
                    menu,
                    List.of(menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT)),
                    1,
                    1,
                    menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT),
                    menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT),
                    true
            );
        }
        return null;
    }

    private static StationView crafting(AbstractRecipeBookScreen<?> screen, AbstractCraftingMenu menu, boolean bigResult) {
        return new StationView(
                screen,
                Station.CRAFTING,
                menu,
                menu.getInputGridSlots(),
                menu.getGridWidth(),
                menu.getGridHeight(),
                menu.getResultSlot(),
                null,
                bigResult
        );
    }

    /** Slots whose items still belong to the player: the grid, and the fuel of a furnace. */
    public List<Slot> heldSlots() {
        if (fuel == null) {
            return inputs;
        }
        List<Slot> held = new ArrayList<>(inputs);
        held.add(fuel);
        return held;
    }

    /** The recipe book covers the whole container on a narrow window. */
    public boolean coveredByRecipeBook() {
        return screen.widthTooNarrow && screen.recipeBookComponent.isVisible();
    }
}
