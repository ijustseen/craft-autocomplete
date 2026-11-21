package dev.ijustseen.craftautocomplete.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * Picks fuel for a furnace Tab. Burn times are resolved on the server, so only fuels with a
 * well-known yield are used: coal and charcoal smelt 8 items, logs and planks 1.5.
 */
final class FurnaceFuel {
    private static final int MAIN_INVENTORY_SLOTS = 36;
    /** Yields in half items, to stay in integers. */
    private static final int COAL_HALF_ITEMS = 16;
    private static final int WOOD_HALF_ITEMS = 3;

    private FurnaceFuel() {}

    /** The inventory slot with the fuel to use, or null; never an item the recipe itself smelts. */
    static Slot choose(AbstractContainerMenu menu, int[] smeltedItems) {
        Slot best = null;
        int bestYield = 0;
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) || slot.getContainerSlot() >= MAIN_INVENTORY_SLOTS) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.has(DataComponents.COOKING_FUEL)) {
                continue;
            }
            if (Arrays.binarySearch(smeltedItems, BuiltInRegistries.ITEM.getId(stack.getItem())) >= 0) {
                continue;
            }
            int yield = halfItemsPerFuel(stack);
            if (yield > bestYield || (yield == bestYield && best != null && stack.getCount() > best.getItem().getCount())) {
                best = slot;
                bestYield = yield;
            }
        }
        return best;
    }

    /** Moves just enough fuel for {@code itemsToSmelt} into the empty fuel slot, then returns the rest. */
    static void fill(Minecraft client, AbstractContainerMenu menu, Slot fuelSlot, Slot source, int itemsToSmelt) {
        int yield = halfItemsPerFuel(source.getItem());
        if (yield == 0 || itemsToSmelt <= 0 || fuelSlot.hasItem() || !menu.getCarried().isEmpty()) {
            return;
        }
        int needed = Math.min(source.getItem().getCount(), Math.ceilDiv(itemsToSmelt * 2, yield));
        click(client, menu, source, 0);
        if (needed >= menu.getCarried().getCount()) {
            click(client, menu, fuelSlot, 0);
        } else {
            for (int placed = 0; placed < needed; placed++) {
                click(client, menu, fuelSlot, 1);
            }
        }
        if (!menu.getCarried().isEmpty()) {
            click(client, menu, source, 0);
        }
    }

    private static int halfItemsPerFuel(ItemStack stack) {
        if (stack.is(ItemTags.COALS)) {
            return COAL_HALF_ITEMS;
        }
        if (stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS)) {
            return WOOD_HALF_ITEMS;
        }
        return 0;
    }

    private static void click(Minecraft client, AbstractContainerMenu menu, Slot slot, int button) {
        client.gameMode.handleContainerInput(menu.containerId, slot.index, button, ContainerInput.PICKUP, client.player);
    }
}
