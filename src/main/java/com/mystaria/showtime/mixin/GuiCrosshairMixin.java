package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.showtime.battle.BattleCinematic;

/**
 * No crosshair in the middle of the screen while Showtime films (2026-11-10): the battle camera, an intro, a set piece.
 * It only makes sense when the player aims with their own view.
 */
@Mixin(Gui.class)
public abstract class GuiCrosshairMixin {

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void showtime$noCrosshairOnFilm(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (BattleCinematic.cameraTaken()) {
			ci.cancel();
		}
	}
}
