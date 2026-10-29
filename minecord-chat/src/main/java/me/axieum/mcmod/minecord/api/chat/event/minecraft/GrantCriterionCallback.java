package me.axieum.mcmod.minecord.api.chat.event.minecraft;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * A callback for when a player is granted an advancement criterion.
 */
@FunctionalInterface
public interface GrantCriterionCallback
{
    /**
     * Called when a player is granted an advancement criterion.
     */
    Event<GrantCriterionCallback> EVENT =
        EventFactory.createArrayBacked(GrantCriterionCallback.class, callbacks -> (player, advancement, criterion) -> {
            for (GrantCriterionCallback callback : callbacks) {
                callback.onGrantCriterion(player, advancement, criterion);
            }
        });

    /**
     * Called when a player is granted an advancement criterion.
     *
     * @param player      redeeming player
     * @param advancement parent advancement
     * @param criterion   name of the criterion granted
     */
    void onGrantCriterion(ServerPlayer player, Advancement advancement, String criterion);
}
