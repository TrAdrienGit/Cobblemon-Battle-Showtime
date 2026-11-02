package com.mystaria.spectacle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.animation.PlayPosableAnimationHandler;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;

import net.minecraft.client.Minecraft;

import com.mystaria.spectacle.move.MoveEffects;

/**
 * A Pokémon's move pose (its first animation is the move's id): starts Spectacle's effect for a redrawn move (M2).
 * The pose itself still plays.
 */
@Mixin(value = PlayPosableAnimationHandler.class, remap = false, priority = 500)
public abstract class MoveAnimationMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/animation/PlayPosableAnimationPacket;Lnet/minecraft/client/Minecraft;)V",
			at = @At("HEAD"))
	private void spectacle$moveStarts(PlayPosableAnimationPacket packet, Minecraft client, CallbackInfo ci) {
		MoveEffects.onAnimation(packet);
	}
}
