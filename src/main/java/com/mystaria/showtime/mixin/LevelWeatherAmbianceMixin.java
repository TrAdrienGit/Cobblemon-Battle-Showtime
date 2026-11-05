package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

import com.mystaria.showtime.ambiance.BattleAmbiance;

/** A battle ambiance's rain and thunder, on this client only: Minecraft's own rain, its sound and its darker sky. */
@Mixin(Level.class)
abstract class LevelWeatherAmbianceMixin {

	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void showtime$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			cir.setReturnValue(BattleAmbiance.rainLevel(cir.getReturnValueF()));
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void showtime$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			cir.setReturnValue(BattleAmbiance.thunderLevel(cir.getReturnValueF()));
		}
	}
}
