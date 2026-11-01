package com.mystaria.spectacle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.effect.SpawnSnowstormEntityParticleHandler;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.spectacle.move.MoveEffects;

/**
 * Moves redrawn by Spectacle (M1): their Cobblemon particles — sent by the server's action effect, or dispatched
 * locally by Phantasmon for a Ghost battle — are dropped here and handed to {@link MoveEffects}, which draws its own.
 * Read before any other mod's injection (priority 500, as for the battle messages).
 */
@Mixin(value = SpawnSnowstormEntityParticleHandler.class, remap = false, priority = 500)
public abstract class MoveParticleOverrideMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/effect/SpawnSnowstormEntityParticlePacket;Lnet/minecraft/client/Minecraft;)V",
			at = @At("HEAD"), cancellable = true)
	private void spectacle$redrawnMove(SpawnSnowstormEntityParticlePacket packet, Minecraft client, CallbackInfo ci) {
		if (MoveEffects.intercept(packet)) {
			ci.cancel();
		}
	}
}
