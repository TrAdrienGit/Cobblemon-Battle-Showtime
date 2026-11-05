package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.BattleAmbiance;

/** A battle ambiance's sky, clouds and stars. */
@Mixin(ClientLevel.class)
abstract class ClientLevelAmbianceMixin {

	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void showtime$sky(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> cir) {
		cir.setReturnValue(BattleAmbiance.skyColor(cir.getReturnValue()));
	}

	@Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
	private void showtime$clouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
		cir.setReturnValue(BattleAmbiance.cloudColor(cir.getReturnValue()));
	}

	@Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
	private void showtime$stars(float partialTick, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(BattleAmbiance.starBrightness(cir.getReturnValueF()));
	}
}
