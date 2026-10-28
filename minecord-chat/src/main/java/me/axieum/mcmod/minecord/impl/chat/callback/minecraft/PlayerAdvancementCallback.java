package me.axieum.mcmod.minecord.impl.chat.callback.minecraft;

import java.util.Map;

import eu.pb4.placeholders.api.ServerPlaceholderContext;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.level.ServerPlayer;

import me.axieum.mcmod.minecord.api.Minecord;
import me.axieum.mcmod.minecord.api.chat.event.minecraft.GrantCriterionCallback;
import me.axieum.mcmod.minecord.api.util.PlaceholdersExt;
import me.axieum.mcmod.minecord.api.util.PlaceholdersExt.PlaceholderHandler;
import me.axieum.mcmod.minecord.impl.chat.util.DiscordDispatcher;
import static me.axieum.mcmod.minecord.api.util.PlaceholdersExt.string;

/**
 * A listener for when a Minecraft player is granted an advancement.
 */
public class PlayerAdvancementCallback implements GrantCriterionCallback
{
    @Override
    public void onGrantCriterion(ServerPlayer player, Advancement advancement, String criterion)
    {
        Minecord.getInstance().getJDA().ifPresent(jda -> {
            // Only listen for advancements that should be announced
            final DisplayInfo info = advancement.display().orElse(null);
            if (info == null || !info.announceToChat()) return;

            /*
             * Prepare the message placeholders.
             */

            final ServerPlaceholderContext ctx = ServerPlaceholderContext.of(player);
            final Map<String, PlaceholderHandler> placeholders = Map.of(
                // The title of the advancement
                "title", string(info.title().getString()),
                // A description of the advancement
                "description", string(info.description().getString())
            );

            /*
             * Dispatch the message.
             */

            switch (info.type()) {
                // A player reached an advancement goal
                case GOAL -> DiscordDispatcher.embed(
                    (embed, entry) -> embed.setDescription(
                        PlaceholdersExt.parseString(entry.discord.advancementGoalNode, ctx, placeholders)
                    ),
                    entry -> entry.discord.advancementGoal != null && entry.hasWorld(player.level())
                );

                // A player completed an advancement challenge
                case CHALLENGE -> DiscordDispatcher.embed(
                    (embed, entry) -> embed.setDescription(
                        PlaceholdersExt.parseString(entry.discord.advancementChallengeNode, ctx, placeholders)
                    ),
                    entry -> entry.discord.advancementChallenge != null && entry.hasWorld(player.level())
                );

                // A player unlocked an advancement task
                default -> DiscordDispatcher.embed(
                    (embed, entry) -> embed.setDescription(
                        PlaceholdersExt.parseString(entry.discord.advancementTaskNode, ctx, placeholders)
                    ),
                    entry -> entry.discord.advancementTask != null && entry.hasWorld(player.level())
                );
            }
        });
    }
}
