package dev.ijustseen.craftautocomplete.ui;

import dev.ijustseen.craftautocomplete.learn.PlayerWeights;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.net.URI;

/**
 * One chat line the first time a world is joined. Later visits of the same world stay quiet.
 */
public final class PlayNotice {
    private static final URI DISCORD = URI.create("https://discord.gg/WaTNepbtfJ");

    private PlayNotice() {}

    public static void register(PlayerWeights weights) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (client.player == null || !weights.markAnnounced(worldKey(client))) {
                return;
            }
            Component link = Component.literal(DISCORD.toString()).withStyle(style -> style
                    .withClickEvent(new ClickEvent.OpenUrl(DISCORD))
                    .withUnderlined(true)
                    .withColor(ChatFormatting.AQUA));
            client.player.sendSystemMessage(Component.translatable("chat.craft-autocomplete.welcome", link));
        }));
    }

    private static String worldKey(Minecraft client) {
        ServerData server = client.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isEmpty()) {
            return "mp:" + server.ip;
        }
        if (client.hasSingleplayerServer()) {
            return "sp:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        }
        return "local";
    }
}
