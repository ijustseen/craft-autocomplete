package dev.ijustseen.craftautocomplete.index;

import dev.ijustseen.craftautocomplete.CraftAutocomplete;
import dev.ijustseen.craftautocomplete.rank.GearKind;
import dev.ijustseen.craftautocomplete.rank.RecipeFacts;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
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
 * Per station: item id to the recipes that accept it, plus how many recipes mention the item.
 * The recipe book is the list the server agreed to place. Every other crafting and furnace recipe
 * comes from the local world, or, on a remote server, from the mod jars installed on this client.
 * Those are placed by slot clicks. Rebuilt when the book list or that recipe source is replaced.
 */
public final class CraftIndex {
    public static final int CANDIDATE_LIMIT = 200;
    public static final int MIN_CRAFTABLE_BEFORE_EXPANDING = 5;
    private static final int COMPRESSION_STACKS = 4;

    private final Map<Station, StationIndex> stations = new EnumMap<>(Station.class);
    /** Furnace inputs whose smelted output goes into a gear recipe: raw iron, not logs. */
    private final HashSet<Integer> smeltsIntoGear = new HashSet<>();
    private List<RecipeCollection> indexedCollections = List.of();
    private Object indexedSource;
    private RegistryAccess jarRegistries;
    private List<RecipeHolder<?>> jarRecipes = List.of();

    public void rebuildIfNeeded(ClientRecipeBook book, ClientLevel level) {
        List<RecipeCollection> collections = book.getCollections();
        Object source = recipeSource(level);
        if (collections == indexedCollections && source == indexedSource) {
            return;
        }
        rebuild(collections, holdersFrom(level, source), SlotDisplayContext.fromLevel(level), level.enabledFeatures());
        indexedCollections = collections;
        indexedSource = source;
    }

    /** Integrated server in a local world; the connection's registries on a remote server. */
    private static Object recipeSource(ClientLevel level) {
        RecipeManager manager = localRecipes();
        return manager != null ? manager : level.registryAccess();
    }

    private List<RecipeHolder<?>> holdersFrom(ClientLevel level, Object source) {
        if (source instanceof RecipeManager manager) {
            return List.copyOf(manager.getRecipes());
        }
        RegistryAccess registries = level.registryAccess();
        if (registries != jarRegistries) {
            jarRecipes = JarRecipes.read(registries);
            jarRegistries = registries;
        }
        return jarRecipes;
    }

