package dev.ijustseen.craftautocomplete.index;

import dev.ijustseen.craftautocomplete.rank.GearKind;
import dev.ijustseen.craftautocomplete.rank.RecipeFacts;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Per station: item id to the recipe-book entries that accept it, plus how many recipes mention the item.
 * Rebuilt when the client replaces its recipe-book collection list.
 */
public final class CraftIndex {
    public static final int CANDIDATE_LIMIT = 200;
    public static final int MIN_CRAFTABLE_BEFORE_EXPANDING = 5;
    private static final int COMPRESSION_STACKS = 4;

    private final Map<Station, StationIndex> stations = new EnumMap<>(Station.class);
    /** Furnace inputs whose smelted output goes into a gear recipe: raw iron, not logs. */
    private final HashSet<Integer> smeltsIntoGear = new HashSet<>();
    private List<RecipeCollection> indexedCollections = List.of();

    public void rebuildIfNeeded(ClientRecipeBook book, ClientLevel level) {
        List<RecipeCollection> collections = book.getCollections();
        if (collections == indexedCollections) {
            return;
        }
        rebuild(collections, SlotDisplayContext.fromLevel(level), level.enabledFeatures());
        indexedCollections = collections;
    }

    public boolean needsSmelting(Map<Integer, Integer> inventory) {
        for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
            if (entry.getValue() > 0 && smeltsIntoGear.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Candidates for an empty grid.
     *
     * @param carriedItem item on the cursor, -1 when empty; its recipes are read first
     */
    public List<IndexedCraft> craftable(
            Station station,
            StackedItemContents contents,
            int[] recentNewestFirst,
            int carriedItem,
            Map<Integer, Integer> inventory
    ) {
        StationIndex index = stations.get(station);
        if (index == null) {
            return List.of();
        }
        HashSet<Integer> seen = new HashSet<>();
        List<IndexedCraft> pool = new ArrayList<>();
        if (carriedItem >= 0) {
            pool.addAll(index.take(new int[]{carriedItem}, CANDIDATE_LIMIT, seen));
        }
        for (IndexedCraft craft : index.useful) {
            if (seen.add(craft.facts().displayId())) {
                pool.add(craft);
            }
        }
        pool.addAll(index.take(recentNewestFirst, CANDIDATE_LIMIT, seen));
        List<IndexedCraft> alive = keepCraftable(pool, contents);
        if (alive.size() < MIN_CRAFTABLE_BEFORE_EXPANDING) {
            alive.addAll(keepCraftable(index.take(index.rarestOwned(inventory), CANDIDATE_LIMIT, seen), contents));
        }
        return alive;
    }

    /** Every indexed recipe that mentions all of {@code placedItems}, read from the shortest posting list. */
    public List<IndexedCraft> containing(Station station, int[] placedItems) {
        StationIndex index = stations.get(station);
        if (index == null) {
            return List.of();
        }
        List<IndexedCraft> shortest = null;
        for (int itemId : placedItems) {
            List<IndexedCraft> matches = index.byItem.get(itemId);
            if (matches == null) {
                return List.of();
            }
            if (shortest == null || matches.size() < shortest.size()) {
                shortest = matches;
            }
        }
        return shortest == null ? List.of() : shortest;
    }

    public static List<IndexedCraft> keepCraftable(List<IndexedCraft> candidates, StackedItemContents contents) {
        List<IndexedCraft> alive = new ArrayList<>();
        for (IndexedCraft craft : candidates) {
            if (craft.entry().canCraft(contents)) {
                alive.add(craft);
            }
        }
        return alive;
    }

    private void rebuild(List<RecipeCollection> collections, ContextMap context, FeatureFlagSet features) {
        stations.clear();
        smeltsIntoGear.clear();
        int airId = BuiltInRegistries.ITEM.getId(Items.AIR);
        HashSet<Integer> seenDisplays = new HashSet<>();
        List<Draft> drafts = new ArrayList<>();
        for (RecipeCollection collection : collections) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!seenDisplays.add(entry.id().index())) {
                    continue;
                }
                Draft draft = draft(entry, context, features, airId);
                if (draft != null) {
                    drafts.add(draft);
                }
            }
        }
        Map<Integer, Integer> feeds = new HashMap<>();
        HashSet<Integer> gearMaterials = new HashSet<>();
        for (Draft draft : drafts) {
            if (draft.gearKind == null && !draft.essential) {
                continue;
            }
            for (int itemId : mentioned(draft.ingredients)) {
                feeds.merge(itemId, 1, Integer::sum);
                if (draft.gearKind != null) {
                    gearMaterials.add(itemId);
                }
            }
        }
        for (Draft draft : drafts) {
            if (draft.station == Station.FURNACE && gearMaterials.contains(draft.outputId)) {
                for (int input : draft.ingredients[0]) {
                    smeltsIntoGear.add(input);
                }
            }
            int feedsUseful = feeds.getOrDefault(draft.outputId, 0);
            IndexedCraft craft = new IndexedCraft(draft.entry, draft.facts(feedsUseful));
            StationIndex index = stations.computeIfAbsent(draft.station, ignored -> new StationIndex());
            for (int itemId : mentioned(draft.ingredients)) {
                index.byItem.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(craft);
            }
            if (draft.gearKind != null || draft.essential || draft.food || feedsUseful > 0) {
                index.useful.add(craft);
            }
        }
        for (StationIndex index : stations.values()) {
            for (Map.Entry<Integer, List<IndexedCraft>> entry : index.byItem.entrySet()) {
                index.rarity.put(entry.getKey(), entry.getValue().size());
            }
        }
    }

    private static Draft draft(RecipeDisplayEntry entry, ContextMap context, FeatureFlagSet features, int airId) {
        Station station = Station.of(entry.category());
        if (station == null) {
            return null;
        }
        RecipeDisplay display = entry.display();
        if (!display.isEnabled(features)) {
            return null;
        }
        ItemStack result = display.result().resolveForFirstStack(context);
        if (result.isEmpty()) {
            return null;
        }
        int outputId = BuiltInRegistries.ITEM.getId(result.getItem());
        if (outputId == airId) {
            return null;
        }
        Draft draft;
        if (station == Station.CRAFTING) {
            draft = craftingDraft(entry, display, outputId, context, airId);
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            int[] inputs = Arrays.stream(itemIds(furnace.ingredient(), context, airId))
                    .filter(id -> !breaksGear(BuiltInRegistries.ITEM.byId(id)))
                    .toArray();
            draft = inputs.length == 0 ? null : new Draft(entry, outputId, new int[][]{inputs});
            if (draft != null) {
                draft.cells = draft.ingredients;
            }
        } else {
            draft = null;
        }
        if (draft == null) {
            return null;
        }
        draft.station = station;
        Item output = result.getItem();
        draft.gearKind = ItemTraits.gearKind(output);
        draft.quality = draft.gearKind == null ? 0 : ItemTraits.quality(output);
        draft.essential = ItemTraits.essential(output);
        draft.smelter = ItemTraits.smelter(output);
        draft.food = result.has(DataComponents.FOOD);
        draft.outputMaxStack = output.getDefaultMaxStackSize();
        return draft;
    }

    private static Draft craftingDraft(
            RecipeDisplayEntry entry,
            RecipeDisplay display,
            int outputId,
            ContextMap context,
            int airId
    ) {
        if (!(display instanceof ShapedCraftingRecipeDisplay) && !(display instanceof ShapelessCraftingRecipeDisplay)) {
            return null;
        }
        List<Ingredient> requirements = entry.craftingRequirements().orElse(List.of());
        if (requirements.isEmpty()) {
            return null;
        }
        int[][] ingredients = new int[requirements.size()][];
        for (int index = 0; index < requirements.size(); index++) {
            int[] ids = itemIds(requirements.get(index).display(), context, airId);
            if (ids.length == 0) {
                return null;
            }
            ingredients[index] = ids;
        }
        Draft draft = new Draft(entry, outputId, ingredients);
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            List<SlotDisplay> slots = shaped.ingredients();
            int[][] cells = new int[slots.size()][];
            int filledCells = 0;
            for (int index = 0; index < slots.size(); index++) {
                cells[index] = itemIds(slots.get(index), context, airId);
                if (cells[index].length > 0) {
                    filledCells++;
                }
            }
            if (filledCells != ingredients.length || cells.length != shaped.width() * shaped.height()) {
                return null;
            }
            draft.shaped = true;
            draft.width = shaped.width();
            draft.height = shaped.height();
            draft.cells = cells;
        } else {
            draft.cells = ingredients;
        }
        draft.bulk = sameIngredientNineTimes(ingredients);
        draft.bulkItem = draft.bulk && ingredients[0].length == 1 ? ingredients[0][0] : -1;
        if (draft.bulkItem >= 0) {
            Item bulkStackItem = BuiltInRegistries.ITEM.byId(draft.bulkItem);
            draft.penaltyBelow = COMPRESSION_STACKS * bulkStackItem.getDefaultMaxStackSize();
        }
        return draft;
    }

    /** Smelting tools and armor back into nuggets destroys gear, so it is never suggested. */
    private static boolean breaksGear(Item item) {
        return item.getDefaultInstance().getMaxDamage() > 0;
    }

    private static int[] itemIds(SlotDisplay display, ContextMap context, int airId) {
        return display.resolveForStacks(context).stream()
                .map(ItemStack::getItem)
                .mapToInt(BuiltInRegistries.ITEM::getId)
                .filter(id -> id != airId)
                .distinct()
                .sorted()
                .toArray();
    }

    private static int[] mentioned(int[][] ingredients) {
        return Arrays.stream(ingredients).flatMapToInt(Arrays::stream).distinct().toArray();
    }

    private static boolean sameIngredientNineTimes(int[][] ingredients) {
        if (ingredients.length != 9) {
            return false;
        }
        int[] first = ingredients[0];
        for (int index = 1; index < ingredients.length; index++) {
            if (!Arrays.equals(first, ingredients[index])) {
                return false;
            }
        }
        return true;
    }

    private static final class StationIndex {
        final Map<Integer, List<IndexedCraft>> byItem = new HashMap<>();
        final Map<Integer, Integer> rarity = new HashMap<>();
        /** Gear, essentials, food and the materials that feed them: always checked, whatever was picked up. */
        final List<IndexedCraft> useful = new ArrayList<>();

        List<IndexedCraft> take(int[] itemIdsNewestFirst, int limit, HashSet<Integer> seen) {
            List<IndexedCraft> taken = new ArrayList<>();
            for (int itemId : itemIdsNewestFirst) {
                List<IndexedCraft> matches = byItem.get(itemId);
                if (matches == null) {
                    continue;
                }
                for (IndexedCraft craft : matches) {
                    if (!seen.add(craft.facts().displayId())) {
                        continue;
                    }
                    taken.add(craft);
                    if (taken.size() == limit) {
                        return taken;
                    }
                }
            }
            return taken;
        }

        int[] rarestOwned(Map<Integer, Integer> inventory) {
            ArrayList<Integer> items = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
                if (entry.getValue() <= 0 || rarity.getOrDefault(entry.getKey(), 0) <= 0) {
                    continue;
                }
                items.add(entry.getKey());
            }
            items.sort(Comparator
                    .comparingInt((Integer itemId) -> rarity.getOrDefault(itemId, Integer.MAX_VALUE))
                    .thenComparingInt(itemId -> itemId));
            int[] ids = new int[items.size()];
            for (int index = 0; index < items.size(); index++) {
                ids[index] = items.get(index);
            }
            return ids;
        }
    }

    private static final class Draft {
        final RecipeDisplayEntry entry;
        final int outputId;
        final int[][] ingredients;
        Station station;
        boolean shaped;
        int width;
        int height;
        int[][] cells;
        boolean bulk;
        int bulkItem = -1;
        int penaltyBelow;
        GearKind gearKind;
        int quality;
        boolean essential;
        boolean smelter;
        boolean food;
        int outputMaxStack;

        Draft(RecipeDisplayEntry entry, int outputId, int[][] ingredients) {
            this.entry = entry;
            this.outputId = outputId;
            this.ingredients = ingredients;
        }

        RecipeFacts facts(int feedsUseful) {
            return new RecipeFacts(
                    entry.id().index(),
                    outputId,
                    ingredients,
                    shaped,
                    width,
                    height,
                    cells,
                    bulk,
                    bulkItem,
                    penaltyBelow,
                    gearKind,
                    quality,
                    essential,
                    smelter,
                    food,
                    outputMaxStack,
                    feedsUseful
            );
        }
    }
}
