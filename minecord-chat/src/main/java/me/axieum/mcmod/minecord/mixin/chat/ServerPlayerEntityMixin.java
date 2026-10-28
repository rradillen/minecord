package me.axieum.mcmod.minecord.mixin.chat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import me.axieum.mcmod.minecord.api.chat.event.minecraft.EntityDeathEvents;

/**
 * Injects into, and broadcasts any player deaths.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin
{
    /**
     * Broadcasts any player deaths.
     *
     * @param source damage source
     * @param ci     mixin callback info
     */
    @Inject(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;"
                + "broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"
        )
    )
    public void onDeath(DamageSource source, CallbackInfo ci)
    {
        EntityDeathEvents.PLAYER.invoker().onPlayerDeath((ServerPlayer) (Object) this, source);
    }
}
