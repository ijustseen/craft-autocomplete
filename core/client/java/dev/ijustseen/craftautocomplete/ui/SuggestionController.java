package dev.ijustseen.craftautocomplete.ui;

import dev.ijustseen.craftautocomplete.CraftAutocomplete;
import dev.ijustseen.craftautocomplete.index.CraftIndex;
import dev.ijustseen.craftautocomplete.index.IndexedCraft;
import dev.ijustseen.craftautocomplete.index.ItemTraits;
import dev.ijustseen.craftautocomplete.rank.GearKind;
import dev.ijustseen.craftautocomplete.rank.GridMatch;
import dev.ijustseen.craftautocomplete.rank.ScoreBreakdown;
import dev.ijustseen.craftautocomplete.rank.SuggestionRanker;
import dev.ijustseen.craftautocomplete.recent.RecentItems;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows one ghost recipe on a crafting table, the 2x2 inventory grid or a furnace, and places it
 * through the vanilla recipe packet. With items already in the grid it suggests a recipe that
 * contains them; Tab on a finished grid sends the same recipe again, which vanilla answers with
 * one more of every ingredient.
 */
public final class SuggestionController {
    private static final int HIDE_AFTER_PLACE_TICKS = 10;
    private static final int MAIN_INVENTORY_SLOTS = 36;
    /** Two drumsticks down: food then ranks above materials. */
    private static final int HUNGRY_BELOW = 17;

    private final RecentItems recentItems;
    private final CraftIndex index;
    private LocalPlayer trackedPlayer;
    private Map<Integer, Integer> previousTotals = Map.of();
    private Map<Integer, Integer> currentTotals = Map.of();
    private int lastResultItem = -1;
    private int lastCraftItem = -1;
    private long lastCraftTime;
    private long lastFingerprint = Long.MIN_VALUE;
    private int[] lastGrid = new int[0];
    private long lastSecond = Long.MIN_VALUE;
    private int hideTicks;
    private int selectedDisplayId = -1;
    private int selectedIndex;
    private int loggedWinnerId = Integer.MIN_VALUE;
    private boolean acceptHeld;
    private boolean pinned;
    private List<GhostIcon> ghosts = List.of();
    private List<RankedCraft> ranked = List.of();

    public SuggestionController(RecentItems recentItems, CraftIndex index) {
        this.recentItems = recentItems;
        this.index = index;
    }

    public void onScreenInit(AbstractRecipeBookScreen<?> screen) {
        selectedDisplayId = -1;
        selectedIndex = 0;
        pinned = false;
        acceptHeld = false;
        hideTicks = 0;
        lastFingerprint = Long.MIN_VALUE;
        lastGrid = new int[0];
        loggedWinnerId = Integer.MIN_VALUE;
        ghosts = List.of();
        ranked = List.of();
        // Drawn after the GUI texture. Vanilla ghost slots paint a red fill; these do not.
        ScreenEvents.afterForeground(screen).register((current, graphics, mouseX, mouseY, tickProgress) ->
                drawSuggestion(StationView.of(current), graphics, mouseX, mouseY));
        ScreenKeyboardEvents.allowKeyPress(screen).register(this::onKeyPress);
        ScreenKeyboardEvents.afterKeyRelease(screen).register(this::onKeyRelease);
        Minecraft client = Minecraft.getInstance();
        StationView view = StationView.of(screen);
        if (client.player != null && view != null) {
            currentTotals = totals(client.player, view);
            refresh(client, view, currentTotals, System.currentTimeMillis(), true);
        }
    }

