package dev.ijustseen.craftautocomplete;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge only. A file under versions/ replaces core at the same path, and this path is absent from core. */
@Mod(CraftAutocomplete.FML_MOD_ID)
public class ForgeEntrypoint {
    public ForgeEntrypoint() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        CraftAutocomplete.initClient();
    }
}
