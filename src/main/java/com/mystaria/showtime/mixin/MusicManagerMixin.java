package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sounds.MusicManager;

import com.mystaria.showtime.audio.BattleMusic;

/** Minecraft's background music steps aside while the battle music plays ({@link BattleMusic}). */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void showtime$holdForBattleMusic(CallbackInfo ci) {
		if (BattleMusic.active()) {
			((MusicManager) (Object) this).stopPlaying();
			ci.cancel();
		}
	}
}