    /** The integrated server of a local world, whose recipe manager holds every loaded mod recipe. */
    private static RecipeManager localRecipes() {
        Minecraft client = Minecraft.getInstance();
        if (!client.hasSingleplayerServer()) {
            return null;
        }
        return client.getSingleplayerServer().getRecipeManager();
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
            if (craft.canCraft(contents)) {
                alive.add(craft);
            }
        }
        return alive;
    }

    private void rebuild(
            List<RecipeCollection> collections,
            List<RecipeHolder<?>> holders,
            ContextMap context,
            FeatureFlagSet features
    ) {
        stations.clear();
        smeltsIntoGear.clear();
        int airId = BuiltInRegistries.ITEM.getId(Items.AIR);
        HashSet<Integer> seenDisplays = new HashSet<>();
        HashSet<Long> bookSignatures = new HashSet<>();
        List<Draft> drafts = new ArrayList<>();
        for (RecipeCollection collection : collections) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!seenDisplays.add(entry.id().index())) {
                    continue;
                }
                Draft draft = draft(entry, context, features, airId);
                if (draft != null) {
                    drafts.add(draft);
                    bookSignatures.add(signature(draft));
                }
            }
        }
        int offBook = 0;
        for (RecipeHolder<?> holder : holders) {
            Draft draft = draftWorld(holder, context, features, airId, bookSignatures, seenDisplays);
            if (draft != null) {
                drafts.add(draft);
                offBook++;
            }
        }
        if (offBook > 0) {
            CraftAutocomplete.LOGGER.info("indexed {} recipes the book does not list", offBook);
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
            IndexedCraft craft = new IndexedCraft(draft.entry, draft.facts(feedsUseful), draft.handRecipe);
            StationIndex index = stations.computeIfAbsent(draft.station, ignored -> new StationIndex());
            for (int itemId : mentioned(draft.ingredients)) {
                index.byItem.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(craft);
            }
            if (draft.gearKind != null || draft.essential || draft.food || draft.offBook || feedsUseful > 0) {
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
        describeOutput(draft, result);
        return draft;
    }

    /**
     * A recipe loaded by the local world. Skipped when the book already lists the same grid,
     * and when it is not a crafting or furnace recipe this mod can lay out.
     */
    private static Draft draftWorld(
            RecipeHolder<?> holder,
            ContextMap context,
            FeatureFlagSet features,
            int airId,
            HashSet<Long> bookSignatures,
            HashSet<Integer> seenDisplays
    ) {
        Recipe<?> recipe = holder.value();
        if (recipe.isSpecial() || recipe.placementInfo().isImpossibleToPlace()) {
            return null;
        }
        Station station = stationOf(recipe);
        if (station == null || recipe.display().isEmpty()) {
            return null;
        }
        RecipeDisplay display = recipe.display().getFirst();
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
        Draft draft = fromDisplay(station, display, outputId, context, airId);
        if (draft == null || bookSignatures.contains(signature(draft))) {
            return null;
        }
        int displayId = localId(holder);
        if (!seenDisplays.add(displayId)) {
            return null;
        }
        draft.displayId = displayId;
        draft.station = station;
        draft.offBook = true;
        draft.handRecipe = recipe;
        describeOutput(draft, result);
        return draft;
    }

    private static Station stationOf(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        if (type == RecipeType.CRAFTING) {
            return Station.CRAFTING;
        }
        if (type == RecipeType.SMELTING) {
            return Station.FURNACE;
        }
        if (type == RecipeType.BLASTING) {
            return Station.BLAST_FURNACE;
        }
        if (type == RecipeType.SMOKING) {
            return Station.SMOKER;
        }
        return null;
    }

    /** The grid this mod can ghost and fill, read from the recipe's own display. */
    private static Draft fromDisplay(
            Station station,
            RecipeDisplay display,
            int outputId,
            ContextMap context,
            int airId
    ) {
        if (station == Station.CRAFTING && display instanceof ShapedCraftingRecipeDisplay shaped) {
            List<SlotDisplay> slots = shaped.ingredients();
            if (slots.size() != shaped.width() * shaped.height()) {
                return null;
            }
            int[][] cells = new int[slots.size()][];
            int filled = 0;
            for (int index = 0; index < slots.size(); index++) {
                cells[index] = itemIds(slots.get(index), context, airId);
                if (cells[index].length > 0) {
                    filled++;
                }
            }
            if (filled == 0) {
                return null;
            }
            int[][] ingredients = new int[filled][];
            int cursor = 0;
            for (int[] cell : cells) {
                if (cell.length > 0) {
                    ingredients[cursor++] = cell;
                }
            }
            Draft draft = new Draft(null, outputId, ingredients);
            draft.shaped = true;
            draft.width = shaped.width();
            draft.height = shaped.height();
            draft.cells = cells;
            markBulk(draft);
            return draft;
        }
        if (station == Station.CRAFTING && display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            List<SlotDisplay> slots = shapeless.ingredients();
            if (slots.isEmpty()) {
                return null;
            }
            int[][] ingredients = new int[slots.size()][];
            for (int index = 0; index < slots.size(); index++) {
                ingredients[index] = itemIds(slots.get(index), context, airId);
                if (ingredients[index].length == 0) {
                    return null;
                }
            }
            Draft draft = new Draft(null, outputId, ingredients);
            draft.cells = ingredients;
            markBulk(draft);
            return draft;
        }
        if (station != Station.CRAFTING && display instanceof FurnaceRecipeDisplay furnace) {
            int[] inputs = Arrays.stream(itemIds(furnace.ingredient(), context, airId))
                    .filter(id -> !breaksGear(BuiltInRegistries.ITEM.byId(id)))
                    .toArray();
            if (inputs.length == 0) {
                return null;
            }
            Draft draft = new Draft(null, outputId, new int[][]{inputs});
            draft.cells = draft.ingredients;
            return draft;
        }
        return null;
    }

    private static void markBulk(Draft draft) {
        draft.bulk = sameIngredientNineTimes(draft.ingredients);
        draft.bulkItem = draft.bulk && draft.ingredients[0].length == 1 ? draft.ingredients[0][0] : -1;
        if (draft.bulkItem >= 0) {
            draft.penaltyBelow = COMPRESSION_STACKS * BuiltInRegistries.ITEM.byId(draft.bulkItem).getDefaultMaxStackSize();
        }
    }

    private static void describeOutput(Draft draft, ItemStack result) {
        Item output = result.getItem();
        draft.gearKind = ItemTraits.gearKind(output);
        draft.quality = draft.gearKind == null ? 0 : ItemTraits.quality(output);
        draft.essential = ItemTraits.essential(output);
        draft.smelter = ItemTraits.smelter(output);
        draft.food = result.has(DataComponents.FOOD);
        draft.outputMaxStack = output.getDefaultMaxStackSize();
    }

    private static long signature(Draft draft) {
        long hash = draft.outputId;
        hash = hash * 31 + (draft.shaped ? 1 : 0);
        hash = hash * 31 + draft.width;
        hash = hash * 31 + draft.height;
        hash = hash * 31 + Arrays.deepHashCode(draft.cells);
        return hash;
    }

    /** Negative, and never {@link Integer#MIN_VALUE}, which the controller uses for "nothing selected". */
    private static int localId(RecipeHolder<?> holder) {
        int hash = holder.id().identifier().hashCode();
        if (hash == Integer.MIN_VALUE) {
            return -2;
        }
        return hash < 0 ? hash : -hash - 1;
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
        int displayId = Integer.MIN_VALUE;
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
        boolean offBook;
        Recipe<?> handRecipe;

        Draft(RecipeDisplayEntry entry, int outputId, int[][] ingredients) {
            this.entry = entry;
            this.outputId = outputId;
            this.ingredients = ingredients;
            if (entry != null) {
                displayId = entry.id().index();
            }
        }

        RecipeFacts facts(int feedsUseful) {
            return new RecipeFacts(
                    displayId,
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
                    feedsUseful,
                    offBook
            );
        }
    }
}
