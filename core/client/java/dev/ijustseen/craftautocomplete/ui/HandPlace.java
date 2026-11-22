package dev.ijustseen.craftautocomplete.ui;

import dev.ijustseen.craftautocomplete.rank.GridMatch;
import dev.ijustseen.craftautocomplete.rank.RecipeFacts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a recipe into the grid by ordinary slot clicks. The vanilla place packet only accepts a
 * recipe the server has put in the player's book, so a mod recipe that never got an unlock
 * advancement has to be moved by hand. The server still accepts the craft when the grid matches.
 */
final class HandPlace {
    private static final int MAIN_INVENTORY_SLOTS = 36;
    private static final int MAX_SETS = 64;

    private HandPlace() {}

    static void place(Minecraft client, StationView view, RecipeFacts facts, boolean useMaxItems) {
        int sets = useMaxItems ? MAX_SETS : 1;
        for (int set = 0; set < sets; set++) {
            if (!oneMore(client, view, facts)) {
                return;
            }
        }
    }

    private static boolean oneMore(Minecraft client, StationView view, RecipeFacts facts) {
        AbstractContainerMenu menu = view.menu();
        if (!menu.getCarried().isEmpty()) {
            return false;
        }
        int[] grid = grid(view);
        GridMatch match = GridMatch.of(facts, grid, view.gridWidth(), view.gridHeight());
        if (match == null) {
            return false;
        }
        if (match.exact()) {
            return stackAnother(client, menu, view, grid);
        }
        return fillMissing(client, menu, view, match);
    }

    /** One item into every empty ingredient slot. Two slots that share an item each take one. */
    private static boolean fillMissing(Minecraft client, AbstractContainerMenu menu, StationView view, GridMatch match) {
        int[][] missing = match.missing();
        int[] chosen = new int[missing.length];
        Arrays.fill(chosen, -1);
        Map<Integer, Integer> available = available(menu);
        for (int slot = 0; slot < missing.length; slot++) {
            if (missing[slot] == null) {
                continue;
            }
            int pick = -1;
            for (int itemId : missing[slot]) {
                if (available.getOrDefault(itemId, 0) > 0) {
                    pick = itemId;
                    break;
                }
            }
            if (pick < 0) {
                return false;
            }
            available.merge(pick, -1, Integer::sum);
            chosen[slot] = pick;
        }
        List<Slot> inputs = view.inputs();
        for (int slot = 0; slot < chosen.length && slot < inputs.size(); slot++) {
            if (chosen[slot] < 0) {
                continue;
            }
            if (!moveOne(client, menu, inputs.get(slot), chosen[slot])) {
                return false;
            }
        }
        return true;
    }

    /** The grid already holds the recipe, so each filled slot receives one more of its item. */
    private static boolean stackAnother(Minecraft client, AbstractContainerMenu menu, StationView view, int[] grid) {
        Map<Integer, Integer> needed = new HashMap<>();
        for (int itemId : grid) {
            if (itemId >= 0) {
                needed.merge(itemId, 1, Integer::sum);
            }
        }
        Map<Integer, Integer> available = available(menu);
        for (Map.Entry<Integer, Integer> entry : needed.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        List<Slot> inputs = view.inputs();
        for (int slot = 0; slot < grid.length && slot < inputs.size(); slot++) {
            if (grid[slot] < 0) {
                continue;
            }
            Slot target = inputs.get(slot);
            ItemStack stacked = target.getItem();
            if (stacked.getCount() >= target.getMaxStackSize(stacked)) {
                return false;
            }
            if (!moveOne(client, menu, target, grid[slot])) {
                return false;
            }
        }
        return true;
    }

    private static boolean moveOne(Minecraft client, AbstractContainerMenu menu, Slot target, int itemId) {
        Slot source = find(menu, itemId);
        if (source == null) {
            return false;
        }
        click(client, menu, source, 0);
        if (menu.getCarried().isEmpty()) {
            return false;
        }
        click(client, menu, target, 1);
        if (!menu.getCarried().isEmpty()) {
            click(client, menu, source, 0);
        }
        return target.getItem().getCount() > 0
                && BuiltInRegistries.ITEM.getId(target.getItem().getItem()) == itemId;
    }

    private static Slot find(AbstractContainerMenu menu, int itemId) {
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) || slot.getContainerSlot() >= MAIN_INVENTORY_SLOTS) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getId(stack.getItem()) == itemId) {
                return slot;
            }
        }
        return null;
    }

    private static Map<Integer, Integer> available(AbstractContainerMenu menu) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) || slot.getContainerSlot() >= MAIN_INVENTORY_SLOTS) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(BuiltInRegistries.ITEM.getId(stack.getItem()), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static int[] grid(StationView view) {
        List<Slot> slots = view.inputs();
        int[] grid = new int[slots.size()];
        for (int slot = 0; slot < grid.length; slot++) {
            ItemStack stack = slots.get(slot).getItem();
            grid[slot] = stack.isEmpty() ? -1 : BuiltInRegistries.ITEM.getId(stack.getItem());
        }
        return grid;
    }

    private static void click(Minecraft client, AbstractContainerMenu menu, Slot slot, int button) {
        client.gameMode.handleContainerInput(menu.containerId, slot.index, button, ContainerInput.PICKUP, client.player);
    }
}
