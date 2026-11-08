package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleInitializeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicBattles;

/** A Cobblemon battle starts: staged unless wild ({@link ClassicBattles}), once Cobblemon's own handler has run. */
@Mixin(value = BattleInitializeHandler.class, remap = false, priority = 500)
public abstract class ClassicBattleInitializeHandlerMixin {

	/**
	 * Remembered first: Cobblemon Delta's client cancels this handler at times, and then its end, below, never runs
	 * (audit 2026-11-08). {@code ClassicBattles.tick} stages the battle then.
	 */
	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleInitializePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"))
	private void showtime$remember(BattleInitializePacket packet, Minecraft client, CallbackInfo ci) {
		ClassicBattles.initializing(packet);
	}


	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleInitializePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
	private void showtime$stage(BattleInitializePacket packet, Minecraft client, CallbackInfo ci) {
		ClassicBattles.onInitialize(packet);
	}
}
