package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleInitializeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicEmotes;

/**
 * The trainers' emotes start with the battle, read from the packet <b>before</b> Cobblemon's handler runs: on the
 * Cobblemon Academy server DeltaClient cancels that handler for its own battle screen, so nothing after its start is
 * ever reached there (in-game test 2026-10-30: the host on that server saw no emotes). Head injections run lowest
 * priority first: below DeltaClient's (1000).
 */
@Mixin(value = BattleInitializeHandler.class, remap = false, priority = 500)
public abstract class EmoteBattleInitializeHandlerMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleInitializePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"))
	private void showtime$emotes(BattleInitializePacket packet, Minecraft client, CallbackInfo ci) {
		ClassicEmotes.onInitialize(packet);
	}
}
