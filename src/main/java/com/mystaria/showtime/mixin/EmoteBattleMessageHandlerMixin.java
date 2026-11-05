package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicEmotes;

/**
 * The battle's messages (moves ordered, winners) for the trainers' emotes, read <b>before</b> Cobblemon's handler:
 * DeltaClient always cancels it on the Cobblemon Academy server (it queues the messages its own way). Injections at the
 * head run in the order their mixins are applied, lowest priority first: below DeltaClient's (1000), this one runs
 * before its cancel (Adrien's test 2026-10-30: at 1500 no attack nor victory was ever seen).
 */
@Mixin(value = BattleMessageHandler.class, remap = false, priority = 500)
public abstract class EmoteBattleMessageHandlerMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMessagePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"))
	private void showtime$emotes(BattleMessagePacket packet, Minecraft client, CallbackInfo ci) {
		ClassicEmotes.onMessages(packet.getMessages());
	}
}
