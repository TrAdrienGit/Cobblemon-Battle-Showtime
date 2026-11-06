package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.network.syncher.EntityDataAccessor;

import com.mystaria.showtime.classic.BallSignals;

/** A Pokémon's beam mode changes: thrown or called back ({@link BallSignals}). */
@Mixin(value = PokemonClientDelegate.class, remap = false)
public abstract class PokemonClientDelegateBallMixin {

	@Inject(method = "onSyncedDataUpdated", at = @At("RETURN"))
	private void showtime$ball(EntityDataAccessor<?> data, CallbackInfo ci) {
		if (data != PokemonEntity.getBEAM_MODE()) {
			return;
		}
		try {
			BallSignals.onBeamMode(((PokemonClientDelegate) (Object) this).getCurrentEntity());
		} catch (kotlin.UninitializedPropertyAccessException notYet) {
			// The delegate has no entity yet: Cobblemon's own handler skips this update too.
		}
	}
}