    public void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;
        long now = System.currentTimeMillis();
        if (player == null) {
            if (trackedPlayer != null) {
                recentItems.clear();
                trackedPlayer = null;
                previousTotals = Map.of();
                lastResultItem = -1;
                lastCraftItem = -1;
                lastCraftTime = 0L;
            }
            return;
        }
        if (player != trackedPlayer) {
            recentItems.clear();
            trackedPlayer = player;
            previousTotals = Map.of();
            lastResultItem = -1;
            lastCraftItem = -1;
            lastCraftTime = 0L;
            lastFingerprint = Long.MIN_VALUE;
        }
        StationView view = StationView.of(client.gui.screen());
        // Grid and fuel items count as held, so moving them to and from the inventory is not a pickup.
        Map<Integer, Integer> totals = totals(player, view);
        if (view != null) {
            currentTotals = totals;
            noteResultTaken(view, totals, now);
            if (hideTicks > 0) {
                hideTicks--;
            }
        } else {
            lastResultItem = -1;
        }
        recentItems.observe(totals, now);
        previousTotals = totals;
        if (view != null) {
            refresh(client, view, totals, now, false);
        }
    }

    private boolean onKeyPress(Screen screen, KeyEvent event) {
        StationView view = StationView.of(screen);
        if (view == null || editing(screen) || ranked.isEmpty() || view.coveredByRecipeBook()) {
            return true;
        }
        if (CraftKeys.ACCEPT.matches(event)) {
            if (acceptHeld) {
                return false;
            }
            acceptHeld = true;
            place(view, event.hasShiftDown());
            return false;
        }
        if (CraftKeys.PREVIOUS.matches(event)) {
            cycle(view, -1);
            return false;
        }
        if (CraftKeys.NEXT.matches(event)) {
            cycle(view, 1);
            return false;
        }
        return true;
    }

    private void onKeyRelease(Screen screen, KeyEvent event) {
        if (CraftKeys.ACCEPT.matches(event)) {
            acceptHeld = false;
        }
    }

    private void place(StationView view, boolean useMaxItems) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null || ranked.isEmpty()) {
            return;
        }
        RankedCraft selected = ranked.get(selectedIndex);
        IndexedCraft craft = selected.craft();
        AbstractContainerMenu menu = view.menu();
        int itemsToSmelt = view.fuel() == null ? 0 : itemsAfterPlace(view, craft, useMaxItems);
        // The server places only from the inventory, so the cursor stack has to go there first.
        stashCarried(client, menu);
        client.gameMode.handlePlaceRecipe(menu.containerId, craft.entry().id(), useMaxItems);
        if (view.fuel() != null && !view.fuel().hasItem()) {
            Slot fuelSource = FurnaceFuel.choose(menu, craft.facts().ingredients()[0]);
            if (fuelSource != null) {
                FurnaceFuel.fill(client, menu, view.fuel(), fuelSource, itemsToSmelt);
            }
        }
        // Keep this recipe selected once it lands in the grid, so the next Tab adds to it.
        selectedDisplayId = craft.facts().displayId();
        pinned = true;
        hideTicks = HIDE_AFTER_PLACE_TICKS;
    }

    /** How many items the furnace input will hold once the server has placed the recipe. */
    private int itemsAfterPlace(StationView view, IndexedCraft craft, boolean useMaxItems) {
        ItemStack existing = view.inputs().getFirst().getItem();
        if (!useMaxItems) {
            return existing.getCount() + 1;
        }
        int owned = 0;
        for (int itemId : craft.facts().ingredients()[0]) {
            owned += currentTotals.getOrDefault(itemId, 0);
        }
        int maxStack = existing.isEmpty() ? 64 : existing.getMaxStackSize();
        return Math.min(maxStack, owned);
    }

    /** Clicks the cursor stack into matching inventory stacks first, then into an empty slot. */
    private static void stashCarried(Minecraft client, AbstractContainerMenu menu) {
        for (int guard = 0; guard < menu.slots.size() && !menu.getCarried().isEmpty(); guard++) {
            Slot target = stashTarget(menu, menu.getCarried());
            if (target == null) {
                return;
            }
            client.gameMode.handleContainerInput(menu.containerId, target.index, 0, ContainerInput.PICKUP, client.player);
        }
    }

    private static Slot stashTarget(AbstractContainerMenu menu, ItemStack carried) {
        Slot empty = null;
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)
                    || slot.getContainerSlot() >= MAIN_INVENTORY_SLOTS
                    || !slot.mayPlace(carried)) {
                continue;
            }
            ItemStack held = slot.getItem();
            if (held.isEmpty()) {
                if (empty == null) {
                    empty = slot;
                }
            } else if (ItemStack.isSameItemSameComponents(held, carried) && held.getCount() < slot.getMaxStackSize(held)) {
                return slot;
            }
        }
        return empty;
    }

    private void cycle(StationView view, int direction) {
        selectedIndex = Math.floorMod(selectedIndex + direction, ranked.size());
        selectedDisplayId = ranked.get(selectedIndex).craft().facts().displayId();
        pinned = true;
        ghosts = layout(view, ranked.get(selectedIndex));
    }

    private void refresh(Minecraft client, StationView view, Map<Integer, Integer> totals, long now, boolean force) {
        if (client.level == null) {
            return;
        }
        index.rebuildIfNeeded(client.player.getRecipeBook(), client.level);
        int[] grid = gridItems(view);
        int carried = itemId(view.menu().getCarried());
        int fuel = view.fuel() == null ? -1 : itemId(view.fuel().getItem());
        long fingerprint = ((fingerprint(totals) * 31L + Arrays.hashCode(grid)) * 31L + carried) * 31L + fuel;
        long second = now / 1000L;
        if (!Arrays.equals(grid, lastGrid) && hideTicks == 0) {
            // The player changed the grid by hand: follow the new best guess, not an old pick.
            pinned = false;
        }
        lastGrid = grid;
        if (fingerprint != lastFingerprint) {
            // The server answered the last Tab, so the ghost can show what is still missing.
            hideTicks = 0;
        }
        boolean changed = force || fingerprint != lastFingerprint || second != lastSecond;
        lastFingerprint = fingerprint;
        lastSecond = second;
        if (changed) {
            recompute(client, view, totals, grid, now);
        }
    }

    private void recompute(Minecraft client, StationView view, Map<Integer, Integer> totals, int[] grid, long now) {
        AbstractContainerMenu menu = view.menu();
        StackedItemContents contents = new StackedItemContents();
        client.player.getInventory().fillStackedContents(contents);
        view.menu().fillCraftSlotsStackedContents(contents);
        ItemStack carriedStack = menu.getCarried();
        if (!carriedStack.isEmpty()) {
            contents.accountStack(carriedStack);
        }
        int carried = itemId(carriedStack);
        int[] placed = Arrays.stream(grid).filter(id -> id >= 0).distinct().toArray();
        boolean partial = placed.length > 0;
        List<IndexedCraft> pool = partial
                ? index.containing(view.station(), placed)
                : index.craftable(view.station(), contents, recentItems.newestFirst(now), carried, totals);
        List<IndexedCraft> crafts = new ArrayList<>();
        List<SuggestionRanker.Candidate> candidates = new ArrayList<>();
        for (IndexedCraft craft : pool) {
            GridMatch match = GridMatch.of(craft.facts(), grid, view.gridWidth(), view.gridHeight());
            if (match == null || (partial && !craft.entry().canCraft(contents))) {
                continue;
            }
            crafts.add(craft);
            candidates.add(new SuggestionRanker.Candidate(craft.facts(), match));
        }
        SuggestionRanker.Context context = new SuggestionRanker.Context(
                totals,
                recentItems.timestamps(now),
                bestQuality(totals),
                index.needsSmelting(totals),
                client.player.getFoodData().getFoodLevel() < HUNGRY_BELOW,
                carried,
                lastCraftItem,
                lastCraftTime,
                now,
                partial
        );
        List<RankedCraft> next = new ArrayList<>();
        for (SuggestionRanker.Scored scored : SuggestionRanker.rank(candidates, context)) {
            next.add(new RankedCraft(crafts.get(scored.index()), candidates.get(scored.index()).match(), scored.score()));
        }
        ranked = next;
        int keptId = pinned ? selectedDisplayId : -1;
        selectedIndex = 0;
        selectedDisplayId = ranked.isEmpty() ? -1 : ranked.get(0).craft().facts().displayId();
        if (keptId >= 0) {
            for (int position = 0; position < ranked.size(); position++) {
                if (ranked.get(position).craft().facts().displayId() == keptId) {
                    selectedIndex = position;
                    selectedDisplayId = keptId;
                    break;
                }
            }
            if (selectedDisplayId != keptId) {
                pinned = false;
            }
        }
        ghosts = ranked.isEmpty() ? List.of() : layout(view, ranked.get(selectedIndex));
        logWinner(view, ranked.isEmpty() ? null : ranked.get(0));
    }

    private List<GhostIcon> layout(StationView view, RankedCraft craft) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return List.of();
        }
        List<GhostIcon> icons = new ArrayList<>();
        Slot resultSlot = view.result();
        if (!resultSlot.hasItem()) {
            ItemStack result = craft.craft().entry().display().result()
                    .resolveForFirstStack(SlotDisplayContext.fromLevel(client.level));
            if (!result.isEmpty()) {
                icons.add(new GhostIcon(resultSlot.x, resultSlot.y, result, true));
            }
        }
        List<Slot> inputs = view.inputs();
        int[][] missing = craft.match().missing();
        for (int slot = 0; slot < missing.length && slot < inputs.size(); slot++) {
            if (missing[slot] == null) {
                continue;
            }
            Slot input = inputs.get(slot);
            icons.add(new GhostIcon(input.x, input.y, shownStack(missing[slot]), false));
        }
        Slot fuel = view.fuel();
        if (fuel != null && !fuel.hasItem()) {
            Slot source = FurnaceFuel.choose(view.menu(), craft.craft().facts().ingredients()[0]);
            if (source != null) {
                icons.add(new GhostIcon(fuel.x, fuel.y, source.getItem().copyWithCount(1), false));
            }
        }
        return icons;
    }

    /** Prefer the variant the player holds, so a tag does not show an item they lack. */
    private ItemStack shownStack(int[] acceptedItems) {
        int shown = acceptedItems[0];
        for (int itemId : acceptedItems) {
            if (currentTotals.getOrDefault(itemId, 0) > 0) {
                shown = itemId;
                break;
            }
        }
        return new ItemStack(BuiltInRegistries.ITEM.byId(shown));
    }

    private void drawSuggestion(StationView view, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (view == null || ranked.isEmpty() || hideTicks > 0 || view.coveredByRecipeBook()) {
            return;
        }
        AbstractRecipeBookScreen<?> screen = view.screen();
        Minecraft client = Minecraft.getInstance();
        GhostFade.beginFrame();
        for (GhostIcon icon : ghosts) {
            int x = screen.leftPos + icon.x();
            int y = screen.topPos + icon.y();
            GhostFade.captureNext();
            graphics.fakeItem(icon.stack(), x, y);
            GhostFade.cancelCapture();
            if (!icon.result()) {
                continue;
            }
            graphics.itemDecorations(screen.getFont(), icon.stack(), x, y);
            if (mouseX < x || mouseY < y || mouseX >= x + 16 || mouseY >= y + 16) {
                continue;
            }
            graphics.setComponentTooltipForNextFrame(
                    screen.getFont(),
                    Screen.getTooltipFromItem(client, icon.stack()),
                    mouseX,
                    mouseY
            );
        }
        TabHint.draw(view, graphics);
    }

    private void noteResultTaken(StationView view, Map<Integer, Integer> totals, long now) {
        if (lastResultItem >= 0 && count(totals, lastResultItem) > count(previousTotals, lastResultItem)) {
            lastCraftItem = lastResultItem;
            lastCraftTime = now;
        }
        lastResultItem = itemId(view.result().getItem());
    }

    private void logWinner(StationView view, RankedCraft winner) {
        int id = winner == null ? -1 : winner.craft().facts().displayId();
        if (id == loggedWinnerId) {
            return;
        }
        loggedWinnerId = id;
        if (winner == null) {
            CraftAutocomplete.LOGGER.info("suggestion {} none", view.station());
            return;
        }
        ScoreBreakdown score = winner.score();
        Item item = BuiltInRegistries.ITEM.byId(winner.craft().facts().outputItem());
        CraftAutocomplete.LOGGER.info(
                "suggestion {} {} id={} tier={} within={} recency={} fill={} legacy={} of={}",
                view.station(),
                BuiltInRegistries.ITEM.getKey(item),
                id,
                score.tier(),
                format(score.within()),
                format(score.recency()),
                format(score.fill()),
                format(score.legacy()),
                ranked.size()
        );
    }

    private static Map<GearKind, Integer> bestQuality(Map<Integer, Integer> totals) {
        Map<GearKind, Integer> best = new EnumMap<>(GearKind.class);
        for (Map.Entry<Integer, Integer> entry : totals.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.byId(entry.getKey());
            GearKind kind = ItemTraits.gearKind(item);
            if (kind != null) {
                best.merge(kind, ItemTraits.quality(item), Math::max);
            }
        }
        return best;
    }

    private static boolean editing(Screen screen) {
        GuiEventListener focused = screen.getFocused();
        for (int guard = 0; focused != null && guard < 8; guard++) {
            if (focused instanceof EditBox box && box.isFocused() && box.visible) {
                return true;
            }
            if (!(focused instanceof ContainerEventHandler handler)) {
                return false;
            }
            GuiEventListener inner = handler.getFocused();
            if (inner == null || inner == focused) {
                return false;
            }
            focused = inner;
        }
        return false;
    }

    private static int itemId(ItemStack stack) {
        return stack.isEmpty() ? -1 : BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    private static int[] gridItems(StationView view) {
        List<Slot> slots = view.inputs();
        int[] grid = new int[slots.size()];
        for (int slot = 0; slot < grid.length; slot++) {
            grid[slot] = itemId(slots.get(slot).getItem());
        }
        return grid;
    }

    private static Map<Integer, Integer> totals(LocalPlayer player, StationView view) {
        Map<Integer, Integer> totals = new HashMap<>();
        int airId = BuiltInRegistries.ITEM.getId(Items.AIR);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            add(totals, inventory.getItem(slot), airId);
        }
        add(totals, player.containerMenu.getCarried(), airId);
        if (view != null) {
            for (Slot slot : view.heldSlots()) {
                add(totals, slot.getItem(), airId);
            }
        }
        return totals;
    }

    private static void add(Map<Integer, Integer> totals, ItemStack stack, int airId) {
        if (stack.isEmpty()) {
            return;
        }
        int id = BuiltInRegistries.ITEM.getId(stack.getItem());
        if (id == airId) {
            return;
        }
        totals.merge(id, stack.getCount(), Integer::sum);
    }

    private static int count(Map<Integer, Integer> totals, int itemId) {
        return totals.getOrDefault(itemId, 0);
    }

    private static long fingerprint(Map<Integer, Integer> totals) {
        int[] keys = totals.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        long hash = 1L;
        for (int key : keys) {
            hash = hash * 31L + key;
            hash = hash * 31L + totals.get(key);
        }
        return hash;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record RankedCraft(IndexedCraft craft, GridMatch match, ScoreBreakdown score) {}

    private record GhostIcon(int x, int y, ItemStack stack, boolean result) {}
}
