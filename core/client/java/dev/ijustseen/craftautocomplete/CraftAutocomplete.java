package dev.ijustseen.craftautocomplete;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared code. Written with the Yarn names of the active version and compiled for every loader:
 * {@code fabric/src}, {@code forge/src} and {@code neoforge/src} are symlinks to {@code core/}.
 */
public final class CraftAutocomplete {
    public static final String MOD_ID = "craft-autocomplete";
    /** Forge and NeoForge accept only [a-z0-9_]. */
    public static final String FML_MOD_ID = "craft_autocomplete";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CraftAutocomplete() {}

    public static void initClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        // The active version compiles this file as stored, so inactive branches stay commented out.
        // Stonecutter removes the comment when it builds another branch.
        //? if fabric
        String loader = "fabric";
        //? if forge
        //String loader = "forge";
        //? if neoforge
        //String loader = "neoforge";
        LOGGER.info("Craft Autocomplete ready on {} (in world: {})", loader, client.world != null);
    }
}
