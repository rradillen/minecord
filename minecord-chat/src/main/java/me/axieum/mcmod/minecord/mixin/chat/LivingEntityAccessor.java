package me.axieum.mcmod.minecord.mixin.chat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/**
 * Minecraft living entity accessor mixin.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor
{
    /**
     * Retrieves the private last block position of the entity.
     *
     * @return last block position
     */
    @Accessor(value = "lastPos")
    BlockPos getLastBlockPos();
}
