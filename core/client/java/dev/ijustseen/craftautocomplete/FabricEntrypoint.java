package dev.ijustseen.craftautocomplete;

import net.fabricmc.api.ClientModInitializer;

/** Fabric. On Forge and NeoForge this file is replaced by an empty stub in versions/{mc}/src. */
public class FabricEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CraftAutocomplete.initClient();
    }
}
