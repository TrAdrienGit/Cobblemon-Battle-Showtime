package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;

import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.showtime.battle.BattleShowtime;

/** Cobblemon's battle screen steps aside while a set piece plays ({@link BattleShowtime}). */
@Mixin(BattleGUI.class)
public abstract class BattleGuiShowtimeMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void showtime$hideDuringShowtime(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (BattleShowtime.playing()) {
			ci.cancel();
		}
	}
}
