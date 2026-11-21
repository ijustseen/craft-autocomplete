package dev.ijustseen.craftautocomplete;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = CraftAutocomplete.FML_MOD_ID, dist = Dist.CLIENT)
public class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modBus) {
        CraftAutocomplete.initClient();
    }
}
