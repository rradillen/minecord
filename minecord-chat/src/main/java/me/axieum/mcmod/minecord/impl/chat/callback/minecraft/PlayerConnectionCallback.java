package me.axieum.mcmod.minecord.impl.chat.callback.minecraft;

import java.util.Collections;
import java.util.Map;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderHandler;
import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;

import me.axieum.mcmod.minecord.api.Minecord;
import me.axieum.mcmod.minecord.api.util.PlaceholdersExt;
import me.axieum.mcmod.minecord.impl.chat.util.DiscordDispatcher;

/**
 * A listener for when a Minecraft player joins or leaves.
 */
public class PlayerConnectionCallback implements Join, Disconnect
{
    @Override
    public void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server)
    {
        Minecord.getInstance().getJDA().ifPresent(jda -> {
            final ServerPlayer player = handler.player;

            /*
             * Prepare the message placeholders.
             */

            final @Nullable PlaceholderContext ctx = PlaceholderContext.of(player);
            final Map<String, PlaceholderHandler> placeholders = Collections.emptyMap();

            /*
             * Dispatch the message.
             */

            DiscordDispatcher.embedWithAvatar(
                (embed, entry) -> embed.setDescription(
                    PlaceholdersExt.parseString(entry.discord.joinNode, ctx, placeholders)
                ),
                entry -> entry.discord.join != null && entry.hasWorld(player.level()),
                player.getStringUUID()
            );
        });
    }

    @Override
    public void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server)
    {
        Minecord.getInstance().getJDA().ifPresent(jda -> {
            final ServerPlayer player = handler.player;

            /*
             * Prepare the message placeholders.
             */

            final @Nullable PlaceholderContext ctx = PlaceholderContext.of(player);
            final Map<String, PlaceholderHandler> placeholders = Collections.emptyMap();

            /*
             * Dispatch the message.
             */

            DiscordDispatcher.embedWithAvatar(
                (embed, entry) -> embed.setDescription(
                    PlaceholdersExt.parseString(entry.discord.leaveNode, ctx, placeholders)
                ),
                entry -> entry.discord.leave != null && entry.hasWorld(player.level()),
                player.getStringUUID()
            );
        });
    }
}
