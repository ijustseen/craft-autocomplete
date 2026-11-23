package dev.ijustseen.craftautocomplete;

import dev.ijustseen.craftautocomplete.index.CraftIndex;
import dev.ijustseen.craftautocomplete.learn.PlayerWeights;
import dev.ijustseen.craftautocomplete.recent.RecentItems;
import dev.ijustseen.craftautocomplete.ui.CraftKeys;
import dev.ijustseen.craftautocomplete.ui.PlayNotice;
import dev.ijustseen.craftautocomplete.ui.StationView;
import dev.ijustseen.craftautocomplete.ui.SuggestionController;
import dev.ijustseen.craftautocomplete.ui.WeightCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared code. Written in the names of the active version and compiled for every loader:
 * {@code fabric/src}, {@code forge/src} and {@code neoforge/src} are symlinks to {@code core/}.
 */
public final class CraftAutocomplete {
    public static final String MOD_ID = "craft-autocomplete";
    /** Forge and NeoForge accept only [a-z0-9_]. */
    public static final String FML_MOD_ID = "craft_autocomplete";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CraftAutocomplete() {}

    public static void initClient() {
        Minecraft client = Minecraft.getInstance();
        // The active version compiles this file as stored, so inactive branches stay commented out.
        // Stonecutter removes the comment when it builds another branch.
        //? if fabric
        String loader = "fabric";
        //? if forge
        //String loader = "forge";
        //? if neoforge
        //String loader = "neoforge";
        LOGGER.info("Craft Autocomplete ready on {} (in world: {})", loader, client.level != null);

        RecentItems recentItems = new RecentItems();
        CraftIndex index = new CraftIndex();
        PlayerWeights weights = PlayerWeights.load(FabricLoader.getInstance().getConfigDir());
        SuggestionController suggestions = new SuggestionController(recentItems, index, weights);
        WeightCommands.register(weights);
        PlayNotice.register(weights);
        // Touch the key bindings so they register during client init, before options are built.
        CraftKeys.ACCEPT.getName();
        ClientTickEvents.END_CLIENT_TICK.register(suggestions::onEndTick);
        ScreenEvents.AFTER_INIT.register((minecraft, screen, scaledWidth, scaledHeight) -> {
            if (StationView.of(screen) != null) {
                suggestions.onScreenInit((AbstractRecipeBookScreen<?>) screen);
            }
        });
    }
}
