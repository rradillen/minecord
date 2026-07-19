package me.axieum.mcmod.minecord.mixin.chat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import me.axieum.mcmod.minecord.api.chat.event.minecraft.GrantCriterionCallback;

/**
 * Injects into, and broadcasts any granted advancement criterion.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementTrackerMixin
{
    @Shadow
    private ServerPlayer player;

    /**
     * Broadcasts any granted advancement criterion.
     *
     * @param advancement parent advancement
     * @param criterion   name of the criterion granted
     * @param cir         mixin callback info
     */
    @Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/"
        + "AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    public void grantCriterion(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir)
    {
        GrantCriterionCallback.EVENT.invoker().onGrantCriterion(player, advancement.value(), criterion);
    }
}
