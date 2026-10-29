package me.axieum.mcmod.minecord.impl.chat.callback.minecraft;

import java.util.Map;

import eu.pb4.placeholders.api.ServerPlaceholderContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;

import me.axieum.mcmod.minecord.api.Minecord;
import me.axieum.mcmod.minecord.api.util.PlaceholdersExt;
import me.axieum.mcmod.minecord.api.util.PlaceholdersExt.PlaceholderHandler;
import me.axieum.mcmod.minecord.api.util.StringUtils;
import me.axieum.mcmod.minecord.impl.chat.util.DiscordDispatcher;
import me.axieum.mcmod.minecord.mixin.chat.LivingEntityAccessor;
import static me.axieum.mcmod.minecord.api.util.PlaceholdersExt.string;

/**
 * A listener for when a Minecraft player changes world.
 */
public class PlayerChangeWorldCallback implements ServerEntityLevelChangeEvents.AfterPlayerChange
{
    @Override
    public void afterChangeLevel(ServerPlayer player, ServerLevel origin, ServerLevel dest)
    {
        Minecord.getInstance().getJDA().ifPresent(jda -> {
            final BlockPos lastBlockPos = ((LivingEntityAccessor) player).getLastBlockPos();
            if (lastBlockPos == null) return;

            /*
             * Prepare the message placeholders.
             */

            final ServerPlaceholderContext ctx = ServerPlaceholderContext.of(player);
            final Map<String, PlaceholderHandler> placeholders = Map.of(
                // The name of the world the player entered
                "world", string(StringUtils.getWorldName(dest)),
                // The X coordinate of where the player entered
                "pos_x", string(String.valueOf(player.getBlockX())),
                // The Y coordinate of where the player entered
                "pos_y", string(String.valueOf(player.getBlockY())),
                // The Z coordinate of where the player entered
                "pos_z", string(String.valueOf(player.getBlockZ())),
                // The name of the world the player left
                "origin", string(StringUtils.getWorldName(origin)),
                // The X coordinate of where the player left
                "origin_pos_x", string(String.valueOf(lastBlockPos.getX())),
                // The Y coordinate of where the player left
                "origin_pos_y", string(String.valueOf(lastBlockPos.getY())),
                // The Z coordinate of where the player left
                "origin_pos_z", string(String.valueOf(lastBlockPos.getZ()))
            );

            /*
             * Dispatch the message.
             */

            DiscordDispatcher.embedWithAvatar(
                (embed, entry) -> embed.setDescription(
                    PlaceholdersExt.parseString(entry.discord.teleportNode, ctx, placeholders)
                ),
                entry -> entry.discord.teleport != null && entry.hasWorld(dest),
                player.getStringUUID()
            );
        });
    }
}
