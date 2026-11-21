package dev.ijustseen.craftautocomplete.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class CraftKeys {
    public static final KeyMapping ACCEPT = register("key.craft-autocomplete.accept", InputConstants.KEY_TAB);
    public static final KeyMapping PREVIOUS = register("key.craft-autocomplete.previous", InputConstants.KEY_LBRACKET);
    public static final KeyMapping NEXT = register("key.craft-autocomplete.next", InputConstants.KEY_RBRACKET);

    private CraftKeys() {}

    private static KeyMapping register(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                name,
                InputConstants.Type.KEYBOARD,
                key,
                KeyMapping.Category.INVENTORY
        ));
    }
}
