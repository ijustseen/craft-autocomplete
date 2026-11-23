package dev.ijustseen.craftautocomplete.ui;

import dev.ijustseen.craftautocomplete.learn.PlayerWeights;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public final class WeightCommands {
    private WeightCommands() {}

    public static void register(PlayerWeights weights) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("craftweights")
                        .then(ClientCommands.literal("export").executes(context -> {
                            Path exported = weights.export();
                            context.getSource().sendFeedback(Component.translatable(
                                    "chat.craft-autocomplete.exported",
                                    exported.toString()
                            ));
                            return 1;
                        }))));
    }
}
