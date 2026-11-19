package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.mystaria.showtime.classic.LeagueWayCompat;

/**
 * Before LeagueWay (default priority) takes Cobblemon's battle screen for its own intro: with Showtime's intro picked,
 * it is told this battle's intro already played ({@link LeagueWayCompat#beforeBattleScreen}). Priority 500 so this
 * runs first; it never cancels anything.
 */
@Mixin(value = Minecraft.class, priority = 500)
public abstract class MinecraftLeagueWayMixin {

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void showtime$beforeBattleScreen(Screen screen, CallbackInfo ci) {
		if (screen instanceof BattleGUI) {
			LeagueWayCompat.beforeBattleScreen();
		}
	}
}
